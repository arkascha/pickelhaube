package org.rustygnome.pickelhaube.ui

import kotlinx.coroutines.flow.update
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.PhonebookNumber

/**
 * Speed dials: this phone's own (1–9) and the box' (in its phone books) – setting them from the
 * phone books, removing them, and the box' copies of online contacts.
 */
internal class SpeedDialsPart(private val host: DialerHost) {

    private val state get() = host.state
    private val settings get() = host.settings

    /** What puts back the removal offered for undoing ([UiState.undoOffer]); null: none. */
    @Volatile
    private var undoAction: (() -> Unit)? = null

    @Volatile
    private var undoSerial = 0L

    /** Offers to undo a removal: [message] with "Undo", which runs [action]. Replaces an earlier offer. */
    private fun offerUndo(message: UiText, action: () -> Unit) {
        undoAction = action
        val offer = UndoOffer(message, ++undoSerial)
        state.update { it.copy(undoOffer = offer) }
    }

    /** "Undo" was tapped: the last removal is put back. */
    fun undo() {
        val action = undoAction ?: return
        dropUndo()
        action()
    }

    /** The offer [serial] went by without "Undo" (or the picker was left): it cannot be taken any more. */
    fun dropUndo(serial: Long? = null) {
        if (serial != null && serial != state.value.undoOffer?.serial) return
        undoAction = null
        state.update { it.copy(undoOffer = null) }
    }

    /** Opens the phone books to choose the number for this phone's speed dial [slot]. */
    fun startAssigning(slot: String) =
        state.update { it.copy(assigningSpeedDial = SpeedDialAssignment.Phone(slot), pickerTab = PickerTab.PHONEBOOKS) }

    /** Opens the phone books to choose the number for the box' speed dial [slot] (null: a new one). */
    fun startAssigningBox(slot: String?) =
        state.update { it.copy(assigningSpeedDial = SpeedDialAssignment.Box(slot), pickerTab = PickerTab.PHONEBOOKS) }

    fun cancelAssigning() =
        state.update { it.copy(assigningSpeedDial = null, pickerTab = PickerTab.SPEED_DIALS) }

    /** Sets (or replaces) this phone's speed dial [slot]. */
    fun setPhone(slot: String, name: String, number: String) {
        settings.phoneSpeedDials = settings.phoneSpeedDials.filterNot { it.slot == slot } + SpeedDial(slot, name, number)
        state.update { it.copy(phoneSpeedDials = settings.phoneSpeedDials) }
    }

    /** Removes this phone's speed dial [slot] – right away, with "Undo" to put it back. */
    fun removePhone(slot: String) {
        val removed = settings.phoneSpeedDials.firstOrNull { it.slot == slot } ?: return
        settings.phoneSpeedDials = settings.phoneSpeedDials.filterNot { it.slot == slot }
        state.update { it.copy(phoneSpeedDials = settings.phoneSpeedDials) }
        offerUndo(uiText(R.string.speed_dial_removed_phone, slot, removed.name)) { setPhone(removed.slot, removed.name, removed.number) }
    }

    /**
     * Makes [number] the box' speed dial [slot]: in the entry of the box' phone books that has the
     * number – or, if none of its own (not online) ones has, in a new entry for [name] in phone book
     * [newEntryBook] (null: the first of its own). Wherever else the slot was used, it is taken away. Then the phone books are read anew.
     */
    fun setBox(slot: String, name: String, number: String, type: String, newEntryBook: Int?) {
        val assigning = state.value.assigningSpeedDial is SpeedDialAssignment.Box
        host.runOnBoxEntries(uiText(R.string.status_setting_speed_dial, slot), retry = true) { dialer ->
            val key = host.numberKeys()
            // Only the box' own phone books can be changed, not the online ones it syncs.
            val localBooks = state.value.phonebooks.filterNot { it.online }
            val contacts = localBooks.flatMap { it.contacts }.filter { it.bookId != null && it.uid != null }
            val target = contacts.firstOrNull { c -> c.numbers.any { key(it.number) == key(number) } }
            contacts.filter { c -> c != target && c.numbers.any { it.speedDial == slot } }
                .forEach { dialer.setSpeedDial(it.bookId!!, it.uid!!, slot, null) }
            if (target != null) {
                val written = target.numbers.first { key(it.number) == key(number) }.number
                dialer.setSpeedDial(target.bookId!!, target.uid!!, slot, written)
            } else {
                val book = localBooks.firstOrNull { it.id == newEntryBook } ?: localBooks.first()
                dialer.addToPhonebook(book.id, book.name, PhonebookContact(name, listOf(PhonebookNumber(number, type, slot)), ""))
            }
            val books = dialer.loadPhonebooks()
            state.update {
                it.copy(
                    phonebooks = books,
                    phonebooksLoaded = true,
                    assigningSpeedDial = if (assigning) null else it.assigningSpeedDial,
                    pickerTab = if (assigning) PickerTab.SPEED_DIALS else it.pickerTab,
                )
            }
            uiText(R.string.status_speed_dial_set, slot, target?.name ?: name)
        }
    }

    /**
     * Deletes [copy], an entry copying a contact of an online phone book, from the box; then reads the
     * phone books anew. "Undo" puts the entry back (see [restoreCopy]).
     */
    fun deleteOnlineCopy(copy: OnlineCopy) = host.runOnBoxEntries(uiText(R.string.status_deleting_copy, copy.entry.name), retry = false) { dialer ->
        dialer.deletePhonebookEntry(copy.entry.bookId!!, copy.entry.uid!!)
        val books = dialer.loadPhonebooks()
        state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
        offerUndo(uiText(R.string.status_copy_deleted, copy.entry.name)) { restoreCopy(copy.entry) }
        uiText(R.string.status_copy_deleted, copy.entry.name)
    }

    /**
     * Puts [entry], deleted just before, back into its phone book on the box – a new entry with its
     * name and numbers, speed dials included. The phone book is looked for by its name, as the box
     * numbers its phone books by their place in its list; the box checks the name once more before
     * the entry goes in.
     */
    private fun restoreCopy(entry: PhonebookContact) =
        host.runOnBoxEntries(uiText(R.string.status_restoring_copy, entry.name), retry = true) { dialer ->
            val book = state.value.phonebooks.filterNot { it.online }.firstOrNull { it.name == entry.book }
                ?: return@runOnBoxEntries uiText(R.string.status_restore_book_gone, entry.name, entry.book)
            dialer.addToPhonebook(book.id, book.name, PhonebookContact(entry.name, entry.numbers, ""))
            val books = dialer.loadPhonebooks()
            state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
            uiText(R.string.status_copy_restored, entry.name)
        }

    /**
     * Takes the box' speed dial [slot] away from the numbers that have it; then reads the phone books
     * anew. "Undo" gives it back to the number that had it.
     */
    fun removeBox(slot: String) = host.runOnBoxEntries(uiText(R.string.status_removing_speed_dial, slot), retry = true) { dialer ->
        val holders = state.value.phonebooks.filterNot { it.online }.flatMap { it.contacts }
            .filter { c -> c.bookId != null && c.uid != null && c.numbers.any { it.speedDial == slot } }
        holders.forEach { dialer.setSpeedDial(it.bookId!!, it.uid!!, slot, null) }
        val books = dialer.loadPhonebooks()
        state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
        holders.firstOrNull()?.let { holder ->
            val number = holder.numbers.first { it.speedDial == slot }
            offerUndo(uiText(R.string.status_speed_dial_removed, slot)) {
                setBox(slot, holder.name, number.number, number.type, holder.bookId)
            }
        }
        uiText(R.string.status_speed_dial_removed, slot)
    }

    /** Switches this phone's speed dials on or off; switched off, they are kept, just not used. */
    fun setPhoneEnabled(enabled: Boolean) {
        settings.phoneSpeedDialsEnabled = enabled
        state.update { it.copy(phoneSpeedDialsEnabled = enabled) }
    }

    /**
     * Reads the phone books of the box in use in the background, so its speed dials and the
     * suggestions while typing work right away in the dialer – not only once the picker was opened.
     * Checked when it is its turn, as the box in use may change until then.
     */
    fun preload() = host.runWhenIdle("speedDials") {
        val profile = settings.activeProfile()
        if (profile != null && (profile.speedDialsEnabled || profile.phonebooksEnabled) && !state.value.phonebooksLoaded &&
            !state.value.noBoxReachable
        ) {
            host.reloadRouterPhonebooks()
        }
    }
}
