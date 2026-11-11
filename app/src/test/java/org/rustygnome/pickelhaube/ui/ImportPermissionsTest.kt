package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rustygnome.pickelhaube.data.AppOptions
import org.rustygnome.pickelhaube.data.SettingsBackup

class ImportPermissionsTest {

    private val backup = SettingsBackup(
        profiles = emptyList(),
        activeProfileId = null,
        options = AppOptions(
            deviceContactsEnabled = true,
            callLogEnabled = true,
            phoneSpeedDialsEnabled = true,
            phoneSpeedDials = emptyList(),
            tileDeflections = emptyMap(),
            language = null,
        ),
    )

    @Test
    fun `the phone's contacts and calls stay on only where reading them is granted`() {
        assertEquals(backup, backup.limitedTo(contactsGranted = true, callLogGranted = true))
        val none = backup.limitedTo(contactsGranted = false, callLogGranted = false).options
        assertEquals(false, none.deviceContactsEnabled)
        assertEquals(false, none.callLogEnabled)
        // The other options come along unchanged.
        assertEquals(true, none.phoneSpeedDialsEnabled)
        assertEquals(true, backup.limitedTo(contactsGranted = false, callLogGranted = true).options.callLogEnabled)
    }

    @Test
    fun `switched off in the file, granted or not, they stay off`() {
        val off = backup.copy(options = backup.options.copy(deviceContactsEnabled = false, callLogEnabled = false))
        assertEquals(off, off.limitedTo(contactsGranted = true, callLogGranted = true))
    }
}
