package org.rustygnome.pickelhaube.contacts

import android.provider.ContactsContract.CommonDataKinds.Phone

/**
 * What the "Call via FRITZ!Box" rows in the phone's contacts should be, and what to change to get there –
 * without Android, so it can be tested on its own. [ContactActions] reads the contacts, asks [wanted]
 * and [plan], and carries the plan out.
 */
object ContactActionsPlan {

    /** One action row: the number it calls and the text the contacts app shows for it. */
    data class Row(val number: String, val detail: String)

    /** A phone number as Android's contacts hold it ([accountType] of the raw contact it belongs to). */
    data class PhoneNumber(
        val contactId: Long,
        val rawContactId: Long,
        val number: String,
        val type: Int,
        val accountType: String?,
    )

    /**
     * The rows one contact should have. They sit in a raw contact of Pickelhaube's account that is kept
     * together with the contact's raw contact [anchor] – raw contact ids stay put when contacts are
     * joined or split, the contact's own id does not.
     */
    data class Wanted(val anchor: Long, val rows: List<Row>)

    /** A raw contact of Pickelhaube's account as it is now; [anchor] is null if it has none (or a broken one). */
    data class Existing(val rawContactId: Long, val anchor: Long?, val deleted: Boolean, val rows: List<Row>)

    data class Plan(
        /** Raw contacts of Pickelhaube's account to remove, with their rows. */
        val remove: List<Long>,
        /** Raw contacts whose rows are to be replaced by these. */
        val replace: List<Pair<Long, List<Row>>>,
        /** Contacts that get a raw contact of Pickelhaube's account. */
        val add: List<Wanted>,
    ) {
        val isEmpty get() = remove.isEmpty() && replace.isEmpty() && add.isEmpty()
    }

    /** Kinds of numbers that cannot take a call: no row for them. */
    private val NOT_CALLABLE = setOf(
        Phone.TYPE_FAX_HOME,
        Phone.TYPE_FAX_WORK,
        Phone.TYPE_OTHER_FAX,
        Phone.TYPE_PAGER,
        Phone.TYPE_WORK_PAGER,
        Phone.TYPE_TELEX,
    )

    /**
     * The rows each contact should have: one per callable number – a number stored twice (e.g. synced
     * from two accounts) once –, in the order Android gives them. Numbers of [ownAccountType] (none,
     * normally: its raw contacts hold only the action rows) are left out. [detail] words a row's text.
     */
    fun wanted(numbers: List<PhoneNumber>, ownAccountType: String, detail: (String) -> String): List<Wanted> =
        numbers
            .filter { it.accountType != ownAccountType && it.type !in NOT_CALLABLE && it.number.isNotBlank() }
            .groupBy { it.contactId }
            .values
            .map { list ->
                val rows = list
                    .map { it.number.trim() }
                    .distinctBy { n -> n.filter { it.isDigit() || it == '+' } }
                    .map { Row(it, detail(it)) }
                Wanted(list.minOf { it.rawContactId }, rows)
            }
            .sortedBy { it.anchor }

    /** The changes that turn [existing] into [wanted]; nothing for rows already as they should be. */
    fun plan(wanted: List<Wanted>, existing: List<Existing>): Plan {
        val remove = mutableListOf<Long>()
        val replace = mutableListOf<Pair<Long, List<Row>>>()
        val add = mutableListOf<Wanted>()

        // Deleted ones (the contact was deleted in the contacts app) and those without an anchor go.
        val live = existing.filter { !it.deleted && it.anchor != null }
        remove += existing.filter { it.deleted || it.anchor == null }.map { it.rawContactId }
        val byAnchor = live.groupBy { it.anchor!! }
        val wantedAnchors = wanted.mapTo(HashSet()) { it.anchor }

        for (w in wanted) {
            val have = byAnchor[w.anchor].orEmpty().sortedBy { it.rawContactId }
            val keep = have.firstOrNull()
            remove += have.drop(1).map { it.rawContactId }
            when {
                keep == null -> add += w
                keep.rows.sortedBy { it.number } != w.rows.sortedBy { it.number } -> replace += keep.rawContactId to w.rows
            }
        }
        remove += byAnchor.filterKeys { it !in wantedAnchors }.values.flatten().map { it.rawContactId }
        return Plan(remove.distinct(), replace, add)
    }
}
