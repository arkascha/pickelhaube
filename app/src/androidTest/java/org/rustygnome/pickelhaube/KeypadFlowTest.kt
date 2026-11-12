package org.rustygnome.pickelhaube

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** The keypad's tones: off at first, switched on in the settings – kept after a new start, and typing still works with them. */
@RunWith(AndroidJUnit4::class)
class KeypadFlowTest : FlowTest() {

    private fun openKeyTonesSetting(): String {
        compose.onNode(labelled(text(R.string.settings))).performClick()
        val title = text(R.string.settings_key_tones)
        compose.onNodeWithText(title).performScrollTo()
        return title
    }

    @Test
    fun keyTonesSwitchedOn() {
        setUpDemoBox()
        val title = openKeyTonesSetting()
        switchNextTo(title).assertIsOff().performClick()
        switchNextTo(title).assertIsOn()

        restart()
        awaitText(DEMO_PHONE)
        NUMBER.forEach { compose.onNode(key(it)).performClick() }
        awaitText(NUMBER)
        switchNextTo(openKeyTonesSetting()).assertIsOn()
    }

    private companion object {
        const val NUMBER = "0301234567*#"
    }
}
