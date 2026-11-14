package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberEditingTest {

    @Test
    fun `typed at the cursor, at the end, or in place of a selection`() {
        // A digit put in the middle, after "03012"; a selection from..to (to not included) replaced.
        assertEquals(Edited("03012434567", 6), typed("0301234567", 5..5, '4'))
        assertEquals(Edited("0301234567", 10), typed("030123456", 9..9, '7'))
        assertEquals(Edited("03094567", 4), typed("0301234567", 3..6, '9'))
    }

    @Test
    fun `deleted before the cursor, or what is selected`() {
        assertEquals(Edited("030134567", 4), deletedBefore("0301234567", 5..5))
        assertEquals(Edited("030123456", 9), deletedBefore("0301234567", 10..10))
        assertEquals(Edited("030567", 3), deletedBefore("0301234567", 3..7))
        // At the start of the field: nothing before it.
        assertNull(deletedBefore("0301234567", 0..0))
    }
}
