package org.rustygnome.pickelhaube.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberTextTest {

    @Test
    fun `cleans the usual ways of writing a number`() {
        assertEquals(listOf("+4930123456"), NumberText.extract("+49 (0)30 / 123 45-6"))
        assertEquals(listOf("0301234556"), NumberText.extract("(030) 12 34 55 6"))
        assertEquals(listOf("+4930123456"), NumberText.extract("Call +49.30.123456 now"))
        assertEquals(listOf("004930123456"), NumberText.extract("0049 (0)30 123456"))
    }

    @Test
    fun `finds several numbers, each once`() {
        val signature = """
            Anna Muster
            Tel. 030 123456-0
            Fax: 030 123456-99
            Mobile +49 171 2345678
            Tel. 030 123456-0
        """.trimIndent()
        assertEquals(listOf("0301234560", "03012345699", "+491712345678"), NumberText.extract(signature))
    }

    @Test
    fun `leaves out dates, times and what is too short or too long`() {
        assertEquals(emptyList<String>(), NumberText.extract("Meeting on 12.03.2026 at 14:30 in room 12"))
        assertEquals(emptyList<String>(), NumberText.extract("Order 2026-03-12, IBAN DE89 3704 0044 0532 0130 00"))
        assertEquals(emptyList<String>(), NumberText.extract("No number here."))
        assertEquals(emptyList<String>(), NumberText.extract("Pickelhaube 1.2.0 (4), full"))
        assertEquals(emptyList<String>(), NumberText.extract("Room 1204, floor 12"))
    }

    @Test
    fun `keeps the box' service codes`() {
        assertEquals("*21#", NumberText.clean(" *21# "))
        assertEquals("+4930123", NumberText.clean("+49 (0) 30 123"))
    }

    @Test
    fun `keeps what is typed once connected after a comma`() {
        assertEquals("0301234567,42#", NumberText.clean("030 1234567 x 42#"))
        assertEquals("+49301234567,42", NumberText.clean("+49 (0)30 1234567;postd=42"))
        // Cleaning twice changes nothing (Redial cleans the number kept from the last call again).
        assertEquals("0301234567,42,1", NumberText.clean(NumberText.clean("0301234567p42w1")))
    }
}
