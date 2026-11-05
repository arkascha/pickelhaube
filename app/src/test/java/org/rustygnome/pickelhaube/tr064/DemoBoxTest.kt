package org.rustygnome.pickelhaube.tr064

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBoxTest {

    private val config = FritzConfig(host = DemoBox.HOST, username = DemoBox.USERNAME, password = DemoBox.PASSWORD)
    private val dialer = FritzDialer(config)

    @Test
    fun `is told by its host`() {
        assertTrue(DemoBox.isDemo(" Demo "))
        assertFalse(DemoBox.isDemo("fritz.box"))
    }

    @Test
    fun `refuses wrong credentials`() {
        val result = runCatching { FritzDialer(config.copy(password = "wrong")).listPhones() }
        assertTrue(result.exceptionOrNull() is AuthenticationException)
    }

    @Test
    fun `tells its users without login`() {
        assertEquals(listOf(BoxUser(DemoBox.USERNAME, true)), BoxUsers.list(config.copy(username = "", password = "")))
    }

    @Test
    fun `describes itself as its device info says`() {
        val identity = BoxDiscovery.identify(DemoBox.HOST)!!
        assertEquals(identity.info, dialer.deviceInfo())
        assertEquals(true, dialer.userRights().phone)
        assertEquals(DialingCodes("49", "00", "30", "0"), dialer.dialingCodes())
    }

    @Test
    fun `has its numbers registered`() {
        val numbers = dialer.loadNumbers()
        assertEquals(listOf(VoipLine(0, "03023125300", "registered"), VoipLine(1, "03023125301", "registered")), numbers.lines)
        assertEquals(listOf("*121#", "*122#"), numbers.numbers.map { it.selectCode })
    }

    @Test
    fun `lists its phones and keeps the dial-help phone`() {
        val phones = dialer.listPhones()
        assertEquals(5, phones.size)
        dialer.ensureDialPhone(phones[2])
        assertEquals(phones[2], dialer.getDialPhone())
    }

    @Test
    fun `hands out phone books, call list and call forwardings`() {
        val books = dialer.loadPhonebooks()
        assertEquals(listOf("Family & friends", "Services"), books.map { it.name })
        assertTrue(books.all { book -> book.contacts.all { it.uid != null } })
        assertTrue(dialer.loadRecentCalls().isNotEmpty())
        val deflections = dialer.loadDeflections()
        dialer.setDeflectionEnabled(deflections[0].id, true)
        assertTrue(dialer.loadDeflections()[0].enabled)
        dialer.setDeflectionEnabled(deflections[0].id, false)
    }

    @Test
    fun `changes speed dials and entries`() {
        val book = dialer.loadPhonebooks().first { it.name == "Services" }
        val garage = book.contacts.first { it.name == "Garage Krüger" }
        dialer.setSpeedDial(book.id, garage.uid!!, "07", garage.numbers[0].number)
        assertEquals("07", speedDialOf("Garage Krüger"))
        dialer.setSpeedDial(book.id, garage.uid, "07", null)
        assertEquals(null, speedDialOf("Garage Krüger"))

        dialer.addToPhonebook(book.id, PhonebookContact("Taxi", listOf(PhonebookNumber("03023125299", "work")), book.name))
        val taxi = dialer.loadPhonebooks().first { it.id == book.id }.contacts.first { it.name == "Taxi" }
        dialer.deletePhonebookEntry(book.id, taxi.uid!!)
        assertTrue(dialer.loadPhonebooks().first { it.id == book.id }.contacts.none { it.name == "Taxi" })
    }

    private fun speedDialOf(name: String): String? =
        dialer.loadPhonebooks().flatMap { it.contacts }.first { it.name == name }.numbers[0].speedDial
}
