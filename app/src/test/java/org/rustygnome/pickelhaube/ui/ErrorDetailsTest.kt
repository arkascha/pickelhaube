package org.rustygnome.pickelhaube.ui

import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.Tr064Call
import org.rustygnome.pickelhaube.tr064.Tr064Exception
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ErrorDetailsTest {

    @Test
    fun `tells the failed request and the box' answer`() {
        val error = Tr064Exception(713, "SpecifiedArrayIndexInvalid", "FRITZ!Box error 713")
        error.addSuppressed(
            Tr064Call(
                "http://fritz.box:49000", "urn:dslforum-org:service:X_AVM-DE_OnTel:1", "GetPhonebookEntryUID",
                mapOf("NewPhonebookID" to "2", "NewPhonebookEntryData" to "x".repeat(200)), 500,
            ),
        )
        val details = describeError(error, FritzConfig()).technical!!
        assertEquals(
            "X_AVM-DE_OnTel:1 · GetPhonebookEntryUID\n" +
                "  NewPhonebookID = 2\n" +
                "  NewPhonebookEntryData = ${"x".repeat(120)}… (200)\n" +
                "HTTP 500 · UPnP 713 SpecifiedArrayIndexInvalid\n" +
                "http://fritz.box:49000",
            details.asText(),
        )
    }

    @Test
    fun `has none for an error without request`() {
        assertNull(describeError(IOException("no network"), FritzConfig()).technical)
    }
}
