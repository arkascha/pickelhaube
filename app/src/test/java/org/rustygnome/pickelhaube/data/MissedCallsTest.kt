package org.rustygnome.pickelhaube.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rustygnome.pickelhaube.tr064.RecentCall
import org.rustygnome.pickelhaube.tr064.RecentCall.Direction
import org.rustygnome.pickelhaube.tr064.RecentCall.Source

class MissedCallsTest {

    private fun call(time: Long, direction: Direction, number: String, source: Source = Source.FRITZBOX) =
        RecentCall(number, null, time, source, direction)

    private val calls = listOf(
        call(500, Direction.MISSED, "0301"),
        call(400, Direction.INCOMING, "0302"),
        call(300, Direction.MISSED, "0303"),
        call(600, Direction.MISSED, "0304", Source.PHONE),
    )

    @Test
    fun `counts the box' missed calls after the last look only`() {
        assertEquals(listOf(500L, 300L), MissedCalls.newSince(calls, 200).map { it.time })
        assertEquals(listOf(500L), MissedCalls.newSince(calls, 400).map { it.time })
        assertEquals(emptyList<RecentCall>(), MissedCalls.newSince(calls, 500))
    }

    @Test
    fun `a later call with the number settles a missed one`() {
        val calledBack = calls + call(700, Direction.OUTGOING, "+49301", Source.PHONE)
        val sameNumber = { number: String -> number.removePrefix("+49").removePrefix("0") }
        assertEquals(listOf(300L), MissedCalls.newSince(calledBack, 200, sameNumber).map { it.time })
        // Missed twice: counted once, at the later one.
        val twice = calls + call(550, Direction.MISSED, "0303")
        assertEquals(listOf(550L, 500L), MissedCalls.newSince(twice, 200).map { it.time })
    }

    @Test
    fun `seen up to the box' newest call, never back`() {
        assertEquals(500L, MissedCalls.seenUntil(calls, 200))
        assertEquals(900L, MissedCalls.seenUntil(calls, 900))
        assertEquals(200L, MissedCalls.seenUntil(emptyList(), 200))
    }
}
