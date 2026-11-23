package org.rustygnome.pickelhaube.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.NumberText
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.data.SpeedDials
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.InternalPhone
import org.rustygnome.pickelhaube.tr064.Phonebook
import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.PhonebookNumber
import org.rustygnome.pickelhaube.tr064.RecentCall

/** Besides digits, what the number field takes from a hardware keyboard or a paste: + * # and the pauses. */
private const val DIALLABLE = "+*#,;pPwWxX"

/**
 * The number field: typed on the keypad or a hardware keyboard, edited at the cursor, filled from a
 * tel: link, a shared or pasted text, a speed dial, a suggestion or one of the box' phones – and the
 * suggestions shown while typing ([watchSuggestions]).
 */
internal class NumberInputPart(private val host: DialerHost, private val widget: WidgetPart) {

    private val state get() = host.state
    private val settings get() = host.settings

    // A number changed by hand is no longer the one picked: "back" leaves the app again.
    fun onNumberChange(value: String) = state.update { it.copy(number = value, numberLabel = null, backToPicker = false) }

    /** A key of the keypad: its character at the cursor (in place of what is selected), the cursor after it. */
    fun appendDigit(c: Char) = state.update {
        val (number, cursor) = typed(it.number, it.cursor, c)
        it.copy(number = number, fieldCursor = FieldCursor(number, cursor), numberLabel = null, backToPicker = false)
    }

    /** ⌫: the character before the cursor (or what is selected) deleted; at the start of the field, nothing. */
    fun backspace() = state.update {
        val (number, cursor) = deletedBefore(it.number, it.cursor) ?: return@update it
        it.copy(number = number, fieldCursor = FieldCursor(number, cursor), numberLabel = null, backToPicker = false)
    }

    /**
     * The number field itself: the cursor set (a tap between two digits) or a selection made – or, from a
     * hardware keyboard or "Paste" in its menu, the text changed: only what can be dialled is kept.
     */
    fun editField(text: String, start: Int, end: Int) {
        val shown = state.value
        if (text == shown.number) {
            state.update { it.copy(fieldCursor = FieldCursor(it.number, start, end)) }
            return
        }
        val kept = text.filter { it.isDigit() || it in DIALLABLE }
        // Where the cursor was, counted in what is kept.
        val at = text.take(end.coerceIn(0, text.length)).count { it.isDigit() || it in DIALLABLE }
        state.update {
            it.copy(number = kept, fieldCursor = FieldCursor(kept, at), numberLabel = null, backToPicker = false)
        }
    }

    /** A phone of the box was tapped in the picker: its internal number into the dialer, with its name as the field's label. */
    fun pickInternalPhone(number: String, label: String) {
        if (state.value.addingToWidget) return widget.add(label, number)
        state.update { it.closing(Page.PICKER).copy(number = number, numberLabel = UiText.Raw(label), backToPicker = true) }
    }

    /** A suggestion was tapped: its number into the dialer, with whose it is as the field's label. */
    fun takeSuggestion(suggestion: Suggestion) = state.update {
        it.copy(number = NumberText.clean(suggestion.number), numberLabel = suggestion.name?.let(UiText::Raw), backToPicker = false)
    }

    /** What the numbers to suggest are taken from; a change prepares them anew. The lists are compared as the same ones mostly. */
    private data class SuggestionSources(
        val phonebooks: List<Phonebook>,
        val deviceContacts: Phonebook?,
        val internalPhones: List<InternalPhone>,
        val boxCalls: List<RecentCall>,
        val phoneCalls: List<RecentCall>?,
        val codes: DialingCodes?,
    )

    /**
     * Keeps [UiState.suggestions] and [UiState.matchedName] up to date with the number typed – never on
     * the main thread, and never holding up typing: the phone books and calls are made ready to be
     * searched ([SuggestionIndex]) only when they change, a key press then only searches them, and a
     * search still running when the next key is pressed is broken off. A number taken from a suggestion,
     * the picker or a speed dial is not offered to itself once more; one typed in full is.
     */
    fun watchSuggestions() = host.scope.launch {
        val countryIso = deviceCountryIso(host.app)
        val index = state
            .map { s ->
                SuggestionSources(
                    phonebooks = if (s.activeProfile?.phonebooksEnabled != false) s.phonebooks else emptyList(),
                    deviceContacts = s.deviceContacts,
                    internalPhones = s.internalPhones,
                    boxCalls = s.boxCalls,
                    phoneCalls = s.phoneCalls,
                    codes = s.activeProfile?.dialingCodes,
                )
            }
            .distinctUntilChanged()
            // Each one made ready to the end and used – one cut short by the next change (as they come in
            // one after the other after connecting) would leave none at all for a while; changes meanwhile
            // are taken together into the next one.
            .conflate()
            .map { sources ->
                withContext(Dispatchers.Default) {
                    // Android's number formatting reads its tables on first use: here, not on the main thread
                    // when the number field is first shown grouped (see NumberGrouping).
                    phoneNumberFormat(countryIso, sources.codes)("0301234567")
                    val key = { n: String -> numberKey(n, countryIso, sources.codes) }
                    // The box' phones, by their names, too ("Kitchen"); "all phones" has none to type.
                    val internal = sources.internalPhones.filter { it.name.isNotEmpty() }
                        .map { PhonebookContact(it.name, listOf(PhonebookNumber(it.number, "intern")), "") }
                    val contacts = (sources.phonebooks + listOfNotNull(sources.deviceContacts)).flatMap { it.contacts } + internal
                    SuggestionIndex(contacts, sources.boxCalls + sources.phoneCalls.orEmpty(), key, sources.codes) to key
                }
            }
        val typing = state.map { it.number to (it.numberLabel != null) }.distinctUntilChanged()
        combine(typing, index) { typed, prepared -> typed to prepared }
            .collectLatest { (typed, prepared) ->
                val (number, labelled) = typed
                val (searchable, key) = prepared
                val (found, matched) = withContext(Dispatchers.Default) {
                    // A number typed in full that is somebody's: their name as the field's label.
                    val matched = if (labelled) null else searchable.nameOf(number)
                    val wanted = key(FritzDialer.sanitizeNumber(number))
                    searchable.suggest(number) { ensureActive() }
                        // Not the number taken from a suggestion, the picker or a speed dial once more. One typed
                        // in full keeps its suggestion: it was there a key before, and should not vanish now.
                        .filterNot { labelled && key(it.number) == wanted } to matched
                }
                state.update { it.copy(suggestions = found, matchedName = matched) }
            }
    }

    /** Called with the number from a tel: link (ACTION_DIAL / ACTION_VIEW). */
    fun prefill(number: String) = state.update { it.copy(number = NumberText.clean(number), numberLabel = null, backToPicker = false) }

    /**
     * A text shared from another app ([fromClipboard]: pasted) – an email, a signature, a web page: its
     * phone number into the dialer, or – with several – the one chosen from them.
     */
    fun takeNumbersFrom(text: String, fromClipboard: Boolean) {
        if (settings.isConfigured()) host.showDialer()
        val numbers = NumberText.extract(text)
        when (numbers.size) {
            0 -> state.update {
                it.copy(
                    error = BoxError(
                        uiText(R.string.error_no_number_found_title),
                        uiText(if (fromClipboard) R.string.error_no_number_in_clipboard else R.string.error_no_number_in_text),
                        false,
                    ),
                    canRetry = false,
                )
            }
            1 -> prefill(numbers.single())
            else -> state.update { it.copy(numberChoice = numbers) }
        }
    }

    fun chooseNumber(number: String) {
        state.update { it.copy(numberChoice = null) }
        prefill(number)
    }

    fun dismissNumberChoice() = state.update { it.copy(numberChoice = null) }

    /**
     * Long press on a digit of the dial pad: a speed dial if there is one for it (see
     * [SpeedDials.forLongPress]); otherwise like a tap – except "0", which gives "+". A long press
     * on "*" gives a pause ",".
     */
    fun longPressDigit(digit: Char) {
        // A pause: what follows is typed on the phone once the call is connected.
        if (digit == '*') return appendDigit(',')
        val shown = state.value
        val speedDial = SpeedDials.forLongPress(
            shown.number, digit, shown.usablePhoneSpeedDials, shown.usableBoxSpeedDials,
        )
        if (speedDial != null) useSpeedDial(speedDial) else appendDigit(if (digit == '0') '+' else digit)
    }

    /** Puts a speed dial's number into the field, with its slot and name as the field's label. */
    fun useSpeedDial(speedDial: SpeedDial) = state.update {
        it.closing(Page.PICKER).copy(
            number = speedDial.number,
            numberLabel = uiText(R.string.dialer_speed_dial_label, speedDial.slot, speedDial.name),
            // Taken in the picker's speed dial tab: "back" leads there again; a long press in the dialer has none behind it.
            backToPicker = it.isOpen(Page.PICKER),
        )
    }
}
