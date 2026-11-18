package org.rustygnome.pickelhaube.shortcut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InitialsTest {

    @Test
    fun `takes the first letters of up to two words`() {
        assertEquals("AB", LauncherShortcuts.initials("Anna Becker"))
        assertEquals("M", LauncherShortcuts.initials("Mum"))
        assertEquals("PD", LauncherShortcuts.initials("Pizzeria Da Marco"))
        assertEquals("DW", LauncherShortcuts.initials("Dr. Weber, dentist"))
        assertEquals("ÖS", LauncherShortcuts.initials("öztürk süleyman"))
    }

    @Test
    fun `skips words that do not start with a letter`() {
        assertEquals("O", LauncherShortcuts.initials("Office 2"))
        assertEquals("K", LauncherShortcuts.initials("(Home) Kitchen"))
    }

    @Test
    fun `has none for a number`() {
        assertNull(LauncherShortcuts.initials("030 1234567"))
        assertNull(LauncherShortcuts.initials("+49 30 1234"))
    }
}
