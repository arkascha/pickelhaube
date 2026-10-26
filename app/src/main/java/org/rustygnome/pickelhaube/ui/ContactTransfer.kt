package org.rustygnome.pickelhaube.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.DeviceContacts
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.Phonebook
import org.rustygnome.pickelhaube.tr064.PhonebookContact

/** One entry of the menu a long press on a number opens. */
data class NumberAction(val label: String, val onClick: () -> Unit)

/** The menu of a long-pressed number; nothing while [expanded] is false. */
@Composable
fun NumberActionsMenu(expanded: Boolean, actions: List<NumberAction>, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                onClick = {
                    onDismiss()
                    action.onClick()
                },
            )
        }
    }
}

/** Takes [contact] over into this phone's contacts: the contacts app opens with it filled in. */
fun takeOverIntoDeviceContacts(context: Context, contact: PhonebookContact) {
    try {
        context.startActivity(DeviceContacts.insertIntent(contact))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_contacts_app, Toast.LENGTH_LONG).show()
    }
}

/**
 * Asks into which phone book of the box [contact] is handed over (preselected: the first), and
 * says if one of its numbers is already in it.
 */
@Composable
fun HandOverDialog(
    contact: PhonebookContact,
    books: List<Phonebook>,
    boxName: String?,
    dialingCodes: DialingCodes?,
    onHandOver: (Phonebook) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(books.first().id) }
    val book = books.firstOrNull { it.id == selected } ?: books.first()
    val context = LocalContext.current
    val countryIso = remember { deviceCountryIso(context) }
    // A number of the contact that the chosen book already has, and with whom.
    val duplicate = remember(book, contact, dialingCodes) {
        val key = { number: String -> numberKey(number, countryIso, dialingCodes) }
        contact.numbers.firstNotNullOfOrNull { n ->
            book.contacts.firstOrNull { other -> other.numbers.any { key(it.number) == key(n.number) } }
                ?.let { n.number to it.name }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contact_to_box)) },
        text = {
            Column {
                Text(stringResource(R.string.hand_over_text, contact.name, boxName ?: stringResource(R.string.fritzbox)))
                Column(Modifier.selectableGroup().padding(top = 8.dp)) {
                    books.forEach { b ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = b.id == book.id, onClick = { selected = b.id }, role = Role.RadioButton),
                        ) {
                            RadioButton(selected = b.id == book.id, onClick = null)
                            Text(b.name, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
                        }
                    }
                }
                duplicate?.let { (number, name) ->
                    Text(
                        stringResource(R.string.hand_over_duplicate, number, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onHandOver(book) }) { Text(stringResource(R.string.hand_over)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Asks whether to delete [copy], an entry of the box that copies a contact of an online phone book. */
@Composable
fun DeleteCopyDialog(copy: OnlineCopy, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.copy_delete)) },
        text = { Text(stringResource(R.string.copy_delete_text, copy.entry.name, copy.entry.book, copy.onlineBook)) },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
