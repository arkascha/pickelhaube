package org.rustygnome.pickelhaube.shortcut

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.data.BoxRecognition
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.tile.DeflectionTileService
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.forTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A launcher shortcut that does its job without opening the app: invisible, a toast says [starting]
 * and then how it went. Not exported: only the launcher (on Pickelhaube's behalf) starts it.
 */
abstract class QuietShortcutActivity : ComponentActivity() {

    /** What is being done, said at once. */
    protected abstract val starting: Int

    /** Does the job (off the main thread), and says how it went. */
    protected abstract suspend fun run(): String

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Started again from the recent apps, or recreated: the job was done already.
        if (savedInstanceState != null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            finish()
            return
        }
        Toast.makeText(this, starting, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) { run() }
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            finish()
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

    override suspend fun run(): String {
        val settings = SettingsRepository(this)
        val box = BoxRecognition.reachableBox(this, settings)
        LauncherShortcuts.updateBox(this, box)
        if (box == null) return getString(R.string.tile_no_box)
        val dialer = FritzDialer(box.config)
        val deflection = runCatching { dialer.loadDeflections() }.getOrNull()
            ?.forTile(settings.tileDeflection(box.id))
            ?: return getString(R.string.shortcut_deflection_unknown)
        val on = !deflection.enabled
        if (runCatching { dialer.setDeflectionEnabled(deflection.id, on) }.isFailure) {
            return getString(R.string.tile_deflection_failed)
        }
        // As the box has it now.
        val now = runCatching { dialer.loadDeflections() }.getOrNull()?.forTile(settings.tileDeflection(box.id))
            ?: deflection.copy(enabled = on)
        LauncherShortcuts.updateDeflection(this, now)
        DeflectionTileService.refresh(this)
        return getString(if (now.enabled) R.string.status_deflection_on_done else R.string.status_deflection_off_done)
    }
}

/** The launcher shortcut showing the FRITZ!Box reachable from here: looks for it again. */
class BoxShortcutActivity : QuietShortcutActivity() {

    override val starting = R.string.shortcut_box_looking

    override suspend fun run(): String {
        val box = BoxRecognition.reachableBox(this, SettingsRepository(this))
        LauncherShortcuts.updateBox(this, box)
        return box?.let { getString(R.string.shortcut_box_reachable, it.name) } ?: getString(R.string.tile_no_box)
    }
}
