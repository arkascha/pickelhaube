package org.rustygnome.pickelhaube

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Speed dials: this phone's own (set from the phone books, used with a long press, removed) and the demo box' ones. */
@RunWith(AndroidJUnit4::class)
class SpeedDialFlowTest : FlowTest() {

    /** A slot row of the speed dial tab, by its slot and what it shows (a name, or "Not set"). */
    private fun slot(slot: String, shows: String) = hasText(slot) and hasText(shows)

    private fun longPressKey(digit: Char) = compose.onNode(key(digit)).performTouchInput { longClick() }

    private fun back() = compose.onNode(labelled(text(R.string.back))).performClick()

    /** The row that folds this phone's [count] free slots. */
    private fun freeSlots(count: Int) = context.resources.getQuantityString(R.plurals.speed_dial_free_slots, count, count)

    /** Slot 1 of this phone set to Anna from the phone books, used with a long press on "1" – also after a new start – and removed. */
    @Test
    fun phoneSpeedDialSetUsedAndRemoved() {
        setUpDemoBox()
        openPicker(R.string.speed_dial)
        // All nine free: folded into one row, shown with a tap.
        compose.onNodeWithText(freeSlots(9)).performClick()
        compose.onNode(slot("1", text(R.string.speed_dial_free))).performClick()

        // The phone books, saying which slot the tapped number becomes.
        notNowForContacts()
        awaitText(text(R.string.speed_dial_choose_number, "1"))
        awaitText(ANNA_HOME)
        compose.onNodeWithText(ANNA_HOME).performClick()
        awaitNode(slot("1", ANNA))
        back()

        // A long press on "1" in the dialer: Anna's number, the field saying whose it is.
        awaitText(R.string.dialer_number_label)
        longPressKey('1')
        awaitText(ANNA_HOME)
        awaitText(text(R.string.dialer_speed_dial_label, "1", ANNA))

        // Stored on the phone: still there after a new start.
        restart()
        awaitText(DEMO_PHONE)
        longPressKey('1')
        awaitText(text(R.string.dialer_speed_dial_label, "1", ANNA))

        // Removed with a long press on the slot, right away – and put back with "Undo".
        openPicker(R.string.speed_dial)
        compose.onNode(slot("1", ANNA)).performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.speed_dial_remove)).performClick()
        // Free again: folded with the others.
        awaitText(freeSlots(9))
        compose.onNodeWithText(text(R.string.undo)).performClick()
        awaitNode(slot("1", ANNA))

        // With one set, eight are folded.
        awaitText(freeSlots(8))

        // Removed again, and left so: it stays removed.
        compose.onNode(slot("1", ANNA)).performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.speed_dial_remove)).performClick()
        awaitText(freeSlots(9))
    }

    /** The demo box' speed dial 01 (Anna) removed on the box – right away, without asking – and put back with "Undo". */
    @Test
    fun boxSpeedDialRemovedAndUndone() {
        setUpDemoBox()
        openPicker(R.string.speed_dial)
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(slot("01", ANNA))
        compose.onNode(slot("01", ANNA)).performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.speed_dial_remove)).performClick()
        awaitText(text(R.string.status_speed_dial_removed, "01"))
        awaitGone(slot("01", ANNA))

        compose.onNodeWithText(text(R.string.undo)).performClick()
        awaitBoxIdle()
        list.performScrollToNode(slot("01", ANNA))
    }

    /**
     * The demo box' speed dials: listed, "0" and a long "1" for its speed dial 01 – and a new one
     * set up in its phone books for Ben, at slot 06, which is then listed and used.
     */
    @Test
    fun boxSpeedDialsUsedAndSetUp() {
        setUpDemoBox()
        openPicker(R.string.speed_dial)
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(slot("01", ANNA))
        back()

        compose.onNode(key('0')).performClick()
        longPressKey('1')
        awaitText(ANNA_MOBILE)
        awaitText(text(R.string.dialer_speed_dial_label, "01", ANNA))

        // A new one: Ben's number from the phone books, then its slot.
        openPicker(R.string.speed_dial)
        list.performScrollToNode(hasText(text(R.string.speed_dial_box_add)))
        compose.onNodeWithText(text(R.string.speed_dial_box_add)).performClick()
        notNowForContacts()
        awaitText(R.string.speed_dial_choose_number_box_new)
        awaitText(BEN_MOBILE)
        compose.onNodeWithText(BEN_MOBILE).performClick()
        awaitText(text(R.string.speed_dial_box_for, BEN))
        compose.onNode(hasText(NEW_SLOT) and hasAnyAncestor(isDialog())).performClick()

        // Written into the box' phone book entry, read anew: listed – and a tap takes it into the dialer.
        compose.waitUntil(TIMEOUT_MILLIS) { runCatching { list.performScrollToNode(slot(NEW_SLOT, BEN)) }.isSuccess }
        compose.onNode(slot(NEW_SLOT, BEN)).performClick()
        // In the dialer, the field labelled with whose number it is.
        awaitText(text(R.string.dialer_speed_dial_label, NEW_SLOT, BEN))
        awaitText(BEN_MOBILE)
    }

    /** This phone's speed dials switched off in the settings: gone from the speed dial tab, also after a new start. */
    @Test
    fun phoneSpeedDialsSwitchedOff() {
        setUpDemoBox()
        compose.onNode(labelled(text(R.string.settings))).performClick()
        val title = text(R.string.settings_phone_speed_dials)
        compose.onNodeWithText(title).performScrollTo()
        switchNextTo(title).performClick()
        back()

        restart()
        awaitText(DEMO_PHONE)
        openPicker(R.string.speed_dial)
        // The box' speed dials only.
        awaitNode(slot("01", ANNA))
        compose.onNodeWithText(text(R.string.this_phone)).assertDoesNotExist()
        compose.onAllNodesWithText(freeSlots(9)).fetchSemanticsNodes().let {
            check(it.isEmpty()) { "no free slots of this phone are shown" }
        }
    }

    private companion object {
        /** Contacts of the demo box' phone book "Family & friends" (see DemoBox). */
        const val ANNA = "Anna Becker"
        const val ANNA_HOME = "03023125101"

        /** Anna's mobile number: the demo box' speed dial 01. */
        const val ANNA_MOBILE = "017139200101"
        const val BEN = "Ben Hoffmann"
        const val BEN_MOBILE = "017604069002"

        /** A slot the demo box does not use (it has 01–05). */
        const val NEW_SLOT = "06"
    }
}
