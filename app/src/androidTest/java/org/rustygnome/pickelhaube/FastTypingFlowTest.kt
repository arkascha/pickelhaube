package org.rustygnome.pickelhaube

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Typed fast, as with a thumb, every key counts: the taps are real touches, one every few dozen
 * milliseconds, not waiting for the app – while the suggestions are looked for behind them.
 */
@RunWith(AndroidJUnit4::class)
class FastTypingFlowTest : FlowTest() {

    /**
     * A touch on the screen at [x], [y] (window coordinates – the dialer is drawn edge to edge), let go
     * after a moment – [slidePx] further down (up, when negative), as a thumb does when typing in a hurry.
     */
    private fun touch(x: Float, y: Float, slidePx: Float = 0f) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, time: Long, dy: Float = 0f) =
            MotionEvent.obtain(down, time, action, x, y + dy, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        automation.injectInputEvent(event(MotionEvent.ACTION_DOWN, down), false)
        if (slidePx != 0f) {
            Thread.sleep(TOUCH_MILLIS / 2)
            automation.injectInputEvent(event(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis(), slidePx), false)
            Thread.sleep(TOUCH_MILLIS / 2)
        } else {
            Thread.sleep(TOUCH_MILLIS)
        }
        automation.injectInputEvent(event(MotionEvent.ACTION_UP, SystemClock.uptimeMillis(), slidePx), false)
    }

    /** What the number field holds, its digits only (it is shown grouped). */
    private fun typedDigits(): String {
        compose.waitForIdle()
        // The number field, above "Ring phone" (a text field as well).
        return compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
            .fetchSemanticsNodes().minBy { it.boundsInWindow.top }
            .config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
            .filter(Char::isDigit)
    }

    /**
     * A thumb in a hurry slides a little on the key, down or up – more than the touch slop, still on the
     * key: that is a tap on the key, not a pull of the dialer.
     */
    @Test
    fun keysTappedWithASlide() {
        setUpDemoBox()
        awaitBoxIdle()
        // The welcome tour slides away first: touches during that would go to it.
        compose.waitForIdle()
        val density = context.resources.displayMetrics.density
        val keys = NUMBER.toSet().associateWith { compose.onNode(key(it)).fetchSemanticsNode().boundsInWindow.center }
        NUMBER.forEachIndexed { i, digit ->
            val slide = SLIDE_DP * density * if (i % 2 == 0) 1 else -1
            keys.getValue(digit).let { touch(it.x, it.y, slide) }
            Thread.sleep(PAUSE_MILLIS)
        }
        assertEquals(NUMBER, typedDigits())
    }

    @Test
    fun everyKeyCounts() {
        setUpDemoBox()
        // The phone books are read: the suggestions have something to search.
        awaitBoxIdle()
        // The welcome tour slides away first: touches during that would go to it.
        compose.waitForIdle()
        val keys = NUMBER.toSet().associateWith { compose.onNode(key(it)).fetchSemanticsNode().boundsInWindow.center }
        NUMBER.forEach { digit ->
            keys.getValue(digit).let { touch(it.x, it.y) }
            Thread.sleep(PAUSE_MILLIS)
        }
        assertEquals(NUMBER, typedDigits())
    }

    private companion object {
        /** Starts like many numbers in the demo box' phone books, so there is a lot to suggest. */
        const val NUMBER = "01713920010"
        const val TOUCH_MILLIS = 40L
        const val PAUSE_MILLIS = 30L

        /** How far a thumb slides on a key: more than the touch slop (8 dp), less than half a key. */
        const val SLIDE_DP = 14f
    }
}
