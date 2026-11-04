package org.rustygnome.pickelhaube.data

import org.rustygnome.pickelhaube.tr064.RecentCall

/**
 * The box' missed calls not looked at yet: those after the newest call of its list as it was last
 * shown in "Recent" – and not followed by another call with that number, e.g. a call back, which
 * settles it. The phone's own missed calls are left to Android, which reports them itself.
 */
object MissedCalls {

    /**
     * The box' missed calls in [calls] after [seenUntil] (ms since the epoch) that are still the
     * latest call with their number, compared via [key] – as "Recent" shows each number once, at its
     * latest call.
     */
    fun newSince(calls: List<RecentCall>, seenUntil: Long, key: (String) -> String = { it }): List<RecentCall> =
        calls.groupBy { key(it.number) }.values
            .map { same -> same.maxBy { it.time } }
            .filter { it.source == RecentCall.Source.FRITZBOX && it.direction == RecentCall.Direction.MISSED && it.time > seenUntil }
            .sortedByDescending { it.time }

    /** Up to when [calls] count as seen once shown: their newest call; [seenUntil] if there is none newer. */
    fun seenUntil(calls: List<RecentCall>, seenUntil: Long): Long =
        maxOf(seenUntil, calls.filter { it.source == RecentCall.Source.FRITZBOX }.maxOfOrNull { it.time } ?: 0L)
}
