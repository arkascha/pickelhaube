package org.rustygnome.pickelhaube

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** The call buttons in the picker: called right away through the demo box; "back" leads to the list again. */
@RunWith(AndroidJUnit4::class)
class CallButtonFlowTest : FlowTest() {

    @Test
    fun calledFromThePhoneBooks() {
        setUpDemoBox()
        openPicker(R.string.phone_books)
        notNowForContacts()
        compose.onNode(hasSetTextAction()).performTextInput(NAME)
        compose.onNode(labelled(text(R.string.picker_call, NAME))).performClick()

        // The dialer, following the call – no green button pressed.
        awaitText(text(R.string.call_connected, NUMBER, "").trimEnd(), CALL_TIMEOUT_MILLIS)

        // Back: the phone books again, still searched for Felix.
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitText(R.string.picker_title)
        compose.onNode(labelled(text(R.string.picker_call, NAME))).assertExists()
    }

    /** The call button of the demo box' speed dial 01, Anna's mobile: called right away, the field saying which speed dial. */
    @Test
    fun calledFromTheSpeedDials() {
        setUpDemoBox()
        openPicker(R.string.speed_dial)
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasContentDescription(text(R.string.picker_call, ANNA)))
        compose.onNode(hasContentDescription(text(R.string.picker_call, ANNA))).performClick()

        awaitText(text(R.string.dialer_speed_dial_label, "01", ANNA))
        awaitText(text(R.string.call_connected, ANNA_MOBILE, "").trimEnd(), CALL_TIMEOUT_MILLIS)
    }

    private companion object {
        const val ANNA = "Anna Becker"
        /** Her mobile, the demo box' speed dial 01. */
        const val ANNA_MOBILE = "017139200101"
        const val NAME = "Felix Neumann"
        /** His number in the demo box' phone book. */
        const val NUMBER = "03023125106"
        const val CALL_TIMEOUT_MILLIS = 30_000L
    }
}
