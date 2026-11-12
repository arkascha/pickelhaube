package org.rustygnome.pickelhaube

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule

/**
 * A flow through the real app, with the demo box built into it – no FRITZ!Box needed. Each test
 * starts with the app's data cleared (see the orchestrator in app/build.gradle.kts); texts are looked
 * up by their resources, so the tests run in any language the emulator is set to.
 */
@OptIn(ExperimentalTestApi::class)
abstract class FlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected var scenario: ActivityScenario<MainActivity>? = null

    protected fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Waits until [text] is shown (also within a longer one) – connecting to the demo box takes a moment. */
    protected fun awaitText(text: String, timeoutMillis: Long = TIMEOUT_MILLIS) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMillis)

    protected fun awaitText(@StringRes id: Int) = awaitText(text(id))

    /** Waits until a node matching [matcher] is shown. */
    protected fun awaitNode(matcher: SemanticsMatcher, timeoutMillis: Long = TIMEOUT_MILLIS) =
        compose.waitUntilAtLeastOneExists(matcher, timeoutMillis)

    /** Waits until no node matches [matcher] any more. */
    protected fun awaitGone(matcher: SemanticsMatcher, timeoutMillis: Long = TIMEOUT_MILLIS) =
        compose.waitUntilDoesNotExist(matcher, timeoutMillis)

    /**
     * Waits until the box is not busy (no progress bar): an action started meanwhile would be
     * dropped, as the app runs one request to the box at a time.
     */
    protected fun awaitBoxIdle() = awaitGone(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))

    /** A node with [label] as its text or its content description (icon buttons have only the latter). */
    protected fun labelled(label: String): SemanticsMatcher = hasText(label) or hasContentDescription(label)

    protected fun launch(intent: Intent? = null) {
        scenario = if (intent == null) ActivityScenario.launch(MainActivity::class.java) else ActivityScenario.launch(intent)
    }

    /** Started anew: a new activity and view model, with what the app stored (the demo box keeps its state). */
    protected fun restart(intent: Intent? = null) {
        scenario?.close()
        launch(intent)
    }

    /** A key of the dialer's keypad (the number field above it may hold the same digits). */
    protected fun key(digit: Char): SemanticsMatcher =
        hasText(digit.toString()) and SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText)

    /** From the dialer to the number picker, on tab [tab] (one of its titles). */
    protected fun openPicker(@StringRes tab: Int) {
        compose.onNode(labelled(text(R.string.dialer_choose_number))).performClick()
        awaitText(R.string.picker_title)
        pickerTab(tab)
    }

    /** Switches the number picker to tab [tab]. */
    protected fun pickerTab(@StringRes tab: Int) {
        compose.onNode(hasText(text(tab)) and hasClickAction()).performClick()
    }

    /** The phone books ask once per picker whether to include the phone's contacts: not now. */
    protected fun notNowForContacts() {
        awaitText(R.string.device_contacts_question_title)
        compose.onNodeWithText(text(R.string.not_now)).performClick()
    }

    /** First start through the welcome tour with the demo box, on to the dialer. */
    protected fun setUpDemoBox() {
        launch()
        compose.onNodeWithText(text(R.string.connection_demo)).performClick()
        awaitText(R.string.welcome_ready_title)
        compose.onNodeWithText(text(R.string.welcome_start_dialling)).performClick()
        awaitText(R.string.dialer_number_label)
        // The phones are loaded: the demo box' phone for dial help is offered.
        awaitText(DEMO_PHONE)
    }

    @After
    fun close() {
        scenario?.close()
    }

    protected companion object {
        /** The demo box' phone for dial help (see DemoBox). */
        const val DEMO_PHONE = "Kitchen"

        /** The name the welcome tour gives the demo box. */
        const val DEMO_BOX = "Demo"

        const val TIMEOUT_MILLIS = 15_000L
    }
}
