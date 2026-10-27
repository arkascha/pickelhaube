package org.rustygnome.pickelhaube.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.SystemClock
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.BoxRecognition
import org.rustygnome.pickelhaube.data.DeviceCallLog
import org.rustygnome.pickelhaube.data.DeviceContacts
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.data.InvalidSettingsFileException
import org.rustygnome.pickelhaube.data.SettingsBackup
import org.rustygnome.pickelhaube.data.SettingsFile
import org.rustygnome.pickelhaube.data.SettingsFileInfo
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.data.WrongPassphraseException
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.data.SpeedDials
import org.rustygnome.pickelhaube.tile.DeflectionTileService
import org.rustygnome.pickelhaube.tile.RedialTileService
import org.rustygnome.pickelhaube.tr064.BoxDiscovery
import org.rustygnome.pickelhaube.tr064.BoxIdentity
import org.rustygnome.pickelhaube.tr064.BoxInfo
import org.rustygnome.pickelhaube.tr064.CallDeflection
import org.rustygnome.pickelhaube.tr064.CallEvent
import org.rustygnome.pickelhaube.tr064.CallMonitor
import org.rustygnome.pickelhaube.tr064.BoxUser
import org.rustygnome.pickelhaube.tr064.BoxUsers
import org.rustygnome.pickelhaube.tr064.CertificateInfo
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.Phonebook
import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.PhonebookNumber
import org.rustygnome.pickelhaube.tr064.RecentCall
import org.rustygnome.pickelhaube.tr064.SecondFactorChallenge
import org.rustygnome.pickelhaube.tr064.Tr064Exception
import org.rustygnome.pickelhaube.tr064.UntrustedCertificateException
import org.rustygnome.pickelhaube.tr064.UserRights
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The box presented a certificate that needs the user's decision. */
data class CertificatePrompt(
    val certificate: CertificateInfo,
    /** A different certificate had been trusted before – the certificate changed. */
    val changed: Boolean,
)

/** The tabs of the number picker, in their order. */
enum class PickerTab { RECENT, SPEED_DIALS, PHONEBOOKS }

/** What the number tapped next in the phone books becomes. */
sealed interface SpeedDialAssignment {
    /** This phone's speed dial [slot] (1–9). */
    data class Phone(val slot: String) : SpeedDialAssignment

    /** The box' speed dial [slot] (00–99); null: a new one, its slot still to be chosen. */
    data class Box(val slot: String?) : SpeedDialAssignment
}

data class UiState(
    val number: String = "",
    /** Who the number is, when it came from a speed dial ("Speed dial 2: Office"); shown as the field's label. */
    val numberLabel: UiText? = null,
    val phones: List<String> = emptyList(),
    val selectedPhone: String? = null,
    /** Progress / success information (not an error). */
    val status: UiText? = null,
    /** The last failure, shown prominently until dismissed or the next action starts. */
    val error: BoxError? = null,
    /** The failed action can be retried as-is. */
    val canRetry: Boolean = false,
    val busy: Boolean = false,
    /**
     * A dial-help call was started and may still be ringing / being set up, so it can be
     * cancelled. TR-064 does not report the call's progress: this ends on hang-up, when the box'
     * call monitor reports the call connected or ended – or, without it, after [DIAL_ACTIVE_MILLIS].
     */
    val dialActive: Boolean = false,
    /** How the call placed last is going; null: nothing to show. */
    val callProgress: CallProgress? = null,
    /** Optional feature, switched in the settings: follow the calls placed on the box' call monitor. */
    val callStatusEnabled: Boolean = true,
    /** The settings are asking the boxes whether their call monitor is on. */
    val checkingCallMonitors: Boolean = false,
    /** Connection in use – or, while the connection page is open, the one being edited. */
    val config: FritzConfig = FritzConfig(),
    /** All configured FritzBoxes; [activeProfileId] is the one in use. */
    val profiles: List<BoxProfile> = emptyList(),
    val activeProfileId: String? = null,
    /** Connection page: the box being edited, or null when adding a new one. */
    val editingProfileId: String? = null,
    val editingName: String = "",
    /** Connection page: the users of the box at the entered address, to choose from (empty = none known). */
    val boxUsers: List<BoxUser> = emptyList(),
    /** Connection page: the HTTPS port the box at the entered host reported (null = not known). */
    val securityPort: SecurityPort? = null,
    /**
     * Whether the box in use answered the last detection in the current network (null = not
     * detected yet), and whether the last request to it worked (null = none since it became active).
     */
    val activeBoxSeen: Boolean? = null,
    val activeBoxWorking: Boolean? = null,
    /** The configured boxes (ids) that answered the last detection by their device id; null before the first one. */
    val boxesSeen: Set<String>? = null,
    /** Note about the current network, e.g. an unknown FritzBox was found there. */
    val networkNotice: UiText? = null,
    val showSettings: Boolean = false,
    /** The connection sub-page of the settings is open. */
    val showConnectionSettings: Boolean = false,
    /** A working configuration has been saved before, so the settings screen may be left. */
    val settingsSaved: Boolean = false,
    /** Non-null while the "trust this certificate?" dialog is shown. */
    val certificatePrompt: CertificatePrompt? = null,
    /** Non-null while the trusted certificate is shown read-only. */
    val viewedCertificate: CertificateInfo? = null,
    /** The language screen is open. */
    val showLanguage: Boolean = false,
    /** The call deflections screen is open. */
    val showDeflections: Boolean = false,
    /** The call deflections of the box in use, as last read (null: not read, or reading failed). */
    val deflections: List<CallDeflection>? = null,
    /** The call deflection the quick settings tile switches for the box in use, as chosen (null: not chosen). */
    val tileDeflection: Int? = null,
    /** The number picker (tabs "Recent", "Speed dial" and "Phone books") is open. */
    val showPicker: Boolean = false,
    val pickerTab: PickerTab = PickerTab.RECENT,
    /** Choosing the number for a speed dial in the phone books tab. */
    val assigningSpeedDial: SpeedDialAssignment? = null,
    /** Phone books of the box, loaded on first use of the number picker (cached). */
    val phonebooks: List<Phonebook> = emptyList(),
    val phonebooksLoaded: Boolean = false,
    /** Optional feature, switched in the settings: include the phone's own contacts. */
    val deviceContactsEnabled: Boolean = false,
    /**
     * Ask (when opening the phone books) whether to include the phone's contacts: every time,
     * as long as the feature is off and the user did not choose "Don't ask again".
     */
    val askForDeviceContacts: Boolean = false,
    /** Contacts stored on this device, once enabled and allowed (null = not loaded). */
    val deviceContacts: Phonebook? = null,
    /** Non-null while the box waits for a second-factor confirmation (error 866). */
    val secondFactor: SecondFactorChallenge? = null,
    /** Dialled, received and missed calls from the box' call list, loaded whenever the picker is opened. */
    val boxCalls: List<RecentCall> = emptyList(),
    val boxCallsLoaded: Boolean = false,
    /** Optional feature, switched in the settings: include the phone's own calls. */
    val callLogEnabled: Boolean = false,
    /** Dialled, received and missed calls from the phone's call log, once enabled (null = not loaded). */
    val phoneCalls: List<RecentCall>? = null,
    /** Why the phone's call log could not be read (on enabling the feature, or later). */
    val callLogProblem: UiText? = null,
    /** Optional feature, switched in the settings: this phone's own speed dials 1–9. */
    val phoneSpeedDialsEnabled: Boolean = true,
    val phoneSpeedDials: List<SpeedDial> = emptyList(),
    /** A settings file opened for import, waiting to be confirmed (and unlocked). */
    val pendingImport: SettingsFileInfo? = null,
    /** Why the opened settings file could not be imported (e.g. wrong passphrase). */
    val importError: UiText? = null,
    /** A settings file is being written or read. */
    val transferBusy: Boolean = false,
)

/** The FritzBox in use, if any. */
val UiState.activeProfile: BoxProfile?
    get() = profiles.firstOrNull { it.id == activeProfileId }

/** This phone's speed dials, if switched on. */
val UiState.usablePhoneSpeedDials: List<SpeedDial>
    get() = if (phoneSpeedDialsEnabled) phoneSpeedDials else emptyList()

/** The speed dials of the box in use (from its phone books, once loaded), if switched on for it. */
val UiState.usableBoxSpeedDials: List<SpeedDial>
    get() = if (activeProfile?.speedDialsEnabled != false) SpeedDials.fromPhonebooks(phonebooks) else emptyList()

/** Why the box in use does not meet the expectations, if it does not; a failed request weighs more. */
val UiState.activeBoxIssue: ActiveBoxIssue?
    get() = when {
        activeBoxWorking == false -> ActiveBoxIssue.NOT_WORKING
        activeBoxSeen == false -> ActiveBoxIssue.NOT_FOUND
        else -> null
    }

enum class ActiveBoxIssue { NOT_FOUND, NOT_WORKING }

/** The HTTPS port the box at [host] reported. */
data class SecurityPort(val host: String, val port: Int)

/**
 * The last detection found none of the configured boxes (e.g. on mobile data, or via VPN): the box
 * in use is only tried, and only now can one be chosen by hand.
 */
val UiState.noKnownBoxFound: Boolean
    get() = boxesSeen?.isEmpty() == true

/**
 * Not even the box in use answered at its configured host: nothing is loaded from it on its own
 * (each request would only run into a timeout), and dialling is not possible.
 */
val UiState.noBoxReachable: Boolean
    get() = noKnownBoxFound && activeBoxSeen != true

/** The box answering at the configured address is not the stored one: its serial number differs. */
class OtherBoxException(val host: String, val boxName: String) :
    IOException("The FRITZ!Box at $host is not $boxName")

/** How long to wait for the user to confirm on the box before giving up. */
private const val SECOND_FACTOR_TIMEOUT_MILLIS = 120_000L

/** The user did not confirm on the box in time. */
class SecondFactorNotConfirmedException : IOException("The action was not confirmed on the FRITZ!Box")

/** Second detection after one that found none of the configured boxes. */
private const val DETECT_RETRY_MILLIS = 4_000L

/** Bigger than any settings file (they take a few kilobytes): such a file is not read. */
private const val MAX_SETTINGS_FILE = 1_000_000

/** Coming to the foreground detects again only if the last detection is at least this old. */
private const val DETECT_MIN_INTERVAL_MILLIS = 10_000L

/** How long a started dial-help call counts as "cancellable" (the handset rings about this long). */
private const val DIAL_ACTIVE_MILLIS = 30_000L

/** How long the box rings the phone for dial help before giving up (a little longer, to be safe). */
private const val PICK_UP_TIMEOUT_MILLIS = 45_000L

/** How long the end of a call stays shown. */
private const val CALL_END_SHOWN_MILLIS = 20_000L

/** Answers to the "include this phone's contacts?" question. */
enum class DeviceContactsAnswer { ALLOWED, NOT_NOW, NEVER }

private typealias BoxAction = suspend (FritzDialer) -> UiText

class DialerViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)

    private val _state = MutableStateFlow(
        UiState(
            config = settings.activeProfile()?.config ?: FritzConfig(),
            profiles = settings.profiles(),
            activeProfileId = settings.activeProfileId,
            showSettings = !settings.isConfigured(),
            showConnectionSettings = !settings.isConfigured(),
            settingsSaved = settings.isConfigured(),
            deviceContactsEnabled = settings.deviceContactsEnabled,
            callLogEnabled = settings.callLogEnabled,
            phoneSpeedDialsEnabled = settings.phoneSpeedDialsEnabled,
            phoneSpeedDials = settings.phoneSpeedDials,
            callStatusEnabled = settings.callStatusEnabled,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Action interrupted by an untrusted certificate; re-run once the user trusts it. */
    private var pendingAction: Pair<UiText, BoxAction>? = null

    /** Ends [UiState.dialActive] after [DIAL_ACTIVE_MILLIS]. */
    private var dialTimeout: Job? = null

    /** The last action that failed, for "Retry". */
    private var failedAction: Pair<UiText, BoxAction>? = null

    /**
     * True while settings entered on the settings screen are being verified. The edited
     * config is only persisted once the router accepted it.
     */
    private var verifyingSettings = false

    // --- number input ---------------------------------------------------------------

    fun onNumberChange(value: String) = _state.update { it.copy(number = value, numberLabel = null) }

    fun appendDigit(c: Char) = _state.update { it.copy(number = it.number + c, numberLabel = null) }

    fun backspace() = _state.update { it.copy(number = it.number.dropLast(1), numberLabel = null) }

    /** Called with the number from a tel: link (ACTION_DIAL / ACTION_VIEW). */
    fun prefill(number: String) = _state.update { it.copy(number = number, numberLabel = null) }

    /**
     * Long press on a digit of the dial pad: a speed dial if there is one for it (see
     * [SpeedDials.forLongPress]); otherwise like a tap – except "0", which gives "+".
     */
    fun longPressDigit(digit: Char) {
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
        )
    }

    fun dismissError() {
        failedAction = null
        _state.update { it.copy(error = null, canRetry = false) }
    }

    fun retry() {
        val action = failedAction ?: return
        dismissError()
        runOnBox(action.first, action.second)
    }

    /** From the error dialog: go to the settings of the box in use to fix the connection. */
    fun openSettingsFromError() {
        dismissError()
        val active = settings.activeProfile()
        if (active != null) {
            _state.update { it.copy(showSettings = true) }
            editProfile(active.id)
        } else {
            openSettings()
        }
    }

    // --- settings ---------------------------------------------------------------------

    /** Opens the settings; without any box configured directly on the "add" page. */
    fun openSettings() {
        _state.update { it.copy(showSettings = true) }
        if (!settings.isConfigured()) addProfile()
    }

    /** Connection page for a new FritzBox. */
    fun addProfile() = _state.update {
        it.copy(
            showConnectionSettings = true,
            editingProfileId = null,
            editingName = "",
            config = FritzConfig(),
            boxUsers = emptyList(),
            securityPort = null,
        )
    }

    /** Connection page for an existing FritzBox. */
    fun editProfile(id: String) {
        val profile = settings.profiles().firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                showConnectionSettings = true,
                editingProfileId = id,
                editingName = profile.name,
                config = profile.config,
                boxUsers = emptyList(),
                securityPort = null,
            )
        }
        refreshUserRights(profile)
    }

    private var rightsJob: Job? = null

    /**
     * Asks the box again what [profile]'s user may do, so the connection page shows the rights as
     * they are now – they may have been changed on the box since they were last read.
     */
    private fun refreshUserRights(profile: BoxProfile) {
        rightsJob?.cancel()
        rightsJob = viewModelScope.launch {
            val rights = withContext(Dispatchers.IO) { readUserRights(FritzDialer(profile.config)) } ?: return@launch
            settings.updateUserRights(profile.id, rights)
            _state.update { it.copy(profiles = settings.profiles()) }
        }
    }

    private var usersJob: Job? = null

    /**
     * Asks the box at the address entered on the connection page ([edited]: host, port, HTTPS) for
     * its users. Only a suggestion, so a failure – e.g. an older FRITZ!OS, or no box there – just
     * leaves the list empty.
     */
    fun lookUpBoxUsers(edited: FritzConfig) {
        usersJob?.cancel()
        _state.update { it.copy(boxUsers = emptyList()) }
        val config = edited.copy(trustedCertDer = _state.value.config.trustedCertDer)
        usersJob = viewModelScope.launch {
            val users = withContext(Dispatchers.IO) { runCatching { BoxUsers.list(config) }.getOrNull() }
            if (users != null && _state.value.showConnectionSettings) _state.update { it.copy(boxUsers = users) }
        }
    }

    private var securityPortJob: Job? = null

    /** Asks the box at [host] (entered on the connection page) on which port it talks HTTPS. */
    fun lookUpSecurityPort(host: String) {
        securityPortJob?.cancel()
        securityPortJob = viewModelScope.launch {
            val port = withContext(Dispatchers.IO) { BoxDiscovery.securityPort(host) } ?: return@launch
            if (_state.value.showConnectionSettings) _state.update { it.copy(securityPort = SecurityPort(host, port)) }
        }
    }

    /** Leaves the connection page without saving: back to the connection in use. */
    fun closeConnectionSettings() {
        verifyingSettings = false
        _state.update {
            it.copy(
                showConnectionSettings = false,
                editingProfileId = null,
                config = activeConfig(),
                error = null,
            )
        }
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

    /**
     * Tries the edited connection against the router. Only if that works it is saved – and made
     * the active one, since the box evidently is reachable from here; otherwise the error is shown.
     * [phonebooksEnabled], [callListEnabled], [speedDialsEnabled]: whether this box' phone books, call
     * list and speed dials are used.
     */
    fun saveSettings(
        edited: FritzConfig,
        name: String,
        phonebooksEnabled: Boolean,
        callListEnabled: Boolean,
        speedDialsEnabled: Boolean,
    ) {
        val config = edited.copy(trustedCertDer = _state.value.config.trustedCertDer)
        val editingId = _state.value.editingProfileId
        verifyingSettings = true
        _state.update { it.copy(config = config, error = null) }
        runOnBox(uiText(R.string.status_connecting, config.host)) { dialer ->
            val message = loadPhones(dialer)
            // Learn the box' device id, so it can be recognised automatically later.
            val identity = BoxDiscovery.identify(config.host)
            val info = readBoxInfo(dialer)
            val codes = readDialingCodes(dialer)
            val rights = readUserRights(dialer)
            val previous = settings.profiles().firstOrNull { it.id == editingId }
            val profile = BoxProfile(
                id = editingId ?: java.util.UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { identity?.friendlyName ?: previous?.name ?: config.host },
                config = _state.value.config,
                deviceId = identity?.udn ?: previous?.deviceId,
                model = info?.model ?: identity?.modelName ?: previous?.model,
                // Without the rights for GetInfo, the public device description tells them as well.
                softwareVersion = info?.softwareVersion ?: identity?.softwareVersion ?: previous?.softwareVersion,
                serialNumber = info?.serialNumber ?: identity?.serialNumber ?: previous?.serialNumber,
                dialingCodes = codes ?: previous?.dialingCodes,
                // Rights read before belong to the user of then.
                userRights = rights ?: previous?.userRights?.takeIf { previous.config.username == config.username },
                phonebooksEnabled = phonebooksEnabled,
                // Without the right the call list could only fail: off, the connection page says why.
                callListEnabled = callListEnabled && rights?.phone != false,
                speedDialsEnabled = speedDialsEnabled,
            )
            settings.saveProfile(profile)
            settings.activeProfileId = profile.id
            verifyingSettings = false
            // First setup: straight to the dialer. Later changes: back to the settings overview.
            _state.update {
                it.copy(
                    profiles = settings.profiles(),
                    activeProfileId = profile.id,
                    // It just answered its device description (if not: it is not a FritzBox we can recognise).
                    activeBoxSeen = identity != null,
                    boxesSeen = if (identity != null) it.boxesSeen.orEmpty() + profile.id else it.boxesSeen,
                    editingProfileId = null,
                    networkNotice = null,
                    phonebooks = emptyList(),
                    phonebooksLoaded = false,
                    boxCalls = emptyList(),
                    boxCallsLoaded = false,
                    deflections = null,
                    showSettings = it.settingsSaved,
                    showConnectionSettings = false,
                    settingsSaved = true,
                )
            }
            message
        }
        preloadBoxSpeedDials()
        preloadDeflections()
    }

    /** Switches to another configured FritzBox (manually, or after automatic recognition). */
    fun activateProfile(id: String) {
        val profile = settings.profiles().firstOrNull { it.id == id } ?: return
        settings.activeProfileId = id
        _state.update {
            it.copy(
                activeProfileId = id,
                // Chosen by hand, it may well not be the box of this network.
                activeBoxSeen = seenInNetwork(profile),
                activeBoxWorking = null,
                config = if (it.showConnectionSettings) it.config else profile.config,
                phones = emptyList(),
                selectedPhone = null,
                phonebooks = emptyList(),
                phonebooksLoaded = false,
                boxCalls = emptyList(),
                boxCallsLoaded = false,
                deflections = null,
                networkNotice = null,
            )
        }
        refreshPhones()
    }

    fun deleteProfile(id: String) {
        val wasActive = settings.activeProfileId == id
        settings.deleteProfile(id)
        val remaining = settings.profiles()
        _state.update {
            it.copy(
                profiles = remaining,
                activeProfileId = settings.activeProfileId,
                boxesSeen = it.boxesSeen?.minus(id),
                settingsSaved = remaining.isNotEmpty(),
                showConnectionSettings = if (it.editingProfileId == id) false else it.showConnectionSettings,
                editingProfileId = if (it.editingProfileId == id) null else it.editingProfileId,
            )
        }
        when {
            remaining.isEmpty() -> {
                _state.update { it.copy(phones = emptyList(), selectedPhone = null, config = FritzConfig(), deflections = null) }
                addProfile()
            }
            wasActive -> activateProfile(remaining.first().id)
        }
    }

    private fun activeConfig(): FritzConfig = settings.activeProfile()?.config ?: FritzConfig()

    // --- recognising the FritzBox of the current network -------------------------------

    private var detectJob: Job? = null

    /** When the last detection started ([SystemClock.elapsedRealtime]), to skip needless repeats. */
    private var lastDetection = 0L

    /** What a detection found: the configured box of this network, if any, and whether it switched to it. */
    private data class Detection(val match: BoxProfile?, val switched: Boolean)

    /**
     * Looks for the configured boxes in the current network (see [BoxRecognition.recognise]) and
     * switches to the one found. A connection without device id learns it only once logged in (see
     * verifyAndLearn).
     */
    private suspend fun detectBox(): Detection {
        lastDetection = SystemClock.elapsedRealtime()
        val profiles = settings.profiles()
        if (profiles.isEmpty()) return Detection(null, false)
        val recognition = BoxRecognition.recognise(getApplication(), profiles)
        val found = recognition.found
        val gateway = recognition.gateway
        val match = recognition.match
        lastFound = found
        val unknown = if (match == null) found.firstOrNull { it.host == gateway } else null
        _state.update {
            it.copy(
                profiles = profiles,
                activeBoxSeen = profiles.firstOrNull { p -> p.id == settings.activeProfileId }?.let(::seenInNetwork),
                // By device id only: a connection without one is not known to be the box at its host.
                boxesSeen = profiles.filter { p -> found.any { box -> box.udn == p.deviceId } }.map { p -> p.id }.toSet(),
                // A request that failed in the previous network says nothing about this one.
                activeBoxWorking = null,
                networkNotice = unknown?.let { box ->
                    (box.friendlyName ?: box.modelName)?.let { uiText(R.string.notice_unknown_box, it) }
                        ?: uiText(R.string.notice_unknown_box_generic)
                },
            )
        }
        if (match != null && match.id != settings.activeProfileId) {
            activateProfile(match.id)
            return Detection(match, true)
        }
        return Detection(match, false)
    }

    /** Right after a network change the box often does not answer yet: one more try a little later. */
    private suspend fun detectAgainIfNotFound(detection: Detection) {
        if (detection.match != null) return
        delay(DETECT_RETRY_MILLIS)
        detectBox()
    }

    /**
     * The app came to the foreground: the network may have changed meanwhile without the callback
     * being delivered, so detect again – unless that just happened.
     */
    fun onForeground() {
        if (!settings.isConfigured()) return
        // Possibly switched meanwhile – from the quick settings tile, or on the box.
        if (_state.value.deflections != null) preloadDeflections()
        if (SystemClock.elapsedRealtime() - lastDetection < DETECT_MIN_INTERVAL_MILLIS) return
        if (detectJob?.isActive == true) return
        detectJob = viewModelScope.launch { detectAgainIfNotFound(detectBox()) }
    }

    /** The boxes that answered the last detection; null before the first one. */
    private var lastFound: List<BoxIdentity>? = null

    /**
     * Whether [profile]'s box answered the last detection – by its device id, or by its host for a
     * connection whose device id is not known yet. Null before the first detection.
     */
    private fun seenInNetwork(profile: BoxProfile): Boolean? = lastFound?.any { box ->
        if (profile.deviceId != null) box.udn == profile.deviceId
        else box.host.equals(profile.config.host, ignoreCase = true)
    }

    /** Re-detects the box whenever the phone's network changes (e.g. Wi-Fi at home vs. office). */
    private fun watchNetwork() {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = scheduleDetect()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                scheduleDetect()
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        networkCallback = callback
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun scheduleDetect() {
        if (!settings.isConfigured()) return
        detectJob?.cancel()
        detectJob = viewModelScope.launch {
            delay(1_500) // let the network settle (DHCP, routes)
            detectAgainIfNotFound(detectBox())
        }
    }

    override fun onCleared() {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        networkCallback?.let { cb -> runCatching { cm?.unregisterNetworkCallback(cb) } }
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
        action?.let { (progress, block) -> runOnBox(progress, block) }
    }

    fun rejectCertificate() {
        failedAction = pendingAction
        pendingAction = null
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
    fun openPicker() {
        _state.update {
            it.copy(
                showPicker = true,
                pickerTab = PickerTab.RECENT,
                assigningSpeedDial = null,
                askForDeviceContacts = !it.deviceContactsEnabled &&
                    !settings.deviceContactsNeverAsk,
            )
        }
        // Without a reachable box only the phone's own sources; "reload" still tries the box.
        if (_state.value.noBoxReachable) loadCallLog() else reloadRecents()
        if (!_state.value.phonebooksLoaded && !_state.value.noBoxReachable) reloadRouterPhonebooks()
        if (_state.value.deviceContacts == null) loadDeviceContacts()
    }

    fun closePicker() = _state.update { it.copy(showPicker = false, assigningSpeedDial = null) }

    fun selectPickerTab(tab: PickerTab) = _state.update { it.copy(pickerTab = tab, assigningSpeedDial = null) }

    fun openLanguage() = _state.update { it.copy(showLanguage = true) }

    // --- settings export and import ---------------------------------------------------

    /** Writes the settings to [uri]; with [passphrase] the passwords too, encrypted with it. */
    fun exportSettings(uri: Uri, passphrase: CharArray?) {
        val app = getApplication<Application>()
        _state.update { it.copy(transferBusy = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = SettingsFile.write(settings.backup(AppLanguage.current(app)), passphrase)
                    val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Not writable: $uri")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }
            passphrase?.fill('\u0000')
            _state.update { it.copy(transferBusy = false) }
            toast(if (result.isSuccess) R.string.settings_exported else R.string.settings_export_failed)
        }
    }

    /** The text of the settings file waiting for [importSettings]. */
    private var importText: String? = null

    /** Opens the settings file at [uri] for import: says what it holds, so the import can be confirmed. */
    fun openSettingsFile(uri: Uri) {
        val app = getApplication<Application>()
        _state.update { it.copy(transferBusy = true) }
        viewModelScope.launch {
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
            _state.update { it.copy(transferBusy = false, pendingImport = info, importError = null) }
            if (info == null) toast(R.string.settings_file_invalid)
        }
    }

    /**
     * Imports the opened settings file, replacing all connections and options; with [passphrase]
     * its passwords as well. [onLanguage] then gets the file's language, for the activity to switch.
     */
    fun importSettings(passphrase: CharArray?, onLanguage: (String?) -> Unit) {
        val text = importText ?: return
        _state.update { it.copy(transferBusy = true, importError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { SettingsFile.read(text, passphrase) } }
            passphrase?.fill('\u0000')
            val backup = result.getOrElse { error ->
                val message = if (error is WrongPassphraseException) R.string.settings_wrong_passphrase else R.string.settings_file_invalid
                _state.update { it.copy(transferBusy = false, importError = uiText(message)) }
                return@launch
            }
            importText = null
            applyImport(backup)
            _state.update { it.copy(transferBusy = false, pendingImport = null) }
            toast(R.string.settings_imported)
            onLanguage(backup.options.language)
        }
    }

    fun cancelImport() {
        importText = null
        _state.update { it.copy(pendingImport = null, importError = null) }
    }

    /**
     * Stores [backup] and starts over with it, as on a fresh start. The phone's contacts and call
     * log stay off unless Android already grants reading them here.
     */
    private fun applyImport(backup: SettingsBackup) {
        val app = getApplication<Application>()
        fun granted(permission: String) = ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
        val options = backup.options.copy(
            deviceContactsEnabled = backup.options.deviceContactsEnabled && granted(Manifest.permission.READ_CONTACTS),
            callLogEnabled = backup.options.callLogEnabled && granted(Manifest.permission.READ_CALL_LOG),
        )
        settings.restore(backup.copy(options = options))
        _state.update {
            it.copy(
                config = settings.activeProfile()?.config ?: FritzConfig(),
                profiles = settings.profiles(),
                activeProfileId = settings.activeProfileId,
                settingsSaved = settings.isConfigured(),
                // Imported instead of entering the first box: on to the settings with the boxes now there.
                showConnectionSettings = it.showConnectionSettings && !settings.isConfigured(),
                editingProfileId = if (settings.isConfigured()) null else it.editingProfileId,
                activeBoxSeen = null,
                activeBoxWorking = null,
                boxesSeen = null,
                networkNotice = null,
                phones = emptyList(),
                selectedPhone = null,
                phonebooks = emptyList(),
                phonebooksLoaded = false,
                boxCalls = emptyList(),
                boxCallsLoaded = false,
                deflections = null,
                deviceContactsEnabled = options.deviceContactsEnabled,
                deviceContacts = null,
                callLogEnabled = options.callLogEnabled,
                phoneCalls = null,
                callLogProblem = null,
                phoneSpeedDialsEnabled = options.phoneSpeedDialsEnabled,
                phoneSpeedDials = settings.phoneSpeedDials,
                callStatusEnabled = options.callStatusEnabled,
            )
        }
        if (options.deviceContactsEnabled) loadDeviceContacts()
        if (options.callLogEnabled) loadCallLog()
        DeflectionTileService.refresh(app)
        RedialTileService.refresh(app)
        if (settings.isConfigured()) {
            viewModelScope.launch {
                val detection = detectBox()
                if (!detection.switched && !_state.value.noBoxReachable) refreshPhones()
                detectAgainIfNotFound(detection)
            }
        }
    }

    private fun toast(message: Int) = Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()

    fun closeLanguage() = _state.update { it.copy(showLanguage = false) }

    // --- call deflections -------------------------------------------------------------

    /** Opens the call deflections of the box in use, read anew. */
    fun openDeflections() {
        _state.update {
            it.copy(showDeflections = true, tileDeflection = settings.activeProfileId?.let(settings::tileDeflection))
        }
        loadDeflections()
    }

    /** Makes call deflection [id] the one the quick settings tile switches, for the box in use. */
    fun chooseTileDeflection(id: Int) {
        val profileId = settings.activeProfileId ?: return
        settings.setTileDeflection(profileId, id)
        _state.update { it.copy(tileDeflection = id) }
        DeflectionTileService.refresh(getApplication())
    }

    /**
     * Reads the call deflections of the box in use in the background, so the dialer can show whether
     * one is switched on. Quietly: a box whose user may not read them just shows none.
     */
    private fun preloadDeflections() = runWhenIdle("deflections") {
        if (_state.value.noBoxReachable) return@runWhenIdle
        val config = _state.value.config
        viewModelScope.launch {
            val deflections = withContext(Dispatchers.IO) { runCatching { FritzDialer(config).loadDeflections() }.getOrNull() }
            // Unless the box in use changed meanwhile.
            if (deflections != null && _state.value.config == config) _state.update { it.copy(deflections = deflections) }
        }
    }

    fun closeDeflections() = _state.update { it.copy(showDeflections = false) }

    fun loadDeflections() = runOnBox(uiText(R.string.status_loading_deflections)) { dialer ->
        val deflections = dialer.loadDeflections()
        _state.update { it.copy(deflections = deflections) }
        uiText(R.string.status_deflections_loaded)
    }

    /** Switches call deflection [id] on or off on the box, then reads them anew to show what the box did. */
    fun setDeflectionEnabled(id: Int, enabled: Boolean) =
        runOnBox(uiText(if (enabled) R.string.status_deflection_on else R.string.status_deflection_off)) { dialer ->
            dialer.setDeflectionEnabled(id, enabled)
            val deflections = dialer.loadDeflections()
            _state.update { it.copy(deflections = deflections) }
            DeflectionTileService.refresh(getApplication())
            uiText(if (enabled) R.string.status_deflection_on_done else R.string.status_deflection_off_done)
        }

    /** The picker's reload button: recent calls and phone books. */
    fun reloadPicker() {
        reloadRecents()
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
            if (book != null) _state.update { it.copy(deviceContacts = book) }
        }
    }

    /**
     * Switches the optional "this phone's contacts" feature. The settings screen only enables it
     * once Android granted the contacts permission. Disabling stops reading the contacts at once
     * (the permission itself can only be revoked in the Android settings).
     */
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
            UiText.Plural(R.plurals.status_contacts_loaded, books.sumOf { it.contacts.size })
        }
    }

    /** Hands [contact] over to the box' phone book [book] as a new entry, then reads the phone books anew. */
    fun handOverToBox(book: Phonebook, contact: PhonebookContact) =
        runOnBox(uiText(R.string.status_handing_over, contact.name)) { dialer ->
            dialer.addToPhonebook(book.id, contact)
            val books = dialer.loadPhonebooks()
            _state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
            uiText(R.string.status_handed_over, contact.name, book.name)
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
    fun pickNumber(number: String) =
        _state.update { it.copy(number = number, numberLabel = null, showPicker = false) }

    /** A contact's number was tapped in the phone books: for the dialer, or for the speed dial being set up. */
    fun pickContactNumber(name: String, number: String) {
        when (val assignment = _state.value.assigningSpeedDial) {
            null -> pickNumber(number)
            is SpeedDialAssignment.Phone -> {
                setPhoneSpeedDial(assignment.slot, name, number)
                _state.update { it.copy(assigningSpeedDial = null, pickerTab = PickerTab.SPEED_DIALS) }
            }
            // The phone book tab asks what is still open, then calls setBoxSpeedDial.
            is SpeedDialAssignment.Box -> Unit
        }
    }

    // --- speed dials ------------------------------------------------------------------

    /** Opens the phone books to choose the number for this phone's speed dial [slot]. */
    fun startAssigningSpeedDial(slot: String) =
        _state.update { it.copy(assigningSpeedDial = SpeedDialAssignment.Phone(slot), pickerTab = PickerTab.PHONEBOOKS) }

    /** Opens the phone books to choose the number for the box' speed dial [slot] (null: a new one). */
    fun startAssigningBoxSpeedDial(slot: String?) =
        _state.update { it.copy(assigningSpeedDial = SpeedDialAssignment.Box(slot), pickerTab = PickerTab.PHONEBOOKS) }

    fun cancelAssigningSpeedDial() =
        _state.update { it.copy(assigningSpeedDial = null, pickerTab = PickerTab.SPEED_DIALS) }

    /** Sets (or replaces) this phone's speed dial [slot]. */
    fun setPhoneSpeedDial(slot: String, name: String, number: String) {
        settings.phoneSpeedDials = settings.phoneSpeedDials.filterNot { it.slot == slot } + SpeedDial(slot, name, number)
        _state.update { it.copy(phoneSpeedDials = settings.phoneSpeedDials) }
    }

    fun removePhoneSpeedDial(slot: String) {
        settings.phoneSpeedDials = settings.phoneSpeedDials.filterNot { it.slot == slot }
        _state.update { it.copy(phoneSpeedDials = settings.phoneSpeedDials) }
    }

    /**
     * Makes [number] the box' speed dial [slot]: in the entry of the box' phone books that has the
     * number – or, if none of its own (not online) ones has, in a new entry for [name] in phone book
     * [newEntryBook] (null: the first of its own). Wherever else the slot was used, it is taken away. Then the phone books are read anew.
     */
    fun setBoxSpeedDial(slot: String, name: String, number: String, type: String, newEntryBook: Int?) {
        val assigning = _state.value.assigningSpeedDial is SpeedDialAssignment.Box
        runOnBox(uiText(R.string.status_setting_speed_dial, slot)) { dialer ->
            val key = numberKeys()
            // Only the box' own phone books can be changed, not the online ones it syncs.
            val localBooks = _state.value.phonebooks.filterNot { it.online }
            val contacts = localBooks.flatMap { it.contacts }.filter { it.bookId != null && it.uid != null }
            val target = contacts.firstOrNull { c -> c.numbers.any { key(it.number) == key(number) } }
            contacts.filter { c -> c != target && c.numbers.any { it.speedDial == slot } }
                .forEach { dialer.setSpeedDial(it.bookId!!, it.uid!!, slot, null) }
            if (target != null) {
                val written = target.numbers.first { key(it.number) == key(number) }.number
                dialer.setSpeedDial(target.bookId!!, target.uid!!, slot, written)
            } else {
                val book = newEntryBook ?: localBooks.first().id
                dialer.addToPhonebook(book, PhonebookContact(name, listOf(PhonebookNumber(number, type, slot)), ""))
            }
            val books = dialer.loadPhonebooks()
            _state.update {
                it.copy(
                    phonebooks = books,
                    phonebooksLoaded = true,
                    assigningSpeedDial = if (assigning) null else it.assigningSpeedDial,
                    pickerTab = if (assigning) PickerTab.SPEED_DIALS else it.pickerTab,
                )
            }
            uiText(R.string.status_speed_dial_set, slot, target?.name ?: name)
        }
    }

    /** Deletes [copy], an entry copying a contact of an online phone book, from the box; then reads the phone books anew. */
    fun deleteOnlineCopy(copy: OnlineCopy) = runOnBox(uiText(R.string.status_deleting_copy, copy.entry.name)) { dialer ->
        dialer.deletePhonebookEntry(copy.entry.bookId!!, copy.entry.uid!!)
        val books = dialer.loadPhonebooks()
        _state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
        uiText(R.string.status_copy_deleted, copy.entry.name)
    }

    /** Takes the box' speed dial [slot] away from the numbers that have it; then reads the phone books anew. */
    fun removeBoxSpeedDial(slot: String) = runOnBox(uiText(R.string.status_removing_speed_dial, slot)) { dialer ->
        _state.value.phonebooks.filterNot { it.online }.flatMap { it.contacts }
            .filter { c -> c.bookId != null && c.uid != null && c.numbers.any { it.speedDial == slot } }
            .forEach { dialer.setSpeedDial(it.bookId!!, it.uid!!, slot, null) }
        val books = dialer.loadPhonebooks()
        _state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
        uiText(R.string.status_speed_dial_removed, slot)
    }

    /** Compares numbers however they are written, by the rules of the box in use (see [numberKey]). */
    private fun numberKeys(): (String) -> String {
        val countryIso = deviceCountryIso(getApplication())
        val codes = _state.value.activeProfile?.dialingCodes
        return { numberKey(it, countryIso, codes) }
    }

    /** Switches this phone's speed dials on or off; switched off, they are kept, just not used. */
    fun setPhoneSpeedDialsEnabled(enabled: Boolean) {
        settings.phoneSpeedDialsEnabled = enabled
        _state.update { it.copy(phoneSpeedDialsEnabled = enabled) }
    }

    /**
     * Reads the phone books of the box in use in the background, so its speed dials work right
     * away in the dialer – not only once the picker was opened. Checked when it is its turn, as
     * the box in use may change until then.
     */
    private fun preloadBoxSpeedDials() = runWhenIdle("speedDials") {
        if (settings.activeProfile()?.speedDialsEnabled == true && !_state.value.phonebooksLoaded &&
            !_state.value.noBoxReachable
        ) {
            reloadRouterPhonebooks()
        }
    }

    // --- recent calls -----------------------------------------------------------------

    private fun reloadRecents() {
        loadCallLog()
        if (settings.activeProfile()?.callListEnabled == false) return
        if (_state.value.busy) return runWhenIdle("recents") { if (_state.value.showPicker) reloadRecents() }
        runOnBox(uiText(R.string.recents_loading)) { dialer ->
            val calls = dialer.loadRecentCalls()
            _state.update { it.copy(boxCalls = calls, boxCallsLoaded = true) }
            UiText.Plural(R.plurals.status_calls_loaded, calls.size)
        }
    }

    /** (Re)reads the phone's calls, if the feature is on. A failure is shown, not thrown. */
    fun loadCallLog() {
        if (!_state.value.callLogEnabled) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DeviceCallLog.load(app) } }
            _state.update {
                it.copy(
                    phoneCalls = result.getOrNull() ?: it.phoneCalls,
                    callLogProblem = result.exceptionOrNull()?.let(::describeCallLogProblem),
                )
            }
        }
    }

    /**
     * Turns the "this phone's calls" feature on – but only if reading the call log actually works
     * ([granted]: Android's answer to the permission request). Otherwise it stays off and the
     * settings explain why.
     */
    fun enableCallLog(granted: Boolean) {
        if (!granted) {
            _state.update {
                it.copy(callLogProblem = uiText(R.string.call_log_denied))
            }
            return
        }
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DeviceCallLog.load(app) } }
            val calls = result.getOrNull()
            settings.callLogEnabled = calls != null
            _state.update {
                it.copy(
                    callLogEnabled = calls != null,
                    phoneCalls = calls,
                    callLogProblem = result.exceptionOrNull()?.let(::describeCallLogProblem),
                )
            }
        }
    }

    fun disableCallLog() {
        settings.callLogEnabled = false
        _state.update { it.copy(callLogEnabled = false, phoneCalls = null, callLogProblem = null) }
    }

    /** No access / no call log say enough as they are; other failures add their (technical) message. */
    private fun describeCallLogProblem(error: Throwable): UiText =
        error.message?.takeIf { error !is SecurityException && error !is IllegalStateException }
            ?.let { uiText(R.string.call_log_failed, it) }
            ?: uiText(R.string.call_log_failed_plain)

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
        preloadBoxSpeedDials()
        preloadDeflections()
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

    /** Model, FRITZ!OS version and serial number: optional, so a failure (e.g. missing rights) is ignored. */
    private fun readBoxInfo(dialer: FritzDialer): BoxInfo? = runCatching { dialer.deviceInfo() }.getOrNull()

    /** Country and area code: only for comparing numbers, so a failure is ignored as well. */
    private fun readDialingCodes(dialer: FritzDialer): DialingCodes? = runCatching { dialer.dialingCodes() }.getOrNull()

    /**
     * The user's rights: for explaining refusals, so a failure is ignored as well – except a refusal
     * to tell them, which makes the ones read before unknown: none (not shown) instead of outdated.
     */
    private fun readUserRights(dialer: FritzDialer): UserRights? = try {
        dialer.userRights()
    } catch (e: Tr064Exception) {
        if (e.errorCode == Tr064Exception.NOT_AUTHORIZED) UserRights(null, emptyMap()) else null
    } catch (_: Exception) {
        null
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

    private fun startQueuedRequests() {
        while (whenIdle.isNotEmpty() && !_state.value.busy) {
            val key = whenIdle.keys.first()
            whenIdle.remove(key)?.invoke()
        }
    }

    private fun loadPhones(dialer: FritzDialer): UiText {
        val phones = dialer.listPhones()
        val current = dialer.getDialPhone()
        _state.update {
            it.copy(
                phones = phones,
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

    fun dial() {
        if (_state.value.noBoxReachable) return
        // A box speed dial entered as on a handset (**712): dial – and show – the number it stands for.
        SpeedDials.forCode(FritzDialer.sanitizeNumber(_state.value.number), _state.value.usableBoxSpeedDials)
            ?.let(::useSpeedDial)
        val number = FritzDialer.sanitizeNumber(_state.value.number)
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
        runOnBox(uiText(R.string.status_dialling, number)) { dialer ->
            // Only changes the box setting if needed: a change may require confirmation on the box.
            if (phone != null) dialer.ensureDialPhone(phone)
            markDialActive()
            // Listening before the box dials, so its report of the call is not missed.
            followCall(number, phone)
            try {
                dialer.dial(number)
            } catch (e: Exception) {
                stopFollowingCall()
                clearDialActive()
                throw e
            }
            settings.lastDialledNumber = number
            RedialTileService.refresh(getApplication())
            phone?.let { uiText(R.string.status_pick_up, FritzDialer.displayName(it), number) }
                ?: uiText(R.string.status_pick_up_any, number)
        }
    }

    /** Without the call monitor: the call counts as cancellable (and "pick up" as shown) for a while. */
    private fun markDialActive() {
        dialTimeout?.cancel()
        _state.update { it.copy(dialActive = true) }
        dialTimeout = viewModelScope.launch {
            delay(DIAL_ACTIVE_MILLIS)
            _state.update { it.copy(dialActive = false, callProgress = it.callProgress.takeUnless { p -> p is CallProgress.PickUp }) }
        }
    }

    private fun clearDialActive() {
        dialTimeout?.cancel()
        dialTimeout = null
        _state.update { it.copy(dialActive = false) }
    }

    fun hangup() = runOnBox(uiText(R.string.status_hanging_up)) { dialer ->
        dialer.hangup()
        stopFollowingCall()
        clearDialActive()
        _state.update { it.copy(callProgress = null) }
        uiText(R.string.status_call_cancelled)
    }

    // --- call status ------------------------------------------------------------------

    /** Follows the call placed last on the box' call monitor. */
    private var monitorJob: Job? = null

    /** Clears the end of a call from the dialer after [CALL_END_SHOWN_MILLIS]. */
    private var callEndJob: Job? = null

    /**
     * Shows that the box rings [phone] to dial [number], and – if the feature is on – follows the call
     * on the call monitor of the box in use: dialling once picked up (or not picked up in time), then
     * connected, ended or not answered. Only the box' report of dialling [number] counts; the other
     * calls of the household are ignored. A box with the monitor off is noted for the settings and,
     * the first time, the dialer says how to switch it on.
     */
    private fun followCall(number: String, phone: String?) {
        stopFollowingCall()
        _state.update { it.copy(callProgress = CallProgress.PickUp(phone, number)) }
        if (!_state.value.callStatusEnabled) return
        val profile = settings.activeProfile() ?: return
        val host = _state.value.config.host
        val key = numberKeys()
        monitorJob = viewModelScope.launch {
            val call = this
            var listening = false
            var connection: String? = null
            var connected = false
            val pickUpTimeout = launch {
                delay(PICK_UP_TIMEOUT_MILLIS)
                showCallEnd(CallProgress.NotPickedUp)
                call.cancel()
            }
            try {
                CallMonitor.events(host) {
                    listening = true
                    noteCallMonitor(profile.id, true)
                    // From now on the monitor says how long the call can be cancelled.
                    dialTimeout?.cancel()
                }.first { event ->
                    when {
                        connection == null -> {
                            if (event is CallEvent.Call && isDialledNumber(event.called, number, key)) {
                                connection = event.connection
                                pickUpTimeout.cancel()
                                _state.update {
                                    it.copy(callProgress = CallProgress.Dialling(number, event.caller.ifBlank { null }))
                                }
                            }
                            false
                        }
                        event.connection != connection -> false
                        event is CallEvent.Connect -> {
                            connected = true
                            clearDialActive()
                            _state.update {
                                it.copy(callProgress = CallProgress.Connected(number, SystemClock.elapsedRealtime()))
                            }
                            false
                        }
                        event is CallEvent.Disconnect -> {
                            showCallEnd(if (connected) CallProgress.Ended(event.seconds) else CallProgress.NotAnswered)
                            true
                        }
                        else -> false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                pickUpTimeout.cancel()
                if (!listening) {
                    // Refused: the monitor is off. Said once in the dialer; the settings keep saying it.
                    val knownOff = profile.callMonitor == false
                    noteCallMonitor(profile.id, false)
                    if (!knownOff) {
                        _state.update {
                            val shown = it.callProgress
                            if (shown is CallProgress.PickUp) it.copy(callProgress = shown.copy(monitorOff = true)) else it
                        }
                    }
                } else {
                    // Lost during the call (network change, box restarted): nothing more is known.
                    clearDialActive()
                    _state.update { it.copy(callProgress = null) }
                }
            }
        }
    }

    private fun stopFollowingCall() {
        monitorJob?.cancel()
        monitorJob = null
        callEndJob?.cancel()
        callEndJob = null
    }

    /** The call is over: shows how it ended for a while. */
    private fun showCallEnd(end: CallProgress) {
        clearDialActive()
        _state.update { it.copy(callProgress = end) }
        callEndJob?.cancel()
        callEndJob = viewModelScope.launch {
            delay(CALL_END_SHOWN_MILLIS)
            _state.update { if (it.callProgress == end) it.copy(callProgress = null) else it }
        }
    }

    private fun noteCallMonitor(profileId: String, available: Boolean) {
        settings.updateCallMonitor(profileId, available)
        _state.update { it.copy(profiles = settings.profiles()) }
    }

    /** Switches following the calls on the box' call monitor on or off. */
    fun setCallStatusEnabled(enabled: Boolean) {
        settings.callStatusEnabled = enabled
        if (!enabled) {
            stopFollowingCall()
            clearDialActive()
        }
        _state.update { it.copy(callStatusEnabled = enabled, callProgress = if (enabled) it.callProgress else null) }
        if (enabled) checkCallMonitors()
    }

    /**
     * Asks the boxes that answered the last detection whether their call monitor is on (the others
     * keep what was known: they could not tell).
     */
    fun checkCallMonitors() {
        if (_state.value.checkingCallMonitors) return
        val seen = _state.value.boxesSeen.orEmpty()
        val profiles = settings.profiles().filter { it.id in seen }
        if (profiles.isEmpty()) return
        _state.update { it.copy(checkingCallMonitors = true) }
        viewModelScope.launch {
            profiles.map { p -> async { p.id to CallMonitor.isAvailable(p.config.host) } }.awaitAll()
                .forEach { (id, available) -> settings.updateCallMonitor(id, available) }
            _state.update { it.copy(profiles = settings.profiles(), checkingCallMonitors = false) }
        }
    }

    private fun runOnBox(progress: UiText, block: BoxAction) {
        if (_state.value.busy) return
        failedAction = null
        _state.update { it.copy(busy = true, status = progress, error = null, canRetry = false) }
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
    }

    // Runs last: all fields above are initialised by now (Kotlin initialises in source order).
    init {
        if (settings.isConfigured()) {
            // Pick the box of the current network first; if that did not switch (and load), load now.
            viewModelScope.launch {
                val detection = detectBox()
                if (!detection.switched && !_state.value.noBoxReachable) refreshPhones()
                detectAgainIfNotFound(detection)
            }
        }
        watchNetwork()
    }
}

/** Reads up to [limit] bytes (InputStream.readNBytes only exists from Android 13 on). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
