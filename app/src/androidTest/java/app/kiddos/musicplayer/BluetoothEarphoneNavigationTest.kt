package app.kiddos.musicplayer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kiddos.musicplayer.model.AudioFile
import app.kiddos.musicplayer.playback.MusicPlayerManager
import app.kiddos.musicplayer.service.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluetoothEarphoneNavigationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val playerManager = MusicPlayerManager.getInstance(context)

    private val playlist = listOf(
        AudioFile(
            id = 101L,
            title = "Track 1",
            artist = "Artist A",
            album = "Album A",
            durationMs = 120000L,
            uri = null,
            path = "/dummy/track1.mp3",
            folderName = "Music",
            folderPath = "/dummy",
            size = 1024L
        ),
        AudioFile(
            id = 102L,
            title = "Track 2",
            artist = "Artist B",
            album = "Album B",
            durationMs = 150000L,
            uri = null,
            path = "/dummy/track2.mp3",
            folderName = "Music",
            folderPath = "/dummy",
            size = 2048L
        ),
        AudioFile(
            id = 103L,
            title = "Track 3",
            artist = "Artist C",
            album = "Album C",
            durationMs = 180000L,
            uri = null,
            path = "/dummy/track3.mp3",
            folderName = "Music",
            folderPath = "/dummy",
            size = 3072L
        )
    )

    @Before
    fun setup() = runBlocking(Dispatchers.Main) {
        playerManager.stopAndRelease()
        MusicService.start(context)
        Thread.sleep(200L)
    }

    private fun sendMediaButton(keyCode: Int) {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setClass(context, MusicService::class.java)
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    @Test
    fun bluetoothNextButton_navigatesToNextTrack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            playerManager.playSong(playlist[0], playlist)
        }
        assertEquals(101L, playerManager.currentSong.value?.id)

        sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT)
        Thread.sleep(300L)

        assertEquals(102L, playerManager.currentSong.value?.id)
    }

    @Test
    fun bluetoothPreviousButton_navigatesToPreviousTrack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            playerManager.playSong(playlist[1], playlist)
        }
        assertEquals(102L, playerManager.currentSong.value?.id)

        sendMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        Thread.sleep(300L)

        assertEquals(101L, playerManager.currentSong.value?.id)
    }

    @Test
    fun bluetoothHeadsetHook_doubleClick_navigatesToNextTrack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            playerManager.playSong(playlist[0], playlist)
        }
        assertEquals(101L, playerManager.currentSong.value?.id)

        sendMediaButton(KeyEvent.KEYCODE_HEADSETHOOK)
        Thread.sleep(50L)
        sendMediaButton(KeyEvent.KEYCODE_HEADSETHOOK)

        // Wait for double-click debounce window (~300ms) to trigger
        Thread.sleep(500L)

        assertEquals(102L, playerManager.currentSong.value?.id)
    }

    @Test
    fun bluetoothHeadsetHook_tripleClick_navigatesToPreviousTrack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            playerManager.playSong(playlist[1], playlist)
        }
        assertEquals(102L, playerManager.currentSong.value?.id)

        sendMediaButton(KeyEvent.KEYCODE_HEADSETHOOK)
        Thread.sleep(40L)
        sendMediaButton(KeyEvent.KEYCODE_HEADSETHOOK)
        Thread.sleep(40L)
        sendMediaButton(KeyEvent.KEYCODE_HEADSETHOOK)

        Thread.sleep(400L)

        assertEquals(101L, playerManager.currentSong.value?.id)
    }

    @Test
    fun bluetoothNextButton_rapidDuplicate_doesNotJumpTwoSongs() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            playerManager.playSong(playlist[0], playlist)
        }
        assertEquals(101L, playerManager.currentSong.value?.id)

        // Rapid duplicate events (e.g. within 50ms) as sent by glitchy Bluetooth headsets or dual AVRCP/HID
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT)
        Thread.sleep(50L)
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT)

        Thread.sleep(400L)

        // Must only advance 1 track to Track 2 (id 102L), NOT jump 2 songs to Track 3 (id 103L)
        assertEquals(102L, playerManager.currentSong.value?.id)
    }
}
