package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.rustygnome.pickelhaube.tr064.OwnNumber
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.awaitCancellation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.content.ClipboardManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.tr064.FritzDialer

/**
 * Single, non-scrolling screen: number, dial pad, phone selector and call buttons.
 * The dial pad takes whatever height is left and sizes its keys to fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    state: UiState,
    onNumberChange: (String) -> Unit,
    /** The number field itself: the cursor set or a selection made (start, end), or its text changed. */
    onFieldChange: (String, Int, Int) -> Unit,
    /** The quiet note about a failure likely to pass ([UiState.quietError]) was tapped: try again. */
    onRetry: () -> Unit,
    onDigit: (Char) -> Unit,
    /** Long press on a digit: speed dial, else like a tap ("0": "+"). */
    onLongDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDial: () -> Unit,
    onHangup: () -> Unit,
    onSelectPhone: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPicker: () -> Unit,
    /** The line about new missed calls was tapped: "Recent" with just the missed calls. */
    onShowMissedCalls: () -> Unit,
    /** The clipboard's text, for the phone number in it. */
    onPaste: (String) -> Unit,
    onChooseNumber: (String) -> Unit,
    onDismissNumberChoice: () -> Unit,
    /** Opens the call forwarding of the box in use. */
    onOpenDeflections: () -> Unit,
    /** Places the call with the own number withheld (long press on the green button). */
    onDialHidden: () -> Unit,
    /** Asks the box again whether its numbers are registered (tap on the warning). */
    onCheckLines: () -> Unit,
    /** Places the call from another own number of the box (long press on the green button). */
    onDialFrom: (OwnNumber) -> Unit,
    onSuggestion: (Suggestion) -> Unit,
    /** Pulled down: read everything the dialer shows from the box anew. */
    onPullRefresh: () -> Unit,
) {
    // Pull to refresh: how far the dialer is pulled down (1: letting go refreshes) – shown by the helmet.
    val pull = remember { Animatable(0f) }
    val pullScope = rememberCoroutineScope()
    val pullHaptics = LocalHapticFeedback.current
    val pullDistance = with(LocalDensity.current) { PULL_DISTANCE_DP.dp.toPx() }
    val refreshLabel = stringResource(R.string.dialer_refresh)
    state.numberChoice?.let { NumberChoiceDialog(it, onChooseNumber, onDismissNumberChoice) }
    Scaffold(
        // Pulled down anywhere – the title included – the dialer reads the box anew.
        modifier = Modifier.pullToRefresh(pull, pullDistance, pullScope, pullHaptics, onPullRefresh),
        topBar = {
            TopAppBar(
                title = {
                    // The helmet and the FritzBox in use – the helmet stands for the app's name.
                    // The helmet rocks while the app talks to the box; a long press on the title spins it (just for fun).
                    val box = state.profiles.firstOrNull { it.id == state.activeProfileId }?.name
                    val spin = rememberHelmetSpin()
                    val haptics = LocalHapticFeedback.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                spin.start()
                            })
                        },
                    ) {
                        TitleHelmet(
                            busy = state.busy,
                            spin = spin,
                            pull = pull.value,
                            // The app's name, no longer written beside it: for accessibility services.
                            description = stringResource(R.string.app_name),
                            number = state.number,
                            connectedSince = (state.callProgress as? CallProgress.Connected)?.since,
                            rest = helmetRest(state),
                        )
                        Spacer(Modifier.width(12.dp))
                        // The box in use as the title, centred beside the helmet – or, in its place, a note
                        // about the network (smaller, in up to two lines, as it is longer).
                        val notice = state.networkNotice?.asString()
                            ?: if (state.noBoxReachable) noBoxNotice(state.networkKind) else null
                        if (notice != null) {
                            // While no box is reachable, a tap says why and what helps (Wi-Fi at home, a VPN).
                            var explain by rememberSaveable { mutableStateOf(false) }
                            if (explain && state.noBoxReachable) {
                                NotAtHomeDialog(state.networkKind, onSearchAgain = onPullRefresh, onDismiss = { explain = false })
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = if (state.noBoxReachable) {
                                    Modifier.clickable(onClickLabel = stringResource(R.string.not_at_home_what_now)) { explain = true }
                                } else {
                                    Modifier
                                },
                            ) {
                                Text(
                                    notice,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (state.noBoxReachable) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        } else {
                            Text(box ?: stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                actions = {
                    // Call forwarding: only for a reachable box that has some set up. While one is on, the
                    // button is filled (a badge would sit right on the icon's arrow); while none is, greyed
                    // out like a disabled one – but it still opens the page.
                    val deflections = state.deflections?.takeIf { it.isNotEmpty() && !state.noBoxReachable }
                    if (deflections != null) {
                        val on = deflections.any { it.enabled }
                        val description = stringResource(if (on) R.string.deflections_on else R.string.deflections_off)
                        if (on) {
                            FilledTonalIconButton(onClick = onOpenDeflections) {
                                Icon(PhoneForwardedIcon, contentDescription = description)
                            }
                        } else {
                            IconButton(onClick = onOpenDeflections) {
                                Icon(
                                    PhoneForwardedIcon,
                                    contentDescription = description,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                // The gesture's way for accessibility services.
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(refreshLabel) { onPullRefresh(); true })
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Activity indicator with a fixed height, so the layout never jumps.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // The demo box in use: its contacts are made up and its calls ring nowhere – say so all along.
            if (state.activeProfile?.isDemo == true) DemoStrip()

            // Wide and low (a phone turned, a tablet across): the keypad beside the rest, which scrolls if it must.
            // Otherwise one column – on a tablet held upright no wider than a phone's, the keys a little larger.
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                val wide = maxWidth >= WIDE_MIN_WIDTH && maxWidth > maxHeight
                val paneHeight = maxHeight
                val low = paneHeight < LOW_MAX_HEIGHT
                // Side by side: two panes of the same width, together in the middle.
                val paneWidth = min((maxWidth - PANE_GAP) / 2, PANE_MAX_WIDTH)
                val maxKey = if (LocalConfiguration.current.smallestScreenWidthDp >= 600) 96.dp else 80.dp

                val numberField: @Composable () -> Unit = {
                    // Input comes from the dial pad, at the cursor: a tap in the field sets it (to fix a typo
                    // in the middle), but no keyboard comes up – the field's requests for one are held back.
                    val onSurface = MaterialTheme.colorScheme.onSurface
                    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                    // When ⌫ was last tapped (or held), so a tap too many does not reach the paste button in its place.
                    var lastDeletion by remember { mutableLongStateOf(0L) }
                    // Grouped as numbers are written in the box' country, to be read at a glance.
                    val fieldContext = LocalContext.current
                    val codes = state.activeProfile?.dialingCodes
                    val grouping = remember(codes) { NumberGrouping(phoneNumberFormat(deviceCountryIso(fieldContext), codes)) }
                    val cursor = state.cursor
                    InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                        OutlinedTextField(
                            value = TextFieldValue(state.number, TextRange(cursor.first, cursor.last)),
                            onValueChange = { onFieldChange(it.text, it.selection.min, it.selection.max) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = grouping,
                            // Never on two lines (long translation, large font): rather cut off with "…".
                            // After a speed dial it says which one, and whose number it is.
                            label = {
                                Text(
                                    state.numberLabel?.asString() ?: state.matchedName ?: stringResource(R.string.dialer_number_label),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedTextColor = onSurface,
                                unfocusedLabelColor = onSurfaceVariant,
                                unfocusedTrailingIconColor = onSurfaceVariant,
                                focusedTrailingIconColor = onSurfaceVariant,
                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            ),
                            leadingIcon = {
                                IconButton(onClick = onOpenPicker) {
                                    // The box' missed calls not looked at yet, counted on the way to them.
                                    BadgedBox(
                                        badge = {
                                            if (state.newMissedCalls > 0) Badge { Text(state.newMissedCalls.coerceAtMost(99).toString()) }
                                        },
                                    ) {
                                        Icon(ContactsIcon, contentDescription = stringResource(R.string.dialer_choose_number))
                                    }
                                }
                            },
                            trailingIcon = {
                                if (state.number.isNotEmpty()) {
                                    BackspaceButton(
                                        onBackspace = { lastDeletion = SystemClock.uptimeMillis(); onBackspace() },
                                        onClear = { lastDeletion = SystemClock.uptimeMillis(); onNumberChange("") },
                                    )
                                } else {
                                    // An empty field offers the clipboard instead: read only when tapped.
                                    val context = LocalContext.current
                                    IconButton(onClick = {
                                        // ⌫ tapped in a row, once too often: that tap is not meant for the clipboard,
                                        // which has taken its place just now.
                                        if (SystemClock.uptimeMillis() - lastDeletion < PASTE_AFTER_DELETION_MILLIS) return@IconButton
                                        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                                        onPaste(clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty())
                                    }) {
                                        Icon(PasteIcon, contentDescription = stringResource(R.string.dialer_paste))
                                    }
                                }
                            },
                        )
                    }
                }
                // Its room stays reserved, so the keypad does not jump while typing.
                val suggestionRow: @Composable () -> Unit = {
                    SuggestionRow(state.suggestions, onSuggestion)
                }
                val tones = rememberKeyTones(state.keyTonesEnabled)
                val keypad: @Composable (Modifier) -> Unit = { modifier ->
                    DialPad(
                        onDigit = { tones?.play(it); onDigit(it) },
                        onLongDigit = { tones?.play(it); onLongDigit(it) },
                        speedDials = state.usablePhoneSpeedDials,
                        maxKey = maxKey,
                        modifier = modifier,
                    )
                }
                val phoneSelector: @Composable () -> Unit = {
                    PhoneSelector(
                        phones = state.phones,
                        selected = state.selectedPhone,
                        busy = state.busy,
                        onSelect = onSelectPhone,
                        onRefresh = onRefresh,
                    )
                }
                val statusLine: @Composable () -> Unit = {
                    // Its room stays reserved while the feature is on, so the layout does not jump with each call.
                    // Numbers not registered are warned about even without it.
                    val linesDown = state.linesDown
                    if (state.callStatusEnabled || linesDown.isNotEmpty() || state.quietError != null) {
                        Spacer(Modifier.height(8.dp))
                        CallStatusLine(
                            state.callProgress,
                            FritzDialer.splitExtension(state.number),
                            state.newMissedCalls,
                            linesDown,
                            allLinesDown = linesDown.size == state.lines.size,
                            state.activeProfile?.name ?: stringResource(R.string.fritzbox),
                            onShowMissedCalls,
                            onCheckLines,
                            state.quietError,
                            onRetry,
                            // Only for the number of the call that failed, still in the field.
                            onDial.takeIf {
                                val dialled = state.redialNumber?.let { FritzDialer.splitExtension(it).number }
                                dialled != null && FritzDialer.splitExtension(state.number).number == dialled &&
                                    !state.busy && !state.noBoxReachable
                            },
                        )
                    }
                }
                val actionButtons: @Composable () -> Unit = {
                    val redial = state.number.isEmpty() && state.redialNumber != null
                    // Side by side – or, beside the keys of a phone turned, one above the other.
                    val stacked = wide && low
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        maxItemsInEachRow = if (stacked) 1 else 2,
                    ) {
                        RoundActionButton(
                            onClick = onDial,
                            // A long press offers the call with the own number withheld.
                            // and, with several internet numbers on the box, the call from one of them.
                            // Not for an empty field, whose tap only brings the last number back.
                            menu = if (redial) emptyList() else listOf(
                                stringResource(R.string.dialer_dial) to onDial,
                                stringResource(R.string.dialer_dial_hidden) to onDialHidden,
                            ) + state.dialFromNumbers.map { own ->
                                val label = if (own.name.isBlank()) own.number else "${own.name} (${own.number})"
                                stringResource(R.string.dialer_dial_from, label) to { onDialFrom(own) }
                            },
                            // Only when there is something dialable (same check the dial action uses).
                            // No box to dial through: entering a number still works, dialling does not.
                            // Not for the number of the call under way: dialling it again would only ring the phone again.
                            // An empty field: it brings back the number dialled last (dialled with the next tap).
                            enabled = !state.busy && !state.noBoxReachable && (
                                redial ||
                                    FritzDialer.sanitizeNumber(state.number).isNotEmpty() &&
                                    FritzDialer.splitExtension(state.number).number != state.callUnderway
                                ),
                            color = CallGreen,
                        ) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = stringResource(if (redial) R.string.dialer_redial else R.string.dialer_dial),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        RoundActionButton(onClick = onHangup, enabled = state.dialActive && !state.busy, color = HangupRed) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialer_hang_up), modifier = Modifier.size(30.dp))
                        }
                    }
                }

                if (wide) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(PANE_GAP, Alignment.CenterHorizontally),
                    ) {
                        Column(
                            Modifier
                                .width(paneWidth)
                                .verticalScroll(rememberScrollState())
                                .heightIn(min = paneHeight),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            numberField()
                            suggestionRow()
                            Spacer(Modifier.height(12.dp))
                            phoneSelector()
                            statusLine()
                            if (!low) {
                                Spacer(Modifier.height(12.dp))
                                actionButtons()
                            }
                        }
                        if (low) {
                            // Too low for all of it in one pane (a phone turned): the call buttons beside the keys.
                            Row(
                                Modifier
                                    .width(paneWidth)
                                    .fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                keypad(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                                Spacer(Modifier.width(16.dp))
                                actionButtons()
                            }
                        } else {
                            keypad(
                                Modifier
                                    .width(paneWidth)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .widthIn(max = PANE_MAX_WIDTH)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        numberField()
                        suggestionRow()
                        // Takes all remaining height; keys shrink on small screens.
                        keypad(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        phoneSelector()
                        statusLine()
                        Spacer(Modifier.height(12.dp))
                        actionButtons()
                    }
                }
            }
        }
    }
}

/** Under the top bar while the demo box is in use: it is no real box. */
@Composable
private fun DemoStrip() {
    Text(
        stringResource(R.string.dialer_demo_strip),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Several numbers in a shared or pasted text: which one goes into the dialer. */
@Composable
private fun NumberChoiceDialog(numbers: List<String>, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.number_choice_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                numbers.forEach { number ->
                    Text(
                        number,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(number) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Material "content_paste" glyph (clipboard, Apache 2.0). */
private val PasteIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ContentPaste",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2L5,2c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h14" +
                "c1.1,0 2,-0.9 2,-2L21,4c0,-1.1 -0.9,-2 -2,-2zM12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1z" +
                "M19,20L5,20L5,4h2v3h10L17,4h2v16z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** From this width on, a screen wider than high shows the keypad beside the rest. */
private val WIDE_MIN_WIDTH = 560.dp

/** The dialer's column (or each of its two panes) is at most this wide – on a tablet, too. */
private val PANE_MAX_WIDTH = 480.dp

/** Between the two panes. */
private val PANE_GAP = 32.dp

/** Side by side and lower than this, the call buttons go beside the keys. */
private val LOW_MAX_HEIGHT = 420.dp

/** How long after the last ⌫ the paste button, in its place once the field is empty, ignores taps. */
private const val PASTE_AFTER_DELETION_MILLIS = 1000L
