package org.rustygnome.pickelhaube

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.data.NetworkKind
import org.rustygnome.pickelhaube.tr064.DemoBox

/**
 * No box answers, as away from home: the title says so for the kind of network the phone is in, a tap
 * on it explains what helps, and "Search again" finds the box once it answers again.
 */
@RunWith(AndroidJUnit4::class)
class NotAtHomeFlowTest : FlowTest() {

    @After
    fun answerAgain() {
        DemoBox.answering = true
    }

    @Test
    fun explainedAndFoundAgain() {
        setUpDemoBox()
        awaitBoxIdle()
        DemoBox.answering = false
        // Pulled down from the title: looks for the boxes again – and finds none.
        compose.onRoot().performTouchInput {
            swipeDown(startY = height * 0.07f, endY = height * 0.57f, durationMillis = 500)
        }
        // The emulator is in a Wi-Fi (or a cable), with no VPN.
        val kind = NetworkKind.current(context)
        val notice = text(
            when (kind) {
                NetworkKind.NONE -> R.string.notice_no_network
                NetworkKind.MOBILE -> R.string.notice_mobile_data
                NetworkKind.LOCAL -> R.string.notice_other_network
                NetworkKind.VPN -> R.string.notice_vpn_no_box
            },
        )
        awaitText(notice)

        compose.onNode(hasText(notice)).performClick()
        awaitText(R.string.not_at_home_how)
        DemoBox.answering = true
        compose.onNode(hasText(text(R.string.not_at_home_search_again)) and hasAnyAncestor(isDialog())).performClick()
        // Found again: the box' name is back in the title.
        awaitGone(hasText(notice))
        awaitText(DEMO_BOX)
    }
}
