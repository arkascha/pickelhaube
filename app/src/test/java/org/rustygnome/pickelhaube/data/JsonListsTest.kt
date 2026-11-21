package org.rustygnome.pickelhaube.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonListsTest {

    @Test
    fun `an unreadable entry is left out, the others are kept`() {
        val array = JSONArray("""[{"slot":"1","number":"0301"}, {"slot":"2"}, "no object", {"slot":"3","number":"0303"}]""")
        val skipped = mutableListOf<Int>()
        val dials = array.readEach({ i, _ -> skipped += i }, ::speedDialFromJson)
        assertEquals(listOf("0301", "0303"), dials.map { it.number })
        assertEquals(listOf(1, 2), skipped)
    }
}
