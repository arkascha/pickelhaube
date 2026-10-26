package org.rustygnome.pickelhaube.tr064

import org.junit.Assert.assertEquals
import org.junit.Test

class DeflectionsTest {

    @Test
    fun `reads the deflections as the box lists them`() {
        val xml = "<List><Item><DeflectionId>0</DeflectionId><Enable>1</Enable><Type>toVoIP</Type>" +
            "<Number>4052470036</Number><DeflectionToNumber>017644452996</DeflectionToNumber><Mode>eParallelCall</Mode>" +
            "<Outgoing></Outgoing><PhonebookID></PhonebookID></Item>" +
            "<Item><DeflectionId>1</DeflectionId><Enable>0</Enable><Type>fromAll</Type><Number></Number>" +
            "<DeflectionToNumber>030 1</DeflectionToNumber><Mode>eBusy</Mode></Item></List>"
        assertEquals(
            listOf(
                CallDeflection(0, true, "toVoIP", "4052470036", "017644452996", "eParallelCall"),
                CallDeflection(1, false, "fromAll", "", "030 1", "eBusy"),
            ),
            FritzDeflections.parse(xml),
        )
        assertEquals(emptyList<CallDeflection>(), FritzDeflections.parse(""))
    }
}
