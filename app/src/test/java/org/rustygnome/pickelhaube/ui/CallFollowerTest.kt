package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rustygnome.pickelhaube.tr064.CallEvent
import org.rustygnome.pickelhaube.ui.CallFollower.Step

class CallFollowerTest {

    private val follower = CallFollower("0301234567") { it }

    private fun call(connection: String, called: String = "0301234567", caller: String = "301111111") =
        CallEvent.Call(connection, extension = "10", caller = caller, called = called, line = "SIP0")

    @Test
    fun `follows the call dialled from picked up to ended`() {
        assertEquals(Step.Dialling("301111111"), follower.take(call("1")))
        assertEquals(Step.Connected, follower.take(CallEvent.Connect("1", "10", "0301234567")))
        assertEquals(Step.Ended(CallProgress.Ended(42)), follower.take(CallEvent.Disconnect("1", 42)))
    }

    @Test
    fun `ended without connecting is not answered`() {
        follower.take(call("1"))
        assertEquals(Step.Ended(CallProgress.NotAnswered), follower.take(CallEvent.Disconnect("1", 0)))
    }

    @Test
    fun `other calls of the household are ignored`() {
        // Before the call followed is reported: another number dialled, an incoming call.
        assertEquals(Step.Ignored, follower.take(call("7", called = "0409999999")))
        assertEquals(Step.Ignored, follower.take(CallEvent.Ring("8", "0171222", "301111111")))
        assertEquals(Step.Ignored, follower.take(CallEvent.Disconnect("7", 5)))
        follower.take(call("1"))
        // Meanwhile: the other connection's reports.
        assertEquals(Step.Ignored, follower.take(CallEvent.Connect("8", "11", "0171222")))
        assertEquals(Step.Ignored, follower.take(CallEvent.Disconnect("8", 30)))
        assertEquals(Step.Ended(CallProgress.NotAnswered), follower.take(CallEvent.Disconnect("1", 0)))
    }

    @Test
    fun `nothing counts before the box reports dialling the number`() {
        // A connect or end without the dialling report first cannot be told to be this call.
        assertEquals(Step.Ignored, follower.take(CallEvent.Connect("1", "10", "0301234567")))
        assertEquals(Step.Ignored, follower.take(CallEvent.Disconnect("1", 3)))
    }

    @Test
    fun `no own number said, no own number shown`() {
        assertEquals(Step.Dialling(null), follower.take(call("1", caller = "")))
    }

    @Test
    fun `a second dialling report of the same call says nothing new`() {
        follower.take(call("1"))
        assertEquals(Step.Ignored, follower.take(call("1")))
    }
}
