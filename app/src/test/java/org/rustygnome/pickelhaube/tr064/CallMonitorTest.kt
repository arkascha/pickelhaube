package org.rustygnome.pickelhaube.tr064

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lines as the call monitor sends them (numbers made up). */
class CallMonitorTest {

    @Test
    fun `reads an outgoing call`() {
        assertEquals(
            CallEvent.Call("0", extension = "10", caller = "4930555", called = "0301234567", line = "SIP0"),
            CallMonitor.parse("27.10.26 14:03:11;CALL;0;10;4930555;0301234567;SIP0;"),
        )
    }

    @Test
    fun `reads connect and disconnect`() {
        assertEquals(CallEvent.Connect("0", "10", "0301234567"), CallMonitor.parse("27.10.26 14:03:18;CONNECT;0;10;0301234567;"))
        assertEquals(CallEvent.Disconnect("0", 134), CallMonitor.parse("27.10.26 14:05:32;DISCONNECT;0;134;\r\n"))
    }

    @Test
    fun `reads an incoming call`() {
        assertEquals(
            CallEvent.Ring("1", caller = "0401234", called = "4930555"),
            CallMonitor.parse("27.10.26 15:00:00;RING;1;0401234;4930555;SIP1;"),
        )
    }

    @Test
    fun `ignores what it cannot read`() {
        assertNull(CallMonitor.parse(""))
        assertNull(CallMonitor.parse("27.10.26 15:00:00;HOLD;1;"))
        assertNull(CallMonitor.parse("27.10.26 15:00:00;DISCONNECT;1;x;"))
        assertNull(CallMonitor.parse("27.10.26 15:00:00;CALL;1;10;"))
    }
}
