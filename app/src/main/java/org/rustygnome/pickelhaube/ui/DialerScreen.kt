package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.TextUnit
import org.rustygnome.pickelhaube.ui.theme.TextRoles
import org.rustygnome.pickelhaube.tr064.OwnNumber
import org.rustygnome.pickelhaube.tr064.VoipLine
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.tr064.DialSplit
import org.rustygnome.pickelhaube.tr064.FritzDialer
import kotlinx.coroutines.delay

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
                        )
                        Spacer(Modifier.width(12.dp))
                        // The box in use as the title, centred beside the helmet – or, in its place, a note
                        // about the network (smaller, in up to two lines, as it is longer).
                        val notice = state.networkNotice?.asString()
                            ?: if (state.noBoxReachable) stringResource(R.string.notice_no_known_box) else null
                        if (notice != null) {
                            Text(
                                notice,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                    if (state.callStatusEnabled || linesDown.isNotEmpty()) {
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

/**
 * The best of the numbers the one typed may be heading for, between the number field and the keypad:
 * a tap takes it; "2 more ▾" opens all of them. Empty while there are none – its height stays.
 */
@Composable
private fun SuggestionRow(suggestions: List<Suggestion>, onSuggestion: (Suggestion) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val best = suggestions.firstOrNull() ?: return@Box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
        ) {
            SuggestionText(
                best,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.dialer_take_suggestion),
                        onClick = { onSuggestion(best) },
                    )
                    .padding(horizontal = 14.dp),
            )
            if (suggestions.size > 1) {
                var open by remember { mutableStateOf(false) }
                Box {
                    val more = suggestions.size - 1
                    val description = pluralStringResource(R.plurals.dialer_more_suggestions, more, more)
                    // Looks like the drop-down of "Ring phone": a list to open, not a count of calls.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxHeight()
                            .clickable(role = Role.DropdownList, onClick = { open = true })
                            .semantics { contentDescription = description }
                            .padding(start = 12.dp, end = 8.dp),
                    ) {
                        VerticalDivider(Modifier.height(24.dp).padding(end = 12.dp))
                        Text(
                            pluralStringResource(R.plurals.dialer_more_suggestions_short, more, more),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        suggestion.name?.let { Text(it, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        Text(
                                            LocalNumberDisplay.current(suggestion.number),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = { open = false; onSuggestion(suggestion) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Name and number of a [suggestion] on one line: the number is cut short first, the name only when it is long. */
@Composable
private fun SuggestionText(suggestion: Suggestion, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        suggestion.name?.let {
            Text(it, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            LocalNumberDisplay.current(suggestion.number),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = if (suggestion.name != null) MaterialTheme.typography.bodyMedium else LocalTextStyle.current,
            color = if (suggestion.name != null) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** How the call placed last is going, in up to two lines; empty while there is none. */
@Composable
private fun CallStatusLine(
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
) {
    // Numbers grouped as they are written, as in the number field.
    val show = LocalNumberDisplay.current
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
        CallStatusText(text, hint, progress, extension, Modifier.weight(1f))
    }
}

/** The call's status in up to two lines: what happens, and what to do. */
@Composable
private fun CallStatusText(text: String, hint: String?, progress: CallProgress?, extension: String?, modifier: Modifier) {
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
        modifier = modifier,
    )
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

/** ⌫ button: tap deletes the last digit, long press clears the whole number. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackspaceButton(onBackspace: () -> Unit, onClear: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.dialer_delete_digit),
                onLongClickLabel = stringResource(R.string.dialer_clear_number),
                onClick = onBackspace,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClear()
                },
            ),
    ) {
        Icon(BackspaceIcon, contentDescription = stringResource(R.string.dialer_backspace_description))
    }
}

/**
 * Round call button. With a [menu] (label and action each), a long press opens it – a Button has no
 * long press, so this one is drawn by hand in the Button's colours.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
    icon: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val container = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val content = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(container)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                    onLongClickLabel = menu.getOrNull(1)?.first,
                    onLongClick = if (menu.isEmpty()) null else {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            open = true
                        }
                    },
                ),
        ) {
            CompositionLocalProvider(LocalContentColor provides content) { icon() }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSelector(
    phones: List<String>,
    selected: String?,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val canOpen = !busy && phones.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded && canOpen,
        onExpandedChange = { if (canOpen) expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.let(FritzDialer::displayName)
                ?: stringResource(if (phones.isEmpty()) R.string.dialer_no_phones else R.string.dialer_choose_phone),
            onValueChange = {},
            readOnly = true,
            enabled = !busy,
            label = { Text(stringResource(R.string.dialer_ring_phone)) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Reloads the list from the router. The menu anchor sees the same tap and would
                    // toggle the list open, so it is explicitly kept closed here.
                    IconButton(
                        onClick = {
                            expanded = false
                            onRefresh()
                        },
                        enabled = !busy,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.dialer_reload_phones))
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && canOpen)
                    Spacer(Modifier.size(8.dp))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, canOpen),
        )
        ExposedDropdownMenu(expanded = expanded && canOpen, onDismissRequest = { expanded = false }) {
            phones.forEach { phone ->
                DropdownMenuItem(
                    text = { Text(FritzDialer.displayName(phone)) },
                    onClick = {
                        expanded = false
                        onSelect(phone)
                    },
                )
            }
        }
    }
}

private data class Key(val main: Char, val sub: String = "")

private val KEYS = listOf(
    listOf(Key('1'), Key('2', "ABC"), Key('3', "DEF")),
    listOf(Key('4', "GHI"), Key('5', "JKL"), Key('6', "MNO")),
    listOf(Key('7', "PQRS"), Key('8', "TUV"), Key('9', "WXYZ")),
    listOf(Key('*'), Key('0', "+"), Key('#')),
)

/**
 * 4×3 keypad whose keys are sized to fit the available width *and* height. Keys with one of this
 * phone's [speedDials] have a small dot.
 */
@Composable
private fun DialPad(
    onDigit: (Char) -> Unit,
    onLongDigit: (Char) -> Unit,
    speedDials: List<SpeedDial>,
    /** The keys grow up to this size, no further. */
    maxKey: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val gap = 10.dp
        val byWidth = (maxWidth - gap * 2) / 3
        val byHeight = (maxHeight - gap * 3) / 4
        val keySize: Dp = min(min(byWidth, byHeight), maxKey).coerceAtLeast(36.dp)

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            KEYS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { key ->
                        DialKey(key, keySize, speedDials.firstOrNull { it.slot == key.main.toString() }, onDigit, onLongDigit)
                    }
                }
            }
        }
    }
}

/**
 * Round key. A long press on a digit is for speed dials; without one it acts like a tap, except
 * "0", which inserts "+" like on a regular phone keypad.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    key: Key,
    size: Dp,
    speedDial: SpeedDial?,
    onDigit: (Char) -> Unit,
    onLongDigit: (Char) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val longPress: (() -> Unit)? = if (key.main.isDigit() || key.main == '*') {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongDigit(key.main)
        }
    } else null
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberKeyBounce(interaction)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = {
                    // A tiny tap, as on a phone's dial pad (as far as the system's touch feedback is on).
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onDigit(key.main)
                },
                onLongClickLabel = if (key.main == '*') stringResource(R.string.dialer_insert_pause)
                else speedDial?.let {
                    stringResource(R.string.dialer_speed_dial_label, it.slot, it.name)
                },
                onLongClick = longPress,
            ),
    ) {
        if (speedDial != null) {
            // Marks a key with a speed dial, at the top of the round key.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = size * 0.1f)
                    .size((size * 0.08f).coerceAtLeast(4.dp))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                key.main.toString(),
                fontSize = (size.value * 0.36f).sp,
                lineHeight = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            // Letters only when the key is large enough to show them legibly.
            if (key.sub.isNotEmpty() && size >= 52.dp) {
                Text(
                    key.sub,
                    fontSize = (size.value * 0.13f).sp,
                    lineHeight = (size.value * 0.15f).sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** How far a key gives way under the finger. */
private const val KEY_PRESSED_SCALE = 0.9f

/**
 * A key's size while pressed: it gives way under the finger and springs back with a little
 * overshoot when let go. A tap is often over before it has given way much – then it dips first, so
 * each key press is seen. Animations switched off in the system (developer options, accessibility)
 * leave it at its size.
 */
@Composable
private fun rememberKeyBounce(interaction: InteractionSource): Animatable<Float, AnimationVector1D> {
    val scale = remember { Animatable(1f) }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(KEY_PRESSED_SCALE, spring(stiffness = Spring.StiffnessHigh))
        } else {
            if (scale.value > (1f + KEY_PRESSED_SCALE) / 2) scale.animateTo(KEY_PRESSED_SCALE, tween(KEY_DIP_MILLIS))
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    return scale
}

/** How long a tap's dip takes, when the key had hardly given way. */
private const val KEY_DIP_MILLIS = 50
