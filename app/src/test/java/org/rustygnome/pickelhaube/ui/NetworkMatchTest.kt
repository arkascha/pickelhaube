package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.BoxRecognition
import org.rustygnome.pickelhaube.tr064.BoxIdentity
import org.rustygnome.pickelhaube.tr064.FritzConfig

class NetworkMatchTest {

    private val home = BoxProfile(id = "home", name = "Home", config = FritzConfig(host = "fritz.box"), deviceId = "uuid:home")
    private val office = BoxProfile(id = "office", name = "Office", config = FritzConfig(host = "office.example.org"), deviceId = "uuid:office")
    private val newOne = BoxProfile(id = "new", name = "New", config = FritzConfig(host = "192.168.1.1"))

    private fun box(udn: String, host: String, name: String? = null) = BoxIdentity(udn, name, null, host)

    @Test
    fun `the box of this very network wins over one only reachable at its host`() {
        // At home, with the office box reachable through a VPN.
        val found = listOf(box("uuid:office", "office.example.org"), box("uuid:home", "192.168.178.1"))
        assertEquals(home, BoxRecognition.match(found, listOf("192.168.178.1", "fritz.box"), listOf(home, office)))
        // Elsewhere, only the office answers – at its configured host.
        val away = listOf(box("uuid:office", "office.example.org"))
        assertEquals(office, BoxRecognition.match(away, listOf("10.0.0.1", "fritz.box"), listOf(home, office)))
    }

    @Test
    fun `a box with an unknown device id matches none`() {
        val found = listOf(box("uuid:stranger", "192.168.178.1"))
        assertNull(BoxRecognition.match(found, listOf("192.168.178.1"), listOf(home, office, newOne)))
    }

    @Test
    fun `seen by device id, or by host while the device id is not known`() {
        val found = listOf(box("uuid:home", "fritz.box"), box("uuid:x", "192.168.1.1"))
        assertTrue(home.answeredAmong(found))
        assertFalse(office.answeredAmong(found))
        assertTrue(newOne.answeredAmong(found))
        // Host case does not matter.
        assertTrue(newOne.copy(config = FritzConfig(host = "FRITZ.BOX")).answeredAmong(listOf(box("uuid:y", "fritz.box"))))
        // With a device id, the host is not enough.
        assertFalse(office.answeredAmong(listOf(box("uuid:other", "office.example.org"))))
    }

    @Test
    fun `boxes counted as seen by device id only`() {
        val found = listOf(box("uuid:home", "fritz.box"), box("uuid:x", "192.168.1.1"))
        assertEquals(setOf("home"), seenByDeviceId(listOf(home, office, newOne), found))
    }

    @Test
    fun `an unknown box at the gateway is mentioned, only when none matched`() {
        val stranger = box("uuid:stranger", "192.168.0.1", "FRITZ!Box 7590")
        val found = listOf(box("uuid:office", "office.example.org"), stranger)
        assertEquals(stranger, unknownGatewayBox(found, "192.168.0.1", match = null))
        assertNull(unknownGatewayBox(found, "192.168.0.1", match = office))
        assertNull(unknownGatewayBox(found, gateway = null, match = null))
    }
}
