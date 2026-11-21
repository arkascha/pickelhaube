package org.rustygnome.pickelhaube.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Each object of this array as [read] makes it; one that cannot be read (not an object, a value
 * missing) is left out and handed to [onUnreadable] – instead of losing the whole list for it.
 */
internal inline fun <T> JSONArray.readEach(
    onUnreadable: (index: Int, error: Exception) -> Unit = { _, _ -> },
    read: (JSONObject) -> T,
): List<T> = (0 until length()).mapNotNull { i ->
    try {
        read(getJSONObject(i))
    } catch (e: Exception) {
        onUnreadable(i, e)
        null
    }
}
