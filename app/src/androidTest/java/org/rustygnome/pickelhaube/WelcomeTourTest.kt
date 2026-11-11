package org.rustygnome.pickelhaube

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The welcome tour page by page, as someone with a FRITZ!Box goes through it: what the box needs,
 * where it is, who logs in, connecting – with the demo box at its address "demo" standing in for a
 * real one. And what goes wrong: a wrong password, an address where nothing answers, leaving the tour.
 */
@RunWith(AndroidJUnit4::class)
class WelcomeTourTest : FlowTest() {

    private val next get() = compose.onNodeWithText(text(R.string.welcome_next))

    /** From the first page to "Where is your FRITZ!Box?", through "What your FRITZ!Box needs". */
    private fun toFindPage() {
        launch()
        compose.onNodeWithText(text(R.string.welcome_start)).performClick()
        awaitText(R.string.welcome_prepare_title)
        // The link to the guide's how-to, below the three settings.
        compose.onNodeWithText(text(R.string.welcome_prepare_how)).performScrollTo().assertIsDisplayed()
        next.performClick()
        awaitText(R.string.welcome_find_title)
    }

    /** The address entered on the find page, replacing the "fritz.box" offered. */
    private fun enterHost(host: String) {
        compose.onNodeWithText(text(R.string.connection_host)).performTextReplacement(host)
    }

    /** On to the login page and its password; [user] typed unless the box offers its only one. */
    private fun login(password: String, user: String? = null) {
        next.performClick()
        awaitText(R.string.welcome_login_title)
        if (user != null) compose.onNodeWithText(text(R.string.connection_user)).performTextInput(user)
        // Without a password there is no going on.
        next.assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.welcome_password_label)).performTextInput(password)
        next.assertIsEnabled()
        next.performClick()
        awaitText(R.string.welcome_connect_title)
    }

    private fun connect() = compose.onNodeWithText(text(R.string.welcome_connect_button)).performClick()

    /** All pages, with the demo box entered as a FRITZ!Box would be: found, its only user taken over, connected. */
    @Test
    fun throughAllPages() {
        toFindPage()
        enterHost(DEMO_HOST)
        awaitText(text(R.string.welcome_find_found, DEMO_MODEL))

        next.performClick()
        awaitText(R.string.welcome_login_title)
        // The box' only user, entered already.
        awaitNode(hasText(text(R.string.connection_user)) and hasText(DEMO_USER))
        compose.onNodeWithText(text(R.string.welcome_password_label)).performTextInput(DEMO_PASSWORD)
        next.performClick()

        // The summary: the box' own name (none was entered), its address, the user.
        awaitText(R.string.welcome_connect_title)
        awaitText(DEMO_NAME)
        connect()
        awaitText(R.string.welcome_ready_title)
        awaitText(DEMO_PHONE)
        compose.onNodeWithText(text(R.string.welcome_start_dialling)).performClick()
        awaitText(R.string.dialer_number_label)
        awaitText(DEMO_PHONE)
    }

    /** The box rejects the password: "Enter the password again" goes back to it, emptied – right then, it connects. */
    @Test
    fun wrongPasswordThenTheRightOne() {
        toFindPage()
        enterHost(DEMO_HOST)
        awaitText(text(R.string.welcome_find_found, DEMO_MODEL))
        login(password = "wrong")
        connect()

        awaitText(R.string.error_login_title)
        // Offered twice: in the error dialog, instead of its "Close" – and on the page behind it, next to "Connect".
        val changePassword = hasText(text(R.string.welcome_change_password))
        compose.onNode(changePassword and hasAnyAncestor(isDialog()).not()).assertExists()
        compose.onNode(changePassword and hasAnyAncestor(isDialog())).performClick()
        awaitText(R.string.welcome_login_title)
        // The password field is empty again: no going on without one.
        next.assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.welcome_password_label)).performTextInput(DEMO_PASSWORD)
        next.performClick()
        awaitText(R.string.welcome_connect_title)
        connect()
        awaitText(R.string.welcome_ready_title)
    }

    /** Nothing answers at the address: the tour says so, connecting fails, and "All settings" keeps what was entered. */
    @Test
    fun nothingAnswersAtTheAddress() {
        toFindPage()
        enterHost(NOWHERE)
        awaitText(text(R.string.welcome_find_none, NOWHERE))
        login(password = "secret", user = "anna")
        connect()

        awaitText(R.string.error_not_found_title)
        compose.onNodeWithText(text(R.string.close)).performClick()
        awaitText(R.string.welcome_connect_failed)
        compose.onNodeWithText(text(R.string.welcome_all_settings)).performClick()
        // The full connection page, with the address entered in the tour.
        awaitNode(hasText(text(R.string.connection_host)) and hasText(NOWHERE))
        compose.onNodeWithText(text(R.string.welcome_hello)).assertDoesNotExist()
    }

    /** Back goes page by page; no address, no going on; "Skip" leaves for the empty connection page. */
    @Test
    fun backAndSkip() {
        toFindPage()
        enterHost("")
        next.assertIsNotEnabled()
        compose.onNode(labelled(text(R.string.back))).performClick()
        awaitText(R.string.welcome_prepare_title)
        compose.onNode(labelled(text(R.string.back))).performClick()
        awaitText(R.string.welcome_hello)
        // The first page on first start: nothing to go back to.
        compose.onNode(labelled(text(R.string.back))).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.welcome_skip)).performClick()
        awaitText(R.string.connection_host)
        compose.onNodeWithText(text(R.string.welcome_hello)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.connection_demo)).assertIsDisplayed()
    }

    private companion object {
        /** The demo box' address, user and password (see DemoBox). */
        const val DEMO_HOST = "demo"
        const val DEMO_USER = "demo"
        const val DEMO_PASSWORD = "demo"
        const val DEMO_MODEL = "FRITZ!Box 7590 AX"

        /** The name the demo box gives itself – taken when none is entered. */
        const val DEMO_NAME = "FRITZ!Box 7590 AX (Demo)"

        /** No box anywhere: the reserved top-level domain never resolves. */
        const val NOWHERE = "fritzbox.invalid"
    }
}
