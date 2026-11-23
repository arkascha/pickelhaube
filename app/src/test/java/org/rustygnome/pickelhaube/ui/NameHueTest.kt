package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameHueTest {

    @Test
    fun `the same name always the same colour, whatever its case`() {
        assertEquals(nameHue("Anna Becker"), nameHue("anna becker"))
        assertEquals(nameHue("Anna Becker"), nameHue(" Anna Becker "))
    }

    @Test
    fun `different names mostly different colours, all of them hues`() {
        val names = listOf("Anna Becker", "Mum", "Dr. Weber, dentist", "Pizzeria Da Marco", "Office", "Kitchen")
        assertTrue(names.map(::nameHue).all { it in 0f..359f })
        assertNotEquals(nameHue("Anna Becker"), nameHue("Mum"))
        // Spread around the wheel, not bunched together (the plain hash put most of these on green).
        assertTrue(names.map(::nameHue).toSet().size >= 4)
    }
}
