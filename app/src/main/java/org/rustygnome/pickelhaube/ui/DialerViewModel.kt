package org.rustygnome.pickelhaube.ui

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.NumberText
import org.rustygnome.pickelhaube.data.DeviceContacts
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.data.SpeedDials
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts
import org.rustygnome.pickelhaube.tile.DeflectionTileService
import org.rustygnome.pickelhaube.tile.RedialTileService
import org.rustygnome.pickelhaube.tr064.AuthenticationException
import org.rustygnome.pickelhaube.tr064.BoxDiscovery
import org.rustygnome.pickelhaube.tr064.CertificateInfo
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.Phonebook
import org.rustygnome.pickelhaube.tr064.InternalPhone
import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.PhonebookNumber
import org.rustygnome.pickelhaube.tr064.RecentCall
import org.rustygnome.pickelhaube.tr064.Tr064Exception
import org.rustygnome.pickelhaube.tr064.UntrustedCertificateException
import org.rustygnome.pickelhaube.tr064.OwnNumber
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Besides digits, what the number field takes from a hardware keyboard or a paste: + * # and the pauses. */
private const val DIALLABLE = "+*#,;pPwWxX"

/** How long to wait for the user to confirm on the box before giving up. */
private const val SECOND_FACTOR_TIMEOUT_MILLIS = 120_000L

class DialerViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)

    private val _state = MutableStateFlow(
        UiState(
            config = settings.activeProfile()?.config ?: FritzConfig(),
            profiles = settings.profiles(),
            activeProfileId = settings.activeProfileId,
            showSettings = !settings.isConfigured(),
            showConnectionSettings = !settings.isConfigured(),
            // Nothing set up yet: the welcome tour comes first, in front of the page for the first box.
            showWelcome = !settings.isConfigured(),
            settingsSaved = settings.isConfigured(),
            deviceContactsEnabled = settings.deviceContactsEnabled,
            callLogEnabled = settings.callLogEnabled,
            phoneSpeedDialsEnabled = settings.phoneSpeedDialsEnabled,
            phoneSpeedDials = settings.phoneSpeedDials,
            callStatusEnabled = settings.callStatusEnabled,
            keyTonesEnabled = settings.keyTonesEnabled,
            redialNumber = settings.lastDialledNumber,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** What the parts below share with the view model. */
    private val host = object : DialerHost {
        override val app: Application get() = getApplication()
        override val settings get() = this@DialerViewModel.settings
        override val state get() = _state
        override val scope get() = viewModelScope
        override var verifyingSettings
            get() = this@DialerViewModel.verifyingSettings
            set(value) { this@DialerViewModel.verifyingSettings = value }
        override fun showDialer() = this@DialerViewModel.showDialer()
        override fun openPicker() = this@DialerViewModel.openPicker()
        override fun startOver() = this@DialerViewModel.startOver()
        override fun dismissError() = this@DialerViewModel.dismissError()
        override fun numberKeys() = this@DialerViewModel.numberKeys()
        override fun activateProfile(id: String) = this@DialerViewModel.activateProfile(id)
        override fun refreshPhones() = this@DialerViewModel.refreshPhones()
        override fun runOnBox(progress: UiText, block: BoxAction) = this@DialerViewModel.runOnBox(progress, block)
        override fun loadPhones(dialer: FritzDialer, internal: Boolean) = this@DialerViewModel.loadPhones(dialer, internal)
        override fun preloadFromBox() = this@DialerViewModel.preloadFromBox()
        override fun runWhenIdle(key: String, action: () -> Unit) = this@DialerViewModel.runWhenIdle(key, action)
        override fun startQueuedRequests() = this@DialerViewModel.startQueuedRequests()
        override fun reloadRouterPhonebooks() = this@DialerViewModel.reloadRouterPhonebooks()
    }

    /** The home screen widget's setup, and a tap on one of its rows. */
    internal val widget = WidgetPart(host)

    /** Export and import of the settings. */
    internal val transfer = TransferPart(host)

    /** The connection page: adding, editing and deleting a box. */
    internal val connections = ConnectionsPart(host)

    /** The welcome tour. */
    internal val welcome = WelcomePart(host, connections)

    /** The "What's new" note after an update. */
    internal val whatsNew = WhatsNewPart(host)

    /** The call just placed, followed on the box' call monitor. */
    internal val callStatus = CallStatusPart(host)

    /** The box of the network the phone is in. */
    private val network = NetworkPart(host)

    /** The call deflections of the box in use. */
    internal val deflections = DeflectionsPart(host)

    /** This phone's and the box' speed dials. */
    internal val speedDials = SpeedDialsPart(host)

    /** The box' calls and this phone's, missed calls and line status. */
    internal val calls = CallsPart(host)

    /** Action interrupted by an untrusted certificate; re-run once the user trusts it. */
    private var pendingAction: Pair<UiText, BoxAction>? = null

    /** The last action that failed, for "Retry". */
    private var failedAction: Pair<UiText, BoxAction>? = null

    /**
     * True while settings entered on the settings screen are being verified. The edited
     * config is only persisted once the router accepted it.
     */
    private var verifyingSettings = false

    // --- number input ---------------------------------------------------------------

    // A number changed by hand is no longer the one picked: "back" leaves the app again.
    fun onNumberChange(value: String) = _state.update { it.copy(number = value, numberLabel = null, backToPicker = false) }

    /** A key of the keypad: its character at the cursor (in place of what is selected), the cursor after it. */
    fun appendDigit(c: Char) = _state.update {
        val (number, cursor) = typed(it.number, it.cursor, c)
        it.copy(number = number, fieldCursor = FieldCursor(number, cursor), numberLabel = null, backToPicker = false)
    }

    /** ⌫: the character before the cursor (or what is selected) deleted; at the start of the field, nothing. */
    fun backspace() = _state.update {
        val (number, cursor) = deletedBefore(it.number, it.cursor) ?: return@update it
        it.copy(number = number, fieldCursor = FieldCursor(number, cursor), numberLabel = null, backToPicker = false)
    }

    /**
     * The number field itself: the cursor set (a tap between two digits) or a selection made – or, from a
     * hardware keyboard or "Paste" in its menu, the text changed: only what can be dialled is kept.
     */
    fun editField(text: String, start: Int, end: Int) {
        val state = _state.value
        if (text == state.number) {
            _state.update { it.copy(fieldCursor = FieldCursor(it.number, start, end)) }
            return
        }
        val kept = text.filter { it.isDigit() || it in DIALLABLE }
        // Where the cursor was, counted in what is kept.
        val at = text.take(end.coerceIn(0, text.length)).count { it.isDigit() || it in DIALLABLE }
        _state.update {
            it.copy(number = kept, fieldCursor = FieldCursor(kept, at), numberLabel = null, backToPicker = false)
        }
    }

    /** A phone of the box was tapped in the picker: its internal number into the dialer, with its name as the field's label. */
    fun pickInternalPhone(number: String, label: String) {
        if (_state.value.addingToWidget) return widget.add(label, number)
        _state.update { it.copy(number = number, numberLabel = UiText.Raw(label), showPicker = false, backToPicker = true) }
    }

    /** A suggestion was tapped: its number into the dialer, with whose it is as the field's label. */
    fun takeSuggestion(suggestion: Suggestion) = _state.update {
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
    private fun watchSuggestions() = viewModelScope.launch {
        val countryIso = deviceCountryIso(getApplication())
        val index = _state
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
        val typing = _state.map { it.number to (it.numberLabel != null) }.distinctUntilChanged()
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
                _state.update { it.copy(suggestions = found, matchedName = matched) }
            }
    }

    /** Called with the number from a tel: link (ACTION_DIAL / ACTION_VIEW). */
    fun prefill(number: String) = _state.update { it.copy(number = NumberText.clean(number), numberLabel = null, backToPicker = false) }

    /**
     * A text shared from another app ([fromClipboard]: pasted) – an email, a signature, a web page: its
     * phone number into the dialer, or – with several – the one chosen from them.
     */
    fun takeNumbersFrom(text: String, fromClipboard: Boolean) {
        if (settings.isConfigured()) showDialer()
        val numbers = NumberText.extract(text)
        when (numbers.size) {
            0 -> _state.update {
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
            else -> _state.update { it.copy(numberChoice = numbers) }
        }
    }

    fun chooseNumber(number: String) {
        _state.update { it.copy(numberChoice = null) }
        prefill(number)
    }

    fun dismissNumberChoice() = _state.update { it.copy(numberChoice = null) }

    /**
     * Long press on a digit of the dial pad: a speed dial if there is one for it (see
     * [SpeedDials.forLongPress]); otherwise like a tap – except "0", which gives "+". A long press
     * on "*" gives a pause ",".
     */
    fun longPressDigit(digit: Char) {
        // A pause: what follows is typed on the phone once the call is connected.
        if (digit == '*') return appendDigit(',')
        val state = _state.value
        val speedDial = SpeedDials.forLongPress(
            state.number, digit, state.usablePhoneSpeedDials, state.usableBoxSpeedDials,
        )
        if (speedDial != null) useSpeedDial(speedDial) else appendDigit(if (digit == '0') '+' else digit)
    }

    /** Puts a speed dial's number into the field, with its slot and name as the field's label. */
    fun useSpeedDial(speedDial: SpeedDial) = _state.update {
        it.copy(
            number = speedDial.number,
            numberLabel = uiText(R.string.dialer_speed_dial_label, speedDial.slot, speedDial.name),
            showPicker = false,
            // Taken in the picker's speed dial tab: "back" leads there again; a long press in the dialer has none behind it.
            backToPicker = it.showPicker,
        )
    }

    fun dismissError() {
        failedAction = null
        _state.update { it.copy(error = null, canRetry = false) }
        // Tapped while the failed one was running: its turn now that the error has been read.
        startQueuedAction()
    }

    fun retry() {
        val action = failedAction ?: return
        dismissError()
        runOnBox(action.first, action.second)
    }

    /** From the error dialog: go to the settings of the box in use to fix the connection. */
    fun openSettingsFromError() {
        dismissError()
        // From the welcome tour: on to the full connection page, with what was entered there.
        if (_state.value.showWelcome) return welcome.leave(toForm = true)
        val active = settings.activeProfile()
        if (active != null) {
            _state.update { it.copy(showSettings = true) }
            connections.edit(active.id)
        } else {
            openSettings()
        }
    }

    // --- settings ---------------------------------------------------------------------

    /** Opens the settings; without any box configured directly on the "add" page. */
    fun openSettings() {
        _state.update { it.copy(showSettings = true) }
        if (!settings.isConfigured()) connections.add()
    }

    /** Leaves the settings without saving: back to the connection in use. */
    fun closeSettings() {
        verifyingSettings = false
        _state.update {
            it.copy(
                showSettings = false,
                showConnectionSettings = false,
                showLanguage = false,
                editingProfileId = null,
                config = activeConfig(),
                error = null,
            )
        }
    }

    /** Switches to another configured FritzBox (manually, or after automatic recognition). */
    fun activateProfile(id: String) {
        val profile = settings.profiles().firstOrNull { it.id == id } ?: return
        settings.activeProfileId = id
        _state.update {
            it.copy(
                activeProfileId = id,
                // Chosen by hand, it may well not be the box of this network.
                activeBoxSeen = network.seenInNetwork(profile),
                activeBoxWorking = null,
                config = if (it.showConnectionSettings) it.config else profile.config,
                phones = emptyList(),
                internalPhones = emptyList(),
                selectedPhone = null,
                phonebooks = emptyList(),
                phonebooksLoaded = false,
                boxCalls = emptyList(),
                newMissedCalls = 0,
                lines = emptyList(),
                ownNumbers = emptyList(),
                boxCallsLoaded = false,
                deflections = null,
                networkNotice = null,
            )
        }
        refreshPhones()
    }

    private fun activeConfig(): FritzConfig = settings.activeProfile()?.config ?: FritzConfig()

    // --- the box of the current network (see NetworkPart) ----------------------------

    /**
     * The app came to the foreground: the network may have changed meanwhile without the callback
     * being delivered, so detect again – unless that just happened.
     */
    fun onForeground() {
        if (!settings.isConfigured()) return
        // Possibly switched meanwhile – from the quick settings tile, or on the box.
        if (_state.value.deflections != null) deflections.preload()
        // The phone books (and the box' speed dials in them) may have been changed on the box meanwhile.
        if (_state.value.phonebooksLoaded && !_state.value.noBoxReachable) reloadRouterPhonebooks()
        // Calls may have been missed while the app was in the background.
        calls.preloadMissed()
        calls.preloadLines()
        network.detectIfStale()
    }

    /**
     * Pull to refresh in the dialer: which box is reachable from here, and everything the dialer shows
     * read from it anew – its phones, missed calls, whether its numbers are registered, its call
     * forwardings and phone books. As when the app comes back to the foreground, but all of it, now.
     */
    fun refresh() {
        if (!settings.isConfigured()) return
        // These wait for the box to be free – after the detection and the phones below.
        deflections.preload()
        calls.preloadMissed()
        calls.preloadLines()
        if (_state.value.phonebooksLoaded) reloadRouterPhonebooks() else speedDials.preload()
        // Which box, then its phones – or, if another one is reachable now, everything of that one.
        network.detectAndLoad()
    }

    // ViewModel.onCleared() is empty today (lint's EmptySuperCall), but called anyway in case it ever is not.
    @SuppressLint("EmptySuperCall")
    override fun onCleared() {
        network.stopWatching()
        super.onCleared()
    }

    // --- certificates -----------------------------------------------------------------

    /** User accepted the presented certificate: pin it and retry what was interrupted. */
    fun trustCertificate() {
        val prompt = _state.value.certificatePrompt ?: return
        val config = _state.value.config.copy(trustedCertDer = prompt.certificate.derBase64)
        // While verifying new settings, everything is persisted together once the login works.
        if (!verifyingSettings) persistedProfileId()?.let { settings.updateTrustedCert(it, config.trustedCertDer) }
        _state.update { it.copy(config = config, certificatePrompt = null) }
        val action = pendingAction
        pendingAction = null
        if (action != null) runOnBox(action.first, action.second) else startQueuedAction()
    }

    fun rejectCertificate() {
        failedAction = pendingAction
        pendingAction = null
        // Without the box' certificate, nothing tapped meanwhile can reach it either.
        queuedActions.clear()
        _state.update {
            it.copy(
                certificatePrompt = null,
                status = null,
                canRetry = failedAction != null,
                error = BoxError(
                    uiText(R.string.error_cert_rejected_title),
                    uiText(R.string.error_cert_rejected),
                    settingsRelated = true,
                ),
            )
        }
    }

    fun showTrustedCertificate() {
        val info = _state.value.config.trustedCertDer?.let(CertificateInfo::fromDerBase64) ?: return
        _state.update { it.copy(viewedCertificate = info) }
    }

    fun closeCertificateView() = _state.update { it.copy(viewedCertificate = null) }

    /** The stored connection a certificate decision belongs to: the edited one, else the active one. */
    private fun persistedProfileId(): String? =
        if (_state.value.showConnectionSettings) _state.value.editingProfileId else settings.activeProfileId

    fun forgetCertificate() {
        val config = _state.value.config.copy(trustedCertDer = null)
        if (!verifyingSettings) persistedProfileId()?.let { settings.updateTrustedCert(it, null) }
        _state.update { it.copy(config = config, viewedCertificate = null) }
    }

    // --- phone books ------------------------------------------------------------------

    /**
     * Opens the number picker. The recent calls are read anew each time (they change with every
     * call); the phone books only the first time.
     */
    /** Opens the number picker on "Recent" – [missedOnly]: with just the missed calls (from the dialer's line about them). */
    fun openPicker(missedOnly: Boolean = false) {
        _state.update {
            it.copy(
                showPicker = true,
                pickerTab = PickerTab.RECENT,
                recentsMissedOnly = missedOnly,
                // Opened anew: at the top of its lists, without the search of the last time.
                pickerSession = it.pickerSession + 1,
                backToPicker = false,
                assigningSpeedDial = null,
                addingToWidget = false,
                newCallsSince = null,
                askForDeviceContacts = !it.deviceContactsEnabled &&
                    !settings.deviceContactsNeverAsk,
            )
        }
        calls.markMissedSeen()
        // Without a reachable box only the phone's own sources; "reload" still tries the box.
        if (_state.value.noBoxReachable) calls.loadPhoneCalls() else calls.reloadRecents()
        if (!_state.value.phonebooksLoaded && !_state.value.noBoxReachable) reloadRouterPhonebooks()
        if (_state.value.deviceContacts == null) loadDeviceContacts()
    }

    /** "Back" in the dialer after a number was picked: the picker again, as it was left – nothing read anew. */
    fun reopenPicker() = _state.update { it.copy(showPicker = true, backToPicker = false) }

    /** The app was left (not just turned): coming back, "back" no longer leads to the picker left before. */
    fun onLeftApp() = _state.update { it.copy(backToPicker = false) }

    /** "Recent": just the missed calls, or all calls. */
    fun setRecentsMissedOnly(missedOnly: Boolean) = _state.update { it.copy(recentsMissedOnly = missedOnly) }

    fun closePicker() {
        // What was removed there stays removed: "Undo" went with the picker.
        speedDials.dropUndo()
        _state.update { it.copy(showPicker = false, assigningSpeedDial = null, addingToWidget = false) }
    }

    fun selectPickerTab(tab: PickerTab) {
        _state.update { it.copy(pickerTab = tab, assigningSpeedDial = null) }
        calls.markMissedSeen()
    }

    fun openLanguage() = _state.update { it.copy(showLanguage = true) }

    // --- starting over -----------------------------------------------------------------

    /**
     * Starts over with the settings stored now (after an import), as on a fresh start: nothing of
     * the boxes before is kept, and the box of this network is looked for again.
     */
    private fun startOver() {
        val app = getApplication<Application>()
        _state.update {
            it.copy(
                config = settings.activeProfile()?.config ?: FritzConfig(),
                profiles = settings.profiles(),
                activeProfileId = settings.activeProfileId,
                settingsSaved = settings.isConfigured(),
                // Imported instead of entering the first box: on to the settings with the boxes now there.
                showConnectionSettings = it.showConnectionSettings && !settings.isConfigured(),
                showWelcome = it.showWelcome && !settings.isConfigured(),
                editingProfileId = if (settings.isConfigured()) null else it.editingProfileId,
                activeBoxSeen = null,
                activeBoxWorking = null,
                boxesSeen = null,
                networkNotice = null,
                phones = emptyList(),
                internalPhones = emptyList(),
                selectedPhone = null,
                phonebooks = emptyList(),
                phonebooksLoaded = false,
                boxCalls = emptyList(),
                newMissedCalls = 0,
                lines = emptyList(),
                ownNumbers = emptyList(),
                boxCallsLoaded = false,
                deflections = null,
                deviceContactsEnabled = settings.deviceContactsEnabled,
                deviceContacts = null,
                callLogEnabled = settings.callLogEnabled,
                phoneCalls = null,
                callLogProblem = null,
                phoneSpeedDialsEnabled = settings.phoneSpeedDialsEnabled,
                phoneSpeedDials = settings.phoneSpeedDials,
                callStatusEnabled = settings.callStatusEnabled,
                keyTonesEnabled = settings.keyTonesEnabled,
            )
        }
        if (settings.deviceContactsEnabled) loadDeviceContacts()
        if (settings.callLogEnabled) calls.loadPhoneCalls()
        DeflectionTileService.refresh(app)
        RedialTileService.refresh(app)
        LauncherShortcuts.removeAll(app)
        LauncherShortcuts.publish(app)
        if (settings.isConfigured()) {
            network.detectAndLoad()
        }
    }

    fun closeLanguage() = _state.update { it.copy(showLanguage = false) }

    /** The picker's reload button: recent calls and phone books. */
    fun reloadPicker() {
        // The box' phones come with the list of phones for dial help.
        if (_state.value.pickerTab == PickerTab.INTERNAL) refreshPhones()
        calls.reloadRecents()
        loadDeviceContacts()
        reloadRouterPhonebooks()
    }

    /**
     * (Re)reads the contacts stored on this device. Does nothing without the contacts
     * permission; the phone book screen calls this again once it is granted.
     */
    fun loadDeviceContacts() {
        val app = getApplication<Application>()
        if (!_state.value.deviceContactsEnabled || !DeviceContacts.hasPermission(app)) return
        viewModelScope.launch {
            val book = withContext(Dispatchers.IO) { runCatching { DeviceContacts.load(app) }.getOrNull() }
            if (book != null) {
                _state.update { it.copy(deviceContacts = book) }
                completeRedialName()
            }
        }
    }

    /**
     * Switches the optional "this phone's contacts" feature. The settings screen only enables it
     * once Android granted the contacts permission. Disabling stops reading the contacts at once
     * (the permission itself can only be revoked in the Android settings).
     */
    /** Switches the keypad's tones on or off. */
    fun setKeyTonesEnabled(enabled: Boolean) {
        settings.keyTonesEnabled = enabled
        _state.update { it.copy(keyTonesEnabled = enabled) }
    }

    fun setDeviceContactsEnabled(enabled: Boolean) {
        settings.deviceContactsEnabled = enabled
        // Switching it off on purpose also means: don't keep asking about it.
        if (!enabled) settings.deviceContactsNeverAsk = true
        _state.update {
            it.copy(
                deviceContactsEnabled = enabled,
                deviceContacts = if (enabled) it.deviceContacts else null,
                askForDeviceContacts = false,
            )
        }
        if (enabled) loadDeviceContacts()
    }

    private fun reloadRouterPhonebooks() {
        // Also read for the box' speed dials, which come from its phone books.
        val profile = settings.activeProfile()
        if (profile != null && !profile.phonebooksEnabled && !profile.speedDialsEnabled) return
        if (_state.value.busy) return runWhenIdle("phonebooks", ::reloadRouterPhonebooks)
        runOnBox(uiText(R.string.phonebook_loading)) { dialer ->
            val books = dialer.loadPhonebooks()
            _state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
            completeRedialName()
            UiText.Plural(R.plurals.status_contacts_loaded, books.sumOf { it.contacts.size })
        }
    }

    /** Hands [contact] over to the box' phone book [book] as a new entry, then reads the phone books anew. */
    fun handOverToBox(book: Phonebook, contact: PhonebookContact) =
        host.runOnBoxEntries(uiText(R.string.status_handing_over, contact.name), retry = true) { dialer ->
            // Looked for by its name: read again, its id may have changed – or it is gone.
            val target = _state.value.phonebooks.firstOrNull { !it.online && it.name == book.name }
            if (target == null) {
                uiText(R.string.status_phonebook_gone, book.name, contact.name).also { host.toast(it) }
            } else {
                dialer.addToPhonebook(target.id, target.name, contact)
                val books = dialer.loadPhonebooks()
                _state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
                uiText(R.string.status_handed_over, contact.name, target.name)
            }
        }

    /** Answer to the "include this phone's contacts?" question on the picker's phone book tab. */
    fun answerDeviceContactsQuestion(answer: DeviceContactsAnswer) {
        _state.update { it.copy(askForDeviceContacts = false) }
        when (answer) {
            DeviceContactsAnswer.ALLOWED -> setDeviceContactsEnabled(true)
            // Only for this visit: asked again the next time the phone books are opened.
            DeviceContactsAnswer.NOT_NOW -> Unit
            DeviceContactsAnswer.NEVER -> settings.deviceContactsNeverAsk = true
        }
    }

    /** A number was tapped in the picker (recent calls or phone books): put it into the dialer and go back. */
    fun pickNumber(number: String) {
        if (_state.value.addingToWidget) return widget.add(nameFor(number).orEmpty(), number)
        _state.update { it.copy(number = number, numberLabel = null, showPicker = false, backToPicker = true) }
    }

    /**
     * The call button of a number in the picker: the number into the dialer – "back" there leads to
     * the picker again – and dialled right away (once the box is free, should it be busy).
     */
    fun callFromPicker(number: String) {
        pickNumber(number)
        dialNow()
    }

    /** The call button of a speed dial in the picker: like [callFromPicker], the field saying which speed dial it is. */
    fun callSpeedDialFromPicker(speedDial: SpeedDial) {
        useSpeedDial(speedDial)
        dialNow()
    }

    /** The call button of one of the box' phones in the picker: like [callFromPicker], the field saying which phone. */
    fun callInternalFromPicker(number: String, name: String) {
        pickInternalPhone(number, name)
        dialNow()
    }

    /** Dials the number in the field – once the box is free, should it be busy. */
    private fun dialNow() {
        if (_state.value.busy) runWhenIdle("dial") { dial() } else dial()
    }

    /** A contact's number was tapped in the phone books: for the dialer, or for the speed dial being set up. */
    fun pickContactNumber(name: String, number: String) {
        when (val assignment = _state.value.assigningSpeedDial) {
            null -> if (_state.value.addingToWidget) widget.add(name, number) else pickNumber(number)
            is SpeedDialAssignment.Phone -> {
                speedDials.setPhone(assignment.slot, name, number)
                _state.update { it.copy(assigningSpeedDial = null, pickerTab = PickerTab.SPEED_DIALS) }
            }
            // The phone book tab asks what is still open, then calls setBoxSpeedDial.
            is SpeedDialAssignment.Box -> Unit
        }
    }

    /** Compares numbers however they are written, by the rules of the box in use (see [numberKey]). */
    private fun numberKeys(): (String) -> String {
        val countryIso = deviceCountryIso(getApplication())
        val codes = _state.value.activeProfile?.dialingCodes
        return { numberKey(it, countryIso, codes) }
    }

    // --- router actions ---------------------------------------------------------------

    fun refreshPhones() {
        // Another request is running (e.g. for the previous box): reload once it is done.
        if (_state.value.busy) return runWhenIdle("phones", ::refreshPhones)
        // While a connection is being edited, the request goes to the edited address instead.
        val profile = settings.activeProfile()?.takeIf { !_state.value.showConnectionSettings }
        runOnBox(uiText(R.string.status_loading_phones)) { dialer ->
            profile?.let { verifyAndLearn(dialer, it) }
            loadPhones(dialer)
        }
        preloadFromBox()
    }

    /**
     * Makes sure the box answering is [profile]'s – its serial number must be the stored one, so a
     * box merely posing as it (its device id is public) is noticed – and fills in what is missing
     * or outdated: the device id (of a connection saved while away), model, FRITZ!OS version and
     * serial number.
     */
    private fun verifyAndLearn(dialer: FritzDialer, profile: BoxProfile) {
        val info = readBoxInfo(dialer)
        val serial = info?.serialNumber
        if (serial != null && profile.serialNumber != null && !sameSerial(serial, profile.serialNumber)) {
            throw OtherBoxException(profile.config.host, profile.name)
        }
        // Without the rights for GetInfo, the public device description tells the details as well.
        val identity = if (info == null || profile.deviceId == null) BoxDiscovery.identify(profile.config.host) else null
        (info ?: identity?.info)?.let { settings.updateBoxInfo(profile.id, it) }
        readDialingCodes(dialer)?.let { settings.updateDialingCodes(profile.id, it) }
        readUserRights(dialer)?.let { settings.updateUserRights(profile.id, it) }
        if (profile.deviceId == null) {
            identity?.let { settings.updateDeviceId(profile.id, it.udn, it.modelName) }
        }
        _state.update { it.copy(profiles = settings.profiles()) }
    }

    /** Serial numbers (MAC addresses) alike, with or without separators. */
    private fun sameSerial(a: String, b: String) =
        a.filter(Char::isLetterOrDigit).equals(b.filter(Char::isLetterOrDigit), ignoreCase = true)

    /** Loads in the background what else the box offers: speed dials, call deflections, missed calls, line status. */
    private fun preloadFromBox() {
        speedDials.preload()
        deflections.preload()
        calls.preloadMissed()
        calls.preloadLines()
    }

    /**
     * Box requests that came in while another one was running (one per [runWhenIdle] key, so
     * repeated requests are not repeated). They start one after the other once the running one
     * succeeded – not after a failure, whose error dialog or certificate question they would replace.
     */
    private val whenIdle = LinkedHashMap<String, () -> Unit>()

    private fun runWhenIdle(key: String, action: () -> Unit) {
        whenIdle[key] = action
    }

    /**
     * Actions tapped while the box was busy (another action, or reading in the background) – they
     * used to be dropped, so the tap seemed to do nothing. They run one after the other, each once
     * (the same one tapped twice is queued once), before the requests of [whenIdle]; also after a
     * failure, but only once its error has been read ([dismissError]) and not while the box' certificate
     * is in question.
     */
    private val queuedActions = ArrayDeque<Pair<UiText, BoxAction>>()

    /** The next of [queuedActions], if the box is free for it; true if one was started. */
    private fun startQueuedAction(): Boolean {
        val state = _state.value
        if (state.busy || state.error != null || state.certificatePrompt != null) return false
        val (progress, block) = queuedActions.removeFirstOrNull() ?: return false
        runOnBox(progress, block)
        return true
    }

    private fun startQueuedRequests() {
        if (startQueuedAction()) return
        while (whenIdle.isNotEmpty() && !_state.value.busy) {
            val key = whenIdle.keys.first()
            whenIdle.remove(key)?.invoke()
        }
    }

    /** Reads the phones for dial help – and, if [internal] (switched on for the box), its phones for internal calls. */
    private fun loadPhones(dialer: FritzDialer, internal: Boolean = settings.activeProfile()?.internalCallsEnabled == true): UiText {
        val phones = dialer.listPhones()
        val current = dialer.getDialPhone()
        _state.update { it.copy(dialPhoneOnBox = current != null) }
        // No internal phones is no reason for the phones to fail.
        val internalPhones = if (internal) runCatching { dialer.internalPhones(phones) }.getOrDefault(emptyList()) else emptyList()
        _state.update {
            it.copy(
                phones = phones,
                internalPhones = internalPhones,
                selectedPhone = current ?: it.selectedPhone ?: phones.firstOrNull(),
            )
        }
        return UiText.Plural(R.plurals.status_connected, phones.size)
    }

    fun selectPhone(name: String) {
        _state.update { it.copy(selectedPhone = name) }
        runOnBox(uiText(R.string.status_selecting, FritzDialer.displayName(name))) { dialer ->
            dialer.ensureDialPhone(name)
            uiText(R.string.status_ring_on, FritzDialer.displayName(name))
        }
    }

    /** Places the call; [withhold]: with the own number withheld (see [FritzDialer.withheld]). */
    /**
     * Places the call. [withhold]: with the own number withheld; [from]: over this own number of the
     * box, by its code (see [OwnNumber.dialling]), instead of the one set up for the dial-help phone.
     */
    fun dial(withhold: Boolean = false, from: OwnNumber? = null) {
        if (_state.value.noBoxReachable) return
        // Nothing entered: the number dialled last into the field, as on a phone – dialled with the next tap.
        if (_state.value.number.isEmpty()) {
            val last = _state.value.redialNumber
            if (last != null) {
                _state.update { it.copy(number = last, numberLabel = settings.lastDialledName?.let(UiText::Raw)) }
                return
            }
        }
        // A box speed dial entered as on a handset (**712): dial – and show – the number it stands for.
        SpeedDials.forCode(FritzDialer.sanitizeNumber(_state.value.number), _state.value.usableBoxSpeedDials)
            ?.let(::useSpeedDial)
        // The box dials only the number; what follows a pause is typed on the phone once connected.
        val split = FritzDialer.splitExtension(_state.value.number)
        val number = split.number
        if (number.isEmpty()) {
            _state.update {
                it.copy(
                    status = null,
                    error = BoxError(uiText(R.string.error_no_number_title), uiText(R.string.error_no_number), false),
                    canRetry = false,
                )
            }
            return
        }
        val phone = _state.value.selectedPhone
        // A phone of the box cannot call itself: dial help rings it first, then it would call itself.
        val callee = _state.value.internalPhones.firstOrNull { it.number == number }
        if (callee != null && phone != null && callee.name.isNotEmpty() &&
            FritzDialer.displayName(phone).equals(callee.name, ignoreCase = true)
        ) {
            _state.update {
                it.copy(
                    status = null,
                    error = BoxError(
                        uiText(R.string.error_call_itself_title),
                        uiText(R.string.error_call_itself, callee.name),
                        false,
                    ),
                    canRetry = false,
                )
            }
            return
        }
        runOnBox(uiText(R.string.status_dialling, number)) { dialer ->
            // Only changes the box setting if needed: a change may require confirmation on the box.
            if (phone != null) dialer.ensureDialPhone(phone)
            callStatus.markDialActive()
            // Listening before the box dials, so its report of the call is not missed.
            callStatus.follow(number, phone, split.shownExtension)
            try {
                dialer.dial(
                    when {
                        from != null -> from.dialling(number)
                        withhold -> FritzDialer.withheld(number)
                        else -> number
                    },
                )
            } catch (e: Exception) {
                callStatus.stopFollowing()
                callStatus.clearDialActive()
                throw e
            }
            val name = nameFor(number)
            // Redial brings the later digits back as well.
            settings.lastDialledNumber = split.written
            settings.lastDialledName = name
            _state.update { it.copy(redialNumber = split.written) }
            RedialTileService.refresh(getApplication())
            LauncherShortcuts.updateRedial(getApplication(), split.written, name)
            phone?.let { uiText(R.string.status_pick_up, FritzDialer.displayName(it), number) }
                ?: uiText(R.string.status_pick_up_any, number)
        }
    }

    /** Whose [number] is, as far as the phone books and recent calls loaded so far tell. */
    private fun nameFor(number: String): String? {
        val key = numberKeys()
        val state = _state.value
        val books = state.phonebooks + listOfNotNull(state.deviceContacts)
        return nameOf(key(number), books.flatMap { it.contacts }, state.boxCalls + state.phoneCalls.orEmpty(), key)
    }

    // --- launcher shortcuts -----------------------------------------------------------

    /**
     * The launcher shortcut "Redial": [number] into the dialer, and dialled right away – once it is
     * known which box is reachable from here, and only if one is (otherwise it just stands there).
     */
    fun dialFromShortcut(number: String) {
        if (!settings.isConfigured()) return
        showDialer()
        prefill(number)
        viewModelScope.launch {
            // Where are we? The app may have been in the background for long (onStart comes after this).
            network.awaitDetection()
            // Changed meanwhile by hand, or no box to dial through.
            if (_state.value.number != number || _state.value.noBoxReachable) return@launch
            if (_state.value.busy) runWhenIdle("dial") { dial() } else dial()
        }
    }

    /** The launcher shortcut "Choose number": the number picker. */
    fun openPickerFromShortcut() {
        if (!settings.isConfigured()) return
        showDialer()
        openPicker()
    }

    /**
     * The phone books were read: if the name of the number dialled last was not known when it was
     * dialled (phone books not loaded yet, or dialled before names were kept), the "Redial" shortcut
     * gets it now.
     */
    private fun completeRedialName() {
        val number = settings.lastDialledNumber ?: return
        if (settings.lastDialledName != null) return
        val name = nameFor(number) ?: return
        settings.lastDialledName = name
        LauncherShortcuts.updateRedial(getApplication(), number, name)
    }

    /** Leaves whatever page is open for the dialer; unsaved changes on a connection page are dropped. */
    private fun showDialer() {
        verifyingSettings = false
        _state.update {
            it.copy(
                showSettings = false,
                showConnectionSettings = false,
                showLanguage = false,
                showDeflections = false,
                showPicker = false,
                editingProfileId = null,
                config = activeConfig(),
            )
        }
    }

    fun hangup() = runOnBox(uiText(R.string.status_hanging_up)) { dialer ->
        dialer.hangup()
        callStatus.stopFollowing()
        callStatus.clearDialActive()
        _state.update { it.copy(callProgress = null) }
        uiText(R.string.status_call_cancelled)
    }

    private fun runOnBox(progress: UiText, block: BoxAction) {
        val waiting = _state.value
        if (waiting.busy || waiting.certificatePrompt != null) {
            if (queuedActions.none { it.first == progress }) queuedActions.addLast(progress to block)
            return
        }
        failedAction = null
        _state.update { it.copy(busy = true, status = progress, error = null, canRetry = false, loginRejected = false) }
        viewModelScope.launch {
            val config = _state.value.config
            val result = runCatching {
                withContext(Dispatchers.IO) { block(FritzDialer(config)) }
            }
            val error = result.exceptionOrNull()
            when {
                error == null -> _state.update {
                    // It answered, so it is reachable after all (e.g. a reload while none was found).
                    it.copy(busy = false, status = result.getOrNull(), activeBoxWorking = true, activeBoxSeen = true)
                }

                error is Tr064Exception && error.secondFactorRequired ->
                    confirmAndRepeat(progress, block, config)

                error is UntrustedCertificateException -> {
                    pendingAction = progress to block
                    _state.update {
                        it.copy(
                            busy = false,
                            status = uiText(R.string.status_waiting_cert),
                            certificatePrompt = CertificatePrompt(error.certificate, error.previouslyTrusted),
                        )
                    }
                }

                else -> {
                    failedAction = progress to block
                    // Refused: ask the box what the user may do, to name the missing right.
                    val rights = if (error is Tr064Exception && error.errorCode == Tr064Exception.NOT_AUTHORIZED) {
                        withContext(Dispatchers.IO) { readUserRights(FritzDialer(config)) }
                    } else null
                    // They have changed, apparently: keep them for the connection page as well.
                    if (rights != null && !verifyingSettings) {
                        settings.activeProfile()?.takeIf { it.config.host == config.host && it.config.username == config.username }
                            ?.let { profile ->
                                settings.updateUserRights(profile.id, rights)
                                _state.update { it.copy(profiles = settings.profiles()) }
                            }
                    }
                    _state.update {
                        it.copy(
                            busy = false,
                            status = null,
                            error = describeError(error, config, rights),
                            canRetry = true,
                            loginRejected = error is AuthenticationException,
                            // A failed check of edited settings says nothing about the box in use.
                            activeBoxWorking = if (verifyingSettings) it.activeBoxWorking else false,
                        )
                    }
                }
            }
            if (error == null) startQueuedRequests()
        }
    }

    // --- second factor ----------------------------------------------------------------

    private var secondFactorJob: Job? = null

    /**
     * The box refused [block] with 866: start a second-factor confirmation, show how to
     * confirm (dialog), wait for it, then repeat [block] with the confirmation token.
     */
    private fun confirmAndRepeat(progress: UiText, block: BoxAction, config: FritzConfig) {
        _state.update { it.copy(busy = true, status = uiText(R.string.status_waiting_confirmation)) }
        secondFactorJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val auth = FritzDialer(config)
                    val challenge = auth.startSecondFactor()
                    _state.update { it.copy(secondFactor = challenge) }

                    val deadline = System.currentTimeMillis() + SECOND_FACTOR_TIMEOUT_MILLIS
                    var authState = FritzDialer.STATE_WAITING
                    while (authState == FritzDialer.STATE_WAITING && System.currentTimeMillis() < deadline) {
                        delay(1_000)
                        authState = auth.secondFactorState()
                    }
                    if (authState == FritzDialer.STATE_WAITING) {
                        runCatching { auth.stopSecondFactor() }
                        throw SecondFactorNotConfirmedException()
                    }
                    // Whatever the final state is called, the repeated action tells whether
                    // the confirmation was accepted (it fails with 866 again if not).
                    _state.update { it.copy(secondFactor = null, status = progress) }
                    block(FritzDialer(config, challenge.token))
                }
            }
            val error = result.exceptionOrNull()
            if (error is CancellationException) return@launch
            _state.update { it.copy(secondFactor = null, busy = false) }
            if (error == null) {
                _state.update { it.copy(status = result.getOrNull(), activeBoxWorking = true) }
                startQueuedRequests()
            } else {
                failedAction = progress to block
                val shown = if (error is Tr064Exception && error.secondFactorRequired) {
                    SecondFactorNotConfirmedException()
                } else error
                _state.update {
                    it.copy(
                        status = null,
                        error = describeError(shown, config),
                        canRetry = true,
                        loginRejected = shown is AuthenticationException,
                        activeBoxWorking = if (verifyingSettings) it.activeBoxWorking else false,
                    )
                }
            }
        }
    }

    /** User cancelled the confirmation dialog. */
    fun cancelSecondFactor() {
        secondFactorJob?.cancel()
        secondFactorJob = null
        val config = _state.value.config
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { FritzDialer(config).stopSecondFactor() }
        }
        _state.update { it.copy(secondFactor = null, busy = false, status = null) }
        startQueuedAction()
    }

    // Runs last: all fields above are initialised by now (Kotlin initialises in source order).
    init {
        if (settings.isConfigured()) {
            // Pick the box of the current network first; if that did not switch (and load), load now.
            network.startup()
            // Their texts in the language of now.
            LauncherShortcuts.publish(getApplication())
            }
        network.watch()
        watchSuggestions()
        whatsNew.check()
    }
}
