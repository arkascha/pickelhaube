package org.rustygnome.pickelhaube

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The call forwardings of the demo box: switched on and off on their page, the dialer's button saying
 * whether one is on – read from the box, so also after a new start – and the one the tile switches.
 */
@RunWith(AndroidJUnit4::class)
class DeflectionsFlowTest : FlowTest() {

    private val switchedOn = isToggleable() and isOn()

    /** The dialer's button to the call forwardings, saying whether one is on. */
    private fun awaitButton(on: Boolean) =
        awaitNode(labelled(text(if (on) R.string.deflections_on else R.string.deflections_off)))

    private fun openPage(on: Boolean) {
        awaitButton(on)
        compose.onNode(labelled(text(if (on) R.string.deflections_on else R.string.deflections_off))).performClick()
        awaitText(R.string.deflections)
        // The three of the demo box, read anew.
        awaitText(text(R.string.deflection_to, OWN_NUMBER))
        awaitText(R.string.deflection_all)
        awaitText(R.string.deflection_anonymous)
        // Shown from before while read anew – the switches are off until it is done.
        awaitBoxIdle()
    }

    private fun back() = compose.onNode(labelled(text(R.string.back))).performClick()

    @Test
    fun switchedOnAndOff() {
        setUpDemoBox()
        openPage(on = false)
        compose.onNode(switchedOn).assertDoesNotExist()

        // "Calls without caller ID", the third: switched on – the box' answer read back.
        compose.onAllNodes(isToggleable())[ANONYMOUS].performClick()
        awaitNode(switchedOn)
        back()
        awaitButton(on = true)

        // Read from the box after a new start: still on. Switched off again.
        restart()
        openPage(on = true)
        compose.onAllNodes(isToggleable())[ANONYMOUS].performClick()
        awaitGone(switchedOn)
        back()
        awaitButton(on = false)
    }

    /** With more than one, a radio button chooses the one the quick settings tile switches; kept on the phone. */
    @Test
    fun oneChosenForTheTile() {
        setUpDemoBox()
        openPage(on = false)
        awaitText(R.string.deflections_tile_hint)
        // None chosen yet: the first.
        compose.onAllNodes(isSelectable())[0].assertIsSelected()
        compose.onAllNodes(isSelectable())[ANONYMOUS].performClick()
        compose.onAllNodes(isSelectable())[ANONYMOUS].assertIsSelected()
        back()

        restart()
        openPage(on = false)
        compose.onAllNodes(isSelectable())[ANONYMOUS].assertIsSelected()
    }

    private companion object {
        /** The demo box' own number, the first forwarding being for calls to it (see DemoBox). */
        const val OWN_NUMBER = "03023125300"

        /** Position of "Calls without caller ID" among the demo box' forwardings. */
        const val ANONYMOUS = 2
    }
}
