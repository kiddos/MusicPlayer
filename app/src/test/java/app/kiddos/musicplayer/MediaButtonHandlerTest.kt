package app.kiddos.musicplayer

import android.view.KeyEvent
import app.kiddos.musicplayer.playback.MediaButtonHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaButtonHandlerTest {

    private val testScope = CoroutineScope(Dispatchers.Default)

    @Test
    fun nextKey_triggersNextImmediately() {
        var nextCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            onPlay = {},
            onPause = {},
            onPlayPause = {},
            onNext = { nextCount++ },
            onPrevious = {},
            onStop = {}
        )

        val handled = handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)

        assertTrue(handled)
        assertEquals(1, nextCount)
    }

    @Test
    fun previousKey_triggersPreviousImmediately() {
        var prevCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            onPlay = {},
            onPause = {},
            onPlayPause = {},
            onNext = {},
            onPrevious = { prevCount++ },
            onStop = {}
        )

        val handled = handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

        assertTrue(handled)
        assertEquals(1, prevCount)
    }

    @Test
    fun playAndPauseKeys_triggerRespectiveCallbacks() {
        var playCount = 0
        var pauseCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            onPlay = { playCount++ },
            onPause = { pauseCount++ },
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onStop = {}
        )

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(1, playCount)
        assertEquals(0, pauseCount)

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(1, playCount)
        assertEquals(1, pauseCount)
    }

    @Test
    fun stopKey_triggersStop() {
        var stopCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            onPlay = {},
            onPause = {},
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onStop = { stopCount++ }
        )

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
        assertEquals(1, stopCount)
    }

    @Test
    fun fastForwardAndRewind_triggerCallbacks() {
        var ffCount = 0
        var rwCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            onPlay = {},
            onPause = {},
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onStop = {},
            onFastForward = { ffCount++ },
            onRewind = { rwCount++ }
        )

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_REWIND))

        assertEquals(1, ffCount)
        assertEquals(1, rwCount)
    }

    @Test
    fun singleClickHeadsetHook_triggersPlayPause() {
        var playPauseCount = 0
        var nextCount = 0
        var prevCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            doubleClickTimeoutMs = 60L,
            onPlay = {},
            onPause = {},
            onPlayPause = { playPauseCount++ },
            onNext = { nextCount++ },
            onPrevious = { prevCount++ },
            onStop = {}
        )

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))

        // Wait for single click timeout to lapse
        Thread.sleep(120L)

        assertEquals(1, playPauseCount)
        assertEquals(0, nextCount)
        assertEquals(0, prevCount)
    }

    @Test
    fun doubleClickHeadsetHook_navigatesToNextTrack() {
        var playPauseCount = 0
        var nextCount = 0
        var prevCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            doubleClickTimeoutMs = 100L,
            onPlay = {},
            onPause = {},
            onPlayPause = { playPauseCount++ },
            onNext = { nextCount++ },
            onPrevious = { prevCount++ },
            onStop = {}
        )

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))
        Thread.sleep(30L)
        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))

        // Wait for double click timeout to lapse
        Thread.sleep(150L)

        assertEquals(0, playPauseCount)
        assertEquals(1, nextCount)
        assertEquals(0, prevCount)
    }

    @Test
    fun tripleClickHeadsetHook_navigatesToPreviousTrack() {
        var playPauseCount = 0
        var nextCount = 0
        var prevCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            doubleClickTimeoutMs = 100L,
            onPlay = {},
            onPause = {},
            onPlayPause = { playPauseCount++ },
            onNext = { nextCount++ },
            onPrevious = { prevCount++ },
            onStop = {}
        )

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))
        Thread.sleep(20L)
        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))
        Thread.sleep(20L)
        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))

        // Triple click triggers immediately
        assertEquals(0, playPauseCount)
        assertEquals(0, nextCount)
        assertEquals(1, prevCount)
    }

    @Test
    fun doubleClickPlayPauseKey_navigatesToNextTrack() {
        var playPauseCount = 0
        var nextCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            doubleClickTimeoutMs = 80L,
            onPlay = {},
            onPause = {},
            onPlayPause = { playPauseCount++ },
            onNext = { nextCount++ },
            onPrevious = {},
            onStop = {}
        )

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        Thread.sleep(20L)
        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))

        Thread.sleep(120L)

        assertEquals(0, playPauseCount)
        assertEquals(1, nextCount)
    }

    @Test
    fun actionUp_isConsumedWithoutTriggeringDuplicateActions() {
        var nextCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            onPlay = {},
            onPause = {},
            onPlayPause = {},
            onNext = { nextCount++ },
            onPrevious = {},
            onStop = {}
        )

        val handled = handler.handleKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)

        assertTrue(handled)
        assertEquals(0, nextCount)
    }

    @Test
    fun unhandledKey_returnsFalse() {
        val handler = MediaButtonHandler(
            scope = testScope,
            onPlay = {},
            onPause = {},
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onStop = {}
        )

        val handled = handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)
        assertFalse(handled)
    }

    @Test
    fun rapidDuplicateNextKey_isDebounced() {
        var nextCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            navDebounceMs = 500L,
            onPlay = {},
            onPause = {},
            onPlayPause = {},
            onNext = { nextCount++ },
            onPrevious = {},
            onStop = {}
        )

        // First click executes
        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(1, nextCount)

        // Rapid duplicate click within debounce window is consumed but dropped
        Thread.sleep(40L)
        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(1, nextCount)
    }

    @Test
    fun rapidDuplicatePreviousKey_isDebounced() {
        var prevCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            navDebounceMs = 500L,
            onPlay = {},
            onPause = {},
            onPlayPause = {},
            onNext = {},
            onPrevious = { prevCount++ },
            onStop = {}
        )

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(1, prevCount)

        Thread.sleep(40L)
        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(1, prevCount)
    }

    @Test
    fun handleNext_followedByMediaButtonNext_isDebounced() {
        var nextCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            navDebounceMs = 500L,
            onPlay = {},
            onPause = {},
            onPlayPause = {},
            onNext = { nextCount++ },
            onPrevious = {},
            onStop = {}
        )

        // Framework calls onSkipToNext() -> handler.handleNext()
        assertTrue(handler.handleNext())
        assertEquals(1, nextCount)

        // Simultaneous / slightly delayed KeyEvent.KEYCODE_MEDIA_NEXT arriving from Bluetooth
        Thread.sleep(30L)
        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(1, nextCount)
    }

    @Test
    fun nextKey_afterDebounceTimeout_triggersAgain() {
        var nextCount = 0
        val handler = MediaButtonHandler(
            scope = testScope,
            navDebounceMs = 80L,
            onPlay = {},
            onPause = {},
            onPlayPause = {},
            onNext = { nextCount++ },
            onPrevious = {},
            onStop = {}
        )

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(1, nextCount)

        // Wait past debounce threshold
        Thread.sleep(120L)

        assertTrue(handler.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(2, nextCount)
    }
}
