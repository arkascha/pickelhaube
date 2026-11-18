package org.rustygnome.pickelhaube

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts

/**
 * A number put onto the home screen: offered in the long-press menu of the recent calls (where the
 * launcher can take shortcuts), and the shortcut – started as the launcher starts it – dials at once.
 * The launcher's own question whether to add it is not the app's and is left out.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenShortcutFlowTest : FlowTest() {

    @Test
    fun offeredForARecentCall() {
        assumeTrue(LauncherShortcuts.canPin(context))
        setUpDemoBox()
        openPicker(R.string.picker_tab_recent)
        awaitBoxIdle()
        compose.onAllNodes(hasText(MUM))[0].performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.number_to_home)).assertExists()
    }

    @Test
    fun dialsItsNumberAtOnce() {
        setUpDemoBox()
        restart(LauncherShortcuts.dialNowIntent(context, NUMBER))
        // The dialer, following the call – no green button pressed.
        awaitText(text(R.string.call_connected, shown(NUMBER), "").trimEnd(), CALL_TIMEOUT_MILLIS)
    }

    private companion object {
        const val MUM = "Mum"
        const val NUMBER = "03023125203"
        const val CALL_TIMEOUT_MILLIS = 30_000L
    }
}
