package org.rustygnome.pickelhaube.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts

/** The rows of a dial widget's list: name, and the number below it – or beside it, if the widget is wide enough. */
class DialWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Rows(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))

    private class Rows(private val context: Context, private val id: Int) : RemoteViewsFactory {

        private var entries: List<WidgetEntry> = emptyList()

        /** Wide enough to have the number beside the name rather than below it. */
        private var wide = false

        /** Without the blue frame: the rows get a dark tint of their own, to be readable on the wallpaper. */
        private var transparent = false

        override fun onCreate() = Unit
        override fun onDataSetChanged() {
            val list = WidgetLists.get(context, id)
            entries = list.entries
            transparent = list.transparent
            // The width it has upright (the narrower one): the launcher says so in the widget's options.
            val width = AppWidgetManager.getInstance(context).getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            wide = width >= WIDE_DP
        }
        override fun onDestroy() = Unit
        override fun getCount() = entries.size
        override fun getViewTypeCount() = 2
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val layout = if (wide) R.layout.widget_dial_row_wide else R.layout.widget_dial_row
            val entry = entries.getOrNull(position) ?: return RemoteViews(context.packageName, layout)
            return RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.row_name, entry.name.ifBlank { entry.number })
                setTextViewText(R.id.row_number, if (entry.name.isBlank()) "" else entry.number)
                setInt(R.id.row, "setBackgroundResource", if (transparent) R.drawable.widget_row_transparent else R.drawable.widget_row)
                setOnClickFillInIntent(R.id.row, Intent().putExtra(LauncherShortcuts.EXTRA_WIDGET_ENTRY, position))
            }
        }
    }

    private companion object {
        /** From this width (dp) the number goes beside the name. */
        const val WIDE_DP = 250
    }
}
