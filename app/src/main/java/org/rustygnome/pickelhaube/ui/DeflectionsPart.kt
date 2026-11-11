package org.rustygnome.pickelhaube.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts
import org.rustygnome.pickelhaube.tile.DeflectionTileService
import org.rustygnome.pickelhaube.tr064.CallDeflection
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.forTile

/** The call deflections of the box in use: their page, the one the quick settings tile switches, and the launcher shortcut. */
internal class DeflectionsPart(private val host: DialerHost) {

    private val state get() = host.state
    private val settings get() = host.settings

    /** Opens the call deflections of the box in use, read anew. */
    fun open() {
        state.update {
            it.copy(showDeflections = true, tileDeflection = settings.activeProfileId?.let(settings::tileDeflection))
        }
        load()
    }

    /** Makes call deflection [id] the one the quick settings tile switches, for the box in use. */
    fun chooseTile(id: Int) {
        val profileId = settings.activeProfileId ?: return
        settings.setTileDeflection(profileId, id)
        state.update { it.copy(tileDeflection = id) }
        DeflectionTileService.refresh(host.app)
        state.value.deflections?.let(::publishShortcut)
    }

    /**
     * Reads the call deflections of the box in use in the background, so the dialer can show whether
     * one is switched on. Quietly: a box whose user may not read them just shows none.
     */
    fun preload() = host.runWhenIdle("deflections") {
        if (state.value.noBoxReachable) return@runWhenIdle
        val config = state.value.config
        host.scope.launch {
            val deflections = withContext(Dispatchers.IO) { runCatching { FritzDialer(config).loadDeflections() }.getOrNull() }
            // Unless the box in use changed meanwhile.
            if (deflections != null && state.value.config == config) {
                state.update { it.copy(deflections = deflections) }
                publishShortcut(deflections)
            }
        }
    }

    fun close() = state.update { it.copy(showDeflections = false) }

    fun load() = host.runOnBox(uiText(R.string.status_loading_deflections)) { dialer ->
        val deflections = dialer.loadDeflections()
        state.update { it.copy(deflections = deflections) }
        publishShortcut(deflections)
        uiText(R.string.status_deflections_loaded)
    }

    /** Switches call deflection [id] on or off on the box, then reads them anew to show what the box did. */
    fun setEnabled(id: Int, enabled: Boolean) =
        host.runOnBox(uiText(if (enabled) R.string.status_deflection_on else R.string.status_deflection_off)) { dialer ->
            dialer.setDeflectionEnabled(id, enabled)
            val deflections = dialer.loadDeflections()
            state.update { it.copy(deflections = deflections) }
            DeflectionTileService.refresh(host.app)
            publishShortcut(deflections)
            uiText(if (enabled) R.string.status_deflection_on_done else R.string.status_deflection_off_done)
        }

    /** The launcher shortcut "Call forwarding" switches the one the tile switches: says which, and its state. */
    private fun publishShortcut(deflections: List<CallDeflection>) {
        val id = settings.activeProfileId ?: return
        LauncherShortcuts.updateDeflection(host.app, deflections.forTile(settings.tileDeflection(id)))
    }
}
