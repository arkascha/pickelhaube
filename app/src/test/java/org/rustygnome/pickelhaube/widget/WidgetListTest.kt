package org.rustygnome.pickelhaube.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetListTest {

    @Test
    fun `keeps title and entries in their order`() {
        val list = WidgetList("Family", listOf(WidgetEntry("Anna", "+491715550000"), WidgetEntry("", "0305551234")))
        assertEquals(list, WidgetList.fromJson(list.toJson()))
    }

    @Test
    fun `an unreadable list is an empty one`() {
        assertEquals(WidgetList(), WidgetList.fromJson("not json"))
        assertEquals(WidgetList(), WidgetList.fromJson("{}"))
    }

    private val anna = WidgetEntry("Anna", "+491715550000")
    private val bernd = WidgetEntry("Bernd", "0305551234")
    private val clara = WidgetEntry("Clara", "0405550000")
    private val list = WidgetList("Family", listOf(anna, bernd, clara))

    @Test
    fun `moves an entry up and down, not beyond either end`() {
        assertEquals(listOf(bernd, anna, clara), list.moved(1, -1).entries)
        assertEquals(listOf(anna, clara, bernd), list.moved(1, +1).entries)
        assertEquals(list, list.moved(0, -1))
        assertEquals(list, list.moved(2, +1))
        assertEquals(list, list.moved(5, -1))
    }

    @Test
    fun `removes an entry`() {
        assertEquals(listOf(anna, clara), list.without(1).entries)
        assertEquals(list, list.without(3))
    }

    @Test
    fun `a number is added once only, the first place kept`() {
        val dora = WidgetEntry("Dora", "0895550000")
        assertEquals(listOf(anna, bernd, clara, dora), list.plus(dora).entries)
        assertEquals(list, list.plus(WidgetEntry("Bernd at work", "0305551234")))
    }
}
