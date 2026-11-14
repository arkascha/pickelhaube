package org.rustygnome.pickelhaube

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** A call that is not answered – the demo box' garage is always busy – offers "Try again", which calls it again. */
@RunWith(AndroidJUnit4::class)
class TryAgainFlowTest : FlowTest() {

    @Test
    fun busyNumberCalledAgain() {
        setUpDemoBox()
        BUSY.forEach { compose.onNode(key(it)).performClick() }
        compose.onNode(labelled(text(R.string.dialer_dial))).performClick()

        awaitText(text(R.string.call_not_answered), CALL_TIMEOUT_MILLIS)
        compose.onNodeWithText(text(R.string.call_try_again)).performClick()
        // Called again: the box rings the phone for dial help once more.
        awaitText(text(R.string.status_pick_up, DEMO_PHONE, "").trimEnd(), CALL_TIMEOUT_MILLIS)
    }

    private companion object {
        /** Garage Krüger in the demo box' phone book: always busy. */
        const val BUSY = "08999998202"
        const val CALL_TIMEOUT_MILLIS = 30_000L
    }
}
