package org.rustygnome.pickelhaube

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** "Recent" by day, and just the missed calls: the demo box' call list has one, from Grandma, yesterday. */
@RunWith(AndroidJUnit4::class)
class RecentFilterFlowTest : FlowTest() {

    @Test
    fun missedCallsOnly() {
        setUpDemoBox()
        openPicker(R.string.picker_tab_recent)
        awaitText("Mum")
        awaitText("Grandma")

        compose.onNode(labelled(text(R.string.recents_filter_missed))).performClick()
        awaitGone(androidx.compose.ui.test.hasText("Mum"))
        compose.onNodeWithText("Grandma").assertExists()

        compose.onNode(labelled(text(R.string.recents_filter_missed))).performClick()
        awaitText("Mum")
    }
}
