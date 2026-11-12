package org.rustygnome.pickelhaube

import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.tr064.DemoBox
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.FritzDeflections

/** Pulled down, the dialer reads the box anew: a call forwarding switched on on the box shows at once. */
@RunWith(AndroidJUnit4::class)
class PullToRefreshFlowTest : FlowTest() {

    private val demo = FritzConfig(host = DemoBox.HOST, username = DemoBox.USERNAME, password = DemoBox.PASSWORD)

    /** A drag down from the title, far enough to refresh – or, [short], not (that one over the keypad). */
    private fun pullDown(short: Boolean = false) = compose.onRoot().performTouchInput {
        val from = if (short) height * 0.35f else height * 0.07f
        swipeDown(startY = from, endY = from + if (short) height * 0.05f else height * 0.5f, durationMillis = 500)
    }

    @Test
    fun pulledDownReadsTheBoxAnew() {
        setUpDemoBox()
        awaitNode(labelled(text(R.string.deflections_off)))
        awaitBoxIdle()

        // Switched on on the box itself (in its web interface, say): the dialer does not know yet.
        FritzDeflections(demo).setEnabled(ANONYMOUS, true)
        pullDown(short = true)
        Thread.sleep(1_500)
        compose.onNode(labelled(text(R.string.deflections_off))).assertExists()

        pullDown()
        awaitNode(labelled(text(R.string.deflections_on)))
    }

    private companion object {
        /** "Calls without caller ID" among the demo box' forwardings. */
        const val ANONYMOUS = 2
    }
}
