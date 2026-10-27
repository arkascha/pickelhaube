package org.rustygnome.pickelhaube.shortcut

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.tr064.CallDeflection

/**
 * The entries of the menu a long press on the app icon opens, in this order: redial the number dialled
 * last, the speed dials, switch the call forwarding, choose a number. All are published at run time –
 * also the fixed ones, since launchers put the ones declared in the manifest before all others.
 *
 * The dialer's entries go to [ALIAS], an alias of the main activity that other apps cannot start: only
 * an intent sent to it may dial without the green button being pressed.
 */
object LauncherShortcuts {

    /** The main activity, as started by these entries only (not exported). */
    const val ALIAS = "org.rustygnome.pickelhaube.LauncherShortcut"

    /** Dials the number of the intent's `tel:` data right away. */
    const val ACTION_DIAL_NOW = "org.rustygnome.pickelhaube.action.DIAL_NOW"

    /** Opens the number picker on its speed dial tab. */
    const val ACTION_SPEED_DIALS = "org.rustygnome.pickelhaube.action.SPEED_DIALS"

    /** Opens the number picker. */
    const val ACTION_PICKER = "org.rustygnome.pickelhaube.action.PICKER"

    private const val ID_REDIAL = "redial"
    private const val ID_SPEED_DIALS = "speed_dials"
    private const val ID_DEFLECTION = "deflection"
    private const val ID_PICKER = "picker"

    /** All entries as far as known without the box: at every start, so their texts follow the language. */
    fun publish(context: Context) {
        val settings = SettingsRepository(context)
        if (!settings.isConfigured()) return
        val texts = AppLanguage.wrap(context)
        push(
            context,
            ShortcutInfoCompat.Builder(context, ID_SPEED_DIALS)
                .setShortLabel(texts.getString(R.string.speed_dial))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_speed_dial))
                .setIntent(aliasIntent(context, ACTION_SPEED_DIALS))
                .setRank(1)
                .build(),
        )
        push(
            context,
            ShortcutInfoCompat.Builder(context, ID_PICKER)
                .setShortLabel(texts.getString(R.string.picker_title))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_picker))
                .setIntent(aliasIntent(context, ACTION_PICKER))
                .setRank(3)
                .build(),
        )
        settings.lastDialledNumber?.let { updateRedial(context, it, settings.lastDialledName) }
    }

    /** "Redial": dials [number] – the one dialled last – at once; shows [name] as well, if known. */
    fun updateRedial(context: Context, number: String, name: String?) {
        val texts = AppLanguage.wrap(context)
        push(
            context,
            ShortcutInfoCompat.Builder(context, ID_REDIAL)
                .setShortLabel(name ?: number)
                .setLongLabel(name?.let { texts.getString(R.string.shortcut_redial_named, it, number) } ?: number)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_redial))
                .setIntent(aliasIntent(context, ACTION_DIAL_NOW).setData(Uri.fromParts("tel", number, null)))
                .setRank(0)
                .build(),
        )
    }

    /**
     * "Call forwarding": switches [deflection] – the one the quick settings tile switches as well – on
     * or off, and says which state it is in. Null: the box has none set up, so the entry goes.
     */
    fun updateDeflection(context: Context, deflection: CallDeflection?) {
        if (deflection == null) {
            runCatching { ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(ID_DEFLECTION)) }
            return
        }
        val texts = AppLanguage.wrap(context)
        val on = deflection.enabled
        push(
            context,
            ShortcutInfoCompat.Builder(context, ID_DEFLECTION)
                .setShortLabel(texts.getString(if (on) R.string.shortcut_deflection_on else R.string.shortcut_deflection_off))
                .setLongLabel(
                    texts.getString(
                        if (on) R.string.shortcut_deflection_on_long else R.string.shortcut_deflection_off_long,
                        deflection.toNumber,
                    ),
                )
                .setIcon(
                    IconCompat.createWithResource(
                        context,
                        if (on) R.drawable.ic_shortcut_deflection_on else R.drawable.ic_shortcut_deflection_off,
                    ),
                )
                .setIntent(Intent(Intent.ACTION_VIEW).setClass(context, DeflectionShortcutActivity::class.java))
                .setRank(2)
                .build(),
        )
    }

    /** Everything goes; e.g. once the last connection was deleted. */
    fun removeAll(context: Context) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
    }

    private fun aliasIntent(context: Context, action: String) =
        Intent(action).setComponent(ComponentName(context.packageName, ALIAS))

    /** Adds or updates one entry; a launcher refusing it (rate limit, none at all) is no reason to fail. */
    private fun push(context: Context, shortcut: ShortcutInfoCompat) {
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }
}
