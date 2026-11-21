package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import android.os.SystemClock
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.rustygnome.pickelhaube.tr064.VoipLine
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.tr064.DialSplit
import org.rustygnome.pickelhaube.tr064.FritzDialer
import kotlinx.coroutines.delay

/** How the call placed last is going, in up to two lines; empty while there is none. */
@Composable
internal fun CallStatusLine(
    progress: CallProgress?,
    /** The number entered, split: while there is no call to follow, its later digits are explained. */
    entered: DialSplit,
    /** Shown while there is no call to follow: the box' missed calls not looked at yet, a tap shows them. */
    newMissedCalls: Int,
    /** Shown while there is no call to follow, before the missed calls: the box' numbers not registered. */
    linesDown: List<VoipLine>,
    /** None of the box' numbers is registered. */
    allLinesDown: Boolean,
    /** The connection in use (its display name), where the calls were missed. */
    boxName: String,
    onShowMissed: () -> Unit,
    onCheckLines: () -> Unit,
    /** Shown first while there is no call to follow: a failure likely to pass, a tap tries again. */
    quietError: BoxError?,
    onRetry: () -> Unit,
    /** Calls the number again after it was busy or not answered; null: not offered (the number was changed since). */
    onTryAgain: (() -> Unit)?,
) {
    // Numbers grouped as they are written, as in the number field.
    val show = LocalNumberDisplay.current
    if (progress == null && quietError != null) {
        Text(
            stringResource(R.string.dialer_quiet_error, quietError.title.asString()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.quiet_error_retry), onClick = onRetry),
        )
        return
    }
    if (progress == null && linesDown.isNotEmpty()) {
        Text(
            when {
                allLinesDown && linesDown.size > 1 -> stringResource(R.string.dialer_no_line_registered, boxName)
                linesDown.size == 1 -> stringResource(R.string.dialer_line_not_registered, linesDown.single().number, boxName)
                else -> pluralStringResource(R.plurals.dialer_lines_not_registered, linesDown.size, linesDown.size, boxName)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onCheckLines),
        )
        return
    }
    val entry = entered.shownExtension
    if (progress == null && entry != null && entered.number.isNotEmpty()) {
        Text(
            stringResource(R.string.dialer_extension_hint, show(entered.number), entry),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    if (progress == null && newMissedCalls > 0) {
        Text(
            pluralStringResource(R.plurals.dialer_new_missed_calls, newMissedCalls, newMissedCalls, boxName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onShowMissed),
        )
        return
    }
    val text = when (progress) {
        null -> ""
        is CallProgress.PickUp -> when {
            progress.monitorOff -> stringResource(R.string.call_monitor_off_hint)
            // Nothing tells when the call is connected: what to type then is said right away.
            progress.extension != null && progress.phone != null -> stringResource(
                R.string.status_pick_up_extension, FritzDialer.displayName(progress.phone), show(progress.number), progress.extension,
            )
            progress.extension != null -> stringResource(R.string.status_pick_up_any_extension, show(progress.number), progress.extension)
            progress.phone != null ->
                stringResource(R.string.status_pick_up, FritzDialer.displayName(progress.phone), show(progress.number))
            else -> stringResource(R.string.status_pick_up_any, show(progress.number))
        }
        is CallProgress.Dialling -> progress.from?.let { stringResource(R.string.call_dialling_from, show(progress.number), show(it)) }
            ?: stringResource(R.string.call_dialling, show(progress.number))
        is CallProgress.Connected -> {
            // Counts up while connected.
            var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            LaunchedEffect(progress) {
                while (true) {
                    now = SystemClock.elapsedRealtime()
                    delay(1_000 - (now - progress.since) % 1_000)
                }
            }
            stringResource(R.string.call_connected, show(progress.number), formatDuration(((now - progress.since) / 1_000).toInt()))
        }
        is CallProgress.Ended -> stringResource(R.string.call_ended, formatDuration(progress.seconds))
        CallProgress.NotAnswered -> stringResource(R.string.call_not_answered)
        CallProgress.NotPickedUp -> stringResource(R.string.call_not_picked_up)
    }
    // The box cannot end a connected call – its hang-up only cancels the dial help, so the red
    // button is off then: the call is ended on the device itself, as the second line says.
    // With digits to type once connected: said while dialling, and in bold once connected.
    val extension = progress?.extension
    val hint = when {
        progress is CallProgress.Connected && extension != null -> stringResource(R.string.call_type_now, extension)
        progress is CallProgress.Connected -> stringResource(R.string.call_hang_up_on_device)
        progress is CallProgress.Dialling && extension != null -> stringResource(R.string.call_type_then, extension)
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // The mascot acts the call out – at the height of the two lines, so nothing moves when it comes.
        if (progress != null) {
            CallMascot(progress, Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
        }
        // What screen readers announce on each change: the status without the running time, which would
        // otherwise be announced every second.
        val spoken = when (progress) {
            is CallProgress.Connected -> stringResource(R.string.call_connected, show(progress.number), "").trimEnd(' ', '·')
            else -> text
        } + (hint?.let { "\n$it" } ?: "")
        CallStatusText(text, hint, progress, extension, spoken, Modifier.weight(1f))
        // "Hang up on the device itself": why, in the guide's answer.
        if (progress is CallProgress.Connected && extension == null) {
            val uriHandler = LocalUriHandler.current
            IconButton(onClick = { uriHandler.openUri(WebPages.FAQ_END_CALL) }) {
                Icon(
                    HelpOutlineIcon,
                    contentDescription = stringResource(R.string.call_hang_up_why),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Busy, or nobody answered (or the own phone was not picked up): calling again is the next step.
        if (onTryAgain != null && (progress == CallProgress.NotAnswered || progress == CallProgress.NotPickedUp)) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onTryAgain) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.call_try_again))
            }
        }
    }
}

/** The call's status in up to two lines: what happens, and what to do. */
@Composable
private fun CallStatusText(text: String, hint: String?, progress: CallProgress?, extension: String?, spoken: String, modifier: Modifier) {
    Text(
        buildAnnotatedString {
            append(text)
            hint?.let {
                append("\n")
                val typeNow = progress is CallProgress.Connected && extension != null
                withStyle(
                    if (typeNow) SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    else SpanStyle(fontStyle = FontStyle.Italic),
                ) { append(it) }
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (progress is CallProgress.PickUp && progress.monitorOff) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        // Announced as it changes, so the call can be followed without looking; with no call, nothing to read.
        modifier = modifier.then(
            if (spoken.isBlank()) Modifier.clearAndSetSemantics {}
            else Modifier.semantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        ),
    )
}
