package org.rustygnome.pickelhaube.tr064

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FritzDialerTest {

    @Test
    fun `strips the router's port prefixes for display`() {
        assertEquals("Mobilteil 1", FritzDialer.displayName("ISDN: Mobilteil 1"))
        assertEquals("Telefon", FritzDialer.displayName("FON1: Telefon"))
        assertEquals("Fax", FritzDialer.displayName("FON 2: Fax"))
        assertEquals("Softphone", FritzDialer.displayName("SIP: Softphone"))
        assertEquals("Flur", FritzDialer.displayName("isdn:Flur"))
    }

    @Test
    fun `leaves other names alone`() {
        assertEquals("Wohnzimmer", FritzDialer.displayName("Wohnzimmer"))
        assertEquals("Mein Telefon: alt", FritzDialer.displayName("Mein Telefon: alt"))
        // Nothing left after the prefix: keep the original rather than show an empty entry.
        assertEquals("ISDN:", FritzDialer.displayName("ISDN:"))
    }

    @Test
    fun `parses the second-factor methods`() {
        val c = SecondFactorChallenge.parse("tok", "button,dtmf;*12622")
        assertEquals(true, c.button)
        assertEquals("*12622", c.dtmfCode)
        val onlyButton = SecondFactorChallenge.parse("tok", "button")
        assertEquals(null, onlyButton.dtmfCode)
    }

    @Test
    fun `puts the second-factor token into the SOAP header`() {
        val xml = Tr064Client.buildEnvelope(
            FritzDialer.SERVICE_TYPE, "X_AVM-DE_DialSetConfig",
            mapOf("NewX_AVM-DE_PhoneName" to "ISDN: Mobilteil 1"), authToken = "abc-123",
        )
        assertTrue(xml.contains("<s:Header><avm:token xmlns:avm=\"avm.de\" mustUnderstand=\"1\">abc-123</avm:token></s:Header><s:Body>"))
    }

    @Test
    fun `reads the box' details from GetInfo`() {
        val info = BoxInfo.from(
            mapOf(
                "NewModelName" to "FRITZ!Box 7590",
                "NewSoftwareVersion" to "154.07.57",
                "NewSerialNumber" to "E0286D123456",
                "NewHardwareVersion" to "FRITZ!Box 7590",
            )
        )
        assertEquals(BoxInfo("FRITZ!Box 7590", "154.07.57", "E0286D123456"), info)
        assertEquals(BoxInfo(null, null, null), BoxInfo.from(mapOf("NewModelName" to " ")))
    }

    @Test
    fun `shows the FRITZ!OS version without the product number`() {
        assertEquals("7.57", BoxInfo(null, "154.07.57", null).fritzOsVersion)
        assertEquals("8.00-120123", BoxInfo(null, "164.08.00-120123", null).fritzOsVersion)
        // An unexpected format is shown as it is.
        assertEquals("7.57", BoxInfo(null, "7.57", null).fritzOsVersion)
        assertEquals(null, BoxInfo(null, null, null).fritzOsVersion)
    }

    @Test
    fun `withholds the own number only for real numbers`() {
        assertEquals("*31#0301234567", FritzDialer.withheld("0301234567"))
        assertEquals("*31#+491715550000", FritzDialer.withheld("+491715550000"))
        assertEquals("**610", FritzDialer.withheld("**610"))
        assertEquals("#96*5*", FritzDialer.withheld("#96*5*"))
    }

    @Test
    fun `splits off what is typed once connected`() {
        assertEquals(DialSplit("0301234567", "42#"), FritzDialer.splitExtension("030 1234567,,42#"))
        assertEquals(DialSplit("0301234567", "42"), FritzDialer.splitExtension("0301234567p42"))
        assertEquals(DialSplit("0301234567", "42"), FritzDialer.splitExtension("0301234567W42"))
        assertEquals(DialSplit("0301234567", "42"), FritzDialer.splitExtension("030 1234567 x 42"))
        assertEquals(DialSplit("+4930123", "42,1#"), FritzDialer.splitExtension("+49 30 123,42,,p1#"))
        assertEquals(DialSplit("0301234567", "42"), FritzDialer.splitExtension("0301234567;42"))
        assertEquals(DialSplit("0301234567", null), FritzDialer.splitExtension("0301234567,"))
    }

    @Test
    fun `takes the later digits from tel link parameters, and only those`() {
        assertEquals(DialSplit("+4930123", "42"), FritzDialer.splitExtension("+4930123;postd=42"))
        assertEquals(DialSplit("+4930123", "7"), FritzDialer.splitExtension("+4930123;ext=7"))
        assertEquals(DialSplit("7042", null), FritzDialer.splitExtension("7042;phone-context=example.com"))
        assertEquals(DialSplit("7042", "1"), FritzDialer.splitExtension("7042;phone-context=+4930;ext=1"))
    }

    @Test
    fun `never merges the later digits into the number`() {
        // The box would dial "030123456742" – a wrong number.
        assertEquals("0301234567", FritzDialer.sanitizeNumber("0301234567,,42"))
        assertEquals("*21#", FritzDialer.sanitizeNumber("*21#"))
        assertEquals("0301234567,42#", DialSplit("0301234567", "42#").written)
        assertEquals("42 … 1#", DialSplit("0301234567", "42,1#").shownExtension)
    }
}
