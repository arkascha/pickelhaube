package org.rustygnome.pickelhaube.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.SettingsFileInfo
import java.time.LocalDate

/** Shortest passphrase accepted for the passwords in a settings file. */
private const val MIN_PASSPHRASE = 8

/**
 * Export and import of the settings, on the settings page: to take them to another installation.
 * Export asks whether to include the passwords (then under a passphrase), import shows what the
 * chosen file holds and asks before it replaces everything.
 */
@Composable
fun SettingsTransferSetting(
    busy: Boolean,
    /** A file opened for import, waiting to be confirmed; null while none. */
    pendingImport: SettingsFileInfo?,
    importError: UiText?,
    onExport: (Uri, CharArray?) -> Unit,
    onOpenImport: (Uri) -> Unit,
    onImport: (CharArray?) -> Unit,
    onCancelImport: () -> Unit,
) {
    var exporting by remember { mutableStateOf(false) }
    // The passphrase chosen in the export dialog, until the file to write to is chosen.
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val passphrase = exportPassphrase
        exportPassphrase = null
        if (uri != null) onExport(uri, passphrase) else passphrase?.fill('\u0000')
    }
    val import = rememberSettingsImport(busy, pendingImport, importError, onOpenImport, onImport, onCancelImport)

    Text(
        stringResource(R.string.settings_transfer_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { exporting = true }, enabled = !busy) { Text(stringResource(R.string.settings_export)) }
        OutlinedButton(onClick = import, enabled = !busy) { Text(stringResource(R.string.settings_import)) }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

    if (exporting) {
        ExportDialog(
            onExport = { passphrase ->
                exporting = false
                exportPassphrase = passphrase
                createFile.launch("pickelhaube-settings-${LocalDate.now()}.json")
            },
            onDismiss = { exporting = false },
        )
    }
}

/**
 * In the top bar of the connection page while no box is set up yet (e.g. right after installing):
 * a way aside – instead of entering a connection, import the settings from a file exported before.
 */
@Composable
fun SettingsImportOffer(
    busy: Boolean,
    pendingImport: SettingsFileInfo?,
    importError: UiText?,
    onOpenImport: (Uri) -> Unit,
    onImport: (CharArray?) -> Unit,
    onCancelImport: () -> Unit,
) {
    val import = rememberSettingsImport(busy, pendingImport, importError, onOpenImport, onImport, onCancelImport)
    // Worded for someone who has just installed the app: getting back what was saved before.
    TextButton(onClick = import, enabled = !busy) { Text(stringResource(R.string.settings_restore_backup), maxLines = 1) }
}

/** The import's file chooser and – once a file is opened – its confirmation dialog; returns what opens the chooser. */
@Composable
private fun rememberSettingsImport(
    busy: Boolean,
    pendingImport: SettingsFileInfo?,
    importError: UiText?,
    onOpenImport: (Uri) -> Unit,
    onImport: (CharArray?) -> Unit,
    onCancelImport: () -> Unit,
): () -> Unit {
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onOpenImport)
    }
    pendingImport?.let { info ->
        ImportDialog(info, importError, busy, onImport, onCancelImport)
    }
    // Android does not know a type for JSON files everywhere: offer all, the content decides.
    return { openFile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
}

/** Asks whether the passwords go into the file, and if so for the passphrase (twice). */
@Composable
private fun ExportDialog(onExport: (CharArray?) -> Unit, onDismiss: () -> Unit) {
    var withPasswords by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val problem = when {
        !withPasswords -> null
        passphrase.length < MIN_PASSPHRASE -> stringResource(R.string.settings_passphrase_short, MIN_PASSPHRASE)
        repeated != passphrase -> stringResource(R.string.settings_passphrase_mismatch)
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_export_text))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = withPasswords, onValueChange = { withPasswords = it }, role = Role.Checkbox),
                ) {
                    Checkbox(checked = withPasswords, onCheckedChange = null)
                    Text(stringResource(R.string.settings_export_passwords), modifier = Modifier.padding(start = 8.dp))
                }
                if (withPasswords) {
                    Text(
                        stringResource(R.string.settings_export_passphrase_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PasswordField(passphrase, { passphrase = it }, stringResource(R.string.settings_passphrase))
                    PasswordField(repeated, { repeated = it }, stringResource(R.string.settings_passphrase_repeat))
                    // Only once something was typed: an empty form is no mistake yet.
                    if (problem != null && (passphrase.isNotEmpty() || repeated.isNotEmpty())) {
                        Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(if (withPasswords) passphrase.toCharArray() else null) },
                enabled = problem == null,
            ) { Text(stringResource(R.string.settings_export_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Says what the opened file holds and that it replaces all settings; with passwords in it, asks for
 * the passphrase (left empty, they are not taken over).
 */
@Composable
private fun ImportDialog(
    info: SettingsFileInfo,
    error: UiText?,
    busy: Boolean,
    onImport: (CharArray?) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.settings_import_text, info.boxes, info.boxes))
                if (info.withPasswords) {
                    Text(
                        stringResource(R.string.settings_import_passphrase_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PasswordField(passphrase, { passphrase = it }, stringResource(R.string.settings_passphrase))
                } else {
                    Text(
                        stringResource(R.string.settings_import_no_passwords),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let {
                    Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(passphrase.takeIf { info.withPasswords && it.isNotEmpty() }?.toCharArray()) },
                enabled = !busy,
            ) { Text(stringResource(R.string.settings_import_confirm), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
