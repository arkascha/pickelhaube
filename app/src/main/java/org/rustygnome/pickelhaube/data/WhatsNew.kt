package org.rustygnome.pickelhaube.data

import org.rustygnome.pickelhaube.R

/**
 * The "What's new" note: a few lines about a version, shown once after updating to it (see
 * [toShow]) and on request from the settings. Each version that has something to say gets a string
 * array `whats_new_<version code>` in all languages and an entry in [NOTES]; a version without one
 * shows nothing.
 */
object WhatsNew {

    /** The notes by version code. */
    val NOTES: Map<Int, Int> = mapOf(
        8 to R.array.whats_new_8,
    )

    /** At most this many points at once, when several versions were skipped. */
    const val MAX_POINTS = 6

    /**
     * The versions whose notes to show now, newest first: those after [lastSeen] up to [current].
     * [lastSeen] null means no version was recorded yet – a new install, which the welcome tour
     * explains ([configured] false: nothing), or an update from a version before this note existed
     * ([configured]: a box is set up – the current version's note).
     */
    fun toShow(lastSeen: Int?, current: Int, configured: Boolean, available: Set<Int> = NOTES.keys): List<Int> {
        val after = when {
            lastSeen != null -> lastSeen
            configured -> current - 1
            else -> return emptyList()
        }
        return available.filter { it in (after + 1)..current }.sortedDescending()
    }

    /** The newest version up to [current] that has a note – for "What's new" in the settings; null: none. */
    fun latest(current: Int, available: Set<Int> = NOTES.keys): Int? = available.filter { it <= current }.maxOrNull()
}
