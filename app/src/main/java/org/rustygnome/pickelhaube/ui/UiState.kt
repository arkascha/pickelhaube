package org.rustygnome.pickelhaube.ui

import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.HostCheck
import org.rustygnome.pickelhaube.data.SettingsFileInfo
import org.rustygnome.pickelhaube.data.SpeedDial
import org.rustygnome.pickelhaube.data.SpeedDials
import org.rustygnome.pickelhaube.data.WhatsNew
import org.rustygnome.pickelhaube.tr064.BoxUser
import org.rustygnome.pickelhaube.tr064.CallDeflection
import org.rustygnome.pickelhaube.tr064.CertificateInfo
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.InternalPhone
import org.rustygnome.pickelhaube.tr064.OwnNumber
import org.rustygnome.pickelhaube.tr064.Phonebook
import org.rustygnome.pickelhaube.tr064.RecentCall
import org.rustygnome.pickelhaube.tr064.SecondFactorChallenge
import org.rustygnome.pickelhaube.tr064.VoipLine
import org.rustygnome.pickelhaube.widget.WidgetList
import java.io.IOException

/** The box presented a certificate that needs the user's decision. */
data class CertificatePrompt(
    val certificate: CertificateInfo,
    /** A different certificate had been trusted before – the certificate changed. */
    val changed: Boolean,
)

/** The tabs of the number picker, in their order. */
enum class PickerTab { RECENT, SPEED_DIALS, PHONEBOOKS, INTERNAL }

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
    /** The box' phones with their internal numbers, read with [phones] (empty: none known). */
    val internalPhones: List<InternalPhone> = emptyList(),
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
    /** Missed calls in the call list of the box in use since it was last looked at in "Recent". */
    val newMissedCalls: Int = 0,
    /** The internet numbers of the box in use and whether they are registered with their provider (empty = not known). */
    val lines: List<VoipLine> = emptyList(),
    /** The own numbers of the box in use (empty = not known). */
    val ownNumbers: List<OwnNumber> = emptyList(),
    /** While the picker is open: its box calls after this time (ms) are marked as new; null: none. */
    val newCallsSince: Long? = null,
    /** The setup of a dial widget is open: which widget, and its list as being edited. */
    val widgetSetup: WidgetSetup? = null,
    /** The number picker is open to add a number to [widgetSetup]. */
    val addingToWidget: Boolean = false,
    /** Several numbers came in a shared or pasted text: the one to dial is chosen from these. */
    val numberChoice: List<String>? = null,
    /** Connection page: the HTTPS port the box at the entered host reported (null = not known). */
    val securityPort: SecurityPort? = null,
    /** Connection page: the entered host leads to another box than the one edited, or to one set up already (null: neither). */
    val hostNote: HostNote? = null,
    /** Saving a connection waits for a decision: the box at its host is not (or not only) the one edited. */
    val saveCheck: SaveCheck? = null,
    /** The versions whose "What's new" note is to be shown (newest first); null: none (see [WhatsNew]). */
    val whatsNew: List<Int>? = null,
    /** The note was asked for in the settings: shown there and then, not only over the dialer. */
    val whatsNewRequested: Boolean = false,
    /** The welcome tour is shown: on first start, or started again from the settings (see [WelcomeScreen]). */
    val showWelcome: Boolean = false,
    /** The welcome tour asked which box answers at the entered host (null: not asked yet). */
    val welcomeBox: WelcomeBox? = null,
    /** The last request failed because the box rejected user name or password (the tour offers changing them). */
    val loginRejected: Boolean = false,
    /** The error dialog of a rejected login asked the welcome tour to go back to the password. */
    val welcomeChangePassword: Boolean = false,
    /** The welcome tour connected its box: it now shows how to go on. */
    val welcomeConnected: Boolean = false,
    /** The box in use has a phone set for dial help ([selectedPhone] is the box' own choice, not the app's). */
    val dialPhoneOnBox: Boolean = true,
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
    /** Numbers of the phone books and recent calls the number being typed may be heading for (see [suggestions]). */
    val suggestions: List<Suggestion> = emptyList(),
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
    /** Switched in the settings: the keypad plays a tone for each key. */
    val keyTonesEnabled: Boolean = false,
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

/** The number of the call under way – the box rings the phone for it, dials it, or it is connected; null otherwise. */
val UiState.callUnderway: String?
    get() = when (val progress = callProgress) {
        is CallProgress.PickUp -> progress.number
        is CallProgress.Dialling -> progress.number
        is CallProgress.Connected -> progress.number
        else -> null
    }

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

/** The dial widget [id] being set up, with its [list] as edited so far. */
data class WidgetSetup(val id: Int, val list: WidgetList)

/** The HTTPS port the box at [host] reported. */
data class SecurityPort(val host: String, val port: Int)

/** What answers at the entered [host] on the connection page: [check] is [HostCheck.OtherBox] or [HostCheck.AlreadySetUp]. */
data class HostNote(val host: String, val check: HostCheck)

/** What the welcome tour found at the host entered (see [WelcomePart.find]). */
sealed interface WelcomeBox {
    val host: String

    data class Searching(override val host: String) : WelcomeBox

    /** A FRITZ!Box answers, its [name] as it calls itself and its [model]; [setUpAs]: it is set up already, under that name. */
    data class Found(override val host: String, val name: String?, val model: String?, val setUpAs: String?) : WelcomeBox

    data class NotFound(override val host: String) : WelcomeBox
}

/** Saving the connection named [name] to [host] is held up: [check] is not [HostCheck.Expected]. */
data class SaveCheck(val host: String, val name: String, val check: HostCheck)

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

/** The internet numbers of the box in use that are switched on but not registered with their provider. */
val UiState.linesDown: List<VoipLine>
    get() = if (noBoxReachable) emptyList() else lines.filter { it.down }

/**
 * The box' internet numbers a call can be placed from by their code (see [OwnNumber.selectCode]) –
 * offered only when there are several to choose from, and not one that is known to be down.
 */
val UiState.dialFromNumbers: List<OwnNumber>
    get() {
        val down = linesDown.map { it.index }.toSet()
        val selectable = ownNumbers.filter { it.selectCode != null }
        return if (selectable.size < 2) emptyList() else selectable.filter { it.index !in down }
    }

/** The box answering at the configured address is not the stored one: its serial number differs. */
class OtherBoxException(val host: String, val boxName: String) :
    IOException("The FRITZ!Box at $host is not $boxName")

/** The user did not confirm on the box in time. */
class SecondFactorNotConfirmedException : IOException("The action was not confirmed on the FRITZ!Box")

/** Answers to the "include this phone's contacts?" question. */
enum class DeviceContactsAnswer { ALLOWED, NOT_NOW, NEVER }
