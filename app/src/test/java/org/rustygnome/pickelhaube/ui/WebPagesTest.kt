package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WebPagesTest {

    @Test
    fun `German texts open the German pages, anchors kept`() {
        val base = "https://www.rustygnome.org/pickelhaube/"
        assertEquals("${base}index.de.html", WebPages.localized(WebPages.HOME, "de"))
        assertEquals("${base}guide.de.html", WebPages.localized(WebPages.GUIDE, "de"))
        assertEquals("${base}guide.de.html#faq-end-call", WebPages.localized(WebPages.FAQ_END_CALL, "de"))
        assertEquals("${base}privacy-policy.de.html", WebPages.localized(WebPages.PRIVACY_POLICY, "de"))
        assertEquals("${base}feedback.de.html", WebPages.localized(WebPages.FEEDBACK, "de"))
    }

    @Test
    fun `English, other languages and other addresses stay as they are`() {
        assertEquals(WebPages.GUIDE_PREPARE, WebPages.localized(WebPages.GUIDE_PREPARE, "en"))
        assertEquals(WebPages.GUIDE, WebPages.localized(WebPages.GUIDE, "nl"))
        assertEquals(WebPages.SOURCE_CODE, WebPages.localized(WebPages.SOURCE_CODE, "de"))
        assertEquals("https://example.org/guide.html", WebPages.localized("https://example.org/guide.html", "de"))
    }

    @Test
    fun `a page already in German is not translated again`() {
        val german = "https://www.rustygnome.org/pickelhaube/guide.de.html#faq"
        assertEquals(german, WebPages.localized(german, "de"))
    }
}
