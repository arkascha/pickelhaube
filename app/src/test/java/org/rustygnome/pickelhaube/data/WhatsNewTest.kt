package org.rustygnome.pickelhaube.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {

    private val notes = setOf(8, 10, 11)

    @Test
    fun `nothing on a new install – the welcome tour explains`() {
        assertEquals(emptyList<Int>(), WhatsNew.toShow(lastSeen = null, current = 8, configured = false, available = notes))
    }

    @Test
    fun `the current version's note after an update`() {
        assertEquals(listOf(8), WhatsNew.toShow(lastSeen = 7, current = 8, configured = true, available = notes))
    }

    @Test
    fun `an update from before the note existed counts as an update when a box is set up`() {
        assertEquals(listOf(8), WhatsNew.toShow(lastSeen = null, current = 8, configured = true, available = notes))
    }

    @Test
    fun `skipped versions together, newest first`() {
        assertEquals(listOf(11, 10, 8), WhatsNew.toShow(lastSeen = 7, current = 11, configured = true, available = notes))
        assertEquals(listOf(11, 10), WhatsNew.toShow(lastSeen = 8, current = 11, configured = true, available = notes))
    }

    @Test
    fun `nothing for a version without a note, or when already seen`() {
        assertEquals(emptyList<Int>(), WhatsNew.toShow(lastSeen = 8, current = 9, configured = true, available = notes))
        assertEquals(emptyList<Int>(), WhatsNew.toShow(lastSeen = 11, current = 11, configured = true, available = notes))
    }

    @Test
    fun `the latest note for the settings`() {
        assertEquals(10, WhatsNew.latest(current = 10, available = notes))
        assertEquals(8, WhatsNew.latest(current = 9, available = notes))
        assertNull(WhatsNew.latest(current = 7, available = notes))
    }

    @Test
    fun `the title names the newest note shown, not the version installed`() {
        val named = mapOf(8 to WhatsNew.Note("1.6.0", 0), 10 to WhatsNew.Note("1.8.0", 0))
        assertEquals("1.6.0", WhatsNew.titleVersion(listOf(8), named))
        assertEquals("1.8.0", WhatsNew.titleVersion(listOf(10, 8), named))
        assertNull(WhatsNew.titleVersion(emptyList(), named))
        // Every note has its version name.
        assertTrue(WhatsNew.NOTES.values.all { it.versionName.matches(Regex("""\d+\.\d+\.\d+""")) })
    }
}
