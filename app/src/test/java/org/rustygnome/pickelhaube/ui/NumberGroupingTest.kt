package org.rustygnome.pickelhaube.ui

import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.PhonebookNumber
import org.rustygnome.pickelhaube.tr064.RecentCall
import org.rustygnome.pickelhaube.tr064.RecentCall.Source.FRITZBOX
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberGroupingTest {

    /** Stands in for Android's formatter: Berlin numbers as "030 …", German mobiles as "+49 171 …". */
    private val format = { n: String ->
        when {
            n.startsWith("030") -> "030 " + n.drop(3)
            n.startsWith("+49171") -> "+49 171 " + n.drop(6)
            else -> null
        }
    }

    @Test
    fun `groups the number and keeps what follows a pause`() {
        assertEquals("030 1234567", groupedNumber("0301234567", format))
        assertEquals("+49 171 2345678", groupedNumber("+491712345678", format))
        assertEquals("030 1234567,42#", groupedNumber("0301234567,42#", format))
    }

    @Test
    fun `leaves alone what it should not change`() {
        // Too short, a service code, an internal number, separators of its own, unknown to the formatter.
        listOf("030", "*31#0301234567", "**610", "030 1234567", "0891234567").forEach {
            assertEquals(it, groupedNumber(it, format))
        }
        // A formatter that changes digits is not trusted.
        assertEquals("0301234567", groupedNumber("0301234567") { "+49 30 1234567" })
        assertEquals("0301234567", groupedNumber("0301234567") { "030 1234567 ext" })
    }

    @Test
    fun `maps the places of the characters both ways`() {
        val mapping = groupingOffsets("0301234567,42", "030 1234567,42")
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(2))
        assertEquals(4, mapping.originalToTransformed(3))
        assertEquals(5, mapping.originalToTransformed(4))
        assertEquals(14, mapping.originalToTransformed(13))
        assertEquals(3, mapping.transformedToOriginal(4))
        assertEquals(4, mapping.transformedToOriginal(5))
        assertEquals(13, mapping.transformedToOriginal(14))
    }

    private val berlin = DialingCodes(countryCode = "49", areaCode = "30")
    private val key = { n: String -> numberKey(n, "", berlin) }

    @Test
    fun `finds whose a number is, however it is written`() {
        val contacts = listOf(
            PhonebookContact("", listOf(PhonebookNumber("030 123456", "home")), ""),
            PhonebookContact("Erika", listOf(PhonebookNumber("+49 30 123456", "home")), ""),
        )
        val calls = listOf(
            RecentCall("0171 234567", "Old name", 1L, FRITZBOX),
            RecentCall("0171 234567", "New name", 2L, FRITZBOX),
        )
        assertEquals("Erika", nameOf(key("123456"), contacts, calls, key))
        assertEquals("New name", nameOf(key("+49171234567"), contacts, calls, key))
        assertNull(nameOf(key("0301111111"), contacts, calls, key))
    }
}
