package org.rustygnome.pickelhaube

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** A call through the demo box, followed on its call monitor: it is picked up, answered and hung up. */
@RunWith(AndroidJUnit4::class)
class CallFlowTest : FlowTest() {

    @Test
    fun callFollowedToItsEnd() {
        setUpDemoBox()
        // Typed on the keypad (the number field takes no keyboard input).
        NUMBER.forEach { digit ->
            compose.onNode(key(digit)).performClick()
        }
        awaitText(NUMBER)
        compose.onNode(labelled(text(R.string.dialer_dial))).performClick()

        // "Connected with <number> · 0:01", then "Call ended · 0:08" – the demo call takes a few seconds.
        awaitText(text(R.string.call_connected, NUMBER, "").trimEnd())
        // The green button is off for the number of the call under way: it would only ring the phone again.
        compose.onNode(labelled(text(R.string.dialer_dial))).assertIsNotEnabled()
        awaitText(text(R.string.call_ended, "").trimEnd(), CALL_TIMEOUT_MILLIS)
        compose.onNode(labelled(text(R.string.dialer_dial))).assertIsEnabled()
    }

    private companion object {
        const val NUMBER = "0301234567"

        /** Picked up, connected and ended by the demo box: about ten seconds. */
        const val CALL_TIMEOUT_MILLIS = 30_000L
    }
}
