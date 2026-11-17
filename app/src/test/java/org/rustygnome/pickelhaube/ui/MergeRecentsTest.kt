package org.rustygnome.pickelhaube.ui

import org.rustygnome.pickelhaube.tr064.RecentCall
import org.rustygnome.pickelhaube.tr064.RecentCall.Direction.INCOMING
import org.rustygnome.pickelhaube.tr064.RecentCall.Direction.MISSED
import org.rustygnome.pickelhaube.tr064.RecentCall.Direction.OUTGOING
import org.rustygnome.pickelhaube.tr064.RecentCall.Source.FRITZBOX
import org.rustygnome.pickelhaube.tr064.RecentCall.Source.PHONE
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeRecentsTest {

    /** Stand-in for numberKey (which needs Android): digits only. */
    private val digits: (String) -> String = { n -> n.filter(Char::isDigit) }

    @Test
    fun `sorts both sources newest first`() {
        val merged = mergeRecents(
            listOf(RecentCall("111", null, 10, FRITZBOX), RecentCall("333", null, 30, FRITZBOX)) +
                listOf(RecentCall("222", null, 20, PHONE)),
            digits,
        )
        assertEquals(listOf("333", "222", "111"), merged.map { it.number })
    }

    @Test
    fun `keeps each number once, at its latest call`() {
        val merged = mergeRecents(
            listOf(
                RecentCall("030 123", null, 10, FRITZBOX),
                RecentCall("030-123", null, 50, PHONE),
                RecentCall("999", null, 40, FRITZBOX),
            ),
            digits,
        )
        assertEquals(listOf("030-123", "999"), merged.map { it.number })
        assertEquals(PHONE, merged.first().source)
    }

    @Test
    fun `takes a missing name from an older call of the same number`() {
        val merged = mergeRecents(
            listOf(
                RecentCall("123", "Alice", 10, FRITZBOX),
                RecentCall("123", null, 20, PHONE),
            ),
            digits,
        )
        assertEquals(listOf(RecentCall("123", "Alice", 20, PHONE, count = 2)), merged)
    }

    @Test
    fun `shows a number with the direction of its latest call`() {
        val merged = mergeRecents(
            listOf(
                RecentCall("123", null, 10, FRITZBOX, OUTGOING),
                RecentCall("123", null, 20, PHONE, INCOMING),
            ),
            digits,
        )
        assertEquals(INCOMING, merged.single().direction)
    }

    @Test
    fun `counts the calls of one direction in a row up to the latest`() {
        val merged = mergeRecents(
            listOf(
                RecentCall("123", null, 10, FRITZBOX, MISSED),
                RecentCall("123", null, 20, FRITZBOX, OUTGOING),
                RecentCall("123", null, 30, FRITZBOX, MISSED),
                RecentCall("123", null, 40, PHONE, MISSED),
                RecentCall("123", null, 50, FRITZBOX, MISSED),
            ),
            digits,
        )
        // The missed call before the one dialled back does not count.
        assertEquals(3, merged.single().count)
        assertEquals(MISSED, merged.single().direction)
    }

    @Test
    fun `counts each number on its own`() {
        val merged = mergeRecents(
            listOf(
                RecentCall("111", null, 10, FRITZBOX, MISSED),
                RecentCall("222", null, 20, FRITZBOX, MISSED),
                RecentCall("111", null, 30, FRITZBOX, MISSED),
                RecentCall("333", null, 40, FRITZBOX, INCOMING),
            ),
            digits,
        )
        assertEquals(listOf(1, 2, 1), merged.map { it.count })
    }

    @Test
    fun `a single call counts once`() {
        val merged = mergeRecents(
            listOf(RecentCall("123", null, 10, FRITZBOX, MISSED), RecentCall("123", null, 20, FRITZBOX, INCOMING)),
            digits,
        )
        assertEquals(1, merged.single().count)
    }
}
