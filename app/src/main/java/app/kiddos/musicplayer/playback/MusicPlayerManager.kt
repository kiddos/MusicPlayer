package app.kiddos.musicplayer.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
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
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null

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
        releasePlayer()
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
                    mp.start()
                    _isPlaying.value = true
                    _durationMs.value = mp.duration.toLong().coerceAtLeast(song.durationMs)
                    startPositionTicker()
                    MusicService.start(appContext)
                }
                setOnCompletionListener {
                    handleSongCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    _errorMessage.value = appContext.getString(app.kiddos.musicplayer.R.string.playback_error, what, extra)
                    _isPlaying.value = false
                    stopPositionTicker()
                    false
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = appContext.getString(app.kiddos.musicplayer.R.string.unable_to_play, e.localizedMessage ?: "")
            _isPlaying.value = false
        }
    }

    fun playPause() {
        val player = mediaPlayer
        if (player != null) {
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                stopPositionTicker()
                MusicService.update(appContext)
            } else {
                player.start()
                _isPlaying.value = true
                startPositionTicker()
                MusicService.update(appContext)
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
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopPositionTicker()
                MusicService.update(appContext)
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _isPlaying.value = true
                startPositionTicker()
                MusicService.update(appContext)
            }
        }
    }

    fun next() {
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
        val player = mediaPlayer
        if (player != null && player.currentPosition > 3000) {
            // If played for more than 3 seconds, restart current track
            seekTo(0)
            return
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
        mediaPlayer?.let {
            val target = positionMs.coerceIn(0L, it.duration.toLong().coerceAtLeast(1L)).toInt()
            it.seekTo(target)
            _currentPositionMs.value = target.toLong()
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
                val list = _playlist.value
                val current = _currentSong.value
                val currentIndex = list.indexOfFirst { it.id == current?.id }
                if (currentIndex != -1 && currentIndex + 1 < list.size) {
                    playSong(list[currentIndex + 1])
                } else {
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
                    if (player.isPlaying) {
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
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            } catch (ignored: Exception) {
            }
        }
        mediaPlayer = null
    }

    fun stopAndRelease() {
        releasePlayer()
        _isPlaying.value = false
        _currentSong.value = null
        _currentPositionMs.value = 0L
        MusicService.stop(appContext)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
