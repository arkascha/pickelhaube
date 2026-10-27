package org.rustygnome.pickelhaube.ui

import java.util.Locale

/**
 * How the call placed last is going, as far as Pickelhaube knows: from its own request, then from the
 * box' call monitor (see [org.rustygnome.pickelhaube.tr064.CallMonitor]), if that is on.
 */
sealed interface CallProgress {
    /**
     * The box rings [phone] (null: whichever phone is set up for dial help) and dials [number] once
     * it is picked up. [monitorOff]: the box' call monitor just turned out to be off, so nothing more
     * will be known – shown with how to switch it on.
     */
    data class PickUp(val phone: String?, val number: String, val monitorOff: Boolean = false) : CallProgress

    /** Picked up: the box dials [number], from its own number [from] (if it said). */
    data class Dialling(val number: String, val from: String?) : CallProgress

    /** The other side answered, at [since] ([android.os.SystemClock.elapsedRealtime]). */
    data class Connected(val number: String, val since: Long) : CallProgress

    /** The call ended after [seconds] talking. */
    data class Ended(val seconds: Int) : CallProgress

    /** The other side did not answer, or was busy – the call monitor does not tell them apart. */
    data object NotAnswered : CallProgress

    /** The box never dialled: the phone was not picked up in time. */
    data object NotPickedUp : CallProgress
}

/**
 * Whether the number the box [reported] dialling is the one Pickelhaube asked it to dial: alike
 * however written ([key], see [numberKey]), or one ending in the other – the box may add or drop an
 * area code – as long as at least six digits are left to compare.
 */
internal fun isDialledNumber(reported: String, dialled: String, key: (String) -> String): Boolean {
    if (reported.isBlank()) return false
    if (key(reported) == key(dialled)) return true
    val a = reported.filter(Char::isDigit)
    val b = dialled.filter(Char::isDigit)
    return minOf(a.length, b.length) >= 6 && (a.endsWith(b) || b.endsWith(a))
}

/** A call's duration as shown: `0:42`, `12:05`, `1:02:09`. */
internal fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s % 60) else "%d:%02d".format(Locale.ROOT, m, s % 60)
}
