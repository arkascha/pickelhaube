package org.rustygnome.pickelhaube.ui

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.DeviceContacts
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.Phonebook
import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.PhonebookNumber
import org.rustygnome.pickelhaube.tr064.OwnNumber
import org.rustygnome.pickelhaube.tr064.RecentCall
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/**
 * The number picker's "Recent" tab: the numbers dialled, received or missed last, from the
 * FRITZ!Box' call list (if used for the box in use) and – optionally, switched on in the settings – from the phone's own call
 * log. Each number appears once, at its latest call. Tapping one puts it into the dialer; a long
 * press opens a menu to take it over into this phone's contacts, hand it over to the box, or make
 * it a speed dial of this phone or of the box.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsTab(
    boxCalls: List<RecentCall>,
    boxCallsLoaded: Boolean,
    /** The box' missed calls after this time (ms) are marked as new; null: none. */
    newSince: Long?,
    /** Only the numbers whose latest call was missed are shown. */
    missedOnly: Boolean,
    onMissedOnlyChange: (Boolean) -> Unit,
    /** Name of the FritzBox in use (as in the settings), shown as the source of its calls. */
    boxName: String?,
    /** Country and area code of the box in use, for recognising a number written differently. */
    dialingCodes: DialingCodes?,
    /** The own numbers of the box in use, for naming the one a call went out from or came in on. */
    ownNumbers: List<OwnNumber>,
    /** The contacts of the phone books in use, for the names of calls that come without one. */
    contacts: List<PhonebookContact>,
    /** The call list of the box in use is switched on in the settings. */
    boxCallsEnabled: Boolean,
    phoneCalls: List<RecentCall>?,
    /** The optional "this phone's calls" feature is switched on in the settings. */
    callLogEnabled: Boolean,
    /** Reading the phone's call log failed; shown with a way out. */
    callLogProblem: UiText?,
    busy: Boolean,
    onPick: (String) -> Unit,
    /** Calls a number right away; null: no call buttons (picking for something else). */
    onCall: ((String) -> Unit)?,
    /** A box to call through is there: the call buttons work. */
    callEnabled: Boolean,
    /** The phone books of the box in use, online ones included (empty = none, e.g. not reachable). */
    boxBooks: List<Phonebook>,
    /** Hands a number (with its name, if known) over to a phone book of the box. */
    onHandOver: (Phonebook, PhonebookContact) -> Unit,
    /** This phone's speed dials; null when switched off. */
    phoneSpeedDials: List<SpeedDial>?,
    /** Makes a number this phone's speed dial: slot, name, number. */
    onSetSpeedDial: (String, String, String) -> Unit,
    /** The box' speed dials; null when they cannot be set up (switched off, or the box not there). */
    boxSpeedDials: List<SpeedDial>?,
    /** Makes a number the box' speed dial: slot, name, number, type, phone book of a new entry. */
    onSetBoxSpeedDial: (String, String, String, String, Int?) -> Unit,
    /** Re-reads the phone's call log (when coming back to the app, a call may have been made). */
    onReloadCallLog: () -> Unit,
    onDisableCallLog: () -> Unit,
) {
    val context = LocalContext.current
    // The box' own phone books: only these can be changed (not the online ones it syncs).
    val localBooks = remember(boxBooks) { boxBooks.filterNot { it.online } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onReloadCallLog() }

    // A number to become this phone's speed dial: choosing the slot.
    var speedDialFor by remember { mutableStateOf<PhonebookContact?>(null) }
    speedDialFor?.takeIf { phoneSpeedDials != null }?.let { contact ->
        SpeedDialSlotDialog(
            name = contact.name,
            taken = phoneSpeedDials.orEmpty(),
            onChoose = { slot ->
                speedDialFor = null
                onSetSpeedDial(slot, contact.name, contact.numbers.first().number)
            },
            onDismiss = { speedDialFor = null },
        )
    }
    // A number to become the box' speed dial: asking for the slot (and phone book).
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
    // Number (as a contact) being handed over to the box: choosing the phone book.
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

    val countryIso = remember { deviceCountryIso(context) }
    val calls = remember(boxCalls, boxCallsEnabled, phoneCalls, callLogEnabled, dialingCodes, contacts) {
        val box = if (boxCallsEnabled) boxCalls else emptyList()
        val phone = if (callLogEnabled) phoneCalls.orEmpty() else emptyList()
        val key = { number: String -> numberKey(number, countryIso, dialingCodes) }
        nameRecents(mergeRecents(box + phone, key), contacts, key)
    }
    // Which own number a call was on – only worth saying when the box has several.
    val ownLabel = remember(boxCalls, ownNumbers, dialingCodes) {
        val key = { number: String -> numberKey(number, countryIso, dialingCodes) }
        val several = ownNumbers.size > 1 || boxCalls.mapNotNull { it.ownNumber }.distinct().size > 1
        val label: (RecentCall) -> String? = { call ->
            call.ownNumber?.takeIf { several }?.let { own ->
                ownNumbers.firstOrNull { isDialledNumber(own, it.number, key) }?.label ?: own
            }
        }
        label
    }

    Column(Modifier.fillMaxSize()) {
        if (callLogEnabled && callLogProblem != null) {
            CallLogProblemCard(
                problem = callLogProblem,
                onOpenSettings = { context.startActivity(DeviceContacts.appSettingsIntent(context)) },
                onTurnOff = onDisableCallLog,
            )
        }

        // All calls, or just the missed ones: at the right, above the arrows that say how each call went –
        // on the line of the day's header at the top.
        val filter = @Composable { modifier: Modifier ->
            Box(modifier.height(DAY_HEADER_HEIGHT).padding(end = MISSED_FILTER_END), contentAlignment = Alignment.Center) {
                FilledIconToggleButton(
                    checked = missedOnly,
                    onCheckedChange = onMissedOnlyChange,
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    MissedFilterIcon(missedOnly, contentDescription = stringResource(R.string.recents_filter_missed))
                }
            }
        }
        val shown = remember(calls, missedOnly) {
            if (missedOnly) calls.filter { it.direction == RecentCall.Direction.MISSED } else calls
        }

        when {
            !boxCallsEnabled && !callLogEnabled -> Message(stringResource(R.string.recents_none_enabled), MascotPose.CLIPBOARD)
            calls.isEmpty() && busy -> Message(stringResource(R.string.recents_loading), MascotPose.SEARCHING)
            calls.isEmpty() && boxCallsEnabled && !boxCallsLoaded ->
                Message(stringResource(R.string.recents_load_failed), MascotPose.PUZZLED)
            calls.isEmpty() -> Message(stringResource(R.string.recents_empty), MascotPose.LISTENING, stringResource(R.string.recents_empty_headline))
            shown.isEmpty() -> {
                // Still there, to show all calls again.
                filter(Modifier.align(Alignment.End))
                Message(stringResource(R.string.recents_no_missed), MascotPose.LISTENING, stringResource(R.string.recents_empty_headline))
            }
            else -> {
                // Only the phone's calls are shown: say so, rather than silently leave the box' out.
                if (boxCallsEnabled && !boxCallsLoaded && !busy) {
                    Text(
                        stringResource(R.string.recents_box_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                val now = remember(calls) { System.currentTimeMillis() }
                val times = rememberRelativeTimes()
                val toBox = stringResource(R.string.contact_to_box)
                val toPhone = stringResource(R.string.contact_to_phone)
                val makeSpeedDial = stringResource(R.string.speed_dial_make)
                val makeBoxSpeedDial = stringResource(R.string.speed_dial_make_box)
                val days = remember(shown, now) { byDay(shown, ZoneId.systemDefault()) }
                val today = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() }
                Box(Modifier.fillMaxSize()) {
                    val listState = rememberLazyListState()
                    // The filter switched: the list from its top. (The days' headers have keys, so the list
                    // would otherwise keep the day shown before at the top – and the calls above it out of sight.)
                    var listedMissedOnly by rememberSaveable { mutableStateOf(missedOnly) }
                    LaunchedEffect(missedOnly) {
                        if (missedOnly != listedMissedOnly) {
                            listedMissedOnly = missedOnly
                            listState.scrollToItem(0)
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        days.forEach { (day, dayCalls) ->
                            stickyHeader(key = day.toString()) { DayHeader(times.day(day, today)) }
                            items(dayCalls) { call ->
                                val contact = PhonebookContact(call.name ?: call.number, listOf(PhonebookNumber(call.number, "other")), "")
                                val actions = listOfNotNull(
                                    NumberAction(toPhone) { takeOverIntoDeviceContacts(context, contact) },
                                    if (localBooks.isNotEmpty()) NumberAction(toBox) { handOverFor = contact } else null,
                                    phoneSpeedDials?.let { NumberAction(makeSpeedDial) { speedDialFor = contact } },
                                    if (boxSpeedDials != null && localBooks.isNotEmpty()) {
                                        NumberAction(makeBoxSpeedDial) {
                                            boxSpeedDialFor = BoxSpeedDialRequest(contact.name, call.number, "other", null)
                                        }
                                    } else null,
                                )
                                val isNew = newSince != null && call.source == RecentCall.Source.FRITZBOX &&
                                    call.direction == RecentCall.Direction.MISSED && call.time > newSince
                                RecentRow(call, times.format(call.time, now), boxName, ownLabel(call), isNew, onPick, onCall, callEnabled, actions)
                                HorizontalDivider()
                            }
                        }
                    }
                    // Over the list, on the line of the header that stays at the top.
                    filter(Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }
}

/**
 * Name (or number) and number on the left. On the right, top line: when the call was and how it
 * went (dialled / received / missed); bottom line: where it was recorded (the FritzBox' name and a
 * router icon, or a phone icon). The icons have a fixed size and sit at the right edge, so the times
 * of all rows end at the same position – a right-aligned column.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    call: RecentCall,
    time: String,
    boxName: String?,
    /** The own number the call was on (its name on the box, else the number); null: not shown. */
    own: String?,
    /** A missed call of the box not looked at before: marked. */
    isNew: Boolean,
    onPick: (String) -> Unit,
    /** Calls the number right away; null: no call button. */
    onCall: ((String) -> Unit)?,
    callEnabled: Boolean,
    /** What a long press offers to do with the number. */
    actions: List<NumberAction>,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box {
        ListItem(
            leadingContent = onCall?.let { dial ->
                { CallButton(call.number, call.name, enabled = callEnabled) { dial(call.number) } }
            },
            overlineContent = if (isNew) {
                { Text(stringResource(R.string.recents_new_missed), color = MaterialTheme.colorScheme.error) }
            } else null,
            headlineContent = {
                Text(
                    call.name ?: call.number,
                    fontWeight = if (isNew) FontWeight.Bold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = run {
                val via = own?.let {
                    stringResource(
                        if (call.direction == RecentCall.Direction.OUTGOING) R.string.recents_from_own else R.string.recents_to_own,
                        it,
                    )
                }
                val text = listOfNotNull(call.number.takeIf { call.name != null }, via).joinToString(" · ")
                if (text.isEmpty()) null else {
                    { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            time,
                            // Tabular figures: digits of equal width, so the times line up neatly.
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                        Spacer(Modifier.width(8.dp))
                        DirectionIcon(call.direction)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (call.source == RecentCall.Source.FRITZBOX) {
                            Text(
                                boxName ?: stringResource(R.string.fritzbox),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 96.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(
                            when (call.source) {
                                RecentCall.Source.FRITZBOX -> RouterIcon
                                RecentCall.Source.PHONE -> SmartphoneIcon
                            },
                            contentDescription = when (call.source) {
                                RecentCall.Source.FRITZBOX -> stringResource(R.string.recents_recorded_by_box, boxName ?: stringResource(R.string.fritzbox))
                                RecentCall.Source.PHONE -> stringResource(R.string.recents_recorded_by_phone)
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ICON_SIZE),
                        )
                    }
                }
            },
            modifier = Modifier.combinedClickable(
                onClick = { onPick(call.number) },
                onLongClickLabel = stringResource(R.string.number_actions),
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
            ),
        )
        NumberActionsMenu(expanded = menu, actions = actions, onDismiss = { menu = false })
    }
}

/**
 * The [calls] (newest first) by the calendar day they were on, in [zone]: the days newest first, each
 * with its calls in the order given.
 */
internal fun byDay(calls: List<RecentCall>, zone: ZoneId): List<Pair<LocalDate, List<RecentCall>>> {
    val days = LinkedHashMap<LocalDate, MutableList<RecentCall>>()
    calls.forEach { call -> days.getOrPut(Instant.ofEpochMilli(call.time).atZone(zone).toLocalDate()) { mutableListOf() } += call }
    return days.entries.sortedByDescending { it.key }.map { it.key to it.value }
}

/** "Today", "Yesterday", "Monday", … above the calls of a day; it stays at the top while they scroll by. */
@Composable
private fun DayHeader(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(DAY_HEADER_HEIGHT)
            .background(MaterialTheme.colorScheme.surface)
            // At its right, the filter for the missed calls lies over it.
            .padding(start = 16.dp, end = 16.dp + 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** A day's header: as high as the filter button lying over it at the right needs. */
private val DAY_HEADER_HEIGHT = 48.dp

/** Arrow for how the call went: dialled (up), received (down, green), missed (red). */
@Composable
private fun DirectionIcon(direction: RecentCall.Direction) {
    val (icon, description, tint) = when (direction) {
        RecentCall.Direction.OUTGOING -> Triple(CallMadeIcon, R.string.recents_dialled, MaterialTheme.colorScheme.primary)
        RecentCall.Direction.INCOMING -> Triple(CallReceivedIcon, R.string.recents_received, CallGreen)
        RecentCall.Direction.MISSED -> Triple(CallMissedIcon, R.string.recents_missed, MaterialTheme.colorScheme.error)
    }
    Icon(icon, contentDescription = stringResource(description), tint = tint, modifier = Modifier.size(ICON_SIZE))
}

/** Both icons share one size, so they sit exactly above each other at the right edge. */
private val ICON_SIZE = 18.dp

/**
 * The filter button's distance from the right edge: its middle above the middle of the rows' icons
 * (16 dp from the edge, as a list item's content is) – the button is 40 dp wide.
 */
private val MISSED_FILTER_END = 16.dp + ICON_SIZE / 2 - 20.dp

/**
 * A funnel with the missed-call arrow at its bottom right – red, like the arrow of a missed call in
 * the list, while the filter is off; in the button's colour while it is on (red on its fill would be hard to see).
 */
@Composable
private fun MissedFilterIcon(on: Boolean, contentDescription: String) {
    Box(Modifier.size(24.dp).semantics { this.contentDescription = contentDescription }) {
        Icon(FunnelIcon, contentDescription = null, modifier = Modifier.size(20.dp).offset(x = (-2).dp, y = (-2).dp))
        Icon(
            CallMissedIcon,
            contentDescription = null,
            tint = if (on) LocalContentColor.current else MaterialTheme.colorScheme.error,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(15.dp)
                .offset(x = 2.dp, y = 2.dp),
        )
    }
}

/** Shown when "this phone's calls" is switched on but reading the call log failed. */
@Composable
private fun CallLogProblemCard(
    problem: UiText,
    onOpenSettings: () -> Unit,
    onTurnOff: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(problem.asString(), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onTurnOff) { Text(stringResource(R.string.turn_off)) }
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.app_settings)) }
            }
        }
    }
}

/**
 * A call's time: "now", "14 min. ago", "3 hr. ago" today, the time of day ("14:32") on an earlier day,
 * which its [day] header names – "Today", "Yesterday", the weekday within a week, then the date. In
 * the app's language (DateUtils' relative times always use the system language, even when the app
 * has its own) and the phone's 12/24-hour setting ([hours24]).
 */
private class RelativeTimes(private val locale: Locale, hours24: Boolean) {
    private val formatter = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale), null, RelativeDateTimeFormatter.Style.SHORT,
        DisplayContext.CAPITALIZATION_FOR_STANDALONE,
    )
    private val days = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale), null, RelativeDateTimeFormatter.Style.LONG,
        DisplayContext.CAPITALIZATION_FOR_STANDALONE,
    )
    private val clock = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (hours24) "Hm" else "hm"), locale)
    private val weekday = DateTimeFormatter.ofPattern("cccc", locale)
    private val thisYear = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    private val otherYear = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy"), locale)

    fun format(time: Long, now: Long): String {
        val minutes = (now - time) / 60_000
        return when {
            daysBetween(time, now) != 0 -> clock.format(Date(time))
            minutes < 1 -> formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
            minutes < 60 -> formatter.format(minutes.toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.MINUTES)
            else -> formatter.format((minutes / 60).toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.HOURS)
        }
    }

    /** The header of the calls on [day], seen on [today]. */
    fun day(day: LocalDate, today: LocalDate): String {
        val ago = ChronoUnit.DAYS.between(day, today)
        val text = when {
            ago == 0L -> days.format(RelativeDateTimeFormatter.Direction.THIS, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
            ago == 1L -> days.format(RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
            ago in 2..6 -> day.format(weekday)
            day.year == today.year -> day.format(thisYear)
            else -> day.format(otherYear)
        }
        return text.replaceFirstChar { it.titlecase(locale) }
    }

    /** Calendar days from [time] to [now] (0 = same day), in the phone's time zone. */
    private fun daysBetween(time: Long, now: Long): Int {
        val zone = ZoneId.systemDefault()
        val from = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        val to = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(from, to).toInt()
    }
}

@Composable
private fun rememberRelativeTimes(): RelativeTimes {
    val locale = LocalConfiguration.current.locales[0]
    val hours24 = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, hours24) { RelativeTimes(locale, hours24) }
}
