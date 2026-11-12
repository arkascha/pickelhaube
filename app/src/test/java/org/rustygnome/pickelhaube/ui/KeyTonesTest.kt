package org.rustygnome.pickelhaube.ui

import android.media.ToneGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which tone each key of the keypad plays. */
class KeyTonesTest {

    @Test
    fun digitsAndSigns() {
        assertEquals(ToneGenerator.TONE_DTMF_0, KeyTones.toneFor('0'))
        assertEquals(ToneGenerator.TONE_DTMF_5, KeyTones.toneFor('5'))
        assertEquals(ToneGenerator.TONE_DTMF_9, KeyTones.toneFor('9'))
        assertEquals(ToneGenerator.TONE_DTMF_S, KeyTones.toneFor('*'))
        assertEquals(ToneGenerator.TONE_DTMF_P, KeyTones.toneFor('#'))
    }

    @Test
    fun nothingElse() {
        assertNull(KeyTones.toneFor('+'))
        assertNull(KeyTones.toneFor(','))
        assertNull(KeyTones.toneFor('a'))
    }
}
