package app.kiddos.musicplayer.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.Log
import app.kiddos.musicplayer.model.AudioFile
import app.kiddos.musicplayer.model.RepeatMode
import app.kiddos.musicplayer.service.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class MusicPlayerManager private constructor(private val appContext: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isPreparing = false
    private var isPrepared = false
    private var playWhenReady = false
    private var pendingSeekPositionMs: Long? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null
    private var resumeOnFocusGain = false

    private val audioFocusHelper = AudioFocusHelper(
        context = appContext,
        onFocusLost = {
            resumeOnFocusGain = false
            pause()
        },
        onFocusLostTransient = {
            if (_isPlaying.value) {
                resumeOnFocusGain = true
                pauseInternal()
            }
        },
        onFocusLostTransientCanDuck = {
            mediaPlayer?.setVolume(0.2f, 0.2f)
        },
        onFocusGained = {
            mediaPlayer?.setVolume(1.0f, 1.0f)
            if (resumeOnFocusGain) {
                resumeOnFocusGain = false
                resume()
            }
        }
    )

    private val _currentSong = MutableStateFlow<AudioFile?>(null)
    val currentSong: StateFlow<AudioFile?> = _currentSong.asStateFlow()

    private val _playlist = MutableStateFlow<List<AudioFile>>(emptyList())
    val playlist: StateFlow<List<AudioFile>> = _playlist.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var lastNavTimestamp = 0L

    companion object {
        @Volatile
        private var instance: MusicPlayerManager? = null

        fun getInstance(context: Context): MusicPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun playSong(song: AudioFile, newPlaylist: List<AudioFile> = _playlist.value) {
        if (newPlaylist.isNotEmpty()) {
            _playlist.value = newPlaylist
        }
        _currentSong.value = song
        startPlayback(song)
    }

    private fun startPlayback(song: AudioFile) {
        if (!audioFocusHelper.requestAudioFocus()) {
            return
        }
        releasePlayer()

        isPreparing = true
        isPrepared = false
        playWhenReady = true
        _isPlaying.value = true

        try {
            val player = MediaPlayer().apply {
                setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (song.uri != null) {
                    setDataSource(appContext, song.uri)
                } else {
                    setDataSource(song.path)
                }
                setOnPreparedListener { mp ->
                    isPreparing = false
                    isPrepared = true

                    if (mediaPlayer !== mp) return@setOnPreparedListener

                    mp.setVolume(1.0f, 1.0f)
                    _durationMs.value = mp.duration.toLong().coerceAtLeast(song.durationMs)

                    pendingSeekPositionMs?.let { seekPos ->
                        val target = seekPos.coerceIn(0L, mp.duration.toLong().coerceAtLeast(1L)).toInt()
                        mp.seekTo(target)
                        _currentPositionMs.value = target.toLong()
                        pendingSeekPositionMs = null
                    }

                    if (playWhenReady) {
                        mp.start()
                        _isPlaying.value = true
                        startPositionTicker()
                        MusicService.start(appContext)
                    } else {
                        _isPlaying.value = false
                        stopPositionTicker()
                        MusicService.update(appContext)
                    }
                }
                setOnCompletionListener {
                    handleSongCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    isPreparing = false
                    isPrepared = false
                    playWhenReady = false
                    _errorMessage.value = appContext.getString(app.kiddos.musicplayer.R.string.playback_error, what, extra)
                    _isPlaying.value = false
                    stopPositionTicker()
                    true // Return true to indicate error handled and prevent OnCompletionListener from firing
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            isPreparing = false
            isPrepared = false
            playWhenReady = false
            _errorMessage.value = appContext.getString(app.kiddos.musicplayer.R.string.unable_to_play, e.localizedMessage ?: "")
            _isPlaying.value = false
        }
    }

    fun playPause() {
        if (isPreparing) {
            playWhenReady = !playWhenReady
            _isPlaying.value = playWhenReady
            return
        }

        val player = mediaPlayer
        if (player != null && isPrepared) {
            val isCurrentlyPlaying = try { player.isPlaying } catch (e: Exception) { false }
            if (isCurrentlyPlaying) {
                pause()
            } else {
                resume()
            }
        } else {
            val song = _currentSong.value
            if (song != null) {
                startPlayback(song)
            } else if (_playlist.value.isNotEmpty()) {
                playSong(_playlist.value.first())
            }
        }
    }

    fun pause() {
        resumeOnFocusGain = false
        audioFocusHelper.abandonAudioFocus()
        pauseInternal()
    }

    private fun pauseInternal() {
        if (isPreparing) {
            playWhenReady = false
            _isPlaying.value = false
            return
        }
        mediaPlayer?.let { player ->
            val isCurrentlyPlaying = try { player.isPlaying } catch (e: Exception) { false }
            if (isPrepared && isCurrentlyPlaying) {
                player.pause()
                _isPlaying.value = false
                stopPositionTicker()
                MusicService.update(appContext)
            }
        }
    }

    fun resume() {
        if (!audioFocusHelper.requestAudioFocus()) return
        if (isPreparing) {
            playWhenReady = true
            _isPlaying.value = true
            return
        }
        mediaPlayer?.let { player ->
            val isCurrentlyPlaying = try { player.isPlaying } catch (e: Exception) { false }
            if (isPrepared && !isCurrentlyPlaying) {
                player.setVolume(1.0f, 1.0f)
                player.start()
                _isPlaying.value = true
                startPositionTicker()
                MusicService.update(appContext)
            }
        }
    }

    fun next() {
        val now = System.nanoTime() / 1_000_000L
        if (now - lastNavTimestamp < 500L) return
        lastNavTimestamp = now

        val list = _playlist.value
        val current = _currentSong.value
        if (list.isEmpty()) return

        if (_isShuffle.value && list.size > 1) {
            var nextIndex = Random.nextInt(list.size)
            val currentIndex = list.indexOfFirst { it.id == current?.id }
            if (nextIndex == currentIndex) {
                nextIndex = (nextIndex + 1) % list.size
            }
            playSong(list[nextIndex])
            return
        }

        val currentIndex = list.indexOfFirst { it.id == current?.id }
        if (currentIndex == -1) {
            playSong(list.first())
            return
        }

        if (currentIndex + 1 < list.size) {
            playSong(list[currentIndex + 1])
        } else {
            // At the end of playlist
            when (_repeatMode.value) {
                RepeatMode.ALL, RepeatMode.ONE -> playSong(list.first())
                RepeatMode.OFF -> {
                    seekTo(0)
                    pause()
                }
            }
        }
    }

    fun previous() {
        val now = System.nanoTime() / 1_000_000L
        if (now - lastNavTimestamp < 500L) return
        lastNavTimestamp = now

        val player = mediaPlayer
        if (player != null && isPrepared) {
            val curPos = try { player.currentPosition } catch (e: Exception) { 0 }
            if (curPos > 3000) {
                // If played for more than 3 seconds, restart current track
                seekTo(0)
                return
            }
        }

        val list = _playlist.value
        val current = _currentSong.value
        if (list.isEmpty()) return

        val currentIndex = list.indexOfFirst { it.id == current?.id }
        if (currentIndex <= 0) {
            if (_repeatMode.value == RepeatMode.ALL && list.isNotEmpty()) {
                playSong(list.last())
            } else {
                seekTo(0)
            }
        } else {
            playSong(list[currentIndex - 1])
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = _repeatMode.value.next()
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun seekTo(positionMs: Long) {
        if (isPreparing) {
            pendingSeekPositionMs = positionMs
            _currentPositionMs.value = positionMs
            return
        }
        mediaPlayer?.let { player ->
            if (isPrepared) {
                val target = positionMs.coerceIn(0L, player.duration.toLong().coerceAtLeast(1L)).toInt()
                player.seekTo(target)
                _currentPositionMs.value = target.toLong()
            }
        }
    }

    private fun handleSongCompletion() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentSong.value?.let { startPlayback(it) }
            }
            RepeatMode.ALL -> {
                next()
            }
            RepeatMode.OFF -> {
                Log.d("MusicPlayerManager", "Song completed in OFF repeat mode")
                val list = _playlist.value
                val current = _currentSong.value
                val currentIndex = list.indexOfFirst { it.id == current?.id }
                if (currentIndex != -1 && currentIndex + 1 < list.size) {
                    playSong(list[currentIndex + 1])
                } else {
                    audioFocusHelper.abandonAudioFocus()
                    _isPlaying.value = false
                    stopPositionTicker()
                    seekTo(0)
                    MusicService.update(appContext)
                }
            }
        }
    }

    private fun startPositionTicker() {
        stopPositionTicker()
        tickerJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    val isPlaying = try { player.isPlaying } catch (e: Exception) { false }
                    if (isPlaying) {
                        _currentPositionMs.value = player.currentPosition.toLong()
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopPositionTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun releasePlayer() {
        stopPositionTicker()
        isPreparing = false
        isPrepared = false
        playWhenReady = false
        pendingSeekPositionMs = null

        mediaPlayer?.let { player ->
            try {
                player.setOnPreparedListener(null)
                player.setOnCompletionListener(null)
                player.setOnErrorListener(null)
                val isCurrentlyPlaying = try { player.isPlaying } catch (e: Exception) { false }
                if (isCurrentlyPlaying) player.stop()
                player.reset()
                player.release()
            } catch (ignored: Exception) {
            }
        }
        mediaPlayer = null
    }

    fun stopAndRelease() {
        if (mediaPlayer == null && !_isPlaying.value && _currentSong.value == null) {
            return
        }
        resumeOnFocusGain = false
        audioFocusHelper.abandonAudioFocus()
        releasePlayer()
        lastNavTimestamp = 0L
        _isPlaying.value = false
        _currentSong.value = null
        _currentPositionMs.value = 0L
        MusicService.stop(appContext)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
