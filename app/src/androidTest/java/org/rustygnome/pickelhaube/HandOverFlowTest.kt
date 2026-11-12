package org.rustygnome.pickelhaube

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.tr064.DemoBox
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.FritzPhonebooks
import org.rustygnome.pickelhaube.tr064.Tr064Client

/** A number of the recent calls handed over to a phone book of the demo box – also after phone books changed on the box meanwhile. */
@RunWith(AndroidJUnit4::class)
class HandOverFlowTest : FlowTest() {

    private val demo = FritzConfig(host = DemoBox.HOST, username = DemoBox.USERNAME, password = DemoBox.PASSWORD)

    /** Which phone books of the demo box have [number], read from the box itself. */
    private fun booksWith(number: String): List<String> = FritzDialer(demo).loadPhonebooks()
        .filter { book -> book.contacts.any { c -> c.numbers.any { it.number == number } } }.map { it.name }

    /** The unknown caller of the recent calls, long-pressed and handed over to [book]. */
    private fun handOver(book: String, whileBusy: Boolean = false) {
        // The picker reads the call list and phone books first.
        awaitBoxIdle()
        if (whileBusy) {
            // Read anew, slowly: the hand-over is tapped while the box is still busy with that.
            DemoBox.latencyMillis = SLOW_MILLIS
            compose.onNode(labelled(text(R.string.picker_reload))).performClick()
        }
        compose.onAllNodes(hasText(UNKNOWN))[0].performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.contact_to_box)).performClick()
        compose.onNode(hasText(book) and hasAnyAncestor(isDialog())).performClick()
        if (whileBusy) compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        compose.onNodeWithText(text(R.string.hand_over)).performClick()
        // Queued behind the reading: done once that is.
        compose.waitUntil(BUSY_TIMEOUT_MILLIS) { booksWith(UNKNOWN).isNotEmpty() }
    }

    @Test
    fun handedOverFromTheRecentCalls() {
        setUpDemoBox()
        openPicker(R.string.picker_tab_recent)
        awaitText(UNKNOWN)
        handOver(SERVICES)
        assertEquals(listOf(SERVICES), booksWith(UNKNOWN))
    }

    /**
     * A phone book added on the box after the app read them: the box numbers them by their place,
     * so the id the app knows for "Services" now stands for another one. The entry goes into
     * "Services" all the same – not into the book that has its old id.
     */
    @Test
    fun intoTheRightPhoneBookAfterOneWasAddedOnTheBox() {
        setUpDemoBox()
        openPicker(R.string.picker_tab_recent)
        awaitText(UNKNOWN)
        val before = FritzDialer(demo).loadPhonebooks()
        Tr064Client(demo).call(FritzPhonebooks.SERVICE_TYPE, FritzPhonebooks.CONTROL_URL, "AddPhonebook",
            mapOf("NewPhonebookName" to "Doctors", "NewPhonebookExtraID" to ""))
        val after = FritzDialer(demo).loadPhonebooks()
        assertTrue("\"Services\" has moved", before.first { it.name == SERVICES }.id != after.first { it.name == SERVICES }.id)

        handOver(SERVICES)
        assertEquals(listOf(SERVICES), booksWith(UNKNOWN))
    }

    /** Tapped while the box was busy: it used to be dropped – now it waits its turn. */
    @Test
    fun handedOverWhileTheBoxIsBusy() {
        setUpDemoBox()
        openPicker(R.string.picker_tab_recent)
        awaitText(UNKNOWN)
        try {
            handOver(SERVICES, whileBusy = true)
        } finally {
            DemoBox.latencyMillis = 150
        }
        assertEquals(listOf(SERVICES), booksWith(UNKNOWN))
    }

    private companion object {
        /** Each answer of the demo box this slow: reading the call list and phone books anew takes some seconds. */
        const val SLOW_MILLIS = 600L
        const val BUSY_TIMEOUT_MILLIS = 60_000L

        /** A caller of the demo box' call list that is in none of its phone books. */
        const val UNKNOWN = "08999998350"
        const val SERVICES = "Services"
    }
}
