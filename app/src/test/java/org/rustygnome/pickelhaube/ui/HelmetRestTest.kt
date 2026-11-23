package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HelmetRestTest {

    private val found = UiState(activeBoxSeen = true, boxesSeen = setOf("home"))

    @Test
    fun `looking out while the box is there`() {
        assertEquals(MascotPose.IDLE, helmetRest(found))
    }

    @Test
    fun `looking around while the box is looked for, or not found`() {
        assertEquals(MascotPose.SEARCHING, helmetRest(UiState()))
        assertEquals(MascotPose.SEARCHING, helmetRest(UiState(activeBoxSeen = false, boxesSeen = emptySet())))
    }

    @Test
    fun `puzzled while an error is shown, also a quiet one`() {
        val error = BoxError(UiText.Raw("Title"), UiText.Raw("Text"), false)
        assertEquals(MascotPose.PUZZLED, helmetRest(found.copy(error = error)))
        assertEquals(MascotPose.PUZZLED, helmetRest(found.copy(quietError = error)))
        assertEquals(MascotPose.PUZZLED, helmetRest(UiState(quietError = error)))
    }
}
