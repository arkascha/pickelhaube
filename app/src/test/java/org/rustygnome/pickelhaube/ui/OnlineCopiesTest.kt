package org.rustygnome.pickelhaube.ui

import org.rustygnome.pickelhaube.tr064.Phonebook
import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.PhonebookNumber
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineCopiesTest {

    private val key = { number: String -> number.filter(Char::isDigit) }

    private fun contact(name: String, uid: String, book: Int, vararg numbers: Pair<String, String>) =
        PhonebookContact(name, numbers.map { (n, t) -> PhonebookNumber(n, t) }, "book $book", book, uid)

    private val online = Phonebook(
        1, "Chroud privat",
        listOf(
            contact("Albert Brunkow", "7702", 1, "05361 16392" to "work", "0171 1" to "mobile"),
            contact("Alle (Rundruf)", "3", 1, "**9" to "intern"),
        ),
        online = true,
    )

    @Test
    fun `finds the local copy of an online contact`() {
        val copy = contact("Albert Brunkow", "17", 0, "05361-16392" to "work")
        val local = Phonebook(0, "Telefonbuch", listOf(copy, contact("Anna", "16", 0, "030 1" to "home")))
        assertEquals(listOf(OnlineCopy(copy, "Chroud privat")), onlineCopies(listOf(local, online), key))
    }

    @Test
    fun `leaves other entries alone`() {
        val local = Phonebook(
            0, "Telefonbuch",
            listOf(
                // Another name, or a number the online contact does not have.
                contact("Albert B.", "17", 0, "05361 16392" to "work"),
                contact("Albert Brunkow", "18", 0, "05361 16392" to "work", "030 9" to "home"),
                // Shown by the box in every phone book.
                contact("Alle (Rundruf)", "3", 0, "**9" to "intern"),
            ),
        )
        assertEquals(emptyList<OnlineCopy>(), onlineCopies(listOf(local, online), key))
    }
}
