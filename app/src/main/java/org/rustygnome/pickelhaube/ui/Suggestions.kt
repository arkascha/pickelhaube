package org.rustygnome.pickelhaube.ui

import java.text.Normalizer
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.RecentCall

/** A number offered while typing in the dialer: whose it is (null: not known) and the number itself. */
data class Suggestion(val name: String?, val number: String)

/** Typed less than this, nothing is suggested: too many would match. */
internal const val SUGGEST_MIN_LENGTH = 3

/** At most this many suggestions. */
internal const val SUGGEST_MAX = 8

/**
 * The numbers of the [contacts] and recent [calls] that what was [typed] in the dialer may be heading
 * for, best first:
 * 1. numbers starting with it – as written, or in the box' national form ("+49 30 123…" for "030 1…"),
 * 2. names whose letters are on the keys typed, from the start of a word ("5266" → "Anna", "Muster, Anna"),
 * 3. numbers containing it elsewhere.
 * Within each, numbers called recently come first, the latest call first; then by name. Each number
 * once ([key], see [numberKey]), with a name if one is known.
 */
internal fun suggestions(
    typed: String,
    contacts: List<PhonebookContact>,
    calls: List<RecentCall>,
    key: (String) -> String,
    codes: DialingCodes?,
): List<Suggestion> {
    val wanted = FritzDialer.sanitizeNumber(typed)
    if (wanted.length < SUGGEST_MIN_LENGTH) return emptyList()
    val byName = wanted.all { it in '2'..'9' }

    // Each number once: a contact's name wins over a call's.
    val candidates = LinkedHashMap<String, Suggestion>()
    contacts.forEach { c ->
        c.numbers.forEach { n ->
            val k = key(n.number)
            if (k.isNotEmpty() && k !in candidates) candidates[k] = Suggestion(c.name.takeIf(String::isNotBlank), n.number)
        }
    }
    val lastCall = HashMap<String, Long>()
    calls.forEach { call ->
        val k = key(call.number)
        if (k.isEmpty()) return@forEach
        if ((lastCall[k] ?: Long.MIN_VALUE) < call.time) lastCall[k] = call.time
        val known = candidates[k]
        if (known == null) candidates[k] = Suggestion(call.name?.takeIf(String::isNotBlank), call.number)
        else if (known.name == null && !call.name.isNullOrBlank()) candidates[k] = known.copy(name = call.name)
    }

    return candidates.entries.mapNotNull { (k, s) ->
        val forms = numberForms(s.number, k, codes)
        val rank = when {
            forms.any { it.startsWith(wanted) } -> 0
            byName && s.name != null && nameMatches(s.name, wanted) -> 1
            forms.any { it.contains(wanted) } -> 2
            else -> return@mapNotNull null
        }
        Triple(rank, lastCall[k], s)
    }
        .sortedWith(
            compareBy<Triple<Int, Long?, Suggestion>> { it.first }
                .thenByDescending { it.second ?: Long.MIN_VALUE }
                .thenBy { it.third.name?.lowercase() ?: "￿" },
        )
        .take(SUGGEST_MAX)
        .map { it.third }
}

/** [number] as it may be typed: as written (digits and + * #), in international form [key], and in the box' national form. */
private fun numberForms(number: String, key: String, codes: DialingCodes?): List<String> {
    val written = FritzDialer.sanitizeNumber(number)
    val national = codes?.let { c ->
        val country = "+" + c.countryCode
        if (key.startsWith(country)) c.nationalPrefix + key.drop(country.length) else null
    }
    val withPrefix = codes?.let { c -> if (key.startsWith("+")) c.internationalPrefix + key.drop(1) else null }
    return listOfNotNull(written, key, national, withPrefix)
}

/** Whether a word of [name], or the name from a word on, is spelled on the keys [digits]. */
internal fun nameMatches(name: String, digits: String): Boolean {
    val words = keypadWords(name)
    return words.indices.any { i -> words.drop(i).joinToString("").startsWith(digits) }
}

/** The words of [name], each as the digits of its letters on a phone keypad ("Jörg Müller" → "5674", "685537"). */
internal fun keypadWords(name: String): List<String> =
    Normalizer.normalize(name.lowercase().replace("ß", "ss"), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .split(Regex("[^a-z0-9]+"))
        .filter(String::isNotEmpty)
        .map { word -> word.map(::keypadDigit).joinToString("") }

private fun keypadDigit(c: Char): Char = when (c) {
    in 'a'..'c' -> '2'
    in 'd'..'f' -> '3'
    in 'g'..'i' -> '4'
    in 'j'..'l' -> '5'
    in 'm'..'o' -> '6'
    in 'p'..'s' -> '7'
    in 't'..'v' -> '8'
    in 'w'..'z' -> '9'
    else -> c
}
