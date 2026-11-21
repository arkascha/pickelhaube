package org.rustygnome.pickelhaube.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DecodedCacheTest {

    private var decodes = 0
    private val cache = DecodedCache { raw: String -> decodes++; listOf(raw.length) }

    @Test
    fun sameTextIsDecodedOnce() {
        val first = cache.get("abc")
        val second = cache.get("abc")
        assertSame(first, second)
        assertEquals(1, decodes)
    }

    @Test
    fun equalTextFromAnotherStringIsNotDecodedAgain() {
        cache.get("abc")
        cache.get(StringBuilder("ab").append('c').toString())
        assertEquals(1, decodes)
    }

    @Test
    fun changedTextIsDecodedAnew() {
        cache.get("abc")
        assertEquals(listOf(5), cache.get("abcde"))
        assertEquals(listOf(3), cache.get("abc"))
        assertEquals(3, decodes)
    }
}
