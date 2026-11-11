package org.rustygnome.pickelhaube

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts

/** The welcome tour on a fresh install. */
@RunWith(AndroidJUnit4::class)
class WelcomeFlowTest : FlowTest() {

    /** First start: the tour, its demo link, "All set!" – and the dialer with the demo box, still there after a new start. */
    @Test
    fun firstStartWithDemoBox() {
        launch()
        compose.onNodeWithText(text(R.string.welcome_hello)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.welcome_start)).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.connection_demo)).performClick()
        awaitText(R.string.welcome_ready_title)
        // The tour names the phone the box rings first.
        awaitText(DEMO_PHONE)

        compose.onNodeWithText(text(R.string.welcome_start_dialling)).performClick()
        awaitText(R.string.dialer_number_label)
        awaitText(DEMO_PHONE)

        // Started anew (a new activity and view model): the box is stored, no tour again.
        restart()
        awaitText(R.string.dialer_number_label)
        compose.onNodeWithText(text(R.string.welcome_hello)).assertDoesNotExist()
    }

    /**
     * The widget placed before any box is set up (fixed in 1.6.1): the launcher opens the app for the
     * widget's setup. The tour comes first and connects – then the widget's setup follows.
     */
    @Test
    fun widgetPlacedBeforeAnyBox() {
        launch(LauncherShortcuts.widgetSetupIntent(context, WIDGET_ID))
        compose.onNodeWithText(text(R.string.welcome_hello)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.widget_setup_add), substring = true).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.connection_demo)).performClick()
        awaitText(R.string.welcome_ready_title)

        compose.onNodeWithText(text(R.string.welcome_start_dialling)).performClick()
        awaitText(R.string.widget_setup_add)
        compose.onNodeWithText(text(R.string.widget_setup_name)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.widget_setup_transparent)).assertIsDisplayed()

        // Left without saving: nothing is written for the made-up widget.
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private companion object {
        /** No widget has this id: an unknown one is just an empty list. */
        const val WIDGET_ID = 4711
    }
}
