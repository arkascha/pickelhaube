package org.rustygnome.pickelhaube.tr064

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalPhonesTest {

    @Test
    fun `analogue phones by their port`() {
        assertEquals("**1", FritzInternalPhones.analogueNumber("FON1: Hallway"))
        assertEquals("**2", FritzInternalPhones.analogueNumber("FON 2: Fax"))
        assertNull(FritzInternalPhones.analogueNumber("ISDN: Kitchen"))
        assertNull(FritzInternalPhones.analogueNumber("DECT: All handsets"))
    }

    @Test
    fun `DECT handsets by their id`() {
        assertEquals("**610", FritzInternalPhones.dectNumber("1"))
        assertEquals("**615", FritzInternalPhones.dectNumber("6"))
        assertNull(FritzInternalPhones.dectNumber("7"))
        assertNull(FritzInternalPhones.dectNumber(""))
    }

    @Test
    fun `IP phones as the box gives them`() {
        assertEquals("**620", FritzInternalPhones.ipNumber("620"))
        assertEquals("**621", FritzInternalPhones.ipNumber("**621"))
        assertNull(FritzInternalPhones.ipNumber(""))
        assertNull(FritzInternalPhones.ipNumber(null))
    }

    @Test
    fun `internal numbers, not speed dials or service codes`() {
        listOf("**1", "**9", "**610", "**620", "**51").forEach { assertTrue(it, FritzInternalPhones.isInternal(it)) }
        listOf("**712", "*31#", "#96*5*", "0301234567", "**6100").forEach { assertFalse(it, FritzInternalPhones.isInternal(it)) }
    }

    @Test
    fun `the demo box' phones`() {
        val config = FritzConfig(host = DemoBox.HOST, username = DemoBox.USERNAME, password = DemoBox.PASSWORD)
        val phones = FritzDialer(config).let { it.internalPhones(it.listPhones()) }
        assertEquals(
            listOf("Hallway" to "**1", "Kitchen" to "**610", "Living room" to "**611", "Study" to "**612", "Softphone" to "**620", "" to "**9"),
            phones.map { it.name to it.number },
        )
    }
}
