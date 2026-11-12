package org.rustygnome.pickelhaube.tr064

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which failures may come from phone books changed on the box since they were read (then they are read again). */
class PhonebookChangedTest {

    private fun fault(code: Int) = Tr064Exception(code, "", "fault $code")

    @Test
    fun faultsOfTheBox() {
        assertTrue(fault(714).mayBeStalePhonebook)
        assertTrue(fault(713).mayBeStalePhonebook)
        // A 7530 AX on removing a speed dial of an entry deleted in its web interface.
        assertTrue(fault(820).mayBeStalePhonebook)
        assertTrue(PhonebookEntryGoneException(0, "17").mayBeStalePhonebook)
        assertTrue(PhonebookMovedException(1, "Services").mayBeStalePhonebook)
    }

    @Test
    fun otherFailures() {
        assertFalse(fault(Tr064Exception.NOT_AUTHORIZED).mayBeStalePhonebook)
        assertFalse(fault(Tr064Exception.SECOND_FACTOR_REQUIRED).mayBeStalePhonebook)
        assertFalse(IOException("timeout").mayBeStalePhonebook)
        assertFalse(AuthenticationException().mayBeStalePhonebook)
        assertFalse(HttpStatusException(500).mayBeStalePhonebook)
    }
}
