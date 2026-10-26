package org.rustygnome.pickelhaube.tr064

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonebookEntryTest {

    @Test
    fun `writes a new entry as the box takes it`() {
        val contact = PhonebookContact(
            "Müller & Söhne",
            listOf(PhonebookNumber("+49 30 123456", "work"), PhonebookNumber("0171 98765", "Pager")),
            "This phone",
        )
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><contact><category>0</category><person>" +
                "<realName>Müller &amp; Söhne</realName></person><telephony nid=\"2\">" +
                "<number type=\"work\" prio=\"1\" id=\"0\">+49 30 123456</number>" +
                "<number type=\"other\" prio=\"0\" id=\"1\">0171 98765</number></telephony></contact>",
            FritzPhonebooks.entryXml(contact),
        )
    }

    @Test
    fun `reads back what it wrote`() {
        val contact = PhonebookContact("Anna", listOf(PhonebookNumber("030 123", "home")), "Telefonbuch")
        val xml = "<phonebooks><phonebook>" + FritzPhonebooks.entryXml(contact).substringAfter("?>") + "</phonebook></phonebooks>"
        assertEquals(listOf(contact), FritzPhonebooks.parseContacts(xml, "Telefonbuch"))
    }

    @Test
    fun `moves a speed dial within an entry and keeps the rest`() {
        val entry = "<?xml version=\"1.0\" encoding=\"utf-8\"?><contact><category>0</category><person><realName>Anna</realName></person>" +
            "<telephony><number type=\"home\" quickdial=\"7\" prio=\"1\" id=\"0\">030 123</number>" +
            "<number type=\"mobile\" prio=\"0\" id=\"1\">0171 456</number></telephony><uniqueid>12</uniqueid></contact>"
        val moved = FritzPhonebooks.withSpeedDial(entry, "12", "07", "0171 456")
        val contact = FritzPhonebooks.parseContacts("<phonebooks><phonebook>${moved.substringAfter("?>")}</phonebook></phonebooks>", "T", 0).single()
        assertEquals(listOf(null, "07"), contact.numbers.map { it.speedDial })
        assertEquals("12", contact.uid)
        assertEquals(0, contact.bookId)
        assertEquals(1, Regex("<uniqueid>").findAll(moved).count())

        val removed = FritzPhonebooks.withSpeedDial(moved, "12", "07", null)
        assertEquals(false, removed.contains("quickdial"))
    }

    @Test
    fun `adds the unique id if the entry lacks it`() {
        val entry = "<contact><person><realName>Bob</realName></person><telephony><number type=\"home\">1</number></telephony></contact>"
        assert(FritzPhonebooks.withSpeedDial(entry, "5", "01", "1").contains("<uniqueid>5</uniqueid>"))
    }

    @Test
    fun `writes the speed dial of a new entry`() {
        val contact = PhonebookContact("Bob", listOf(PhonebookNumber("030 1", "home", "05")), "")
        assert(FritzPhonebooks.entryXml(contact).contains("<number type=\"home\" quickdial=\"05\" prio=\"1\" id=\"0\">"))
    }

    @Test
    fun `tells online phone books by their owner`() {
        assertEquals(false, FritzPhonebooks.isOnline("<phonebooks><phonebook owner=\"0\" name=\"Telefonbuch\">"))
        assertEquals(true, FritzPhonebooks.isOnline("<phonebooks>\n<phonebook owner=\"240\" name=\"Chroud privat\">"))
        assertEquals(false, FritzPhonebooks.isOnline("<phonebooks><phonebook name=\"Alt\">"))
    }
}
