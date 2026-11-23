package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxDataTest {

    /** Read from a box: its phones, what was found in its lists, a note about the network. */
    private val loaded = UiState(
        phones = listOf("FON1: Kitchen"),
        selectedPhone = "FON1: Kitchen",
        phonebooksLoaded = true,
        newMissedCalls = 3,
        boxCallsLoaded = true,
        networkNotice = UiText.Raw("Another FRITZ!Box here"),
        // Not the box' data: stays.
        number = "0301234",
    )

    @Test
    fun `nothing of the box before is kept`() {
        assertEquals(UiState(number = "0301234"), loaded.withoutBoxData())
    }

    @Test
    fun `phones just read for the box that follows are kept`() {
        assertEquals(
            UiState(number = "0301234", phones = listOf("FON1: Kitchen"), selectedPhone = "FON1: Kitchen"),
            loaded.withoutBoxData(keepPhones = true),
        )
    }
}
