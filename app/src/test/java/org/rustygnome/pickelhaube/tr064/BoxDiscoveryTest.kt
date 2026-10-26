package org.rustygnome.pickelhaube.tr064

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxDiscoveryTest {

    @Test
    fun `reads the device details from the description`() {
        val xml = """<?xml version="1.0"?>
            <root xmlns="urn:dslforum-org:device-1-0">
            <systemVersion><HW>256</HW><Major>256</Major><Display>256.08.25</Display></systemVersion>
            <device>
            <friendlyName>FRITZ!Box 7530 AX</friendlyName>
            <modelName>FRITZ!Box 7530 AX</modelName>
            <UDN>uuid:739f2409-bccb-40e7-8e6c-487F80226DA8</UDN>
            <serialNumber>48:7f:80:22:6D:A8</serialNumber>
            <deviceList><device><modelName>other</modelName><UDN>uuid:other</UDN></device></deviceList>
            </device>
            </root>"""
        val box = BoxDiscovery.parse(xml, "fritz.box")!!
        assertEquals("uuid:739f2409-bccb-40e7-8e6c-487F80226DA8", box.udn)
        assertEquals(BoxInfo("FRITZ!Box 7530 AX", "256.08.25", "487F80226DA8"), box.info)
        assertEquals("8.25", box.info.fritzOsVersion)
    }
}
