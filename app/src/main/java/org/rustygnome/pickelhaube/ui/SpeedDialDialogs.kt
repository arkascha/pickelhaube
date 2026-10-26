package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.data.SpeedDials
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.Phonebook

/** Which of this phone's slots a number becomes; taken ones show who is there now. */
@Composable
fun SpeedDialSlotDialog(
    name: String,
    taken: List<SpeedDial>,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speed_dial_for, name)) },
        text = { SlotList(SpeedDials.PHONE_SLOTS, taken, onChoose) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A number to become a speed dial of the box: [slot] null while it is still to be chosen. */
data class BoxSpeedDialRequest(val name: String, val number: String, val type: String, val slot: String?)

/**
 * Asks what [request] still leaves open – the slot, and for a number that is in none of the box' own
 * phone books (speed dials belong to their entries; the online ones it syncs cannot be changed) the
 * phone book for its new entry, if there is more than one –, then hands it to [onSet] (slot, name,
 * number, type, phone book of a new entry or null). A number only in an online phone book is said
 * to get an entry of its own. With nothing open or to say, it does so right away.
 */
@Composable
fun BoxSpeedDialRequestDialog(
    request: BoxSpeedDialRequest,
    /** All phone books of the box, online ones included. */
    boxBooks: List<Phonebook>,
    taken: List<SpeedDial>,
    dialingCodes: DialingCodes?,
    onSet: (String, String, String, String, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val countryIso = remember { deviceCountryIso(context) }
    val localBooks = remember(boxBooks) { boxBooks.filterNot { it.online } }
    // The phone books that have the number.
    val holders = remember(request, boxBooks, dialingCodes) {
        val key = numberKey(request.number, countryIso, dialingCodes)
        boxBooks.filter { book -> book.contacts.any { c -> c.numbers.any { numberKey(it.number, countryIso, dialingCodes) == key } } }
    }
    val inBox = holders.any { !it.online }
    val onlineBook = holders.firstOrNull { it.online }?.takeIf { !inBox }
    val chooseBook = !inBox && localBooks.size > 1
    var book by rememberSaveable { mutableIntStateOf(localBooks.firstOrNull()?.id ?: 0) }
    val set = { slot: String ->
        onDismiss()
        onSet(slot, request.name, request.number, request.type, if (inBox) null else book)
    }

    if (request.slot != null && !chooseBook && onlineBook == null) {
        LaunchedEffect(request) { set(request.slot) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speed_dial_box_for, request.name)) },
        text = {
            Column {
                onlineBook?.let {
                    Text(
                        stringResource(R.string.speed_dial_box_online, request.name, it.name),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (chooseBook) {
                    Text(stringResource(R.string.speed_dial_box_new_entry), style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.selectableGroup().padding(bottom = 8.dp)) {
                        localBooks.forEach { b ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = b.id == book, onClick = { book = b.id }, role = Role.RadioButton),
                            ) {
                                RadioButton(selected = b.id == book, onClick = null)
                                Text(b.name, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
                            }
                        }
                    }
                }
                if (request.slot == null) {
                    SlotList(BOX_SLOTS, taken, set, Modifier.heightIn(max = 320.dp))
                }
            }
        },
        // A given slot only waits for the phone book.
        confirmButton = {
            request.slot?.let { slot ->
                TextButton(onClick = { set(slot) }) { Text(stringResource(R.string.speed_dial_set_up)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The box' speed dial slots, as dialled on its handsets after `**7`. */
private val BOX_SLOTS = (0..99).map { it.toString().padStart(2, '0') }

/** One line per slot with who has it now (or that it is free); tapping one chooses it. */
@Composable
private fun SlotList(slots: List<String>, taken: List<SpeedDial>, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier) {
        items(slots) { slot ->
            val current = taken.firstOrNull { it.slot == slot }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChoose(slot) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    slot,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(40.dp),
                )
                Text(
                    current?.name ?: stringResource(R.string.speed_dial_free),
                    color = if (current == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
