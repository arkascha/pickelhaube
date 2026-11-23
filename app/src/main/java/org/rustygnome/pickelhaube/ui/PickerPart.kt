package org.rustygnome.pickelhaube.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.DeviceContacts
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts
import org.rustygnome.pickelhaube.tr064.Phonebook
import org.rustygnome.pickelhaube.tr064.PhonebookContact

/**
 * The number picker and what it shows: the box' phone books, this phone's contacts, and taking a
 * number from it into the dialer. Its "Recent" tab comes from [CallsPart], its speed dials from
 * [SpeedDialsPart]; calling straight from the picker is the view model's (see [DialerViewModel.callFromPicker]).
 */
internal class PickerPart(
    private val host: DialerHost,
    private val calls: CallsPart,
    private val speedDials: SpeedDialsPart,
    private val widget: WidgetPart,
) {

    private val state get() = host.state
    private val settings get() = host.settings

    // --- the picker -------------------------------------------------------------------

    /**
     * Opens the number picker on "Recent" – [missedOnly]: with just the missed calls (from the dialer's
     * line about them). The recent calls are read anew each time (they change with every call); the
     * phone books only the first time.
     */
    fun open(missedOnly: Boolean = false) {
        state.update {
            it.opening(Page.PICKER).copy(
                pickerTab = PickerTab.RECENT,
                recentsMissedOnly = missedOnly,
                // Opened anew: at the top of its lists, without the search of the last time.
                pickerSession = it.pickerSession + 1,
                backToPicker = false,
                assigningSpeedDial = null,
                addingToWidget = false,
                newCallsSince = null,
                askForDeviceContacts = !it.deviceContactsEnabled &&
                    !settings.deviceContactsNeverAsk,
            )
        }
        calls.markMissedSeen()
        // Without a reachable box only the phone's own sources; "reload" still tries the box.
        if (state.value.noBoxReachable) calls.loadPhoneCalls() else calls.reloadRecents()
        if (!state.value.phonebooksLoaded && !state.value.noBoxReachable) reloadRouterPhonebooks()
        if (state.value.deviceContacts == null) loadDeviceContacts()
    }

    /** "Back" in the dialer after a number was picked: the picker again, as it was left – nothing read anew. */
    fun reopen() = state.update { it.opening(Page.PICKER).copy(backToPicker = false) }

    /** The app was left (not just turned): coming back, "back" no longer leads to the picker left before. */
    fun onLeftApp() = state.update { it.copy(backToPicker = false) }

    /** "Recent": just the missed calls, or all calls. */
    fun setRecentsMissedOnly(missedOnly: Boolean) = state.update { it.copy(recentsMissedOnly = missedOnly) }

    fun close() {
        // What was removed there stays removed: "Undo" went with the picker.
        speedDials.dropUndo()
        state.update { it.closing(Page.PICKER).copy(assigningSpeedDial = null, addingToWidget = false) }
    }

    fun selectTab(tab: PickerTab) {
        state.update { it.copy(pickerTab = tab, assigningSpeedDial = null) }
        calls.markMissedSeen()
    }

    /** The picker's reload button: recent calls and phone books. */
    fun reload() {
        // The box' phones come with the list of phones for dial help.
        if (state.value.pickerTab == PickerTab.INTERNAL) host.refreshPhones()
        calls.reloadRecents()
        loadDeviceContacts()
        reloadRouterPhonebooks()
    }

    // --- taking a number --------------------------------------------------------------

    /** A number was tapped in the picker (recent calls or phone books): put it into the dialer and go back. */
    fun pickNumber(number: String) {
        if (state.value.addingToWidget) return widget.add(nameFor(number).orEmpty(), number)
        state.update { it.closing(Page.PICKER).copy(number = number, numberLabel = null, backToPicker = true) }
    }

    /** A contact's number was tapped in the phone books: for the dialer, or for the speed dial being set up. */
    fun pickContactNumber(name: String, number: String) {
        when (val assignment = state.value.assigningSpeedDial) {
            null -> if (state.value.addingToWidget) widget.add(name, number) else pickNumber(number)
            is SpeedDialAssignment.Phone -> {
                speedDials.setPhone(assignment.slot, name, number)
                state.update { it.copy(assigningSpeedDial = null, pickerTab = PickerTab.SPEED_DIALS) }
            }
            // The phone book tab asks what is still open, then calls setBoxSpeedDial.
            is SpeedDialAssignment.Box -> Unit
        }
    }

    /** Whose [number] is, as far as the phone books and recent calls loaded so far tell. */
    fun nameFor(number: String): String? {
        val key = host.numberKeys()
        val shown = state.value
        val books = shown.phonebooks + listOfNotNull(shown.deviceContacts)
        return nameOf(key(number), books.flatMap { it.contacts }, shown.boxCalls + shown.phoneCalls.orEmpty(), key)
    }

    /**
     * The phone books were read: if the name of the number dialled last was not known when it was
     * dialled (phone books not loaded yet, or dialled before names were kept), the "Redial" shortcut
     * gets it now.
     */
    private fun completeRedialName() {
        val number = settings.lastDialledNumber ?: return
        if (settings.lastDialledName != null) return
        val name = nameFor(number) ?: return
        settings.lastDialledName = name
        LauncherShortcuts.updateRedial(host.app, number, name)
    }

    // --- the box' phone books ---------------------------------------------------------

    fun reloadRouterPhonebooks() {
        // Also read for the box' speed dials, which come from its phone books.
        val profile = settings.activeProfile()
        if (profile != null && !profile.phonebooksEnabled && !profile.speedDialsEnabled) return
        if (state.value.busy) return host.runWhenIdle("phonebooks", ::reloadRouterPhonebooks)
        host.runOnBox(uiText(R.string.phonebook_loading)) { dialer ->
            val books = dialer.loadPhonebooks()
            state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
            completeRedialName()
            UiText.Plural(R.plurals.status_contacts_loaded, books.sumOf { it.contacts.size })
        }
    }

    /** Hands [contact] over to the box' phone book [book] as a new entry, then reads the phone books anew. */
    fun handOverToBox(book: Phonebook, contact: PhonebookContact) =
        host.runOnBoxEntries(uiText(R.string.status_handing_over, contact.name), retry = true) { dialer ->
            // Looked for by its name: read again, its id may have changed – or it is gone.
            val target = state.value.phonebooks.firstOrNull { !it.online && it.name == book.name }
            if (target == null) {
                uiText(R.string.status_phonebook_gone, book.name, contact.name).also { host.toast(it) }
            } else {
                dialer.addToPhonebook(target.id, target.name, contact)
                val books = dialer.loadPhonebooks()
                state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
                uiText(R.string.status_handed_over, contact.name, target.name)
            }
        }

    // --- this phone's contacts --------------------------------------------------------

    /**
     * (Re)reads the contacts stored on this device. Does nothing without the contacts
     * permission; the phone book screen calls this again once it is granted.
     */
    fun loadDeviceContacts() {
        val app = host.app
        if (!state.value.deviceContactsEnabled || !DeviceContacts.hasPermission(app)) return
        host.scope.launch {
            val book = withContext(Dispatchers.IO) { runCatching { DeviceContacts.load(app) }.getOrNull() }
            if (book != null) {
                state.update { it.copy(deviceContacts = book) }
                completeRedialName()
            }
        }
    }

    /**
     * Switches the optional "this phone's contacts" feature. The settings screen only enables it
     * once Android granted the contacts permission. Disabling stops reading the contacts at once
     * (the permission itself can only be revoked in the Android settings).
     */
    fun setDeviceContactsEnabled(enabled: Boolean) {
        settings.deviceContactsEnabled = enabled
        // Switching it off on purpose also means: don't keep asking about it.
        if (!enabled) settings.deviceContactsNeverAsk = true
        state.update {
            it.copy(
                deviceContactsEnabled = enabled,
                deviceContacts = if (enabled) it.deviceContacts else null,
                askForDeviceContacts = false,
            )
        }
        if (enabled) loadDeviceContacts()
    }

    /** Answer to the "include this phone's contacts?" question on the picker's phone book tab. */
    fun answerDeviceContactsQuestion(answer: DeviceContactsAnswer) {
        state.update { it.copy(askForDeviceContacts = false) }
        when (answer) {
            DeviceContactsAnswer.ALLOWED -> setDeviceContactsEnabled(true)
            // Only for this visit: asked again the next time the phone books are opened.
            DeviceContactsAnswer.NOT_NOW -> Unit
            DeviceContactsAnswer.NEVER -> settings.deviceContactsNeverAsk = true
        }
    }
}
