package org.rustygnome.pickelhaube.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import org.rustygnome.pickelhaube.R

/**
 * The failed request to the box, folded away under "Technical details": unfolded as plain text,
 * selectable and with a button to copy it – for a message to whoever looks into the problem.
 */
@Composable
private fun TechnicalDetailsSection(details: TechnicalDetails) {
    var open by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val text = remember(details) { details.asText() }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.error_technical_details))
        Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
    }
    if (open) {
        SelectionContainer {
            Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        TextButton(
            onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.error_technical_details), text))
            },
            contentPadding = PaddingValues(0.dp),
        ) { Text(stringResource(R.string.copy)) }
    }
}

/**
 * Modal error: a failed connection blocks all use of the app, so it is shown on top of
 * whatever screen is open, with the obvious next steps as buttons.
 */
@Composable
fun ErrorDialog(
    error: BoxError,
    canRetry: Boolean,
    /** Offer "Open settings" (only when the error is settings related and the settings are not open). */
    offerSettings: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val settingsButton = offerSettings && error.settingsRelated
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(error.title.asString()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(error.detail.asString())
                error.technical?.let { TechnicalDetailsSection(it) }
            }
        },
        confirmButton = {
            if (settingsButton && canRetry) {
                // Three buttons do not fit side by side (in German, "Wiederholen" got squeezed into
                // two lines): stacked, the main action first, as Material does for long buttons.
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            } else {
                when {
                    canRetry -> TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    settingsButton -> TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                    else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
        },
        dismissButton = if ((settingsButton && canRetry) || (!canRetry && !settingsButton)) null else {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
        },
    )
}
