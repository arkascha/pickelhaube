package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rustygnome.pickelhaube.tr064.DialingCodes

class CallProgressTest {

    private val codes = DialingCodes(countryCode = "49", internationalPrefix = "00", areaCode = "30", nationalPrefix = "0")
    private val key = { n: String -> numberKey(n, "DE", codes) }

    @Test
    fun `recognises the dialled number however the box writes it`() {
        assertTrue(isDialledNumber("0301234567", "0301234567", key))
        assertTrue(isDialledNumber("0301234567", "+49301234567", key))
        assertTrue(isDialledNumber("0301234567", "1234567", key))
        // Ending in the other one, beyond the box' rules.
        assertTrue(isDialledNumber("01701234567", "1701234567", key))
    }

    @Test
    fun `does not take another call for it`() {
        assertFalse(isDialledNumber("0409876543", "0301234567", key))
        assertFalse(isDialledNumber("", "0301234567", key))
        // Too short to tell by their ends.
        assertFalse(isDialledNumber("12345", "912345", { it }))
    }

    @Test
    fun `formats durations`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:42", formatDuration(42))
        assertEquals("12:05", formatDuration(725))
        assertEquals("1:02:09", formatDuration(3729))
    }
}
