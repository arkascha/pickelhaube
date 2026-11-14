package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.data.SpeedDials

/**
 * The number picker's "Speed dial" tab, in two parts that never collide: this phone's own speed
 * dials 1–9 (valid with every box; free slots can be set up from the phone books, a long press on
 * a set one changes or removes it), and the two-digit ones set up in the phone books of the box in
 * use (while the box is there: a new one from the phone books, a long press on one changes or
 * removes it). Tapping one puts its number into the dialer; its call button calls it right away.
 */
@Composable
fun SpeedDialTab(
    /** This phone's speed dials; null when switched off in the settings. */
    phoneSpeedDials: List<SpeedDial>?,
    /** The speed dials of the box in use; null when switched off for it. */
    boxSpeedDials: List<SpeedDial>?,
    /** Name of the box in use (as in the settings). */
    boxName: String?,
    /** The box' phone books (which hold its speed dials) have been read. */
    boxLoaded: Boolean,
    busy: Boolean,
    onPick: (SpeedDial) -> Unit,
    /** Calls a speed dial right away; null: no call buttons (picking for a widget). */
    onCall: ((SpeedDial) -> Unit)?,
    /** A box to call through is there: the call buttons work. */
    callEnabled: Boolean,
    /** Choose a number from the phone books for this phone's speed dial slot. */
    onAssign: (String) -> Unit,
    onRemove: (String) -> Unit,
    /** The box' speed dials can be changed now (the box is there and its phone books are read). */
    boxEditable: Boolean,
    /** Choose a number from the phone books for the box' speed dial slot (null: a new one). */
    onAssignBox: (String?) -> Unit,
    onRemoveBox: (String) -> Unit,
    /** Entries of the box' own phone books that copy a contact of an online one: these may be deleted. */
    onlineCopies: List<OnlineCopy>,
    onDeleteCopy: (OnlineCopy) -> Unit,
) {
    if (phoneSpeedDials == null && boxSpeedDials == null) {
        Message(stringResource(R.string.speed_dial_none_enabled), MascotPose.CLIPBOARD)
        return
    }
    // This phone's free slots shown one by one (to set one up), or folded into one row.
    var showFree by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState()) {
        if (phoneSpeedDials != null) {
            item {
                SectionTitle(
                    stringResource(R.string.this_phone),
                    listOf(stringResource(R.string.speed_dial_phone_help)),
                )
            }
            // The free slots folded into one row, so the speed dials in use – the box' ones too – come first.
            val free = SpeedDials.PHONE_SLOTS.filter { slot -> phoneSpeedDials.none { it.slot == slot } }
            val shown = if (showFree) SpeedDials.PHONE_SLOTS else SpeedDials.PHONE_SLOTS - free.toSet()
            items(shown, key = { "phone-$it" }) { slot ->
                PhoneSlotRow(
                    slot = slot,
                    speedDial = phoneSpeedDials.firstOrNull { it.slot == slot },
                    onPick = onPick,
                    onCall = onCall,
                    callEnabled = callEnabled,
                    onAssign = { onAssign(slot) },
                    onRemove = { onRemove(slot) },
                )
                HorizontalDivider()
            }
            if (free.isNotEmpty()) {
                item(key = "phone-free") {
                    FreeSlotsRow(count = free.size, expanded = showFree, onToggle = { showFree = !showFree })
                    HorizontalDivider()
                }
            }
        }
        if (boxSpeedDials != null) {
            item {
                SectionTitle(
                    boxName ?: stringResource(R.string.fritzbox),
                    listOf(stringResource(R.string.speed_dial_box_help)),
                )
            }
            when {
                boxSpeedDials.isEmpty() && busy -> item { Message(stringResource(R.string.phonebook_loading), MascotPose.SEARCHING) }
                boxSpeedDials.isEmpty() && !boxLoaded -> item { Message(stringResource(R.string.speed_dial_box_failed), MascotPose.PUZZLED) }
                boxSpeedDials.isEmpty() -> item { Message(stringResource(R.string.speed_dial_box_none), MascotPose.WAVING, stringResource(R.string.speed_dial_box_none_headline)) }
                else -> items(boxSpeedDials, key = { "box-${it.slot}" }) { speedDial ->
                    val copy = onlineCopies.firstOrNull { c -> c.entry.numbers.any { it.speedDial == speedDial.slot } }
                    BoxSlotRow(
                        speedDial = speedDial,
                        editable = boxEditable,
                        onPick = onPick,
                        onCall = onCall,
                        callEnabled = callEnabled,
                        onChange = { onAssignBox(speedDial.slot) },
                        onRemove = { onRemoveBox(speedDial.slot) },
                        onDeleteCopy = copy?.let { { onDeleteCopy(it) } },
                    )
                    HorizontalDivider()
                }
            }
            if (boxEditable) {
                item(key = "box-new") {
                    ListItem(
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(32.dp)) },
                        headlineContent = { Text(stringResource(R.string.speed_dial_box_add)) },
                        modifier = Modifier.clickable { onAssignBox(null) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, help: List<String>) {
    OptionTitle(
        title,
        help,
        Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.primary),
    )
}

/** The slot number in front of a speed dial, in a fixed width so the names line up. */
@Composable
private fun SlotLabel(slot: String) {
    Text(
        slot,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(32.dp),
    )
}

/**
 * This phone's free slots, folded: how many there are – a tap shows them, one by one, to set one up;
 * shown, the same row folds them again.
 */
@Composable
private fun FreeSlotsRow(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val text = if (expanded) stringResource(R.string.speed_dial_hide_free)
    else pluralStringResource(R.plurals.speed_dial_free_slots, count, count)
    ListItem(
        leadingContent = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(32.dp)) },
        headlineContent = { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        },
        modifier = Modifier.clickable(
            onClickLabel = stringResource(if (expanded) R.string.speed_dial_hide_free else R.string.speed_dial_show_free),
            onClick = onToggle,
        ),
    )
}

/** One of the box' speed dials: tap to use; long press – while the box is there – to replace or remove. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxSlotRow(
    speedDial: SpeedDial,
    editable: Boolean,
    onPick: (SpeedDial) -> Unit,
    onCall: ((SpeedDial) -> Unit)?,
    callEnabled: Boolean,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    /** Deletes the entry, a copy of an online contact; null when it is none. */
    onDeleteCopy: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        ListItem(
            leadingContent = { SlotLabel(speedDial.slot) },
            headlineContent = { Text(speedDial.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(LocalNumberDisplay.current(speedDial.number), maxLines = 1) },
            // At the right, where a free slot of this phone has its "+".
            trailingContent = onCall?.let { call ->
                { CallButton(speedDial.number, speedDial.name, enabled = callEnabled, modifier = AtRowEnd) { call(speedDial) } }
            },
            modifier = Modifier.combinedClickable(
                onClick = { onPick(speedDial) },
                onLongClickLabel = stringResource(R.string.speed_dial_replace_or_remove).takeIf { editable },
                onLongClick = if (editable) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    }
                } else null,
            ),
        )
        SlotMenu(expanded = menu, onDismiss = { menu = false }, onChange = onChange, onRemove = onRemove, onDeleteCopy = onDeleteCopy)
    }
}

/** Replace or remove a speed dial – and for a box entry copying an online contact, delete that entry. */
@Composable
private fun SlotMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    onDeleteCopy: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.speed_dial_replace)) },
            onClick = { onDismiss(); onChange() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.speed_dial_remove), color = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onRemove() },
        )
        onDeleteCopy?.let {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_delete), color = MaterialTheme.colorScheme.error) },
                onClick = { onDismiss(); it() },
            )
        }
    }
}

/** One of this phone's slots: tap to use (or, when free, to set up); long press to replace or remove. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneSlotRow(
    slot: String,
    speedDial: SpeedDial?,
    onPick: (SpeedDial) -> Unit,
    onCall: ((SpeedDial) -> Unit)?,
    callEnabled: Boolean,
    onAssign: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        ListItem(
            leadingContent = { SlotLabel(slot) },
            headlineContent = {
                if (speedDial != null) {
                    Text(speedDial.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(stringResource(R.string.speed_dial_free), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            supportingContent = speedDial?.let { { Text(LocalNumberDisplay.current(it.number), maxLines = 1) } },
            trailingContent = when {
                speedDial == null -> { { Icon(Icons.Default.Add, contentDescription = null) } }
                onCall != null -> { { CallButton(speedDial.number, speedDial.name, enabled = callEnabled, modifier = AtRowEnd) { onCall(speedDial) } } }
                else -> null
            },
            modifier = if (speedDial != null) {
                Modifier.combinedClickable(
                    onClick = { onPick(speedDial) },
                    onLongClickLabel = stringResource(R.string.speed_dial_replace_or_remove),
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
            } else {
                Modifier.combinedClickable(
                    onClickLabel = stringResource(R.string.speed_dial_set_up),
                    onClick = onAssign,
                )
            },
        )
        SlotMenu(expanded = menu, onDismiss = { menu = false }, onChange = onAssign, onRemove = onRemove)
    }
}
