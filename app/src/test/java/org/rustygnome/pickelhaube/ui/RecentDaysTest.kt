package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rustygnome.pickelhaube.tr064.RecentCall
import org.rustygnome.pickelhaube.tr064.RecentCall.Source.FRITZBOX
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecentDaysTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun call(number: String, year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        RecentCall(number, null, ZonedDateTime.of(year, month, day, hour, minute, 0, 0, berlin).toInstant().toEpochMilli(), FRITZBOX)

    @Test
    fun `calls by the day they were on, newest day first, in the order given`() {
        val calls = listOf(
            call("1", 2026, 11, 13, 9),
            call("2", 2026, 11, 13, 0, 5),
            // Late in the evening: still that day in the phone's time zone, though the next one in UTC.
            call("3", 2026, 11, 12, 23, 30),
            call("4", 2026, 11, 12, 8),
            call("5", 2025, 12, 31, 12),
        )
        val days = byDay(calls, berlin)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 12), LocalDate.of(2025, 12, 31)),
            days.map { it.first },
        )
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4"), listOf("5")), days.map { d -> d.second.map { it.number } })
    }

    @Test
    fun `no calls, no days`() {
        assertEquals(emptyList<Pair<LocalDate, List<RecentCall>>>(), byDay(emptyList(), berlin))
    }
}
