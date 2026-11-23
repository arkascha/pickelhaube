package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.widget.WidgetList

class PagesTest {

    private val box = BoxProfile(name = "Home", config = FritzConfig())

    private fun open(vararg pages: Page) = UiState(profiles = listOf(box)).opening(*pages)

    @Test
    fun `nothing open is the dialer`() {
        assertEquals(Page.DIALER, UiState().page)
    }

    @Test
    fun `first start - the welcome tour over the page for the first box over the settings`() {
        val start = open(Page.SETTINGS, Page.CONNECTION, Page.WELCOME)
        assertEquals(Page.WELCOME, start.page)
        // Left for the form: the page for the box, within the settings.
        assertEquals(Page.CONNECTION, start.closing(Page.WELCOME).page)
        assertEquals(Page.SETTINGS, start.closing(Page.WELCOME, Page.CONNECTION).page)
    }

    @Test
    fun `the settings opened over the picker, and closed again`() {
        val both = open(Page.PICKER, Page.SETTINGS)
        assertEquals(Page.SETTINGS, both.page)
        assertTrue(both.isOpen(Page.PICKER))
        assertEquals(Page.PICKER, both.closing(Page.SETTINGS).page)
    }

    @Test
    fun `the language and a box' page only within the settings`() {
        assertEquals(Page.LANGUAGE, open(Page.SETTINGS, Page.LANGUAGE).page)
        assertEquals(Page.DIALER, open(Page.LANGUAGE).page)
        assertEquals(Page.DIALER, open(Page.CONNECTION).page)
    }

    @Test
    fun `the widget setup waits for a box, and the picker opened from it comes first`() {
        val setup = WidgetSetup(1, WidgetList())
        assertEquals(Page.WIDGET_SETUP, open().copy(widgetSetup = setup).page)
        assertEquals(Page.PICKER, open(Page.PICKER).copy(widgetSetup = setup).page)
        // No box yet: the tour for the first one stays in front.
        val first = UiState(widgetSetup = setup).opening(Page.SETTINGS, Page.CONNECTION, Page.WELCOME)
        assertEquals(Page.WELCOME, first.page)
        assertEquals(Page.DIALER, UiState(widgetSetup = setup).page)
    }

    @Test
    fun `opening a page open already keeps it, closing one not open changes nothing`() {
        val state = open(Page.SETTINGS)
        assertEquals(state, state.opening(Page.SETTINGS))
        assertEquals(state, state.closing(Page.PICKER))
        assertFalse(state.withPage(Page.SETTINGS, false).isOpen(Page.SETTINGS))
    }
}
