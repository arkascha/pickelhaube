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
}
