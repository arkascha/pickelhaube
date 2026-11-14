package org.rustygnome.pickelhaube

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A number picked in the number picker: "back" in the dialer leads to the picker again, as it was
 * left – its tab and its search – then to the dialer; once the number was changed, it leaves the app.
 */
@RunWith(AndroidJUnit4::class)
class BackToPickerFlowTest : FlowTest() {

    private fun back() = scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun backLeadsToThePickerAsItWasLeft() {
        setUpDemoBox()
        openPicker(R.string.phone_books)
        notNowForContacts()
        compose.onNode(hasSetTextAction()).performTextInput(SEARCH)
        awaitText(shown(NUMBER))
        compose.onNodeWithText(shown(NUMBER)).performClick()
        // The dialer, with the number – grouped, and named above it (so not "Number to dial").
        awaitText(GROUPED)

        // Back: the phone books again, still searched for Felix.
        back()
        awaitText(R.string.picker_title)
        compose.onNode(hasSetTextAction() and hasText(SEARCH)).assertExists()
        compose.onNodeWithText(shown(NUMBER)).assertExists()

        // Back from there: the dialer, with the number picked.
        back()
        awaitText(GROUPED)

        // Picked once more, then changed by hand: "back" leaves the app.
        openPicker(R.string.phone_books)
        // Opened anew: without the search of the last time.
        compose.onNode(hasSetTextAction() and hasText(SEARCH)).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput(SEARCH)
        awaitText(shown(NUMBER))
        compose.onNodeWithText(shown(NUMBER)).performClick()
        // The dialer, with the number – grouped, and named above it (so not "Number to dial").
        awaitText(GROUPED)
        compose.onNode(key('1')).performClick()
        // "Back" below goes past Compose: the dialer has to be drawn anew with the number changed first.
        compose.waitForIdle()
        back()
        // Left: since Android 12 the app's root activity is only moved to the back, not finished.
        compose.waitUntil(TIMEOUT_MILLIS) { scenario?.state?.isAtLeast(Lifecycle.State.STARTED) == false }
    }

    private companion object {
        const val SEARCH = "Felix"
        /** Felix Neumann's in the demo box' phone book. */
        const val NUMBER = "03023125106"
        const val GROUPED = "030 23125106"
    }
}
