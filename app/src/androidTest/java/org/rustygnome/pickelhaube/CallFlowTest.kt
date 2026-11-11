package org.rustygnome.pickelhaube

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
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
            compose.onNode(hasText(digit.toString()) and SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText)).performClick()
        }
        awaitText(NUMBER)
        compose.onNode(labelled(text(R.string.dialer_dial))).performClick()

        // "Connected with <number> · 0:01", then "Call ended · 0:08" – the demo call takes a few seconds.
        awaitText(text(R.string.call_connected, NUMBER, "").trimEnd())
        awaitText(text(R.string.call_ended, "").trimEnd(), CALL_TIMEOUT_MILLIS)
    }

    private companion object {
        const val NUMBER = "0301234567"

        /** Picked up, connected and ended by the demo box: about ten seconds. */
        const val CALL_TIMEOUT_MILLIS = 30_000L
    }
}
