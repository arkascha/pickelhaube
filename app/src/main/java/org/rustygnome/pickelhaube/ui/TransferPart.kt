package org.rustygnome.pickelhaube.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.data.InvalidSettingsFileException
import org.rustygnome.pickelhaube.data.SettingsBackup
import org.rustygnome.pickelhaube.data.SettingsFile
import org.rustygnome.pickelhaube.data.WrongPassphraseException

/** Bigger than any settings file (they take a few kilobytes): such a file is not read. */
private const val MAX_SETTINGS_FILE = 1_000_000

/** Export and import of the settings (*Settings › Back up and transfer*, and *Restore backup…* on first start). */
internal class TransferPart(private val host: DialerHost) {

    private val state get() = host.state
    private val settings get() = host.settings

    /** Writes the settings to [uri]; with [passphrase] the passwords too, encrypted with it. */
    fun export(uri: Uri, passphrase: CharArray?) {
        val app = host.app
        state.update { it.copy(transferBusy = true) }
        host.scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = SettingsFile.write(settings.backup(AppLanguage.current(app)), passphrase)
                    val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Not writable: $uri")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }
            passphrase?.fill('\u0000')
            state.update { it.copy(transferBusy = false) }
            toast(if (result.isSuccess) R.string.settings_exported else R.string.settings_export_failed)
        }
    }

    /** The text of the settings file waiting for [import]. */
    private var importText: String? = null

    /** Opens the settings file at [uri] for import: says what it holds, so the import can be confirmed. */
    fun openFile(uri: Uri) {
        val app = host.app
        state.update { it.copy(transferBusy = true) }
        host.scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = app.contentResolver.openInputStream(uri)?.use { input ->
                        // A settings file is a few kilobytes; anything far bigger is something else.
                        val bytes = input.readNBytesCompat(MAX_SETTINGS_FILE + 1)
                        if (bytes.size > MAX_SETTINGS_FILE) throw InvalidSettingsFileException("Too big")
                        String(bytes, Charsets.UTF_8)
                    } ?: throw IOException("Not readable: $uri")
                    text to SettingsFile.inspect(text)
                }
            }
            val (text, info) = result.getOrNull() ?: (null to null)
            importText = text
            state.update { it.copy(transferBusy = false, pendingImport = info, importError = null) }
            if (info == null) toast(R.string.settings_file_invalid)
        }
    }

    /**
     * Imports the opened settings file, replacing all connections and options; with [passphrase]
     * its passwords as well. [onLanguage] then gets the file's language, for the activity to switch.
     */
    fun import(passphrase: CharArray?, onLanguage: (String?) -> Unit) {
        val text = importText ?: return
        state.update { it.copy(transferBusy = true, importError = null) }
        host.scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { SettingsFile.read(text, passphrase) } }
            passphrase?.fill('\u0000')
            val backup = result.getOrElse { error ->
                val message = if (error is WrongPassphraseException) R.string.settings_wrong_passphrase else R.string.settings_file_invalid
                state.update { it.copy(transferBusy = false, importError = uiText(message)) }
                return@launch
            }
            importText = null
            apply(backup)
            state.update { it.copy(transferBusy = false, pendingImport = null) }
            toast(R.string.settings_imported)
            onLanguage(backup.options.language)
        }
    }

    fun cancelImport() {
        importText = null
        state.update { it.copy(pendingImport = null, importError = null) }
    }

    /**
     * Stores [backup] and starts over with it, as on a fresh start. The phone's contacts and call
     * log stay off unless Android already grants reading them here.
     */
    private fun apply(backup: SettingsBackup) {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(host.app, permission) == PackageManager.PERMISSION_GRANTED
        settings.restore(backup.limitedTo(granted(Manifest.permission.READ_CONTACTS), granted(Manifest.permission.READ_CALL_LOG)))
        host.startOver()
    }

    private fun toast(message: Int) = Toast.makeText(host.app, message, Toast.LENGTH_LONG).show()
}

/**
 * The backup with the phone's contacts and call log switched off unless reading them is granted
 * here ([contactsGranted], [callLogGranted]) – Android's permissions do not come along with a file.
 */
internal fun SettingsBackup.limitedTo(contactsGranted: Boolean, callLogGranted: Boolean): SettingsBackup = copy(
    options = options.copy(
        deviceContactsEnabled = options.deviceContactsEnabled && contactsGranted,
        callLogEnabled = options.callLogEnabled && callLogGranted,
    ),
)

/** Reads up to [limit] bytes (InputStream.readNBytes only exists from Android 13 on). */
private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
