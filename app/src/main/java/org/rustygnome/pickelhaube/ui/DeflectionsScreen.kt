package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.tr064.CallDeflection
import org.rustygnome.pickelhaube.tr064.forTile

/**
 * The call deflections set up on the box in use, each with a switch. They can only be switched on
 * and off from here; setting them up is done on the box itself, which the page says. With more than
 * one, a radio button marks the one the quick settings tile switches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeflectionsScreen(
    /** Name of the box in use, as in the settings. */
    boxName: String?,
    /** As last read; null while not read (or reading failed). */
    deflections: List<CallDeflection>?,
    busy: Boolean,
    /** The deflection chosen for the quick settings tile (null: not chosen – then the first). */
    tileDeflection: Int?,
    onChooseTile: (CallDeflection) -> Unit,
    onToggle: (CallDeflection, Boolean) -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deflections)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onReload, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.picker_reload))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            ) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Text(
                stringResource(R.string.deflections_hint, boxName ?: stringResource(R.string.fritzbox)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            when {
                deflections == null && busy -> Message(stringResource(R.string.deflections_loading))
                deflections == null -> Message(stringResource(R.string.deflections_failed))
                deflections.isEmpty() -> Message(stringResource(R.string.deflections_none))
                else -> {
                    val forTile = deflections.forTile(tileDeflection)?.takeIf { deflections.size > 1 }
                    if (forTile != null) {
                        Text(
                            stringResource(R.string.deflections_tile_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        )
                    }
                    deflections.forEach { deflection -> DeflectionRow(deflection, forTile, busy, onChooseTile, onToggle) }
                }
            }
        }
    }
}

/** One deflection: which calls, where to and when, its switch – and with [forTile] the radio button for the tile. */
@Composable
private fun DeflectionRow(
    deflection: CallDeflection,
    /** The deflection the tile switches; null when there is no choice (just one). */
    forTile: CallDeflection?,
    busy: Boolean,
    onChooseTile: (CallDeflection) -> Unit,
    onToggle: (CallDeflection, Boolean) -> Unit,
) {
    ListItem(
        leadingContent = forTile?.let {
            {
                RadioButton(
                    selected = deflection.id == it.id,
                    onClick = { onChooseTile(deflection) },
                )
            }
        },
        headlineContent = { Text(deflectionCalls(deflection)) },
        supportingContent = {
            Text("→ ${deflection.toNumber.ifBlank { "–" }} · ${deflectionMode(deflection.mode)}")
        },
        trailingContent = {
            Switch(
                checked = deflection.enabled,
                onCheckedChange = { onToggle(deflection, it) },
                enabled = !busy,
            )
        },
    )
    HorizontalDivider()
}

/** Which calls a deflection is about, in the words of the box' own settings. */
@Composable
private fun deflectionCalls(deflection: CallDeflection): String {
    val number = deflection.number.trim()
    return when (deflection.type) {
        "fromAll", "toAny" -> stringResource(R.string.deflection_all)
        "fromNumber" -> stringResource(R.string.deflection_from, number)
        "fromAnonymous" -> stringResource(R.string.deflection_anonymous)
        "fromPB" -> stringResource(R.string.deflection_from_book)
        "fromNotInPhonebook" -> stringResource(R.string.deflection_not_in_book)
        "toMSN", "toVoIP", "toPOTS" ->
            if (number.isEmpty()) stringResource(R.string.deflection_all) else stringResource(R.string.deflection_to, number)
        else -> listOf(deflection.type, number).filter { it.isNotEmpty() }.joinToString(" ")
    }
}

/** When a deflection forwards a call. */
@Composable
private fun deflectionMode(mode: String): String = when (mode) {
    "eImmediately" -> stringResource(R.string.deflection_mode_immediately)
    "eShortDelayed" -> stringResource(R.string.deflection_mode_short_delay)
    "eLongDelayed" -> stringResource(R.string.deflection_mode_long_delay)
    "eBusy" -> stringResource(R.string.deflection_mode_busy)
    "eDelayedOrBusy" -> stringResource(R.string.deflection_mode_delayed_or_busy)
    "eNoSignal" -> stringResource(R.string.deflection_mode_no_signal)
    "eParallelCall" -> stringResource(R.string.deflection_mode_parallel)
    else -> mode.removePrefix("e")
}
