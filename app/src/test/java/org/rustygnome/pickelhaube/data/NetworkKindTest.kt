package org.rustygnome.pickelhaube.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkKindTest {

    @Test
    fun aVpnCountsBeforeTheNetworkBeneathIt() {
        assertEquals(NetworkKind.NONE, NetworkKind.of(connected = false, vpn = false, cellularOnly = false))
        assertEquals(NetworkKind.VPN, NetworkKind.of(connected = true, vpn = true, cellularOnly = true))
        assertEquals(NetworkKind.MOBILE, NetworkKind.of(connected = true, vpn = false, cellularOnly = true))
        assertEquals(NetworkKind.LOCAL, NetworkKind.of(connected = true, vpn = false, cellularOnly = false))
    }
}
