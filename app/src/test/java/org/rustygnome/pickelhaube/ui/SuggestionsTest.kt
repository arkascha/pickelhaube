package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.PhonebookNumber
import org.rustygnome.pickelhaube.tr064.RecentCall
import org.rustygnome.pickelhaube.tr064.RecentCall.Source.FRITZBOX

class SuggestionsTest {

    private val berlin = DialingCodes(countryCode = "49", areaCode = "30")
    private val key = { n: String -> numberKey(n, "", berlin) }

    private fun contact(name: String, vararg numbers: String) =
        PhonebookContact(name, numbers.map { PhonebookNumber(it, "home") }, "Book")

    private val anna = contact("Anna Muster", "030 1234567")
    private val bernd = contact("Bernd Krause", "+49 171 2345678")
    private val joerg = contact("Jörg Müller", "0221 998877")

    private fun suggest(typed: String, calls: List<RecentCall> = emptyList()) =
        suggestions(typed, listOf(anna, bernd, joerg), calls, key, berlin)

    @Test
    fun `nothing for less than three keys`() {
        assertEquals(emptyList<Suggestion>(), suggest("03"))
    }

    @Test
    fun `numbers starting with what was typed, also in national form`() {
        assertEquals(listOf(Suggestion("Anna Muster", "030 1234567")), suggest("0301"))
        // Stored internationally, typed nationally – and the other way round.
        assertEquals(listOf(Suggestion("Bernd Krause", "+49 171 2345678")), suggest("01712"))
        assertEquals(listOf(Suggestion("Anna Muster", "030 1234567")), suggest("+49301"))
        assertEquals(listOf(Suggestion("Anna Muster", "030 1234567")), suggest("0049301"))
    }

    @Test
    fun `names by the letters on the keys, from the start of a word`() {
        assertEquals(listOf(Suggestion("Anna Muster", "030 1234567")), suggest("2662")) // Anna
        assertEquals(listOf(Suggestion("Anna Muster", "030 1234567")), suggest("6878")) // Muster
        assertEquals(listOf(Suggestion("Jörg Müller", "0221 998877")), suggest("5674")) // Jörg, umlaut as o
        assertEquals(listOf(Suggestion("Jörg Müller", "0221 998877")), suggest("685")) // Müller
        assertEquals(listOf(Suggestion("Anna Muster", "030 1234567")), suggest("26626")) // Anna M…
        assertEquals(emptyList<Suggestion>(), suggest("662")) // "nna" is no word start
    }

    @Test
    fun `starting numbers before names before numbers containing it`() {
        val calls = listOf(RecentCall("0228 2662", null, 1_000, FRITZBOX))
        val found = suggest("2662", calls)
        // "Anna" by name, the call's number by its digits inside.
        assertEquals(listOf("Anna Muster", null), found.map { it.name })
        val start = suggest("0228", calls)
        assertEquals("0228 2662", start.first().number)
    }

    @Test
    fun `recently called first, each number once with its name`() {
        val annaToo = contact("Anna Other", "030 1234999")
        val calls = listOf(
            RecentCall("0301234999", null, 5_000, FRITZBOX),
            RecentCall("+49301234567", "Anna", 1_000, FRITZBOX),
        )
        val found = suggestions("030123", listOf(anna, annaToo), calls, key, berlin)
        assertEquals(listOf("Anna Other", "Anna Muster"), found.map { it.name })
    }

    @Test
    fun `a call's name for a number no phone book knows`() {
        val calls = listOf(RecentCall("0401112233", "Pizzeria", 1_000, FRITZBOX))
        assertEquals(listOf(Suggestion("Pizzeria", "0401112233")), suggest("040111", calls))
    }

    @Test
    fun `keypad words`() {
        assertEquals(listOf("5674", "685537"), keypadWords("Jörg Müller"))
        assertEquals(listOf("7872773"), keypadWords("Straße"))
        assertTrue(nameMatches("Muster, Anna", "2662"))
        assertFalse(nameMatches("Muster, Anna", "8783"))
    }

    @Test
    fun `says whose a number typed in full is, however it is written`() {
        val calls = listOf(RecentCall("0171 555666", "Caller", 1L, FRITZBOX))
        val index = SuggestionIndex(listOf(anna, bernd, joerg), calls, key, berlin)
        assertEquals("Anna Muster", index.nameOf("0301234567"))
        assertEquals("Anna Muster", index.nameOf("1234567"))
        assertEquals("Bernd Krause", index.nameOf("01712345678"))
        // Digits typed once connected do not count.
        assertEquals("Joerg", SuggestionIndex(listOf(contact("Joerg", "0221 998877")), emptyList(), key, berlin).nameOf("0221998877,42"))
        assertEquals("Caller", index.nameOf("0171555666"))
        assertEquals(null, index.nameOf("030123456"))
        assertEquals(null, index.nameOf(""))
    }

    @Test
    fun `a search no longer wanted is broken off`() {
        val many = List(5000) { contact("Person $it", "030 1$it") }
        val index = SuggestionIndex(many, emptyList(), key, berlin)
        var checks = 0
        val stop = RuntimeException("next key")
        val thrown = runCatching { index.suggest("0301") { if (++checks == 2) throw stop } }.exceptionOrNull()
        assertEquals(stop, thrown)
        assertEquals(2, checks)
    }
}
