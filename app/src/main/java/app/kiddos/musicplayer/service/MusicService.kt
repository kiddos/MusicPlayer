package app.kiddos.musicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import app.kiddos.musicplayer.MainActivity
import app.kiddos.musicplayer.R
import app.kiddos.musicplayer.model.MusicFolder
import app.kiddos.musicplayer.playback.MediaButtonHandler
import app.kiddos.musicplayer.playback.MusicPlayerManager
import app.kiddos.musicplayer.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MusicService : Service() {

    private val playerManager by lazy { MusicPlayerManager.getInstance(applicationContext) }
    private var isNoisyReceiverRegistered = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var mediaSession: MediaSessionCompat

    private val mediaButtonHandler by lazy {
        MediaButtonHandler(
            scope = serviceScope,
            onPlay = { playerManager.resume() },
            onPause = { playerManager.pause() },
            onPlayPause = { playerManager.playPause() },
            onNext = { playerManager.next() },
            onPrevious = { playerManager.previous() },
            onStop = {
                playerManager.stopAndRelease()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            },
            onFastForward = {
                val newPos = playerManager.currentPositionMs.value + 10000L
                playerManager.seekTo(newPos)
            },
            onRewind = {
                val newPos = (playerManager.currentPositionMs.value - 10000L).coerceAtLeast(0L)
                playerManager.seekTo(newPos)
            }
        )
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            playerManager.resume()
        }

        override fun onPause() {
            playerManager.pause()
        }

        override fun onSkipToNext() {
            mediaButtonHandler.handleNext()
        }

        override fun onSkipToPrevious() {
            mediaButtonHandler.handlePrevious()
        }

        override fun onStop() {
            playerManager.stopAndRelease()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        override fun onSeekTo(pos: Long) {
            playerManager.seekTo(pos)
        }

        override fun onFastForward() {
            val newPos = playerManager.currentPositionMs.value + 10000L
            playerManager.seekTo(newPos)
        }

        override fun onRewind() {
            val newPos = (playerManager.currentPositionMs.value - 10000L).coerceAtLeast(0L)
            playerManager.seekTo(newPos)
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (mediaButtonHandler.handleKeyEvent(keyEvent)) {
                return true
            }
            return super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getSavedLanguage(newBase)
        val localized = LocaleHelper.createLocalizedContext(newBase, lang)
        super.attachBaseContext(localized)
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                playerManager.pause()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "app.kiddos.musicplayer.ACTION_START"
        const val ACTION_UPDATE = "app.kiddos.musicplayer.ACTION_UPDATE"
        const val ACTION_PLAY_PAUSE = "app.kiddos.musicplayer.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "app.kiddos.musicplayer.ACTION_NEXT"
        const val ACTION_PREV = "app.kiddos.musicplayer.ACTION_PREV"
        const val ACTION_STOP = "app.kiddos.musicplayer.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_UPDATE
            }
            try {
                context.startService(intent)
            } catch (ignored: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, MusicService::class.java))
            } catch (ignored: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNoisyReceiver()

        val mbrComponent = ComponentName(this, MediaButtonReceiver::class.java)
        mediaSession = MediaSessionCompat(this, "MusicService", mbrComponent, null).apply {
            @Suppress("DEPRECATION")
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = mbrComponent
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this@MusicService,
                0,
                mediaButtonIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setMediaButtonReceiver(pendingIntent)
            setCallback(mediaSessionCallback)
            isActive = true
        }

        // Keep media session state and notification synced with player manager
        serviceScope.launch {
            combine(playerManager.isPlaying, playerManager.currentSong) { isPlaying, currentSong ->
                updatePlaybackState()
                updateMetadata()
                if (currentSong != null) {
                    val notification = buildNotification()
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, notification)
                }
            }.collect()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playerManager.playPause()
            ACTION_NEXT -> mediaButtonHandler.handleNext()
            ACTION_PREV -> mediaButtonHandler.handlePrevious()
            ACTION_STOP -> {
                playerManager.stopAndRelease()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE, null -> {
                // Refresh notification
            }
        }

        updatePlaybackState()
        updateMetadata()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun updatePlaybackState() {
        if (!::mediaSession.isInitialized) return
        val isPlaying = playerManager.isPlaying.value
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val position = playerManager.currentPositionMs.value
        val speed = if (isPlaying) 1.0f else 0.0f

        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, position, speed)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateMetadata() {
        if (!::mediaSession.isInitialized) return
        val song = playerManager.currentSong.value
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song?.title ?: getString(R.string.app_name))
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song?.artist ?: "")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song?.album ?: "")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song?.durationMs ?: 0L)
            .build()

        mediaSession.setMetadata(metadata)
    }

    private fun buildNotification(): Notification {
        val song = playerManager.currentSong.value
        val isPlaying = playerManager.isPlaying.value

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePendingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, MusicService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = song?.title ?: getString(R.string.app_name)
        val artist = song?.let {
            val fName = if (it.folderName == MusicFolder.NO_FOLDER_NAME) getString(R.string.no_folder_name) else it.folderName
            val artistName = if (it.artist.contains("<unknown>", ignoreCase = true) || it.artist.equals("Unknown Artist", ignoreCase = true)) {
                getString(R.string.unknown_artist)
            } else it.artist
            "$artistName • $fName"
        } ?: getString(R.string.no_track_playing)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setSilent(true)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.notification_action_prev), prevPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) getString(R.string.notification_action_pause) else getString(R.string.notification_action_play),
                playPausePendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, getString(R.string.notification_action_next), nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notification_action_close), stopPendingIntent)

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)

        if (::mediaSession.isInitialized) {
            mediaStyle.setMediaSession(mediaSession.sessionToken)
        }

        return builder.setStyle(mediaStyle).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(noisyReceiver, filter)
            isNoisyReceiverRegistered = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaButtonHandler.cancelPendingClicks()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (ignored: Exception) {
            }
            isNoisyReceiverRegistered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
