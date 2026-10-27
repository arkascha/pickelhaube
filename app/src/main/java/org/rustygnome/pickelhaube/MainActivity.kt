package org.rustygnome.pickelhaube

import org.rustygnome.pickelhaube.ui.onlineCopies
import org.rustygnome.pickelhaube.ui.numberKey
import org.rustygnome.pickelhaube.ui.deviceCountryIso
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.data.SpeedDials
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts
import org.rustygnome.pickelhaube.ui.ConnectionSettingsScreen
import org.rustygnome.pickelhaube.ui.DialerScreen
import org.rustygnome.pickelhaube.ui.TrustCertificateDialog
import org.rustygnome.pickelhaube.ui.ViewCertificateDialog
import org.rustygnome.pickelhaube.ui.activeBoxIssue
import org.rustygnome.pickelhaube.ui.activeProfile
import org.rustygnome.pickelhaube.ui.noBoxReachable
import org.rustygnome.pickelhaube.ui.DialerViewModel
import org.rustygnome.pickelhaube.ui.ErrorDialog
import org.rustygnome.pickelhaube.ui.DeflectionsScreen
import org.rustygnome.pickelhaube.ui.LanguageScreen
import org.rustygnome.pickelhaube.ui.NumberPickerScreen
import org.rustygnome.pickelhaube.ui.PhonebookTab
import org.rustygnome.pickelhaube.ui.RecentsTab
import org.rustygnome.pickelhaube.ui.SettingsImportOffer
import org.rustygnome.pickelhaube.ui.SettingsScreen
import org.rustygnome.pickelhaube.ui.SecondFactorDialog
import org.rustygnome.pickelhaube.ui.SpeedDialTab
import org.rustygnome.pickelhaube.ui.theme.PickelhaubeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DialerViewModel by viewModels()

    /** Before Android 13: apply the language chosen in the settings (from then on Android does it). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            PickelhaubeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                if (state.showDeflections) {
                    BackHandler { viewModel.closeDeflections() }
                    DeflectionsScreen(
                        boxName = state.activeProfile?.name,
                        deflections = state.deflections,
                        busy = state.busy,
                        tileDeflection = state.tileDeflection,
                        onChooseTile = { viewModel.chooseTileDeflection(it.id) },
                        onToggle = { deflection, enabled -> viewModel.setDeflectionEnabled(deflection.id, enabled) },
                        onReload = viewModel::loadDeflections,
                        onBack = viewModel::closeDeflections,
                    )
                } else if (state.showLanguage && state.showSettings) {
                    BackHandler { viewModel.closeLanguage() }
                    LanguageScreen(onBack = viewModel::closeLanguage)
                } else if (state.showPicker && !state.showSettings) {
                    val routerBooksEnabled = state.activeProfile?.phonebooksEnabled ?: true
                    // The box' phone books a contact can be handed over to: only while it is there.
                    val boxBooks = if (state.phonebooksLoaded && !state.noBoxReachable) state.phonebooks else emptyList()
                    val boxSpeedDialsEnabled = state.activeProfile?.speedDialsEnabled ?: true
                    val boxSpeedDials = remember(state.phonebooks) { SpeedDials.fromPhonebooks(state.phonebooks) }
                    // The box' speed dials as far as they can be set up now.
                    val editableBoxSpeedDials = boxSpeedDials.takeIf { boxSpeedDialsEnabled && boxBooks.any { !it.online } }
                    // Entries the box' own phone books have as copies of online contacts: they may be deleted.
                    val context = LocalContext.current
                    val dialingCodes = state.activeProfile?.dialingCodes
                    val onlineCopies = remember(boxBooks, dialingCodes) {
                        val countryIso = deviceCountryIso(context)
                        onlineCopies(boxBooks) { numberKey(it, countryIso, dialingCodes) }
                    }
                    // While setting up a speed dial, "back" only cancels that.
                    BackHandler {
                        if (state.assigningSpeedDial != null) viewModel.cancelAssigningSpeedDial() else viewModel.closePicker()
                    }
                    NumberPickerScreen(
                        tab = state.pickerTab,
                        onTabChange = viewModel::selectPickerTab,
                        busy = state.busy,
                        onReload = viewModel::reloadPicker,
                        onBack = viewModel::closePicker,
                        recents = {
                            RecentsTab(
                                boxCalls = state.boxCalls,
                                boxCallsLoaded = state.boxCallsLoaded,
                                boxName = state.activeProfile?.name,
                                dialingCodes = state.activeProfile?.dialingCodes,
                                contacts = remember(state.phonebooks, routerBooksEnabled, state.deviceContacts, state.deviceContactsEnabled) {
                                    val books = (if (routerBooksEnabled) state.phonebooks else emptyList()) +
                                        listOfNotNull(state.deviceContacts?.takeIf { state.deviceContactsEnabled })
                                    books.flatMap { it.contacts }
                                },
                                boxCallsEnabled = state.activeProfile?.callListEnabled ?: true,
                                phoneCalls = state.phoneCalls,
                                callLogEnabled = state.callLogEnabled,
                                callLogProblem = state.callLogProblem,
                                busy = state.busy,
                                onPick = viewModel::pickNumber,
                                boxBooks = boxBooks,
                                onHandOver = viewModel::handOverToBox,
                                phoneSpeedDials = state.phoneSpeedDials.takeIf { state.phoneSpeedDialsEnabled },
                                onSetSpeedDial = viewModel::setPhoneSpeedDial,
                                boxSpeedDials = editableBoxSpeedDials,
                                onSetBoxSpeedDial = viewModel::setBoxSpeedDial,
                                onReloadCallLog = viewModel::loadCallLog,
                                onDisableCallLog = viewModel::disableCallLog,
                            )
                        },
                        phonebooks = {
                            PhonebookTab(
                                // Possibly read only for the box' speed dials: then not shown here.
                                routerBooks = if (routerBooksEnabled) state.phonebooks else emptyList(),
                                routerBooksEnabled = routerBooksEnabled,
                                deviceContacts = state.deviceContacts,
                                deviceContactsEnabled = state.deviceContactsEnabled,
                                dialingCodes = state.activeProfile?.dialingCodes,
                                loaded = state.phonebooksLoaded,
                                busy = state.busy,
                                onPick = viewModel::pickContactNumber,
                                phoneSpeedDials = state.phoneSpeedDials.takeIf { state.phoneSpeedDialsEnabled },
                                onSetSpeedDial = viewModel::setPhoneSpeedDial,
                                assigningSpeedDial = state.assigningSpeedDial,
                                boxSpeedDials = editableBoxSpeedDials,
                                onSetBoxSpeedDial = viewModel::setBoxSpeedDial,
                                onlineCopies = onlineCopies,
                                onDeleteCopy = viewModel::deleteOnlineCopy,
                                onCancelAssigning = viewModel::cancelAssigningSpeedDial,
                                boxBooks = boxBooks,
                                boxName = state.activeProfile?.name,
                                onHandOver = viewModel::handOverToBox,
                                onDeviceContactsAllowed = viewModel::loadDeviceContacts,
                                onDisableDeviceContacts = { viewModel.setDeviceContactsEnabled(false) },
                                askForDeviceContacts = state.askForDeviceContacts,
                                onDeviceContactsAnswer = viewModel::answerDeviceContactsQuestion,
                            )
                        },
                        speedDials = {
                            SpeedDialTab(
                                phoneSpeedDials = state.phoneSpeedDials.takeIf { state.phoneSpeedDialsEnabled },
                                boxSpeedDials = boxSpeedDials.takeIf { boxSpeedDialsEnabled },
                                boxName = state.activeProfile?.name,
                                boxLoaded = state.phonebooksLoaded,
                                busy = state.busy,
                                onPick = viewModel::useSpeedDial,
                                onAssign = viewModel::startAssigningSpeedDial,
                                onRemove = viewModel::removePhoneSpeedDial,
                                boxEditable = editableBoxSpeedDials != null,
                                onAssignBox = viewModel::startAssigningBoxSpeedDial,
                                onRemoveBox = viewModel::removeBoxSpeedDial,
                                onlineCopies = onlineCopies,
                                onDeleteCopy = viewModel::deleteOnlineCopy,
                            )
                        },
                    )
                } else if (state.showSettings && state.showConnectionSettings) {
                    val canGoBack = state.settingsSaved
                    BackHandler(enabled = canGoBack) { viewModel.closeConnectionSettings() }
                    // A fresh form for every box being edited (or added).
                    key(state.editingProfileId ?: "new") {
                        val edited = state.profiles.firstOrNull { it.id == state.editingProfileId }
                        ConnectionSettingsScreen(
                            initial = state.config,
                            canGoBack = canGoBack,
                            hasTrustedCert = state.config.trustedCertDer != null,
                            busy = state.busy,
                            status = state.status,
                            initialName = state.editingName,
                            isNew = state.editingProfileId == null,
                            initialPhonebooksEnabled = edited?.phonebooksEnabled ?: true,
                            initialCallListEnabled = edited?.callListEnabled ?: true,
                            initialSpeedDialsEnabled = edited?.speedDialsEnabled ?: true,
                            boxInfo = edited?.info,
                            dialingCodes = edited?.dialingCodes,
                            userRights = edited?.userRights,
                            boxUsers = state.boxUsers,
                            securityPort = state.securityPort,
                            onLookUpSecurityPort = viewModel::lookUpSecurityPort,
                            onLookUpUsers = viewModel::lookUpBoxUsers,
                            onSave = viewModel::saveSettings,
                            onBack = viewModel::closeConnectionSettings,
                            onDelete = state.editingProfileId?.let { id -> { viewModel.deleteProfile(id) } },
                            onShowCertificate = viewModel::showTrustedCertificate,
                            onForgetCertificate = viewModel::forgetCertificate,
                            // Nothing set up yet (e.g. just installed): the settings may come from a file instead.
                            importOffer = if (state.profiles.isEmpty()) {
                                {
                                    SettingsImportOffer(
                                        busy = state.transferBusy,
                                        pendingImport = state.pendingImport,
                                        importError = state.importError,
                                        onOpenImport = viewModel::openSettingsFile,
                                        onImport = ::importSettings,
                                        onCancelImport = viewModel::cancelImport,
                                    )
                                }
                            } else null,
                        )
                    }
                } else if (state.showSettings) {
                    BackHandler { viewModel.closeSettings() }
                    SettingsScreen(
                        profiles = state.profiles,
                        activeProfileId = state.activeProfileId,
                        activeIssue = state.activeBoxIssue,
                        boxesSeen = state.boxesSeen,
                        noBoxReachable = state.noBoxReachable,
                        busy = state.busy,
                        networkNotice = state.networkNotice,
                        onEditProfile = viewModel::editProfile,
                        onActivateProfile = viewModel::activateProfile,
                        onAddProfile = viewModel::addProfile,
                        onBack = viewModel::closeSettings,
                        onOpenLanguage = viewModel::openLanguage,
                        deviceContactsEnabled = state.deviceContactsEnabled,
                        onDeviceContactsChange = viewModel::setDeviceContactsEnabled,
                        callLogEnabled = state.callLogEnabled,
                        callLogProblem = state.callLogProblem,
                        onEnableCallLog = viewModel::enableCallLog,
                        onDisableCallLog = viewModel::disableCallLog,
                        phoneSpeedDialsEnabled = state.phoneSpeedDialsEnabled,
                        onPhoneSpeedDialsChange = viewModel::setPhoneSpeedDialsEnabled,
                        callStatusEnabled = state.callStatusEnabled,
                        onCallStatusChange = viewModel::setCallStatusEnabled,
                        checkingCallMonitors = state.checkingCallMonitors,
                        onCheckCallMonitors = viewModel::checkCallMonitors,
                        transferBusy = state.transferBusy,
                        pendingImport = state.pendingImport,
                        importError = state.importError,
                        onExportSettings = viewModel::exportSettings,
                        onOpenSettingsFile = viewModel::openSettingsFile,
                        onImportSettings = ::importSettings,
                        onCancelImport = viewModel::cancelImport,
                    )
                } else {
                    DialerScreen(
                        state = state,
                        onNumberChange = viewModel::onNumberChange,
                        onDigit = viewModel::appendDigit,
                        onLongDigit = viewModel::longPressDigit,
                        onBackspace = viewModel::backspace,
                        onDial = viewModel::dial,
                        onHangup = viewModel::hangup,
                        onSelectPhone = viewModel::selectPhone,
                        onRefresh = viewModel::refreshPhones,
                        onOpenSettings = viewModel::openSettings,
                        onOpenPicker = viewModel::openPicker,
                        onOpenDeflections = viewModel::openDeflections,
                    )
                }

                // Errors block all use of the app: shown modally above whichever screen is open.
                state.error?.let { error ->
                    ErrorDialog(
                        error = error,
                        canRetry = state.canRetry,
                        // Within the settings (e.g. a box chosen by hand there) the user is where they would lead.
                        offerSettings = !state.showSettings,
                        onRetry = viewModel::retry,
                        onOpenSettings = viewModel::openSettingsFromError,
                        onDismiss = viewModel::dismissError,
                    )
                }
                state.secondFactor?.let { challenge ->
                    SecondFactorDialog(challenge = challenge, onCancel = viewModel::cancelSecondFactor)
                }
                state.certificatePrompt?.let { prompt ->
                    TrustCertificateDialog(
                        prompt = prompt,
                        onTrust = viewModel::trustCertificate,
                        onReject = viewModel::rejectCertificate,
                    )
                }
                state.viewedCertificate?.let { info ->
                    ViewCertificateDialog(
                        info = info,
                        onClose = viewModel::closeCertificateView,
                        onForget = viewModel::forgetCertificate,
                    )
                }
            }
        }
    }

    /** Back in the foreground: the network may have changed meanwhile. */
    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Imports the opened settings file; then switches to the file's language, if it is not the one in use. */
    private fun importSettings(passphrase: CharArray?) {
        viewModel.importSettings(passphrase) { language ->
            if (language != AppLanguage.current(this)) AppLanguage.set(this, language)
        }
    }

    /**
     * Pre-fills the number when opened from a tel: link (e.g. tapping a number in Contacts); carries out
     * the entries of the launcher icon's menu – those only when they come through their alias, which
     * no other app can start, and not again when the app is reopened from the recent apps.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.component?.className == LauncherShortcuts.ALIAS) {
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
            when (intent.action) {
                LauncherShortcuts.ACTION_DIAL_NOW ->
                    intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let(viewModel::dialFromShortcut)
                LauncherShortcuts.ACTION_PICKER -> viewModel.openPickerFromShortcut()
            }
            return
        }
        val data: Uri = intent.data ?: return
        if (data.scheme == "tel") {
            data.schemeSpecificPart?.let(viewModel::prefill)
        }
    }
}
