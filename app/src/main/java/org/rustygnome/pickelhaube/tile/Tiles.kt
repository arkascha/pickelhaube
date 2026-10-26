package org.rustygnome.pickelhaube.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import org.rustygnome.pickelhaube.MainActivity
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.BoxRecognition
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.tr064.CallDeflection
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.forTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick settings tile "Pickelhaube": opens the dialer. It is active (highlighted) while a FritzBox is
 * reachable from the current network – looked for each time the shade is pulled down, like the app
 * does –, and its subtitle (Android 10 and later) names it, or says that none is.
 */
class DialerTileService : TileService() {

    /** Runs the look for the box while the tile is shown. */
    private var scope: CoroutineScope? = null

    override fun onTileAdded() = rememberAdded(PickelhaubeTile.DIALER, true)

    override fun onTileRemoved() = rememberAdded(PickelhaubeTile.DIALER, false)

    override fun onStartListening() {
        super.onStartListening()
        // Shown, so it is there – also when it was added before Pickelhaube kept track.
        rememberAdded(PickelhaubeTile.DIALER, true)
        val tile = qsTile ?: return
        tile.label = getString(PickelhaubeTile.DIALER.label)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val settings = SettingsRepository(this)
        val profiles = settings.profiles()
        if (profiles.isEmpty()) return
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also {
            it.launch {
                val box = reachableBox(settings)
                qsTile?.apply {
                    state = if (box != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    subtitle = box?.name ?: getString(R.string.tile_no_box)
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        openApp(Intent(this, MainActivity::class.java))
    }
}

/**
 * Quick settings tile "Redial": opens the dialer with the number dialled last. It does not dial by
 * itself – a tap in the pulled-down shade is easily made by accident – so the green button still
 * has to be pressed. Unavailable until something was dialled; like the "Pickelhaube" tile, active
 * (highlighted) while a FritzBox is reachable from the current network.
 */
class RedialTileService : TileService() {

    /** Runs the look for the box while the tile is shown. */
    private var scope: CoroutineScope? = null

    override fun onTileAdded() = rememberAdded(PickelhaubeTile.REDIAL, true)

    override fun onTileRemoved() = rememberAdded(PickelhaubeTile.REDIAL, false)

    override fun onStartListening() {
        super.onStartListening()
        // Shown, so it is there – also when it was added before Pickelhaube kept track.
        rememberAdded(PickelhaubeTile.REDIAL, true)
        val tile = qsTile ?: return
        val number = SettingsRepository(this).lastDialledNumber
        tile.label = getString(PickelhaubeTile.REDIAL.label)
        tile.state = if (number == null) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = number ?: getString(R.string.tile_redial_none)
        }
        tile.updateTile()
        if (number == null) return
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also {
            it.launch {
                val box = reachableBox(SettingsRepository(this@RedialTileService))
                qsTile?.apply {
                    state = if (box != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val number = SettingsRepository(this).lastDialledNumber ?: return
        // A tel: link, as from Contacts: the dialer takes the number over.
        openApp(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null), this, MainActivity::class.java))
    }

    companion object {
        /** Lets the tile show the new number the next time the shade is pulled down. */
        fun refresh(context: Context) {
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, RedialTileService::class.java))
            }
        }
    }
}

/**
 * Quick settings tile "Call forwarding": switches a call forwarding of the FritzBox reachable from
 * the current network on and off – the one chosen for it in the app, else the first one set up. It
 * shows whether it is on, and where it forwards to; unavailable without a box or a forwarding.
 * Switching asks for the phone to be unlocked first.
 */
class DeflectionTileService : TileService() {

    /** Reads the box while the tile is shown. */
    private var scope: CoroutineScope? = null

    /** Switches – not stopped when the shade closes before the box answered. */
    private val actions = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The box and the forwarding the tile shows, once read. */
    private var shown: Pair<BoxProfile, CallDeflection>? = null

    override fun onTileAdded() = rememberAdded(PickelhaubeTile.DEFLECTION, true)

    override fun onTileRemoved() = rememberAdded(PickelhaubeTile.DEFLECTION, false)

    override fun onStartListening() {
        super.onStartListening()
        // Shown, so it is there – also when it was added before Pickelhaube kept track.
        rememberAdded(PickelhaubeTile.DEFLECTION, true)
        qsTile?.apply {
            label = getString(PickelhaubeTile.DEFLECTION.label)
            updateTile()
        }
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { it.launch { read() } }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onDestroy() {
        actions.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val (box, deflection) = shown ?: return
        val switch = {
            val on = !deflection.enabled
            // Shown at once; corrected below if the box did otherwise.
            show(box, deflection.copy(enabled = on), getString(R.string.tile_deflection_switching))
            actions.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { FritzDialer(box.config).setDeflectionEnabled(deflection.id, on) }
                }
                if (result.isFailure) {
                    show(box, deflection, getString(R.string.tile_deflection_failed))
                } else {
                    read()
                }
            }
        }
        // Not from a locked phone: forwarding calls elsewhere is nothing for anyone at hand.
        if (isLocked) unlockAndRun { switch() } else switch()
    }

    /** Reads the forwardings of the reachable box and shows the one for the tile. */
    private suspend fun read() {
        val settings = SettingsRepository(this)
        val box = reachableBox(settings) ?: return unavailable(getString(R.string.tile_no_box))
        val deflections = withContext(Dispatchers.IO) { runCatching { FritzDialer(box.config).loadDeflections() }.getOrNull() }
            ?: return unavailable(getString(R.string.tile_deflection_unreadable))
        val deflection = deflections.forTile(settings.tileDeflection(box.id))
            ?: return unavailable(getString(R.string.tile_deflection_none))
        show(box, deflection, null)
    }

    private fun show(box: BoxProfile, deflection: CallDeflection, note: String?) {
        shown = box to deflection
        qsTile?.apply {
            state = if (deflection.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = note ?: "→ ${deflection.toNumber}"
            }
            updateTile()
        }
    }

    private fun unavailable(why: String) {
        shown = null
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = why
            updateTile()
        }
    }

    companion object {
        /** Lets the tile read the forwardings anew, e.g. after one was switched in the app. */
        fun refresh(context: Context) {
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, DeflectionTileService::class.java))
            }
        }
    }
}

/**
 * The configured box reachable from the current network, looked for as the app does: by device id,
 * or – for a connection without one – the box in use when its host answered.
 */
private suspend fun TileService.reachableBox(settings: SettingsRepository): BoxProfile? {
    val profiles = settings.profiles()
    if (profiles.isEmpty()) return null
    val recognition = BoxRecognition.recognise(this, profiles)
    return recognition.match ?: settings.activeProfile()?.takeIf { p ->
        p.deviceId == null && recognition.found.any { box -> box.host.equals(p.config.host, ignoreCase = true) }
    }
}

/** The tiles Pickelhaube offers, for adding them from the settings. */
enum class PickelhaubeTile(val service: Class<out TileService>, val label: Int, val icon: Int) {
    DIALER(DialerTileService::class.java, R.string.app_name, R.drawable.ic_tile_pickelhaube),
    REDIAL(RedialTileService::class.java, R.string.tile_redial, R.drawable.ic_tile_pickelhaube),
    DEFLECTION(DeflectionTileService::class.java, R.string.tile_deflection, R.drawable.ic_tile_pickelhaube),
}

/*
 * Each tile sets its label itself whenever shown: Android's system UI keeps the label it read from
 * the manifest, and does not always read it again after an update (or a language change).
 */

/** Whether [tile] is in the quick settings, as far as its service learned it (Android cannot be asked). */
fun isTileAdded(context: Context, tile: PickelhaubeTile): Boolean = SettingsRepository(context).isTileAdded(tile.name)

private fun TileService.rememberAdded(tile: PickelhaubeTile, added: Boolean) =
    SettingsRepository(this).setTileAdded(tile.name, added)

/** How a request to add a tile ended. */
enum class TileAddResult { ADDED, ALREADY_ADDED, NOT_ADDED, FAILED }

/** Whether this Android lets an app offer to add its tile (Android 13 and later). */
@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
val canRequestAddTile: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Asks Android to add [tile] to the quick settings: Android shows its own dialog, and [onResult]
 * gets the outcome. Only on Android 13 and later (see [canRequestAddTile]).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun requestAddTile(context: Context, tile: PickelhaubeTile, onResult: (TileAddResult) -> Unit) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return onResult(TileAddResult.FAILED)
    runCatching {
        statusBar.requestAddTileService(
            ComponentName(context, tile.service),
            context.getString(tile.label),
            Icon.createWithResource(context, tile.icon),
            context.mainExecutor,
        ) { code ->
            if (code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                SettingsRepository(context).setTileAdded(tile.name, true)
            }
            onResult(
                when (code) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> TileAddResult.ADDED
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> TileAddResult.ALREADY_ADDED
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> TileAddResult.NOT_ADDED
                    // Errors (e.g. a request already showing, or no status bar to add to).
                    else -> TileAddResult.FAILED
                }
            )
        }
    }.onFailure { onResult(TileAddResult.FAILED) }
}

/** Opens Pickelhaube from a tile, collapsing the shade; on a locked phone only once it is unlocked. */
private fun TileService.openApp(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val start = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @SuppressLint("StartActivityAndCollapseDeprecated")
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
    if (isLocked) unlockAndRun(start) else start()
}
