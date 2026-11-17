package org.rustygnome.pickelhaube

import android.util.Log
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's main screens through Google's Accessibility Test Framework – the checks behind the
 * Accessibility Scanner and Play's pre-launch report: labels for screen readers, touch target sizes,
 * text contrast and the like. Errors fail the test; warnings are only logged (tag "A11yCheck"), as some
 * are by design (dimmed letters of the alphabet bar, light placeholders) or guesses of the framework.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest : FlowTest() {

    private val errors = mutableListOf<String>()

    /** Checks what is on the screen now, [screen] naming it in the findings. */
    private fun check(screen: String) {
        compose.waitForIdle()
        val validator = AccessibilityValidator()
            .setCheckPreset(AccessibilityCheckPreset.LATEST)
            .setRunChecksFromRootView(true)
            .setThrowExceptionForErrors(false)
        scenario!!.onActivity { activity ->
            validator.checkAndReturnResults(activity.window.decorView)
                .filter { it.type == AccessibilityCheckResultType.ERROR || it.type == AccessibilityCheckResultType.WARNING }
                .forEach { result ->
                    val element = result.accessibilityHierarchyCheckResult?.element
                    val finding = "$screen: ${result.sourceCheckClass.simpleName} at ${element?.boundsInScreen} " +
                        "(${element?.text ?: element?.contentDescription ?: element?.className}) – ${result.getMessage(Locale.ENGLISH)}"
                    Log.w(TAG, "${result.type} $finding")
                    if (result.type == AccessibilityCheckResultType.ERROR) errors += finding
                }
        }
    }

    @Test
    fun mainScreens() {
        launch()
        awaitText(R.string.connection_demo)
        check("welcome tour")
        compose.onNodeWithText(text(R.string.connection_demo)).performClick()
        awaitText(R.string.welcome_ready_title)
        check("welcome tour, ready")
        compose.onNodeWithText(text(R.string.welcome_start_dialling)).performClick()
        awaitText(DEMO_PHONE)
        check("dialer")

        NUMBER.forEach { compose.onNode(key(it)).performClick() }
        awaitText(shown(NUMBER))
        check("dialer with a number")

        openPicker(R.string.picker_tab_recent)
        awaitBoxIdle()
        check("recent calls")
        pickerTab(R.string.speed_dial)
        awaitBoxIdle()
        check("speed dials")
        pickerTab(R.string.phone_books)
        notNowForContacts()
        awaitBoxIdle()
        check("phone books")
        compose.onNode(labelled(text(R.string.back))).performClick()

        compose.onNode(labelled(text(R.string.deflections_off))).performClick()
        awaitBoxIdle()
        check("call forwarding")
        compose.onNode(labelled(text(R.string.back))).performClick()

        compose.onNode(labelled(text(R.string.settings))).performClick()
        check("settings")
        compose.onNodeWithText(DEMO_BOX).performClick()
        check("connection")
        compose.onNode(labelled(text(R.string.back))).performClick()
        compose.onNode(labelled(text(R.string.back))).performClick()

        compose.onNode(labelled(text(R.string.dialer_dial))).performClick()
        awaitText(text(R.string.call_connected, shown(NUMBER), "").trimEnd())
        check("call")

        assertTrue("Accessibility errors:\n" + errors.joinToString("\n"), errors.isEmpty())
    }

    private companion object {
        const val TAG = "A11yCheck"
        const val NUMBER = "0301234"
    }
}
