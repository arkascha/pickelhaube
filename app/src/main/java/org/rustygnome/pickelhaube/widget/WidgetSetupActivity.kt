package org.rustygnome.pickelhaube.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts

/**
 * Started by the launcher when a dial widget is placed (and, from Android 12, to reconfigure it):
 * accepts the widget at once and opens its setup in the app, where the phone books and recent calls
 * to choose from are at hand.
 */
class WidgetSetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            DialWidget.update(this, id)
            startActivity(LauncherShortcuts.widgetSetupIntent(this, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
