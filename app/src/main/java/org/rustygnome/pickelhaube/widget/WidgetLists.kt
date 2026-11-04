package org.rustygnome.pickelhaube.widget

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** A number offered by a dial widget, with whose number it is. */
data class WidgetEntry(val name: String, val number: String)

/**
 * What a dial widget offers: its [title] (blank: the app's name) and its [entries], in their order;
 * [transparent]: without its blue frame, showing the wallpaper.
 */
data class WidgetList(
    val title: String = "",
    val entries: List<WidgetEntry> = emptyList(),
    val transparent: Boolean = false,
) {

    fun toJson(): String = JSONObject()
        .put("title", title)
        .put("transparent", transparent)
        .put("entries", JSONArray().also { array -> entries.forEach { array.put(JSONObject().put("name", it.name).put("number", it.number)) } })
        .toString()

    companion object {
        fun fromJson(json: String): WidgetList = runCatching {
            val o = JSONObject(json)
            val array = o.optJSONArray("entries") ?: JSONArray()
            WidgetList(
                o.optString("title"),
                (0 until array.length()).map { i -> array.getJSONObject(i).let { WidgetEntry(it.optString("name"), it.getString("number")) } },
                o.optBoolean("transparent"),
            )
        }.getOrDefault(WidgetList())
    }
}

/**
 * The lists of the dial widgets placed, one per widget id. Kept apart from the settings: widget ids
 * belong to this phone's home screen, so they are neither exported nor taken to another phone.
 */
object WidgetLists {

    private fun prefs(context: Context) = context.getSharedPreferences("pickelhaube_widgets", Context.MODE_PRIVATE)

    fun get(context: Context, id: Int): WidgetList =
        prefs(context).getString(id.toString(), null)?.let(WidgetList::fromJson) ?: WidgetList()

    fun set(context: Context, id: Int, list: WidgetList) = prefs(context).edit { putString(id.toString(), list.toJson()) }

    fun remove(context: Context, id: Int) = prefs(context).edit { remove(id.toString()) }
}
