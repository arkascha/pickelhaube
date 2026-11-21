package org.rustygnome.pickelhaube.ui

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.data.SpeedDials
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts
import org.rustygnome.pickelhaube.tile.DeflectionTileService
import org.rustygnome.pickelhaube.tile.RedialTileService
import org.rustygnome.pickelhaube.tr064.BoxDiscovery
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.OwnNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        override fun openPicker() = picker.open()
        override fun startOver() = this@DialerViewModel.startOver()
        override fun dismissError() = boxActions.dismissError()
        override fun numberKeys() = this@DialerViewModel.numberKeys()
        override fun activateProfile(id: String) = this@DialerViewModel.activateProfile(id)
        override fun refreshPhones() = this@DialerViewModel.refreshPhones()
        override fun runOnBox(progress: UiText, block: BoxAction) = boxActions.run(progress, block)
        override fun loadPhones(dialer: FritzDialer, internal: Boolean) = this@DialerViewModel.loadPhones(dialer, internal)
        override fun preloadFromBox() = this@DialerViewModel.preloadFromBox()
        override fun runWhenIdle(key: String, action: () -> Unit) = boxActions.runWhenIdle(key, action)
        override fun startQueuedRequests() = boxActions.startQueuedRequests()
        override fun reloadRouterPhonebooks() = picker.reloadRouterPhonebooks()
    }

    /** The box' certificate: pinning, viewing, forgetting. */
    internal val certificates: CertificatesPart = CertificatesPart(host)

    /** Everything done on the box: one at a time, queued, errors, the certificate question, the second factor. */
    internal val boxActions: BoxActionsPart = BoxActionsPart(host, certificates)

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

    /** The number picker: the phone books, this phone's contacts, taking a number from them. */
    internal val picker: PickerPart = PickerPart(host, calls, speedDials, widget)

    /** The number field: typing, editing, filling it from elsewhere, the suggestions. */
    internal val numberInput = NumberInputPart(host, widget)

    /**
     * True while settings entered on the settings screen are being verified. The edited
     * config is only persisted once the router accepted it.
     */
    private var verifyingSettings = false

    /** From the error dialog: go to the settings of the box in use to fix the connection. */
    fun openSettingsFromError() {
        boxActions.dismissError()
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
        if (_state.value.phonebooksLoaded && !_state.value.noBoxReachable) picker.reloadRouterPhonebooks()
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
        if (_state.value.phonebooksLoaded) picker.reloadRouterPhonebooks() else speedDials.preload()
        // Which box, then its phones – or, if another one is reachable now, everything of that one.
        network.detectAndLoad()
    }

    // ViewModel.onCleared() is empty today (lint's EmptySuperCall), but called anyway in case it ever is not.
    @SuppressLint("EmptySuperCall")
    override fun onCleared() {
        network.stopWatching()
        super.onCleared()
    }

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
        if (settings.deviceContactsEnabled) picker.loadDeviceContacts()
        if (settings.callLogEnabled) calls.loadPhoneCalls()
        DeflectionTileService.refresh(app)
        RedialTileService.refresh(app)
        LauncherShortcuts.removeAll(app)
        LauncherShortcuts.publish(app)
        if (settings.isConfigured()) {
            network.detectAndLoad()
        }
    }

    fun openLanguage() = _state.update { it.copy(showLanguage = true) }

    fun closeLanguage() = _state.update { it.copy(showLanguage = false) }

    /** Switches the keypad's tones on or off. */
    fun setKeyTonesEnabled(enabled: Boolean) {
        settings.keyTonesEnabled = enabled
        _state.update { it.copy(keyTonesEnabled = enabled) }
    }

    // --- calling from the picker (see PickerPart) ------------------------------------

    /**
     * The call button of a number in the picker: the number into the dialer – "back" there leads to
     * the picker again – and dialled right away (once the box is free, should it be busy).
     */
    fun callFromPicker(number: String) {
        picker.pickNumber(number)
        dialNow()
    }

    /** The call button of a speed dial in the picker: like [callFromPicker], the field saying which speed dial it is. */
    fun callSpeedDialFromPicker(speedDial: SpeedDial) {
        numberInput.useSpeedDial(speedDial)
        dialNow()
    }

    /** The call button of one of the box' phones in the picker: like [callFromPicker], the field saying which phone. */
    fun callInternalFromPicker(number: String, name: String) {
        numberInput.pickInternalPhone(number, name)
        dialNow()
    }

    /** Dials the number in the field – once the box is free, should it be busy. */
    private fun dialNow() {
        if (_state.value.busy) boxActions.runWhenIdle("dial") { dial() } else dial()
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
        if (_state.value.busy) return boxActions.runWhenIdle("phones", ::refreshPhones)
        // While a connection is being edited, the request goes to the edited address instead.
        val profile = settings.activeProfile()?.takeIf { !_state.value.showConnectionSettings }
        boxActions.run(uiText(R.string.status_loading_phones)) { dialer ->
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
        boxActions.run(uiText(R.string.status_selecting, FritzDialer.displayName(name))) { dialer ->
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
            ?.let(numberInput::useSpeedDial)
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
        boxActions.run(uiText(R.string.status_dialling, number)) { dialer ->
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
            val name = picker.nameFor(number)
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

    // --- launcher shortcuts -----------------------------------------------------------

    /**
     * The launcher shortcut "Redial": [number] into the dialer, and dialled right away – once it is
     * known which box is reachable from here, and only if one is (otherwise it just stands there).
     */
    fun dialFromShortcut(number: String) {
        if (!settings.isConfigured()) return
        showDialer()
        numberInput.prefill(number)
        viewModelScope.launch {
            // Where are we? The app may have been in the background for long (onStart comes after this).
            network.awaitDetection()
            // Changed meanwhile by hand, or no box to dial through.
            if (_state.value.number != number || _state.value.noBoxReachable) return@launch
            if (_state.value.busy) boxActions.runWhenIdle("dial") { dial() } else dial()
        }
    }

    /** The launcher shortcut "Choose number": the number picker. */
    fun openPickerFromShortcut() {
        if (!settings.isConfigured()) return
        showDialer()
        picker.open()
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

    fun hangup() = boxActions.run(uiText(R.string.status_hanging_up)) { dialer ->
        dialer.hangup()
        callStatus.stopFollowing()
        callStatus.clearDialActive()
        _state.update { it.copy(callProgress = null) }
        uiText(R.string.status_call_cancelled)
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
        numberInput.watchSuggestions()
        whatsNew.check()
    }
}
