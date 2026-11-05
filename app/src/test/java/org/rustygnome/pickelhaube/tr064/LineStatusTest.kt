package org.rustygnome.pickelhaube.tr064

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineStatusTest {

    @Test
    fun `reads the box' own numbers`() {
        val xml = "<List><Item><Number>03023125300</Number><Type>eVoIP</Type><Index>0</Index><Name>Home</Name></Item>" +
            "<Item><Number>0301</Number><Type>ePOTS</Type><Index>0</Index><Name></Name></Item>" +
            "<Item><Number>03023125301</Number><Type>eVoIP</Type><Index>2</Index><Name></Name></Item>" +
            "<Item><Number></Number><Type>eVoIP</Type><Index>3</Index></Item></List>"
        assertEquals(
            listOf(
                OwnNumber(0, "03023125300", "eVoIP", "Home"),
                OwnNumber(0, "0301", "ePOTS"),
                OwnNumber(2, "03023125301", "eVoIP"),
            ),
            FritzLines.parseNumbers(xml),
        )
        assertEquals(emptyList<OwnNumber>(), FritzLines.parseNumbers(""))
        assertEquals("Home", OwnNumber(0, "1", "eVoIP", "Home").label)
        assertEquals("1", OwnNumber(0, "1", "eVoIP").label)
    }

    @Test
    fun `selects an internet number by the box' code`() {
        assertEquals("*121#", OwnNumber(0, "1", "eVoIP").selectCode)
        assertEquals("*129#", OwnNumber(8, "1", "eVoIP").selectCode)
        assertEquals("*120#", OwnNumber(9, "1", "eVoIP").selectCode)
        assertNull(OwnNumber(10, "1", "eVoIP").selectCode)
        assertNull(OwnNumber(0, "1", "ePOTS").selectCode)
        assertEquals("*122#030123", OwnNumber(1, "1", "eVoIP").dialling("030123"))
        assertEquals("*122#+4930123", OwnNumber(1, "1", "eVoIP").dialling("+4930123"))
        // Codes and internal numbers stay as they are.
        assertEquals("**610", OwnNumber(1, "1", "eVoIP").dialling("**610"))
        assertEquals("030123", OwnNumber(1, "1", "ePOTS").dialling("030123"))
    }

    @Test
    fun `only a line not registered is down`() {
        assertTrue(VoipLine(0, "1", "not registered").down)
        listOf("registered", "connected", "disabled", "unknown").forEach { assertFalse(it, VoipLine(0, "1", it).down) }
    }
}
