package org.rustygnome.pickelhaube

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts
import org.rustygnome.pickelhaube.widget.WidgetEntry
import org.rustygnome.pickelhaube.widget.WidgetLists

/** A dial widget's numbers set up: added from the phone books, moved, one removed, saved – and shown again. */
@RunWith(AndroidJUnit4::class)
class WidgetListFlowTest : FlowTest() {

    private val setup get() = LauncherShortcuts.widgetSetupIntent(context, WIDGET_ID)

    /** "Add number", and a number tapped in the phone books. */
    private fun add(number: String) {
        compose.onNodeWithText(text(R.string.widget_setup_add), substring = true).performClick()
        awaitText(R.string.picker_title)
        pickerTab(R.string.phone_books)
        notNowForContacts()
        // Once the box' phone books are read.
        awaitText(number)
        compose.onNodeWithText(number).performClick()
        awaitText(R.string.widget_setup_add)
    }

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun numbersAddedMovedRemovedAndSaved() {
        setUpDemoBox()
        restart(setup)
        awaitText(R.string.widget_setup_add)
        compose.onNodeWithText(text(R.string.widget_setup_hint)).assertExists()
        compose.onNodeWithText(text(R.string.widget_setup_name)).performTextInput(TITLE)

        add(ANNA_HOME)
        add(BEN_MOBILE)
        awaitText(ANNA)
        awaitText(BEN)
        assertTrue("Anna first, as added first", top(ANNA) < top(BEN))

        // Ben moved up (the first one cannot be), Anna removed.
        compose.onAllNodes(labelled(text(R.string.widget_setup_up)))[1].performClick()
        assertTrue("Ben first, moved up", top(BEN) < top(ANNA))
        compose.onNode(labelled(text(R.string.widget_setup_remove, ANNA))).performClick()
        compose.onNodeWithText(ANNA).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.widget_setup_save)).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { WidgetLists.get(context, WIDGET_ID).entries.isNotEmpty() }
        val saved = WidgetLists.get(context, WIDGET_ID)
        assertEquals(TITLE, saved.title)
        assertEquals(listOf(WidgetEntry(BEN, BEN_MOBILE)), saved.entries)

        // Its setup opened again, from the widget: as saved.
        restart(setup)
        awaitText(BEN)
        awaitText(BEN_MOBILE)
        compose.onNodeWithText(TITLE).assertExists()
        compose.onNodeWithText(ANNA).assertDoesNotExist()
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private companion object {
        /** No widget has this id: its list is stored all the same. */
        const val WIDGET_ID = 4712
        const val TITLE = "Family"

        /** Contacts of the demo box' phone book "Family & friends" (see DemoBox). */
        const val ANNA = "Anna Becker"
        const val ANNA_HOME = "03023125101"
        const val BEN = "Ben Hoffmann"
        const val BEN_MOBILE = "017604069002"
    }
}
