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

    @Test
    fun `names with the same initials seen together get different colours`() {
        // Same initials and, each on its own, the same hue.
        assertEquals(nameHue("Katrin Scheile"), nameHue("Kilian Scheile"))
        val hues = nameHues(listOf("Kilian Scheile", "Katrin Scheile", "Mum", "Anna Becker"))
        assertNotEquals(hues.getValue("katrin scheile"), hues.getValue("kilian scheile"))
        // The first in alphabetical order keeps its own; names alone in their initials keep theirs.
        assertEquals(nameHue("Katrin Scheile"), hues.getValue("katrin scheile"))
        assertEquals(nameHue("Mum"), hues.getValue("mum"))
        assertEquals(nameHue("Anna Becker"), hues.getValue("anna becker"))
    }

    @Test
    fun `the same person met twice counts once, a number without letters not at all`() {
        val hues = nameHues(listOf("Anna Becker", " anna becker", "030 1234567"))
        assertEquals(setOf("anna becker"), hues.keys)
    }
}
