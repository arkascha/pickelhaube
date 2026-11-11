package org.rustygnome.pickelhaube.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import androidx.core.widget.RemoteViewsCompat.RemoteCollectionItems
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts

/**
 * Home screen widget with numbers chosen by hand, each widget its own list: a tap puts the number
 * into the dialer – dialled with the green button only, as a tap on the home screen is easily made by
 * mistake. The logo in its corner, the title or the empty list open the widget's setup in the app.
 */
class DialWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, it) }
    }

    /** Resized: its rows may now have the number beside the name, or below it again. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetLists.remove(context, it) }
    }

    companion object {

        /** Redraws all dial widgets placed: e.g. so their texts follow the language. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, DialWidget::class.java)).forEach { update(context, it) }
        }

        /** Redraws the widget [id], its list included: after its setup was saved. */
        fun update(context: Context, id: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val texts = AppLanguage.wrap(context)
            val list = WidgetLists.get(context, id)
            val views = RemoteViews(context.packageName, R.layout.widget_dial)
            views.setInt(
                android.R.id.background, "setBackgroundResource",
                if (list.transparent) R.drawable.widget_background_transparent else R.drawable.widget_background,
            )
            views.setTextViewText(R.id.widget_title, list.title.ifBlank { texts.getString(R.string.app_name) })
            views.setTextViewText(R.id.widget_empty, texts.getString(R.string.widget_empty))
            val setup = PendingIntent.getActivity(
                context, id, LauncherShortcuts.widgetSetupIntent(context, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_header, setup)
            views.setOnClickPendingIntent(R.id.widget_logo, setup)
            views.setContentDescription(R.id.widget_logo, texts.getString(R.string.widget_edit))
            views.setOnClickPendingIntent(R.id.widget_empty, setup)
            // The rows go along with the widget itself (RemoteViewsCompat brings its own service before Android 12).
            RemoteViewsCompat.setRemoteAdapter(context, views, id, R.id.widget_list, rows(context, manager, id, list))
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            // Mutable, as each row fills in which entry it is; the number itself is looked up in the
            // widget's list, so nothing filled in can put anything else into the dialer.
            views.setPendingIntentTemplate(
                R.id.widget_list,
                PendingIntent.getActivity(
                    context, id, LauncherShortcuts.widgetDialIntent(context, id),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            manager.updateAppWidget(id, views)
        }

        /**
         * The rows of the widget's list: name, and the number below it – or beside it, if the widget is
         * wide enough. A tap fills in which entry it is, for the widget's pending intent template.
         */
        private fun rows(context: Context, manager: AppWidgetManager, id: Int, list: WidgetList): RemoteCollectionItems {
            // The width it has upright (the narrower one): the launcher says so in the widget's options.
            val wide = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) >= WIDE_DP
            val layout = if (wide) R.layout.widget_dial_row_wide else R.layout.widget_dial_row
            // Without the blue frame the rows get a dark tint of their own, to be readable on the wallpaper.
            val background = if (list.transparent) R.drawable.widget_row_transparent else R.drawable.widget_row
            val items = RemoteCollectionItems.Builder().setViewTypeCount(2)
            list.entries.forEachIndexed { position, entry ->
                val row = RemoteViews(context.packageName, layout).apply {
                    setTextViewText(R.id.row_name, entry.name.ifBlank { entry.number })
                    setTextViewText(R.id.row_number, if (entry.name.isBlank()) "" else entry.number)
                    setInt(R.id.row, "setBackgroundResource", background)
                    setOnClickFillInIntent(R.id.row, Intent().putExtra(LauncherShortcuts.EXTRA_WIDGET_ENTRY, position))
                }
                items.addItem(position.toLong(), row)
            }
            return items.build()
        }

        /** From this width (dp) the number goes beside the name. */
        private const val WIDE_DP = 250
    }
}
