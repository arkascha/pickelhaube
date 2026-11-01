package org.rustygnome.pickelhaube.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rustygnome.pickelhaube.tr064.BoxIdentity
import org.rustygnome.pickelhaube.tr064.FritzConfig

class HostCheckTest {

    private val home = BoxProfile(id = "home", name = "Home", config = FritzConfig(host = "fritz.box"), deviceId = "uuid:home")
    private val holiday = BoxProfile(id = "holiday", name = "Holiday flat", config = FritzConfig(host = "fritz.box"), deviceId = "uuid:holiday")
    private val profiles = listOf(home, holiday)

    private fun answer(udn: String) = BoxIdentity(udn = udn, friendlyName = "FRITZ!Box 7590", modelName = "FRITZ!Box 7590", host = "fritz.box")

    @Test
    fun `the box being edited answers`() {
        assertEquals(HostCheck.Expected, HostCheck.of(answer("uuid:home"), home, profiles))
    }

    @Test
    fun `another box set up answers, named as set up`() {
        assertEquals(HostCheck.OtherBox("Home"), HostCheck.of(answer("uuid:home"), holiday, profiles))
    }

    @Test
    fun `a box not set up answers, named as it calls itself`() {
        assertEquals(HostCheck.OtherBox("FRITZ!Box 7590"), HostCheck.of(answer("uuid:neighbour"), holiday, profiles))
    }

    @Test
    fun `no box answers for one being edited`() {
        assertEquals(HostCheck.NoAnswer, HostCheck.of(null, holiday, profiles))
    }

    @Test
    fun `a box never recognised takes any box not set up otherwise`() {
        val unknown = holiday.copy(deviceId = null)
        assertEquals(HostCheck.Expected, HostCheck.of(answer("uuid:new"), unknown, listOf(home, unknown)))
        assertEquals(HostCheck.OtherBox("Home"), HostCheck.of(answer("uuid:home"), unknown, listOf(home, unknown)))
    }

    @Test
    fun `a new connection to a box set up already`() {
        assertEquals(HostCheck.AlreadySetUp("Home"), HostCheck.of(answer("uuid:home"), null, profiles))
    }

    @Test
    fun `a new connection to a box not set up, or to none`() {
        assertEquals(HostCheck.Expected, HostCheck.of(answer("uuid:new"), null, profiles))
        assertEquals(HostCheck.Expected, HostCheck.of(null, null, profiles))
    }
}
