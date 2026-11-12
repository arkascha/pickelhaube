package org.rustygnome.pickelhaube.ui

import android.widget.Toast
import java.io.IOException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.tr064.mayBeStalePhonebook

/**
 * [block], a change of the box' phone books as read last – a speed dial set or taken away, a copy
 * deleted, a contact handed over. If the box refuses it, they may have been changed on the box
 * meanwhile (an entry or a phone book deleted in its web interface): they are read again. If they
 * did change, that is no error to show – the user is told why the list changed (in a toast, as the
 * picker shows no status line), and with [retry] [block] runs once more on what is there now. If
 * they did not, the refusal was meant: it is shown.
 */
internal fun DialerHost.runOnBoxEntries(progress: UiText, retry: Boolean, block: BoxAction) = runOnBox(progress) { dialer ->
    val before = state.value.phonebooks
    try {
        block(dialer)
    } catch (e: IOException) {
        if (!e.mayBeStalePhonebook) throw e
        val books = dialer.loadPhonebooks()
        state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
        if (books == before) throw e
        toast(uiText(R.string.status_phonebook_changed_on_box))
        if (retry) block(dialer) else uiText(R.string.status_phonebook_changed_on_box)
    }
}

/** A short note over whatever is shown – for the picker, which has no status line. */
internal fun DialerHost.toast(text: UiText) {
    scope.launch { Toast.makeText(app, text.resolve(app.resources), Toast.LENGTH_LONG).show() }
}
