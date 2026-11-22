package org.rustygnome.pickelhaube

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.NetworkKind
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.tr064.FritzConfig

/**
 * The demo box answers from any network: the dialer says it is in use, and it never stands in for a
 * real box that is out of reach – that one stays in use, and the dialer says no box is reachable.
 */
@RunWith(AndroidJUnit4::class)
class DemoBoxFlowTest : FlowTest() {

    @Test
    fun markedWhileInUse_neverTakenForAnAbsentBox() {
        setUpDemoBox()
        awaitText(R.string.dialer_demo_strip)

        // A real box beside it, in use – one the emulator cannot reach (TEST-NET-1).
        val settings = SettingsRepository(context)
        val home = BoxProfile(
            name = "Home",
            config = FritzConfig(host = "192.0.2.1", username = "user", password = "secret"),
            deviceId = "uuid:home-out-of-reach",
        )
        settings.saveProfile(home)
        settings.activeProfileId = home.id
        restart()

        // Not switched to the demo box: the real one stays in use, and the dialer says it is not reachable.
        val notice = text(
            when (NetworkKind.current(context)) {
                NetworkKind.NONE -> R.string.notice_no_network
                NetworkKind.MOBILE -> R.string.notice_mobile_data
                NetworkKind.LOCAL -> R.string.notice_other_network
                NetworkKind.VPN -> R.string.notice_vpn_no_box
            },
        )
        awaitNode(hasText(notice))
        assertEquals(home.id, SettingsRepository(context).activeProfileId)
        compose.onAllNodesWithText(text(R.string.dialer_demo_strip)).assertCountEquals(0)
    }
}
