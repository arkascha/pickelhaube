package org.rustygnome.pickelhaube.data

import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.UserRights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFileTest {

    private val home = BoxProfile(
        id = "a",
        name = "Moorfleet",
        config = FritzConfig(host = "fritz.box", port = 49443, username = "pickelhaube", password = "geheim ü", useHttps = true, trustedCertDer = "MIIB"),
        deviceId = "uuid:1",
        model = "FRITZ!Box 7530 AX",
        serialNumber = "AABBCC",
        dialingCodes = DialingCodes(countryCode = "49", internationalPrefix = "00", areaCode = "40", nationalPrefix = "0"),
        userRights = UserRights("pickelhaube", mapOf("Phone" to "readwrite")),
        callListEnabled = false,
    )
    private val other = BoxProfile(id = "b", name = "Niendorf", config = FritzConfig(host = "192.168.1.1", username = "u", password = "pw2"))
    private val backup = SettingsBackup(
        profiles = listOf(home, other),
        activeProfileId = "b",
        options = AppOptions(
            deviceContactsEnabled = true,
            callLogEnabled = false,
            phoneSpeedDialsEnabled = true,
            phoneSpeedDials = listOf(SpeedDial("1", "Mama", "040123")),
            tileDeflections = mapOf("a" to 2),
            language = "de",
        ),
    )
    private val passphrase = "richtig lang"

    private fun write(passphrase: String?) = SettingsFile.write(backup, passphrase?.toCharArray(), iterations = 1_000)

    private fun withoutPasswords(b: SettingsBackup) =
        b.copy(profiles = b.profiles.map { it.copy(config = it.config.copy(password = "")) })

    @Test
    fun withPassphraseEverythingComesBack() {
        val text = write(passphrase)
        assertEquals(SettingsFileInfo(boxes = 2, withPasswords = true), SettingsFile.inspect(text))
        assertEquals(backup, SettingsFile.read(text, passphrase.toCharArray()))
    }

    @Test
    fun passwordsAreNotInClearText() {
        val text = write(passphrase)
        assertFalse(text.contains("geheim"))
        assertFalse(text.contains("pw2"))
    }

    @Test
    fun withoutPassphraseTheFileHasNoPasswords() {
        val text = write(null)
        assertEquals(SettingsFileInfo(boxes = 2, withPasswords = false), SettingsFile.inspect(text))
        assertFalse(text.contains("geheim"))
        assertEquals(withoutPasswords(backup), SettingsFile.read(text, null))
    }

    @Test
    fun readWithoutPassphraseLeavesPasswordsEmpty() {
        assertEquals(withoutPasswords(backup), SettingsFile.read(write(passphrase), null))
    }

    @Test(expected = WrongPassphraseException::class)
    fun wrongPassphraseIsRefused() {
        SettingsFile.read(write(passphrase), "falsch falsch".toCharArray())
    }

    @Test
    fun otherFilesAreRefused() {
        for (text in listOf("", "kein JSON", "{}", """{"format":"something-else","boxes":[]}""",
            """{"format":"pickelhaube-settings","version":99,"boxes":[]}""")) {
            try {
                SettingsFile.inspect(text)
                throw AssertionError("accepted: $text")
            } catch (_: InvalidSettingsFileException) {
            }
        }
    }

    @Test
    fun missingOptionsGetDefaults() {
        val read = SettingsFile.read("""{"format":"pickelhaube-settings","version":1,"boxes":[]}""", null)
        assertTrue(read.profiles.isEmpty())
        assertTrue(read.options.phoneSpeedDialsEnabled)
        assertFalse(read.options.deviceContactsEnabled)
        assertEquals(null, read.options.language)
    }
}
