package org.rustygnome.pickelhaube.shortcut

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.os.Build
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.data.BoxRecognition
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.tile.DeflectionTileService
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.forTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs the jobs of [QuietShortcutActivity]s, which are gone long before the box answered. */
private val shortcutJobs = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * A launcher shortcut that does its job without opening the app: it has no window (Theme.NoDisplay),
 * is gone again at once – without an animation, so nothing seems to open and close – and leaves the
 * job to run on its own; a toast says [starting] and then how it went. Not exported: only the launcher
 * (on Pickelhaube's behalf) starts it.
 */
abstract class QuietShortcutActivity : ComponentActivity() {

    /** What is being done, said at once. */
    protected abstract val starting: Int

    /** Does the job (off the main thread) with [context], and says how it went. */
    protected abstract suspend fun run(context: Context): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        noAnimation()
        // Started again from the recent apps, or recreated: the job was done already.
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            val context = AppLanguage.wrap(applicationContext)
            Toast.makeText(context, starting, Toast.LENGTH_SHORT).show()
            shortcutJobs.launch {
                val message = withContext(Dispatchers.IO) { run(context) }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
        // Theme.NoDisplay: finished before it would be shown.
        finish()
        noAnimation()
    }

    private fun noAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}

/**
 * The launcher shortcut "Call forwarding": switches the call forwarding the quick settings tile switches
 * – on the box reachable from the current network – the other way round. The state is read from the box
 * first, so a shortcut showing an outdated one still switches as expected.
 */
class DeflectionShortcutActivity : QuietShortcutActivity() {

    override val starting = R.string.tile_deflection_switching

    override suspend fun run(context: Context): String {
        val settings = SettingsRepository(context)
        val box = BoxRecognition.reachableBox(context, settings)
        LauncherShortcuts.updateBox(context, box)
        if (box == null) return context.getString(R.string.tile_no_box)
        val dialer = FritzDialer(box.config)
        val deflection = runCatching { dialer.loadDeflections() }.getOrNull()
            ?.forTile(settings.tileDeflection(box.id))
            ?: return context.getString(R.string.shortcut_deflection_unknown)
        val on = !deflection.enabled
        if (runCatching { dialer.setDeflectionEnabled(deflection.id, on) }.isFailure) {
            return context.getString(R.string.tile_deflection_failed)
        }
        // As the box has it now.
        val now = runCatching { dialer.loadDeflections() }.getOrNull()?.forTile(settings.tileDeflection(box.id))
            ?: deflection.copy(enabled = on)
        LauncherShortcuts.updateDeflection(context, now)
        DeflectionTileService.refresh(context)
        return context.getString(if (now.enabled) R.string.status_deflection_on_done else R.string.status_deflection_off_done)
    }
}

/** The launcher shortcut showing the FRITZ!Box reachable from here: looks for it again. */
class BoxShortcutActivity : QuietShortcutActivity() {

    override val starting = R.string.shortcut_box_looking

    override suspend fun run(context: Context): String {
        val box = BoxRecognition.reachableBox(context, SettingsRepository(context))
        LauncherShortcuts.updateBox(context, box)
        return box?.let { context.getString(R.string.shortcut_box_reachable, it.name) }
            ?: context.getString(R.string.tile_no_box)
    }
}
