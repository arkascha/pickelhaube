package org.rustygnome.pickelhaube.contacts

import android.provider.ContactsContract.CommonDataKinds.Phone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rustygnome.pickelhaube.contacts.ContactActionsPlan.Existing
import org.rustygnome.pickelhaube.contacts.ContactActionsPlan.PhoneNumber
import org.rustygnome.pickelhaube.contacts.ContactActionsPlan.Row
import org.rustygnome.pickelhaube.contacts.ContactActionsPlan.Wanted

class ContactActionsPlanTest {

    private val own = "org.rustygnome.pickelhaube.contacts"
    private val detail = { n: String -> "Call $n" }
    private fun row(n: String) = Row(n, "Call $n")
    private fun number(contact: Long, raw: Long, n: String, type: Int = Phone.TYPE_MOBILE, account: String? = "com.google") =
        PhoneNumber(contact, raw, n, type, account)

    @Test
    fun oneRowPerCallableNumber_anchoredAtTheLowestRawContact() {
        val wanted = ContactActionsPlan.wanted(
            listOf(
                number(1, 11, "0171 1234567"),
                number(1, 10, "089 123456", Phone.TYPE_HOME),
                number(1, 10, "089 999", Phone.TYPE_FAX_HOME),
                number(1, 12, "0171-1234567", account = "local"), // the same number from another account
                number(2, 20, "  "),
                number(3, 30, "123", Phone.TYPE_PAGER),
                number(4, 40, "0711 1", account = own),
            ),
            own,
            detail,
        )
        assertEquals(listOf(Wanted(10, listOf(row("0171 1234567"), row("089 123456")))), wanted)
    }

    @Test
    fun nothingToDo_whenTheRowsAreAsWanted() {
        val wanted = listOf(Wanted(10, listOf(row("1"), row("2"))))
        val existing = listOf(Existing(100, 10, false, listOf(row("2"), row("1"))))
        assertTrue(ContactActionsPlan.plan(wanted, existing).isEmpty)
    }

    @Test
    fun addsNewContacts_replacesChangedRows_removesGoneDeletedAndDoubled() {
        val wanted = listOf(
            Wanted(10, listOf(row("1"))),
            Wanted(20, listOf(row("2"), row("3"))),
            Wanted(30, listOf(row("4"))),
            Wanted(40, listOf(row("5"))),
        )
        val existing = listOf(
            Existing(100, 10, false, listOf(row("1"))),
            Existing(200, 20, false, listOf(row("2"))), // a number added to the contact
            Existing(300, 30, true, listOf(row("4"))), // deleted in the contacts app: goes, and comes back new
            Existing(400, 40, false, listOf(Row("5", "Anrufen 5"))), // another language
            Existing(401, 40, false, listOf(row("5"))), // twice for one contact
            Existing(500, 50, false, listOf(row("6"))), // contact gone
            Existing(600, null, false, emptyList()),
        )
        val plan = ContactActionsPlan.plan(wanted, existing)
        assertEquals(setOf(300L, 401L, 500L, 600L), plan.remove.toSet())
        assertEquals(listOf(200L to listOf(row("2"), row("3")), 400L to listOf(row("5"))), plan.replace)
        assertEquals(listOf(Wanted(30, listOf(row("4")))), plan.add)
    }
}
