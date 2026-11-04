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
}
