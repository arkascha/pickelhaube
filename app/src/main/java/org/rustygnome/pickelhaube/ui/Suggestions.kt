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
 * for, best first (see [SuggestionIndex.suggest]).
 */
internal fun suggestions(
    typed: String,
    contacts: List<PhonebookContact>,
    calls: List<RecentCall>,
    key: (String) -> String,
    codes: DialingCodes?,
): List<Suggestion> = SuggestionIndex(contacts, calls, key, codes).suggest(typed)

/**
 * The numbers of the [contacts] and recent [calls], made ready to be searched as the dialer is typed
 * in: once for each change of them, so a key press only compares strings – no numbers to normalise,
 * no names to spell on the keypad, hardly any garbage. Each number once ([key], see [numberKey]), with
 * a name if one is known (a contact's wins over a call's).
 */
internal class SuggestionIndex(
    contacts: List<PhonebookContact>,
    calls: List<RecentCall>,
    private val key: (String) -> String,
    codes: DialingCodes?,
) {
    private class Entry(
        val suggestion: Suggestion,
        /** The number as it may be typed (see [numberForms]). */
        val forms: List<String>,
        /** The name spelled on the keypad from each of its words on (see [nameMatches]); empty without a name. */
        val spelled: List<String>,
        /** The latest call with the number; null: none. */
        val lastCall: Long?,
    )

    private val entries: List<Entry>

    /** Whose each number is ([key] → name): a contact's name, else the one of the latest call that has one. */
    private val names = HashMap<String, String>()

    init {
        val candidates = LinkedHashMap<String, Suggestion>()
        contacts.forEach { c ->
            c.numbers.forEach { n ->
                val k = key(n.number)
                if (k.isNotEmpty() && k !in candidates) candidates[k] = Suggestion(c.name.takeIf(String::isNotBlank), n.number)
                if (k.isNotEmpty() && c.name.isNotBlank()) names.putIfAbsent(k, c.name)
            }
        }
        val lastCall = HashMap<String, Long>()
        calls.sortedByDescending { it.time }.forEach { call ->
            val k = key(call.number)
            if (k.isEmpty()) return@forEach
            lastCall.putIfAbsent(k, call.time)
            val name = call.name?.takeIf(String::isNotBlank)
            if (name != null) names.putIfAbsent(k, name)
            val known = candidates[k]
            if (known == null) candidates[k] = Suggestion(name, call.number)
            else if (known.name == null && name != null) candidates[k] = known.copy(name = name)
        }
        entries = candidates.map { (k, s) ->
            Entry(s, numberForms(s.number, k, codes), s.name?.let(::spelledFromEachWord).orEmpty(), lastCall[k])
        }
    }

    /** Whose the number [typed] is, entered in full – however it is written; null if nobody's known. */
    fun nameOf(typed: String): String? = FritzDialer.sanitizeNumber(typed).takeIf { it.isNotEmpty() }?.let { names[key(it)] }

    /**
     * What [typed] may be heading for, best first:
     * 1. numbers starting with it – as written, or in the box' national form ("+49 30 123…" for "030 1…"),
     * 2. names whose letters are on the keys typed, from the start of a word ("5266" → "Anna", "Muster, Anna"),
     * 3. numbers containing it elsewhere.
     * Within each, numbers called recently come first, the latest call first; then by name.
     * [checkCancelled] is called now and then on the way: it may throw to break off a search no longer wanted.
     */
    fun suggest(typed: String, checkCancelled: () -> Unit = {}): List<Suggestion> {
        val wanted = FritzDialer.sanitizeNumber(typed)
        if (wanted.length < SUGGEST_MIN_LENGTH) return emptyList()
        val byName = wanted.all { it in '2'..'9' }
        val found = ArrayList<Pair<Int, Entry>>()
        entries.forEachIndexed { i, e ->
            if (i % CANCEL_CHECK_EVERY == 0) checkCancelled()
            val rank = when {
                e.forms.any { it.startsWith(wanted) } -> 0
                byName && e.spelled.any { it.startsWith(wanted) } -> 1
                e.forms.any { it.contains(wanted) } -> 2
                else -> return@forEachIndexed
            }
            found += rank to e
        }
        return found
            .sortedWith(
                compareBy<Pair<Int, Entry>> { it.first }
                    .thenByDescending { it.second.lastCall ?: Long.MIN_VALUE }
                    .thenBy { it.second.suggestion.name?.lowercase() ?: "\uFFFF" },
            )
            .take(SUGGEST_MAX)
            .map { it.second.suggestion }
    }

    private companion object {
        const val CANCEL_CHECK_EVERY = 512
    }
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
internal fun nameMatches(name: String, digits: String): Boolean = spelledFromEachWord(name).any { it.startsWith(digits) }

/** [name] spelled on the keypad from each of its words on ("Muster, Anna" → "6878372662", "2662"). */
private fun spelledFromEachWord(name: String): List<String> {
    val words = keypadWords(name)
    return words.indices.map { i -> words.subList(i, words.size).joinToString("") }
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val NOT_A_LETTER = Regex("[^a-z0-9]+")

/** The words of [name], each as the digits of its letters on a phone keypad ("Jörg Müller" → "5674", "685537"). */
internal fun keypadWords(name: String): List<String> =
    Normalizer.normalize(name.lowercase().replace("ß", "ss"), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .split(NOT_A_LETTER)
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
