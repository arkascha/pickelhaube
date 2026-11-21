package org.rustygnome.pickelhaube.data

/**
 * Remembers what [decode] made of the last stored text, so the same text is decoded only once.
 * Any other text (it was changed) is decoded anew. Safe to use from several threads: at worst
 * two of them decode the same text at once.
 */
internal class DecodedCache<T>(private val decode: (String) -> T) {

    private class Entry<T>(val raw: String, val value: T)

    @Volatile
    private var last: Entry<T>? = null

    fun get(raw: String): T {
        // SharedPreferences hand out the same String until the value changes: mostly no comparison needed.
        last?.let { if (it.raw === raw || it.raw == raw) return it.value }
        return decode(raw).also { last = Entry(raw, it) }
    }
}
