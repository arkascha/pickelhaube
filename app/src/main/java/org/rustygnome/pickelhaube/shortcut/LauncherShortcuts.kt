package org.rustygnome.pickelhaube.shortcut

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.DeflectionState
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.tr064.CallDeflection

/**
 * The entries of the menu a long press on the app icon opens, in this order: redial the number dialled
 * last, switch the call forwarding, dial a number, the FRITZ!Box reachable from here. All are published
 * at run time – also the fixed one, since launchers put the ones declared in the manifest before all
 * others.
 *
 * Launchers show an entry's long label when it fits in one line, else its short one; so the long
 * labels are kept short, and the short ones carry what matters most.
 *
 * The dialer's entries go to [ALIAS], an alias of the main activity that other apps cannot start: only
 * an intent sent to it may dial without the green button being pressed.
 */
object LauncherShortcuts {

    /** The main activity, as started by these entries only (not exported). */
    const val ALIAS = "org.rustygnome.pickelhaube.LauncherShortcut"

    /** Dials the number of the intent's `tel:` data right away. */
    const val ACTION_DIAL_NOW = "org.rustygnome.pickelhaube.action.DIAL_NOW"
    const val ACTION_WIDGET_SETUP = "org.rustygnome.pickelhaube.action.WIDGET_SETUP"
    const val ACTION_WIDGET_DIAL = "org.rustygnome.pickelhaube.action.WIDGET_DIAL"
    /** Which entry of a dial widget's list was tapped (its position). */
    const val EXTRA_WIDGET_ENTRY = "org.rustygnome.pickelhaube.extra.WIDGET_ENTRY"

    /** Opens the number picker. */
    const val ACTION_PICKER = "org.rustygnome.pickelhaube.action.PICKER"

    private const val ID_REDIAL = "redial"

    /** Shortcuts on the home screen that dial one number ([pinCall]): this and the number. */
    private const val ID_CALL_PREFIX = "call:"

    /** An entry of earlier versions, removed again. */
    private const val ID_SPEED_DIALS = "speed_dials"
    private const val ID_DEFLECTION = "deflection"
    private const val ID_PICKER = "picker"
    private const val ID_BOX = "box"

    /** All entries as far as known without the box: at every start, so their texts follow the language. */
    fun publish(context: Context) {
        val settings = SettingsRepository(context)
        if (!settings.isConfigured()) return
        val texts = AppLanguage.wrap(context)
        runCatching { ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(ID_SPEED_DIALS)) }
        push(
            context,
            ShortcutInfoCompat.Builder(context, ID_PICKER)
                .setShortLabel(texts.getString(R.string.shortcut_dial_number))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_picker))
                .setIntent(aliasIntent(context, ACTION_PICKER))
                .setRank(2)
                .build(),
        )
        settings.lastDialledNumber?.let { updateRedial(context, it, settings.lastDialledName) }
        pushDeflection(context, settings.shortcutDeflection)
        pushBox(context, settings.shortcutBox)
    }

    /** The FRITZ!Box reachable from here: [box], as just looked for; null: none. */
    fun updateBox(context: Context, box: BoxProfile?) {
        val name = box?.name.orEmpty()
        val settings = SettingsRepository(context)
        if (settings.shortcutBox == name) return
        settings.shortcutBox = name
        pushBox(context, name)
    }

    /** The FRITZ!Box entry: [name] reachable, "" none, null not looked for yet. A tap looks again. */
    private fun pushBox(context: Context, name: String?) {
        val texts = AppLanguage.wrap(context)
        val reachable = !name.isNullOrEmpty()
        push(
            context,
            ShortcutInfoCompat.Builder(context, ID_BOX)
                .setShortLabel(
                    when {
                        name == null -> texts.getString(R.string.shortcut_box_unknown)
                        reachable -> name
                        else -> texts.getString(R.string.tile_no_box)
                    },
                )
                .setLongLabel(
                    when {
                        name == null -> texts.getString(R.string.shortcut_box_unknown)
                        reachable -> texts.getString(R.string.shortcut_box_reachable, name)
                        else -> texts.getString(R.string.tile_no_box)
                    },
                )
                .setIcon(
                    IconCompat.createWithResource(
                        context,
                        if (reachable) R.drawable.ic_shortcut_box_on else R.drawable.ic_shortcut_box_off,
                    ),
                )
                .setIntent(Intent(Intent.ACTION_VIEW).setClass(context, BoxShortcutActivity::class.java))
                .setRank(3)
                .build(),
        )
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
                .setIntent(dialNowIntent(context, number))
                .setRank(0)
                .build(),
        )
    }

    /**
     * "Call forwarding": [deflection] – the one the quick settings tile switches as well – as just read
     * from the box, remembered for the next start; null: the box has none set up. The entry stays
     * either way and switches whatever the box has then (or says why it cannot).
     */
    fun updateDeflection(context: Context, deflection: CallDeflection?) {
        val state = deflection?.let { DeflectionState(it.enabled, it.toNumber) }
        SettingsRepository(context).shortcutDeflection = state
        pushDeflection(context, state)
    }

    /** "Call forwarding" with its [state] – or, not known, just its name. */
    private fun pushDeflection(context: Context, state: DeflectionState?) {
        val texts = AppLanguage.wrap(context)
        val on = state?.enabled == true
        val label = when (state) {
            null -> texts.getString(R.string.tile_deflection)
            else -> texts.getString(if (on) R.string.shortcut_deflection_on else R.string.shortcut_deflection_off)
        }
        push(
            context,
            ShortcutInfoCompat.Builder(context, ID_DEFLECTION)
                .setShortLabel(label)
                .setLongLabel(
                    state?.let {
                        texts.getString(
                            if (on) R.string.shortcut_deflection_on_long else R.string.shortcut_deflection_off_long,
                            it.toNumber,
                        )
                    } ?: label,
                )
                .setIcon(
                    IconCompat.createWithResource(
                        context,
                        if (on) R.drawable.ic_shortcut_deflection_on else R.drawable.ic_shortcut_deflection_off,
                    ),
                )
                .setIntent(Intent(Intent.ACTION_VIEW).setClass(context, DeflectionShortcutActivity::class.java))
                .setRank(1)
                .build(),
        )
    }

    /** Whether the launcher can take a shortcut onto the home screen ([pinCall]). */
    fun canPin(context: Context): Boolean =
        runCatching { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }.getOrDefault(false)

    /**
     * Asks the launcher to put a shortcut onto the home screen that dials [number] at once, like
     * "Redial": named [name] (or the number), its icon the name's initials on the app's blue (a phone
     * for a number only) – the launcher adds the app's badge. The launcher asks the user to confirm and
     * places it; one per number: asked again, the launcher keeps the one there. Pinned shortcuts stay
     * when the app's own entries go ([removeAll]).
     */
    fun pinCall(context: Context, number: String, name: String?) {
        val label = name?.takeIf { it.isNotBlank() && it != number }
        val shortcut = ShortcutInfoCompat.Builder(context, ID_CALL_PREFIX + number.filter { it.isDigit() || it in "+*#" })
            .setShortLabel(label ?: number)
            .setLongLabel(label?.let { context.getString(R.string.shortcut_redial_named, it, number) } ?: number)
            .setIcon(label?.let { initialsIcon(context, it) } ?: IconCompat.createWithResource(context, R.drawable.ic_shortcut_redial))
            .setIntent(dialNowIntent(context, number))
            .build()
        runCatching { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }
    }

    /** Dials [number] at once, through [ALIAS]: what "Redial" and the shortcuts of [pinCall] start. */
    fun dialNowIntent(context: Context, number: String): Intent =
        aliasIntent(context, ACTION_DIAL_NOW).setData(Uri.fromParts("tel", number, null))

    /** The first letters of up to two words of [name], white on the app icon's blue, as an adaptive icon. */
    private fun initialsIcon(context: Context, name: String): IconCompat? {
        val initials = initials(name) ?: return null
        // An adaptive icon is 108 dp with the visible part in the middle 72 dp (or less, by the mask).
        val size = (108 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size * if (initials.length > 1) 0.26f else 0.32f
        }
        // Centred on the middle, not on the baseline.
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initials, size / 2f, y, paint)
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    /** "Anna Becker" → "AB", "Mum" → "M", "Dr. Weber, dentist" → "DW"; null: no letter in it (a number). */
    internal fun initials(name: String): String? =
        name.split(' ', ',', '-', '.').filter { word -> word.firstOrNull()?.isLetter() == true }
            .take(2).joinToString("") { it.first().uppercase() }.takeIf { it.isNotEmpty() }

    /** Everything goes; e.g. once the last connection was deleted. */
    fun removeAll(context: Context) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
    }

    /** The setup of the dial widget [id] (see [org.rustygnome.pickelhaube.widget.DialWidget]), through [ALIAS]. */
    fun widgetSetupIntent(context: Context, id: Int): Intent =
        aliasIntent(context, ACTION_WIDGET_SETUP).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    /** Puts an entry of the dial widget [id] into the dialer – which one, its row fills in as [EXTRA_WIDGET_ENTRY]. */
    fun widgetDialIntent(context: Context, id: Int): Intent =
        aliasIntent(context, ACTION_WIDGET_DIAL).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    private fun aliasIntent(context: Context, action: String) =
        Intent(action).setComponent(ComponentName(context.packageName, ALIAS))

    /** Adds or updates one entry; a launcher refusing it (rate limit, none at all) is no reason to fail. */
    private fun push(context: Context, shortcut: ShortcutInfoCompat) {
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }
}
