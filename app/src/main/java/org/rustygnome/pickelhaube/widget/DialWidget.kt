package org.rustygnome.pickelhaube.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
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
        manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
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
            // The rows come from DialWidgetService; the data makes the adapter intent unique per widget.
            val adapter = Intent(context, DialWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("pickelhaube-widget://$id"))
            views.setRemoteAdapter(R.id.widget_list, adapter)
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
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
        }
    }
}
