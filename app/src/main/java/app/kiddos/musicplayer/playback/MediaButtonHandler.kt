package app.kiddos.musicplayer.playback

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles media button KeyEvents from Bluetooth earphones, wired headsets, and media controllers.
 *
 * Supports:
 * - Dedicated next/prev buttons (KEYCODE_MEDIA_NEXT, KEYCODE_MEDIA_PREVIOUS)
 * - Play/Pause/Stop/FastForward/Rewind buttons
 * - Multi-click gestures on single-button Bluetooth earphones (KEYCODE_HEADSETHOOK, KEYCODE_MEDIA_PLAY_PAUSE):
 *   - 1 click: Play / Pause toggle
 *   - 2 clicks (within doubleClickTimeoutMs): Navigate to next track
 *   - 3 clicks: Navigate to previous track
 */
class MediaButtonHandler(
    private val scope: CoroutineScope,
    val doubleClickTimeoutMs: Long = 300L,
    val navDebounceMs: Long = 500L,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onStop: () -> Unit,
    private val onFastForward: () -> Unit = {},
    private val onRewind: () -> Unit = {}
) {
    private var clickCount = 0
    private var pendingJob: Job? = null
    private var lastNavTimestamp = 0L

    /**
     * Handles a KeyEvent.
     * @return true if the event was consumed, false otherwise.
     */
    fun handleKeyEvent(keyEvent: KeyEvent?): Boolean {
        if (keyEvent == null) return false
        return handleKeyEvent(keyEvent.action, keyEvent.keyCode, keyEvent.repeatCount)
    }

    /**
     * Handles key event components directly for testing and framework dispatch.
     * @return true if the event was consumed, false otherwise.
     */
    fun handleKeyEvent(action: Int, keyCode: Int, repeatCount: Int = 0): Boolean {
        val isActionDown = action == KeyEvent.ACTION_DOWN
        val isActionUp = action == KeyEvent.ACTION_UP

        if (isActionDown) {
            // Ignore auto-repeated events from held buttons for discrete navigation actions
            if (repeatCount == 0) {
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                        handleNext()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                    KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                        handlePrevious()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        cancelPendingClicks()
                        onPlay()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        cancelPendingClicks()
                        onPause()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        cancelPendingClicks()
                        onStop()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        cancelPendingClicks()
                        onFastForward()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        cancelPendingClicks()
                        onRewind()
                        return true
                    }
                    KeyEvent.KEYCODE_HEADSETHOOK,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        registerHeadsetHookClick()
                        return true
                    }
                }
            } else {
                // If button is held down (repeatCount > 0):
                // For fast forward / rewind, repeat actions are acceptable
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        onFastForward()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        onRewind()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_STOP,
                    KeyEvent.KEYCODE_HEADSETHOOK,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        return true
                    }
                }
            }
        } else if (isActionUp) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    return true
                }
            }
        }

        return false
    }

    private fun registerHeadsetHookClick() {
        clickCount++
        pendingJob?.cancel()

        if (clickCount >= 3) {
            clickCount = 0
            handlePrevious()
            return
        }

        pendingJob = scope.launch {
            delay(doubleClickTimeoutMs)
            val count = clickCount
            clickCount = 0
            when (count) {
                1 -> onPlayPause()
                2 -> handleNext()
                else -> handlePrevious()
            }
        }
    }

    /**
     * Triggers next track navigation with debounce protection against rapid duplicate events
     * (e.g. from Bluetooth AVRCP commands, double-dispatch, or hardware button bounce).
     * @return true if navigation executed, false if it was debounced.
     */
    fun handleNext(): Boolean {
        cancelPendingClicks()
        val now = System.nanoTime() / 1_000_000L
        if (now - lastNavTimestamp < navDebounceMs) {
            return false
        }
        lastNavTimestamp = now
        onNext()
        return true
    }

    /**
     * Triggers previous track navigation with debounce protection against rapid duplicate events.
     * @return true if navigation executed, false if it was debounced.
     */
    fun handlePrevious(): Boolean {
        cancelPendingClicks()
        val now = System.nanoTime() / 1_000_000L
        if (now - lastNavTimestamp < navDebounceMs) {
            return false
        }
        lastNavTimestamp = now
        onPrevious()
        return true
    }

    fun cancelPendingClicks() {
        pendingJob?.cancel()
        pendingJob = null
        clickCount = 0
    }
}
