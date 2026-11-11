package org.rustygnome.pickelhaube.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import org.rustygnome.pickelhaube.ui.theme.TextRoles
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.DeviceCallLog
import org.rustygnome.pickelhaube.data.DeviceContacts
import org.rustygnome.pickelhaube.tile.PickelhaubeTile
import org.rustygnome.pickelhaube.tile.TileAddResult
import org.rustygnome.pickelhaube.tile.canRequestAddTile
import org.rustygnome.pickelhaube.tile.isTileAdded
import org.rustygnome.pickelhaube.tile.requestAddTile

/**
 * Switch for following the calls on the box' call monitor and, while on, whether each box has its
 * monitor on – asked when the settings open (the boxes reachable from here), and again on request,
 * e.g. right after switching it on at the box.
 */
@Composable
internal fun CallStatusSetting(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    profiles: List<BoxProfile>,
    checking: Boolean,
    canCheck: Boolean,
    onCheck: () -> Unit,
) {
    LaunchedEffect(Unit) { if (enabled) onCheck() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(
            stringResource(R.string.settings_call_status_switch),
            listOf(stringResource(R.string.settings_call_status_hint), stringResource(R.string.settings_call_status_privacy)),
            Modifier.weight(1f),
        )
        Switch(checked = enabled, onCheckedChange = onChange)
    }
    if (!enabled) return
    profiles.forEach { profile ->
        Text(
            when (profile.callMonitor) {
                true -> stringResource(R.string.settings_call_monitor_on, profile.name)
                false -> stringResource(R.string.settings_call_monitor_off, profile.name)
                null -> stringResource(R.string.settings_call_monitor_unknown, profile.name)
            },
            style = TextRoles.finding,
            color = if (profile.callMonitor == false) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
    if (canCheck) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onCheck, enabled = !checking) {
                Text(stringResource(R.string.settings_call_monitor_check))
            }
            if (checking) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * The quick settings tiles: each one already there is shown as such; for the others, from Android 13
 * on, a button which lets Android ask whether to add it; before that (and if adding fails) how to add
 * them by hand.
 */
@Composable
internal fun TilesSetting() {
    val context = LocalContext.current
    // The outcome of the last request, shown under the buttons.
    var result by remember { mutableStateOf<Pair<PickelhaubeTile, TileAddResult>?>(null) }
    // The tiles in the quick settings; checked again on returning (one may have been removed meanwhile).
    var added by remember { mutableStateOf(PickelhaubeTile.entries.filter { isTileAdded(context, it) }.toSet()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        added = PickelhaubeTile.entries.filter { isTileAdded(context, it) }.toSet()
    }
    PickelhaubeTile.entries.filter { it in added }.forEach { tile ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "  " + stringResource(R.string.settings_tile_already_added, stringResource(tile.label)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (canRequestAddTile) {
        PickelhaubeTile.entries.filter { it !in added }.forEach { tile ->
            OutlinedButton(
                onClick = {
                    result = null
                    requestAddTile(context, tile) { outcome ->
                        result = tile to outcome
                        added = PickelhaubeTile.entries.filter { isTileAdded(context, it) }.toSet()
                    }
                },
            ) {
                Icon(painterResource(tile.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + stringResource(R.string.settings_tile_add, stringResource(tile.label)))
            }
        }
    }
    // An "already there" is shown by the list above.
    result?.takeIf { it.first !in added }?.let { (tile, outcome) ->
        val name = stringResource(tile.label)
        Text(
            when (outcome) {
                TileAddResult.ADDED -> stringResource(R.string.settings_tile_added, name)
                TileAddResult.ALREADY_ADDED -> stringResource(R.string.settings_tile_already_added, name)
                TileAddResult.NOT_ADDED -> stringResource(R.string.settings_tile_not_added, name)
                TileAddResult.FAILED -> stringResource(R.string.settings_tile_failed, name)
            },
            style = TextRoles.finding,
            color = if (outcome == TileAddResult.FAILED) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
    if (added.size < PickelhaubeTile.entries.size && (!canRequestAddTile || result?.second == TileAddResult.FAILED)) {
        Text(
            stringResource(R.string.settings_tiles_by_hand),
            style = TextRoles.hint,
        )
    }
}

/** Switch for an optional source (phone books, call list, speed dials); [hint] behind a question mark. */
@Composable
internal fun BoxFeatureSetting(title: String, hint: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(title, listOf(hint), Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

/**
 * Switch for the optional "this phone's contacts" feature. Turning it on asks Android for the
 * contacts permission; the feature is only enabled once that is granted.
 */
@Composable
internal fun DeviceContactsSetting(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(DeviceContacts.hasPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        denied = !granted
        if (granted) onChange(true)
    }
    // Re-check when coming back from the Android app settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = DeviceContacts.hasPermission(context)
        if (allowed) denied = false
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(
            stringResource(R.string.settings_device_contacts),
            listOf(stringResource(R.string.settings_device_contacts_hint)),
            Modifier.weight(1f),
        )
        Switch(
            checked = enabled && allowed,
            onCheckedChange = { on ->
                when {
                    !on -> onChange(false)
                    allowed -> onChange(true)
                    else -> launcher.launch(Manifest.permission.READ_CONTACTS)
                }
            },
        )
    }
    if (denied || (enabled && !allowed)) {
        Text(
            stringResource(R.string.settings_device_contacts_denied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = { context.startActivity(DeviceContacts.appSettingsIntent(context)) }) {
            Text(stringResource(R.string.app_settings))
        }
    }
}

/**
 * Switch for the optional "this phone's calls" feature. Turning it on asks Android for the call log
 * permission and then tries to read the call log; only if that works the feature is enabled,
 * otherwise it stays off and [problem] says why.
 */
@Composable
internal fun CallLogSetting(
    enabled: Boolean,
    problem: UiText?,
    onEnable: (granted: Boolean) -> Unit,
    onDisable: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onEnable)

    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(
            stringResource(R.string.settings_call_log),
            listOf(stringResource(R.string.settings_call_log_hint), stringResource(R.string.settings_call_log_play)),
            Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                when {
                    !on -> onDisable()
                    DeviceCallLog.hasPermission(context) -> onEnable(true)
                    else -> launcher.launch(Manifest.permission.READ_CALL_LOG)
                }
            },
        )
    }
    if (problem != null) {
        Text(problem.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { context.startActivity(DeviceContacts.appSettingsIntent(context)) }) {
            Text(stringResource(R.string.app_settings))
        }
    }
}
