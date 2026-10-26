package org.rustygnome.pickelhaube.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.ArrowDropDown
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.DeviceContacts
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.Phonebook
import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.PhonebookNumber
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * The number picker's "Phone books" tab: searchable list of all contacts from the box' phone
 * books (if used for the box in use) and optionally the phone's own. Tapping a number puts it into
 * the dialer – or, while a speed dial is being set up, makes it that speed dial. A long press on a
 * number opens a menu: make it one of this phone's speed dials, take a contact of the box over into
 * this phone's contacts, or hand one of this phone's over to the box.
 */
@Composable
fun PhonebookTab(
    routerBooks: List<Phonebook>,
    /** The phone books of the box in use are switched on in the settings. */
    routerBooksEnabled: Boolean,
    deviceContacts: Phonebook?,
    /** The optional "this phone's contacts" feature is switched on in the settings. */
    deviceContactsEnabled: Boolean,
    /** Country and area code of the box in use, for recognising a number written differently. */
    dialingCodes: DialingCodes?,
    loaded: Boolean,
    busy: Boolean,
    /** A number was tapped: contact name and number. */
    onPick: (String, String) -> Unit,
    /** This phone's speed dials; null when switched off (then a long press does nothing). */
    phoneSpeedDials: List<SpeedDial>?,
    /** Long press on a number, slot chosen: make it this phone's speed dial (slot, name, number). */
    onSetSpeedDial: (String, String, String) -> Unit,
    /** Setting up a speed dial: the next tapped number becomes it. */
    assigningSpeedDial: SpeedDialAssignment?,
    /** The box' speed dials; null when they cannot be set up (switched off, or the box not there). */
    boxSpeedDials: List<SpeedDial>?,
    /** Makes a number the box' speed dial: slot, name, number, type, phone book of a new entry. */
    onSetBoxSpeedDial: (String, String, String, String, Int?) -> Unit,
    /** Entries of the box' own phone books that copy a contact of an online one: these may be deleted. */
    onlineCopies: List<OnlineCopy>,
    onDeleteCopy: (OnlineCopy) -> Unit,
    /** The phone books of the box in use, online ones included (empty = none, e.g. not reachable). */
    boxBooks: List<Phonebook>,
    /** Name of the box in use, as in the settings. */
    boxName: String?,
    /** Hands a contact over to a phone book of the box. */
    onHandOver: (Phonebook, PhonebookContact) -> Unit,
    onCancelAssigning: () -> Unit,
    /** Called when the feature is on and access is (or became) granted: (re)load the contacts. */
    onDeviceContactsAllowed: () -> Unit,
    /** Switches the feature off (used when the permission was revoked in Android). */
    onDisableDeviceContacts: () -> Unit,
    /** Ask now whether to include the phone's contacts (feature off, not declined for good). */
    askForDeviceContacts: Boolean,
    onDeviceContactsAnswer: (DeviceContactsAnswer) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // The phone book shown alone, by name; null: all of them.
    var shownBook by rememberSaveable { mutableStateOf<String?>(null) }
    // Name and number long-pressed to become a speed dial: choosing its slot.
    var speedDialFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    speedDialFor?.let { (name, number) ->
        SpeedDialSlotDialog(
            name = name,
            taken = phoneSpeedDials.orEmpty(),
            onChoose = { slot ->
                speedDialFor = null
                onSetSpeedDial(slot, name, number)
            },
            onDismiss = { speedDialFor = null },
        )
    }

    val context = LocalContext.current
    // The box' own phone books: only these can be changed (not the online ones it syncs).
    val localBooks = remember(boxBooks) { boxBooks.filterNot { it.online } }
    // A number to become the box' speed dial: asking for the slot (and phone book) still open.
    var boxSpeedDialFor by remember { mutableStateOf<BoxSpeedDialRequest?>(null) }
    boxSpeedDialFor?.takeIf { boxSpeedDials != null && localBooks.isNotEmpty() }?.let { request ->
        BoxSpeedDialRequestDialog(
            request = request,
            boxBooks = boxBooks,
            taken = boxSpeedDials.orEmpty(),
            dialingCodes = dialingCodes,
            onSet = onSetBoxSpeedDial,
            onDismiss = { boxSpeedDialFor = null },
        )
    }
    // A copy of an online contact about to be deleted: asking first.
    var deleteCopy by remember { mutableStateOf<OnlineCopy?>(null) }
    deleteCopy?.let { copy ->
        DeleteCopyDialog(
            copy = copy,
            onDelete = {
                deleteCopy = null
                onDeleteCopy(copy)
            },
            onDismiss = { deleteCopy = null },
        )
    }
    // Contact of this phone being handed over to the box: choosing the phone book.
    var handOverFor by remember { mutableStateOf<PhonebookContact?>(null) }
    handOverFor?.takeIf { localBooks.isNotEmpty() }?.let { contact ->
        HandOverDialog(
            contact = contact,
            books = localBooks,
            boxName = boxName,
            dialingCodes = dialingCodes,
            onHandOver = { book ->
                handOverFor = null
                onHandOver(book, contact)
            },
            onDismiss = { handOverFor = null },
        )
    }

    // --- the phone's own contacts: optional, switched on in the settings ---
    var contactsAllowed by remember { mutableStateOf(DeviceContacts.hasPermission(context)) }
    // Re-check when coming back, e.g. from the system app settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        contactsAllowed = DeviceContacts.hasPermission(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsAllowed = granted
        onDeviceContactsAnswer(if (granted) DeviceContactsAnswer.ALLOWED else DeviceContactsAnswer.NOT_NOW)
    }
    if (askForDeviceContacts) {
        DeviceContactsQuestion(
            onAllow = {
                if (DeviceContacts.hasPermission(context)) {
                    contactsAllowed = true
                    onDeviceContactsAnswer(DeviceContactsAnswer.ALLOWED)
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            },
            onNotNow = { onDeviceContactsAnswer(DeviceContactsAnswer.NOT_NOW) },
            onNever = { onDeviceContactsAnswer(DeviceContactsAnswer.NEVER) },
        )
    }
    val useDeviceContacts = deviceContactsEnabled && contactsAllowed
    LaunchedEffect(useDeviceContacts) {
        if (useDeviceContacts) onDeviceContactsAllowed()
    }

    val phonebooks = routerBooks + listOfNotNull(deviceContacts?.takeIf { useDeviceContacts })

    val countryIso = remember { deviceCountryIso(context) }
    // The phone book chosen to be shown alone, as long as it is there; null: all of them.
    val onlyBook = shownBook?.takeIf { name -> phonebooks.any { it.name == name } }
    val filtered = remember(phonebooks, query, onlyBook, dialingCodes) {
        val key = { number: String -> numberKey(number, countryIso, dialingCodes) }
        // FRITZ!Box books come first, so their entries win over duplicates from "This phone".
        val visible = phonebooks.filter { onlyBook == null || it.name == onlyBook }.flatMap { it.contacts }
        dedupeNumbers(visible, key)
            .filter { it.matches(query, key) }
            // Locale-aware: "Ärzte" sorts with A, not after Z.
            .sortedWith(compareBy(Collator.getInstance()) { it.name })
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val letters = remember(filtered) { filtered.map { indexLetter(it.name) } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.phonebook_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.phonebook_clear_search))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        if (assigningSpeedDial != null) {
            AssigningCard(assigningSpeedDial, onCancel = onCancelAssigning)
        }

        // Feature is on, but the permission was revoked in Android meanwhile.
        if (deviceContactsEnabled && !contactsAllowed) {
            ContactsRevokedCard(
                onOpenSettings = { context.startActivity(DeviceContacts.appSettingsIntent(context)) },
                onTurnOff = onDisableDeviceContacts,
            )
        }

        // Which phone book to show – all, or one of them –, only when there is more than one with entries.
        val choices = phonebooks.filter { it.contacts.isNotEmpty() }
        if (choices.size > 1) {
            PhonebookChoice(choices, onlyBook, onChoose = { shownBook = it })
        }

        when {
            !routerBooksEnabled && !deviceContactsEnabled -> Message(stringResource(R.string.phonebook_none_enabled))
            phonebooks.isEmpty() && busy -> Message(stringResource(R.string.phonebook_loading))
            phonebooks.isEmpty() && routerBooksEnabled && !loaded ->
                Message(stringResource(R.string.phonebook_load_failed))
            phonebooks.all { it.contacts.isEmpty() } -> Message(stringResource(R.string.phonebook_empty))
            filtered.isEmpty() -> Message(stringResource(R.string.phonebook_no_match))
            else -> Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    items(filtered) { contact ->
                        val fromPhone = contact.book == DeviceContacts.BOOK_NAME
                        val makeSpeedDial = stringResource(R.string.speed_dial_make)
                        val makeBoxSpeedDial = stringResource(R.string.speed_dial_make_box)
                        val toBox = stringResource(R.string.contact_to_box)
                        val toPhone = stringResource(R.string.contact_to_phone)
                        val deleteCopyLabel = stringResource(R.string.copy_delete)
                        val copy = onlineCopies.firstOrNull { it.entry.bookId == contact.bookId && it.entry.uid == contact.uid }
                        ContactRow(
                            contact,
                            showBook = phonebooks.size > 1,
                            onPick = { n ->
                                val assignment = assigningSpeedDial
                                if (assignment is SpeedDialAssignment.Box) {
                                    boxSpeedDialFor = BoxSpeedDialRequest(contact.name, n.number, n.type, assignment.slot)
                                } else {
                                    onPick(contact.name, n.number)
                                }
                            },
                            actions = { number ->
                                listOfNotNull(
                                    phoneSpeedDials?.let { NumberAction(makeSpeedDial) { speedDialFor = contact.name to number.number } },
                                    if (boxSpeedDials != null && localBooks.isNotEmpty()) {
                                        NumberAction(makeBoxSpeedDial) {
                                            boxSpeedDialFor = BoxSpeedDialRequest(contact.name, number.number, number.type, null)
                                        }
                                    } else null,
                                    if (fromPhone && localBooks.isNotEmpty()) NumberAction(toBox) { handOverFor = contact } else null,
                                    if (!fromPhone) NumberAction(toPhone) { takeOverIntoDeviceContacts(context, contact) } else null,
                                    copy?.let { NumberAction(deleteCopyLabel) { deleteCopy = it } },
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
                AlphabetBar(
                    available = letters.toSet(),
                    onLetter = { letter ->
                        val index = jumpIndex(letters, letter)
                        if (index >= 0) scope.launch { listState.scrollToItem(index) }
                    },
                )
            }
        }
    }
}

/** A button naming the phone book shown (or all); it opens the list to choose: all, or just one. */
@Composable
private fun PhonebookChoice(books: List<Phonebook>, shown: String?, onChoose: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val all = stringResource(R.string.phonebook_chip, stringResource(R.string.phonebook_all), books.sumOf { it.contacts.size })
    Box(Modifier.padding(horizontal = 16.dp)) {
        OutlinedButton(onClick = { open = true }) {
            Text(
                books.firstOrNull { it.name == shown }
                    ?.let { stringResource(R.string.phonebook_chip, bookLabel(it.name), it.contacts.size) } ?: all,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(all, fontWeight = if (shown == null) FontWeight.Bold else null) },
                onClick = { open = false; onChoose(null) },
            )
            books.forEach { book ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.phonebook_chip, bookLabel(book.name), book.contacts.size),
                            fontWeight = if (book.name == shown) FontWeight.Bold else null,
                        )
                    },
                    onClick = { open = false; onChoose(book.name) },
                )
            }
        }
    }
}

/** Asks whether to include the phone's own contacts, with a way to stop asking for good. */
@Composable
private fun DeviceContactsQuestion(onAllow: () -> Unit, onNotNow: () -> Unit, onNever: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        icon = { Icon(ContactsIcon, contentDescription = null) },
        title = { Text(stringResource(R.string.device_contacts_question_title)) },
        text = {
            Text(stringResource(R.string.device_contacts_question_text))
        },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onNever) { Text(stringResource(R.string.dont_ask_again)) }
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.not_now)) }
            }
        },
    )
}

/** Shown when "this phone's contacts" is switched on but Android no longer allows access. */
@Composable
private fun ContactsRevokedCard(
    onOpenSettings: () -> Unit,
    onTurnOff: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.device_contacts_revoked),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onTurnOff) { Text(stringResource(R.string.turn_off)) }
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.app_settings)) }
            }
        }
    }
}

@Composable
internal fun Message(text: String) {
    Text(
        text,
        modifier = Modifier.padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Says that the next tapped number becomes speed dial [slot], with a way to cancel. */
@Composable
private fun AssigningCard(assignment: SpeedDialAssignment, onCancel: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (assignment) {
                    is SpeedDialAssignment.Phone -> stringResource(R.string.speed_dial_choose_number, assignment.slot)
                    is SpeedDialAssignment.Box -> assignment.slot?.let { stringResource(R.string.speed_dial_choose_number_box, it) }
                        ?: stringResource(R.string.speed_dial_choose_number_box_new)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** Name, then one tappable line per number; a long press opens the menu of the number's [actions]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: PhonebookContact,
    showBook: Boolean,
    onPick: (PhonebookNumber) -> Unit,
    /** What can be done with a number (none: no long press). */
    actions: (PhonebookNumber) -> List<NumberAction>,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                contact.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (showBook) {
                Text(
                    bookLabel(contact.book),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        contact.numbers.forEach { n ->
            val numberActions = actions(n)
            var menu by remember { mutableStateOf(false) }
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onPick(n) },
                            onLongClickLabel = stringResource(R.string.number_actions).takeIf { numberActions.isNotEmpty() },
                            onLongClick = if (numberActions.isEmpty()) null else {
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menu = true
                                }
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(n.number, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        numberTypeLabel(n.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NumberActionsMenu(expanded = menu, actions = numberActions, onDismiss = { menu = false })
            }
        }
    }
}

/**
 * List position for [letter]: the first entry with that letter, else the first entry of the
 * next letter that has entries, else the last entry.
 */
private fun jumpIndex(letters: List<Char>, letter: Char): Int {
    if (letters.isEmpty()) return -1
    val wanted = INDEX_LETTERS.indexOf(letter)
    val i = letters.indexOfFirst { INDEX_LETTERS.indexOf(it) >= wanted }
    return if (i >= 0) i else letters.lastIndex
}

/** Display name of a phone book: the phone's own contacts in the app's language, the box' as they are. */
@Composable
private fun bookLabel(name: String): String =
    if (name == DeviceContacts.BOOK_NAME) stringResource(R.string.this_phone) else name

/**
 * Label of a number's type. The box tags them "home", "mobile", …, and so do the phone's own
 * contacts for these kinds; for others they bring Android's label, which is shown as it is.
 */
@Composable
private fun numberTypeLabel(type: String): String = when (type.lowercase()) {
    "home" -> stringResource(R.string.number_type_home)
    "mobile" -> stringResource(R.string.number_type_mobile)
    "work" -> stringResource(R.string.number_type_work)
    "fax_work", "fax" -> stringResource(R.string.number_type_fax)
    "intern" -> stringResource(R.string.number_type_internal)
    "memo" -> stringResource(R.string.number_type_memo)
    "other", "" -> stringResource(R.string.number_type_other)
    else -> type.replaceFirstChar { it.uppercase() }
}

/** Case-insensitive name match; number match ignores spaces, dashes and the like. */
private fun PhonebookContact.matches(query: String, key: (String) -> String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (name.contains(q, ignoreCase = true)) return true
    return numbers.any { numberMatches(it.number, q, key) }
}
