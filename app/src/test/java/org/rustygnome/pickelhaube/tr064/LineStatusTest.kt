package org.rustygnome.pickelhaube.tr064

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineStatusTest {

    @Test
    fun `takes the VoIP numbers of the box' number list`() {
        val xml = "<List><Item><Number>03023125300</Number><Type>eVoIP</Type><Index>0</Index><Name>Home</Name></Item>" +
            "<Item><Number>0301</Number><Type>ePOTS</Type><Index>0</Index><Name></Name></Item>" +
            "<Item><Number>03023125301</Number><Type>eVoIP</Type><Index>2</Index><Name></Name></Item>" +
            "<Item><Number></Number><Type>eVoIP</Type><Index>3</Index></Item></List>"
        assertEquals(listOf(0 to "03023125300", 2 to "03023125301"), FritzLines.voipNumbers(xml))
        assertEquals(emptyList<Pair<Int, String>>(), FritzLines.voipNumbers(""))
    }

    @Test
    fun `only a line not registered is down`() {
        assertTrue(VoipLine(0, "1", "not registered").down)
        listOf("registered", "connected", "disabled", "unknown").forEach { assertFalse(it, VoipLine(0, "1", it).down) }
    }
}
