package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.tr064.BoxIdentity
import org.rustygnome.pickelhaube.tr064.BoxInfo
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.UserRights

class SavedProfilesTest {

    private val config = FritzConfig(host = "fritz.box", username = "anna", password = "pw")
    private fun request(name: String = "", user: String = "anna", callList: Boolean = true) = SaveRequest(
        config = config.copy(username = user),
        name = name,
        phonebooksEnabled = true,
        callListEnabled = callList,
        speedDialsEnabled = false,
        internalCallsEnabled = true,
    )

    private val identity = BoxIdentity(
        udn = "uuid:new", friendlyName = "FRITZ!Box 7530 AX", modelName = "FRITZ!Box 7530 AX",
        host = "fritz.box", softwareVersion = "164.08.20", serialNumber = "AABBCC",
    )
    private val info = BoxInfo(model = "FRITZ!Box 7530 AX (UI)", softwareVersion = "164.08.21", serialNumber = "AA:BB:CC")
    private val codes = DialingCodes(countryCode = "49", areaCode = "40")
    private val rights = UserRights("anna", mapOf(UserRights.PHONE to "readwrite"))

    private val previous = BoxProfile(
        id = "p",
        name = "Home",
        config = config.copy(password = "old", trustedCertDer = "MIIB"),
        deviceId = "uuid:old",
        model = "old model",
        softwareVersion = "old",
        serialNumber = "old",
        dialingCodes = DialingCodes(countryCode = "41"),
        userRights = UserRights("anna", mapOf(UserRights.PHONE to "none")),
        callMonitor = true,
    )

    @Test
    fun `a new connection takes what the box told, logged in first`() {
        val profile = checkedProfile("n", request(), config, previous = null, report = BoxReport(identity, info, codes, rights))
        assertEquals("FRITZ!Box 7530 AX", profile.name)
        assertEquals("uuid:new", profile.deviceId)
        assertEquals("FRITZ!Box 7530 AX (UI)", profile.model)
        assertEquals("164.08.21", profile.softwareVersion)
        assertEquals("AA:BB:CC", profile.serialNumber)
        assertEquals(codes, profile.dialingCodes)
        assertEquals(rights, profile.userRights)
        assertTrue(profile.phonebooksEnabled)
        assertFalse(profile.speedDialsEnabled)
        assertTrue(profile.internalCallsEnabled)
    }

    @Test
    fun `without the rights for its details, the device description tells them`() {
        val profile = checkedProfile("n", request(), config, previous = null, report = BoxReport(identity, null, null, null))
        assertEquals("FRITZ!Box 7530 AX", profile.model)
        assertEquals("164.08.20", profile.softwareVersion)
        assertEquals("AABBCC", profile.serialNumber)
    }

    @Test
    fun `the name entered wins, then the box' own, then the one before, then the host`() {
        val report = BoxReport(identity, null, null, null)
        assertEquals("Office", checkedProfile("p", request(name = "  Office "), config, previous, report).name)
        assertEquals("FRITZ!Box 7530 AX", checkedProfile("p", request(), config, previous, report).name)
        assertEquals("Home", checkedProfile("p", request(), config, previous, BoxReport(null, null, null, null)).name)
        assertEquals("fritz.box", checkedProfile("n", request(), config, null, BoxReport(null, null, null, null)).name)
    }

    @Test
    fun `what the box did not say is kept from before`() {
        val profile = checkedProfile("p", request(), config, previous, BoxReport(null, null, null, null))
        assertEquals("uuid:old", profile.deviceId)
        assertEquals("old model", profile.model)
        assertEquals("old", profile.softwareVersion)
        assertEquals(previous.dialingCodes, profile.dialingCodes)
        assertEquals(previous.userRights, profile.userRights)
    }

    @Test
    fun `rights read before belong to the user of then`() {
        val profile = checkedProfile("p", request(user = "bernd"), config, previous, BoxReport(null, null, null, null))
        assertNull(profile.userRights)
    }

    @Test
    fun `without the right for it, the call list is switched off`() {
        val none = UserRights("anna", mapOf(UserRights.PHONE to UserRights.NONE))
        assertFalse(checkedProfile("p", request(), config, previous, BoxReport(null, null, null, none)).callListEnabled)
        // Not known: left as chosen.
        assertTrue(checkedProfile("p", request(), config, previous, BoxReport(null, null, null, null)).callListEnabled)
        assertFalse(checkedProfile("p", request(callList = false), config, previous, BoxReport(null, null, null, rights)).callListEnabled)
    }

    @Test
    fun `the config stored is the one given, with the certificate trusted meanwhile`() {
        val trusted = config.copy(trustedCertDer = "MIIC")
        assertEquals(trusted, checkedProfile("p", request(), trusted, previous, BoxReport(null, null, null, null)).config)
    }

    @Test
    fun `saved without checking, all the box told before stays`() {
        val profile = uncheckedProfile(previous, request(name = " ", callList = false))
        assertEquals("Home", profile.name)
        assertEquals(config, profile.config)
        assertEquals("uuid:old", profile.deviceId)
        assertEquals("old", profile.serialNumber)
        assertEquals(previous.userRights, profile.userRights)
        assertEquals(true, profile.callMonitor)
        assertFalse(profile.callListEnabled)
        assertTrue(profile.internalCallsEnabled)
        assertNull(uncheckedProfile(previous, request(user = "bernd")).userRights)
    }
}
