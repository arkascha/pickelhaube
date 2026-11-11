package org.rustygnome.pickelhaube

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts

/**
 * The welcome tour on a fresh install, with the demo box built into the app – no FRITZ!Box needed.
 * Each test starts with the app's data cleared (see the orchestrator in app/build.gradle.kts).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WelcomeFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun text(@StringRes id: Int) = context.getString(id)

    /** Waits until [id]'s text is shown (also within a longer one) – connecting to the demo box takes a moment. */
    private fun awaitText(@StringRes id: Int) =
        compose.waitUntilAtLeastOneExists(hasText(text(id), substring = true), CONNECT_TIMEOUT_MILLIS)

    private fun launch(intent: Intent? = null) {
        scenario = if (intent == null) ActivityScenario.launch(MainActivity::class.java) else ActivityScenario.launch(intent)
    }

    @After
    fun close() {
        scenario?.close()
    }

    /** First start: the tour, its demo link, "All set!" – and the dialer with the demo box, still there after a new start. */
    @Test
    fun firstStartWithDemoBox() {
        launch()
        compose.onNodeWithText(text(R.string.welcome_hello)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.welcome_start)).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.connection_demo)).performClick()
        awaitText(R.string.welcome_ready_title)
        // The tour names the phone the box rings first: the demo box' first DECT handset.
        compose.waitUntilAtLeastOneExists(hasText(DEMO_PHONE, substring = true), CONNECT_TIMEOUT_MILLIS)

        compose.onNodeWithText(text(R.string.welcome_start_dialling)).performClick()
        awaitText(R.string.dialer_number_label)
        compose.onNodeWithText(DEMO_PHONE).assertIsDisplayed()

        // Started anew (a new activity and view model): the box is stored, no tour again.
        scenario?.close()
        launch()
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

        /** The demo box' phone for dial help (see DemoBox). */
        const val DEMO_PHONE = "Kitchen"

        const val CONNECT_TIMEOUT_MILLIS = 15_000L
    }
}
