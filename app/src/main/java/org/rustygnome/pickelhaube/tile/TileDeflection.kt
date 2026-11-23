package org.rustygnome.pickelhaube.tile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.BoxRecognition
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts
import org.rustygnome.pickelhaube.tr064.CallDeflection
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.forTile

/**
 * The call forwarding switched without opening the app – by the quick settings tile and by the
 * launcher shortcut alike: of the box reachable from the current network, the one chosen for the tile
 * in the app, else the first one set up. Reading it keeps the launcher shortcuts up to date as well.
 */
object TileDeflection {

    /** What [read] found. */
    sealed interface Reading {
        data class Found(val box: BoxProfile, val deflection: CallDeflection) : Reading
        /** None of the boxes is reachable from here. */
        data object NoBox : Reading
        /** The box did not tell its forwardings (no rights, an error). */
        data object Unreadable : Reading
        /** The box has no forwarding set up. */
        data object NoneSetUp : Reading
    }

    /** Looks for the reachable box and reads its forwarding for the tile, as the box has it now. */
    suspend fun read(context: Context): Reading = withContext(Dispatchers.IO) {
        val settings = SettingsRepository(context)
        val box = BoxRecognition.reachableBox(context, settings)
        LauncherShortcuts.updateBox(context, box)
        if (box == null) return@withContext Reading.NoBox
        val deflections = runCatching { FritzDialer(box.config).loadDeflections() }.getOrNull()
            ?: return@withContext Reading.Unreadable
        val deflection = deflections.forTile(settings.tileDeflection(box.id))
        LauncherShortcuts.updateDeflection(context, deflection)
        if (deflection == null) Reading.NoneSetUp else Reading.Found(box, deflection)
    }

    /**
     * Switches [deflection] of [box] the other way round; the forwarding as the box has it then – or, if
     * it does not say, as it was switched to – or null if the box refused.
     */
    suspend fun switch(context: Context, box: BoxProfile, deflection: CallDeflection): CallDeflection? =
        withContext(Dispatchers.IO) {
            val dialer = FritzDialer(box.config)
            val on = !deflection.enabled
            if (runCatching { dialer.setDeflectionEnabled(deflection.id, on) }.isFailure) return@withContext null
            val now = runCatching { dialer.loadDeflections() }.getOrNull()
                ?.forTile(SettingsRepository(context).tileDeflection(box.id))
                ?: deflection.copy(enabled = on)
            LauncherShortcuts.updateDeflection(context, now)
            now
        }
}
