package org.rustygnome.pickelhaube

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Recent" by day, and just the missed calls: the demo box' call list has two in a row from Grandma,
 * yesterday – shown once, as "Grandma (2)".
 */
@RunWith(AndroidJUnit4::class)
class RecentFilterFlowTest : FlowTest() {

    @Test
    fun missedCallsOnly() {
        setUpDemoBox()
        openPicker(R.string.picker_tab_recent)
        awaitText("Mum")
        awaitText("Grandma")

        compose.onNode(labelled(text(R.string.recents_filter_missed))).performClick()
        awaitGone(hasText("Mum"))
        compose.onNodeWithText("Grandma").assertExists()
        compose.onNode(hasText("(2)", substring = true)).assertExists()

        compose.onNode(labelled(text(R.string.recents_filter_missed))).performClick()
        awaitText("Mum")
    }
}
