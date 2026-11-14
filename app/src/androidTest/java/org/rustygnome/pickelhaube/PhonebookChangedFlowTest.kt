package org.rustygnome.pickelhaube

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.tr064.DemoBox
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.FritzDialer

/**
 * The box' phone books changed on the box itself while the app shows them – an entry with a speed
 * dial deleted in its web interface: the app copes without an error.
 */
@RunWith(AndroidJUnit4::class)
class PhonebookChangedFlowTest : FlowTest() {

    /** A box speed dial row of the speed dial tab. */
    private fun slot(slot: String, name: String) = hasText(slot) and hasText(name)

    private fun showSpeedDial(slot: String, name: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(slot(slot, name))
    }

    /** Deleted on the box by someone else – here another client of the same (demo) box. */
    private fun deleteOnBox(name: String) {
        val box = FritzDialer(FritzConfig(host = DemoBox.HOST, username = DemoBox.USERNAME, password = DemoBox.PASSWORD))
        val entry = box.loadPhonebooks().flatMap { it.contacts }.first { it.name == name }
        box.deletePhonebookEntry(entry.bookId!!, entry.uid!!)
    }

    /** Removing a speed dial whose entry is gone: no error – the phone books are read anew, and it is gone from the list. */
    @Test
    fun speedDialOfADeletedEntryRemoved() {
        setUpDemoBox()
        openPicker(R.string.speed_dial)
        showSpeedDial("01", ANNA)
        deleteOnBox(ANNA)

        compose.onNode(slot("01", ANNA)).performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.speed_dial_remove)).performClick()
        awaitGone(slot("01", ANNA))
        compose.onNode(isDialog()).assertDoesNotExist()
        // The others are still there.
        showSpeedDial("02", DAD)
    }

    /** Giving its slot to another number, while the entry that had it is gone: read anew, then done. */
    @Test
    fun speedDialOfADeletedEntryChanged() {
        setUpDemoBox()
        openPicker(R.string.speed_dial)
        showSpeedDial("03", GRANDMA)
        deleteOnBox(GRANDMA)

        compose.onNode(slot("03", GRANDMA)).performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.speed_dial_replace)).performClick()
        notNowForContacts()
        awaitText(shown(BEN_MOBILE))
        compose.onNodeWithText(shown(BEN_MOBILE)).performClick()
        // Back on the speed dial tab, read anew: Ben has 03, without an error.
        compose.waitUntil(TIMEOUT_MILLIS) {
            runCatching { compose.onNode(hasScrollToNodeAction()).performScrollToNode(slot("03", BEN)) }.isSuccess
        }
        compose.onNode(slot("03", GRANDMA)).assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    /** Back in the app after the change on the box: the phone books are read anew, the deleted entry's speed dial is gone. */
    @Test
    fun readAnewBackInTheForeground() {
        setUpDemoBox()
        openPicker(R.string.speed_dial)
        showSpeedDial("01", ANNA)

        scenario?.moveToState(Lifecycle.State.CREATED)
        deleteOnBox(ANNA)
        scenario?.moveToState(Lifecycle.State.RESUMED)
        awaitGone(slot("01", ANNA))
        showSpeedDial("02", DAD)
    }

    private companion object {
        /** Contacts of the demo box with speed dials: Anna 01, Dad 02, Grandma 03 (see DemoBox). */
        const val ANNA = "Anna Becker"
        const val DAD = "Dad"
        const val GRANDMA = "Grandma"
        const val BEN = "Ben Hoffmann"
        const val BEN_MOBILE = "017604069002"
    }
}
