package org.rustygnome.pickelhaube

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The number field: a number typed in full says whose it is, it is shown grouped as numbers are
 * written, and an empty field gets the number dialled last back from the green button.
 */
@RunWith(AndroidJUnit4::class)
class NumberFieldFlowTest : FlowTest() {

    @Test
    fun namedGroupedAndRedialled() {
        setUpDemoBox()
        // Before the first call there is nothing to bring back.
        compose.onNode(labelled(text(R.string.dialer_redial))).assertDoesNotExist()
        compose.onNode(labelled(text(R.string.dialer_dial))).assertIsNotEnabled()

        // The phone books are read in the background after connecting.
        awaitBoxIdle()
        NUMBER.forEach { compose.onNode(key(it)).performClick() }
        awaitText(NAME)
        awaitNode(hasText(GROUPED))
        // Its suggestion stays, as it was a key before: the name above the number and in the row below.
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText(NAME, substring = true)).fetchSemanticsNodes().size >= 2 }

        compose.onNode(labelled(text(R.string.dialer_dial))).performClick()
        awaitText(text(R.string.call_ended, "").trimEnd(), CALL_TIMEOUT_MILLIS)

        // Emptied (long press on ⌫), the green button brings the number back, with its name.
        compose.onNode(labelled(text(R.string.dialer_backspace_description))).performTouchInput { longClick() }
        compose.onNode(labelled(text(R.string.dialer_redial))).assertIsEnabled().performClick()
        awaitText(NAME)
        awaitNode(hasText(GROUPED))
        compose.onNode(labelled(text(R.string.dialer_dial))).assertIsEnabled()
    }

    /** ⌫ tapped in a row, once too often: the tap does not reach the paste button that has just taken its place. */
    @Test
    fun deletingFastDoesNotPaste() {
        setUpDemoBox()
        "123".forEach { compose.onNode(key(it)).performClick() }
        awaitText("123")
        repeat(4) { compose.onNode(labelled(text(R.string.dialer_backspace_description)) or labelled(text(R.string.dialer_paste))).performClick() }
        compose.waitForIdle()
        compose.onNode(hasText(text(R.string.error_no_number_found_title))).assertDoesNotExist()
        compose.onNode(labelled(text(R.string.dialer_paste))).assertExists()
    }

    private companion object {
        /** Felix Neumann's in the demo box' phone book. */
        const val NUMBER = "03023125106"
        const val NAME = "Felix Neumann"
        const val GROUPED = "030 23125106"

        /** Picked up, connected and ended by the demo box: about ten seconds. */
        const val CALL_TIMEOUT_MILLIS = 30_000L
    }
}
