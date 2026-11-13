package org.rustygnome.pickelhaube

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The tab "Internal", switched on for the demo box: a phone of the box called with its call button –
 * not the one under "Ring phone", which cannot call itself.
 */
@RunWith(AndroidJUnit4::class)
class InternalCallFlowTest : FlowTest() {

    @Test
    fun phoneOfTheBoxCalled() {
        setUpDemoBox()
        compose.onNode(labelled(text(R.string.settings))).performClick()
        compose.onNodeWithText(DEMO_BOX).performScrollTo().performClick()
        val title = text(R.string.connection_internal_calls)
        compose.onNodeWithText(title).performScrollTo()
        switchNextTo(title).performClick()
        compose.onNodeWithText(text(R.string.connection_save)).performScrollTo().performClick()
        // Saved: back on the settings, and from there to the dialer.
        awaitText(R.string.settings_add_box)
        compose.onNode(labelled(text(R.string.back))).performClick()
        awaitText(R.string.dialer_number_label)
        awaitBoxIdle()

        openPicker(R.string.picker_tab_internal)
        // Kitchen rings for dial help: it cannot call itself.
        awaitNode(labelled(text(R.string.picker_call, DEMO_PHONE)))
        compose.onNode(labelled(text(R.string.picker_call, DEMO_PHONE))).assertIsNotEnabled()
        compose.onNode(labelled(text(R.string.picker_call, STUDY))).performClick()

        // The dialer, the field saying which phone, the call placed.
        awaitText(STUDY)
        awaitText(text(R.string.status_pick_up, DEMO_PHONE, "").trimEnd())
    }

    private companion object {
        const val STUDY = "Study"
    }
}
