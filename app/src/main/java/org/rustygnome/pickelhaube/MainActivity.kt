package org.rustygnome.pickelhaube

import org.rustygnome.pickelhaube.ui.onlineCopies
import org.rustygnome.pickelhaube.ui.numberKey
import org.rustygnome.pickelhaube.ui.deviceCountryIso
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import org.rustygnome.pickelhaube.ui.phoneNumberFormat
import org.rustygnome.pickelhaube.ui.LocalNumberDisplay
import org.rustygnome.pickelhaube.ui.NumberDisplay
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.data.SpeedDial
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
import org.rustygnome.pickelhaube.ui.InternalPhonesTab
import org.rustygnome.pickelhaube.ui.NumberPickerScreen
import org.rustygnome.pickelhaube.ui.PhonebookTab
import org.rustygnome.pickelhaube.ui.RecentsTab
import org.rustygnome.pickelhaube.ui.SettingsImportOffer
import org.rustygnome.pickelhaube.ui.SettingsScreen
import org.rustygnome.pickelhaube.ui.WidgetSetupScreen
import org.rustygnome.pickelhaube.ui.SecondFactorDialog
import org.rustygnome.pickelhaube.ui.SpeedDialTab
import org.rustygnome.pickelhaube.ui.WelcomeScreen
import org.rustygnome.pickelhaube.ui.WhatsNewDialog
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
                // The picker's scroll positions and search, kept while it is away – for "back" from the dialer.
                val pickerStates = rememberSaveableStateHolder()
                // Numbers grouped as they are written in the country of the box in use, on every screen.
                val displayContext = LocalContext.current
                val dialingCodes = state.activeProfile?.dialingCodes
                val numberDisplay = remember(dialingCodes) {
                    NumberDisplay(phoneNumberFormat(deviceCountryIso(displayContext), dialingCodes))::show
                }
                CompositionLocalProvider(LocalNumberDisplay provides numberDisplay) {
                    if (state.showWelcome) {
                        val firstStart = state.profiles.isEmpty()
                        WelcomeScreen(
                            firstStart = firstStart,
                            box = state.welcomeBox,
                            boxUsers = state.boxUsers,
                            busy = state.busy,
                            connected = state.welcomeConnected,
                            changePasswordRequest = state.welcomeChangePassword,
                            onChangePasswordShown = viewModel.welcome::passwordChangeShown,
                            loginRejected = state.loginRejected,
                            ringPhone = state.selectedPhone,
                            ringPhoneOnBox = state.dialPhoneOnBox,
                            saveCheck = state.saveCheck,
                            onSaveUnchecked = viewModel.connections::saveUnchecked,
                            onSaveAnyway = viewModel.connections::saveAnyway,
                            onDismissSaveCheck = viewModel.connections::dismissSaveCheck,
                            onFindBox = viewModel.welcome::find,
                            onConnect = viewModel.welcome::connect,
                            onDemo = viewModel.welcome::connectDemo,
                            onLeave = viewModel.welcome::leave,
                            onFinish = viewModel.welcome::finish,
                            restoreOffer = {
                                SettingsImportOffer(
                                    busy = state.transferBusy,
                                    pendingImport = state.pendingImport,
                                    importError = state.importError,
                                    onOpenImport = viewModel.transfer::openFile,
                                    onImport = ::importSettings,
                                    onCancelImport = viewModel.transfer::cancelImport,
                                )
                            },
                        )
                    } else if (state.showDeflections) {
                        BackHandler { viewModel.deflections.close() }
                        DeflectionsScreen(
                            boxName = state.activeProfile?.name,
                            deflections = state.deflections,
                            busy = state.busy,
                            tileDeflection = state.tileDeflection,
                            onChooseTile = { viewModel.deflections.chooseTile(it.id) },
                            onToggle = { deflection, enabled -> viewModel.deflections.setEnabled(deflection.id, enabled) },
                            onReload = viewModel.deflections::load,
                            onBack = viewModel.deflections::close,
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
                        // A number's call button – not while picking one for a widget or a speed dial.
                        val callFromPicker: ((String) -> Unit)? =
                            if (state.addingToWidget || state.assigningSpeedDial != null) null else viewModel::callFromPicker
                        // While setting up a speed dial, "back" only cancels that.
                        BackHandler {
                            if (state.assigningSpeedDial != null) viewModel.speedDials.cancelAssigning() else viewModel.closePicker()
                        }
                        val session = state.pickerSession
                        // Opened anew: what was kept of the last time is let go.
                        LaunchedEffect(session) { if (session > 1) pickerStates.removeState("picker-${session - 1}") }
                        pickerStates.SaveableStateProvider("picker-$session") {
                        NumberPickerScreen(
                            tab = state.pickerTab,
                            undoOffer = state.undoOffer,
                            onUndo = viewModel.speedDials::undo,
                            onUndoGone = { viewModel.speedDials.dropUndo(it) },
                        quietError = state.quietError,
                        onRetry = viewModel::retry,
                        onQuietErrorGone = viewModel::dismissError,
                            onTabChange = viewModel::selectPickerTab,
                            busy = state.busy,
                            onReload = viewModel::reloadPicker,
                            onBack = viewModel::closePicker,
                            recents = {
                                RecentsTab(
                                    boxCalls = state.boxCalls,
                                    boxCallsLoaded = state.boxCallsLoaded,
                                    newSince = state.newCallsSince,
                                    missedOnly = state.recentsMissedOnly,
                                    onMissedOnlyChange = viewModel::setRecentsMissedOnly,
                                    boxName = state.activeProfile?.name,
                                    dialingCodes = state.activeProfile?.dialingCodes,
                                    ownNumbers = state.ownNumbers,
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
                                    onCall = callFromPicker,
                                    callEnabled = !state.noBoxReachable,
                                    boxBooks = boxBooks,
                                    onHandOver = viewModel::handOverToBox,
                                    phoneSpeedDials = state.phoneSpeedDials.takeIf { state.phoneSpeedDialsEnabled },
                                    onSetSpeedDial = viewModel.speedDials::setPhone,
                                    boxSpeedDials = editableBoxSpeedDials,
                                    onSetBoxSpeedDial = viewModel.speedDials::setBox,
                                    onReloadCallLog = viewModel.calls::loadPhoneCalls,
                                    onDisableCallLog = viewModel.calls::disablePhoneCalls,
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
                                    onCall = callFromPicker?.let { call -> { _: String, number: String -> call(number) } },
                                    callEnabled = !state.noBoxReachable,
                                    phoneSpeedDials = state.phoneSpeedDials.takeIf { state.phoneSpeedDialsEnabled },
                                    onSetSpeedDial = viewModel.speedDials::setPhone,
                                    assigningSpeedDial = state.assigningSpeedDial,
                                    boxSpeedDials = editableBoxSpeedDials,
                                    onSetBoxSpeedDial = viewModel.speedDials::setBox,
                                    onlineCopies = onlineCopies,
                                    onDeleteCopy = viewModel.speedDials::deleteOnlineCopy,
                                    onCancelAssigning = viewModel.speedDials::cancelAssigning,
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
                                    onPick = { if (state.addingToWidget) viewModel.widget.add(it.name, it.number) else viewModel.useSpeedDial(it) },
                                    onCall = callFromPicker?.let { { speedDial: SpeedDial -> viewModel.callSpeedDialFromPicker(speedDial) } },
                                    callEnabled = !state.noBoxReachable,
                                    onAssign = viewModel.speedDials::startAssigning,
                                    onRemove = viewModel.speedDials::removePhone,
                                    boxEditable = editableBoxSpeedDials != null,
                                    onAssignBox = viewModel.speedDials::startAssigningBox,
                                    onRemoveBox = viewModel.speedDials::removeBox,
                                    onlineCopies = onlineCopies,
                                    onDeleteCopy = viewModel.speedDials::deleteOnlineCopy,
                                )
                            },
                            internal = {
                                InternalPhonesTab(
                                    phones = state.internalPhones,
                                    busy = state.busy,
                                    onPick = viewModel::pickInternalPhone,
                                    onCall = callFromPicker?.let { { number: String, name: String -> viewModel.callInternalFromPicker(number, name) } },
                                    callEnabled = !state.noBoxReachable,
                                    ringPhone = state.selectedPhone,
                                )
                            },
                            showInternal = state.activeProfile?.internalCallsEnabled == true,
                        )
                        }
                    } else if (state.widgetSetup != null && state.profiles.isNotEmpty()) {
                        val setup = state.widgetSetup!!
                        // Done or not: back to where the widget was placed or tapped.
                        val leave = { moveTaskToBack(true) }
                        BackHandler { viewModel.widget.cancel(); leave() }
                        WidgetSetupScreen(
                            list = setup.list,
                            onTitleChange = viewModel.widget::setTitle,
                            onTransparentChange = viewModel.widget::setTransparent,
                            onAdd = viewModel.widget::startAdding,
                            onRemove = viewModel.widget::remove,
                            onMove = viewModel.widget::move,
                            onSave = { viewModel.widget.save(); leave() },
                            onCancel = { viewModel.widget.cancel(); leave() },
                        )
                    } else if (state.showSettings && state.showConnectionSettings) {
                        val canGoBack = state.settingsSaved
                        BackHandler(enabled = canGoBack) { viewModel.connections.close() }
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
                                initialInternalCallsEnabled = edited?.internalCallsEnabled ?: false,
                                boxInfo = edited?.info,
                                dialingCodes = edited?.dialingCodes,
                                userRights = edited?.userRights,
                                boxUsers = state.boxUsers,
                                securityPort = state.securityPort,
                                onLookUpSecurityPort = viewModel.connections::lookUpSecurityPort,
                                onLookUpUsers = viewModel.connections::lookUpUsers,
                                hostNote = state.hostNote,
                                saveCheck = state.saveCheck,
                                onSaveUnchecked = viewModel.connections::saveUnchecked,
                                onSaveAnyway = viewModel.connections::saveAnyway,
                                onDismissSaveCheck = viewModel.connections::dismissSaveCheck,
                                onSave = viewModel.connections::save,
                                onBack = viewModel.connections::close,
                                onDelete = state.editingProfileId?.let { id -> { viewModel.connections.delete(id) } },
                                onShowCertificate = viewModel::showTrustedCertificate,
                                onForgetCertificate = viewModel::forgetCertificate,
                                // Nothing set up yet (e.g. just installed): the settings may come from a file instead.
                                importOffer = if (state.profiles.isEmpty()) {
                                    {
                                        SettingsImportOffer(
                                            busy = state.transferBusy,
                                            pendingImport = state.pendingImport,
                                            importError = state.importError,
                                            onOpenImport = viewModel.transfer::openFile,
                                            onImport = ::importSettings,
                                            onCancelImport = viewModel.transfer::cancelImport,
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
                            onEditProfile = viewModel.connections::edit,
                            onActivateProfile = viewModel::activateProfile,
                            onAddProfile = viewModel.connections::add,
                            onStartWelcomeTour = viewModel.welcome::start,
                            onShowWhatsNew = viewModel.whatsNew::show,
                            onBack = viewModel::closeSettings,
                            onOpenLanguage = viewModel::openLanguage,
                            deviceContactsEnabled = state.deviceContactsEnabled,
                            onDeviceContactsChange = viewModel::setDeviceContactsEnabled,
                            callLogEnabled = state.callLogEnabled,
                            callLogProblem = state.callLogProblem,
                            onEnableCallLog = viewModel.calls::enablePhoneCalls,
                            onDisableCallLog = viewModel.calls::disablePhoneCalls,
                            phoneSpeedDialsEnabled = state.phoneSpeedDialsEnabled,
                            onPhoneSpeedDialsChange = viewModel.speedDials::setPhoneEnabled,
                            keyTonesEnabled = state.keyTonesEnabled,
                            onKeyTonesChange = viewModel::setKeyTonesEnabled,
                            callStatusEnabled = state.callStatusEnabled,
                            onCallStatusChange = viewModel.callStatus::setEnabled,
                            checkingCallMonitors = state.checkingCallMonitors,
                            onCheckCallMonitors = viewModel.callStatus::checkMonitors,
                            transferBusy = state.transferBusy,
                            pendingImport = state.pendingImport,
                            importError = state.importError,
                            onExportSettings = viewModel.transfer::export,
                            onOpenSettingsFile = viewModel.transfer::openFile,
                            onImportSettings = ::importSettings,
                            onCancelImport = viewModel.transfer::cancelImport,
                        )
                    } else {
                        // A number just picked: "back" leads to the picker again, as it was left.
                        BackHandler(enabled = state.backToPicker) { viewModel.reopenPicker() }
                        DialerScreen(
                            state = state,
                            onNumberChange = viewModel::onNumberChange,
                            onFieldChange = viewModel::editField,
                            onRetry = viewModel::retry,
                            onDigit = viewModel::appendDigit,
                            onLongDigit = viewModel::longPressDigit,
                            onBackspace = viewModel::backspace,
                            onDial = { viewModel.dial() },
                            onDialHidden = { viewModel.dial(withhold = true) },
                            onHangup = viewModel::hangup,
                            onSelectPhone = viewModel::selectPhone,
                            onRefresh = viewModel::refreshPhones,
                            onOpenSettings = viewModel::openSettings,
                            onOpenPicker = { viewModel.openPicker() },
                            onShowMissedCalls = { viewModel.openPicker(missedOnly = true) },
                            onPaste = { viewModel.takeNumbersFrom(it, fromClipboard = true) },
                            onChooseNumber = viewModel::chooseNumber,
                            onDismissNumberChoice = viewModel::dismissNumberChoice,
                            onOpenDeflections = viewModel.deflections::open,
                            onCheckLines = viewModel.calls::checkLines,
                            onDialFrom = { viewModel.dial(from = it) },
                            onSuggestion = viewModel::takeSuggestion,
                            onPullRefresh = viewModel::refresh,
                        )
                    }
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
                        // In the welcome tour, a rejected login leads back to the password instead of just closing.
                        onDismiss = if (state.showWelcome && state.loginRejected) viewModel.welcome::changePassword else viewModel::dismissError,
                        dismissLabel = stringResource(
                            if (state.showWelcome && state.loginRejected) R.string.welcome_change_password else R.string.close,
                        ),
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
                // After an update, once: over the dialer when nothing else is open; asked for in the settings, there.
                state.whatsNew?.let { versions ->
                    val onDialer = !state.showWelcome && !state.showSettings && !state.showPicker && !state.showDeflections &&
                        state.widgetSetup == null
                    val nothingElse = state.error == null && state.secondFactor == null && state.certificatePrompt == null
                    if (nothingElse && (onDialer || state.whatsNewRequested)) {
                        WhatsNewDialog(versions, onDismiss = viewModel.whatsNew::dismiss)
                    }
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

    override fun onStop() {
        super.onStop()
        // Left, not just turned: "back" no longer leads to the picker left before.
        if (!isChangingConfigurations) viewModel.onLeftApp()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun Intent.widgetId(): Int? =
        getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            .takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }

    /** Imports the opened settings file; then switches to the file's language, if it is not the one in use. */
    private fun importSettings(passphrase: CharArray?) {
        viewModel.transfer.import(passphrase) { language ->
            if (language != AppLanguage.current(this)) AppLanguage.set(this, language)
        }
    }

    /**
     * Pre-fills the number when opened from a tel: link (e.g. tapping a number in Contacts) or with a
     * shared text; carries out
     * the entries of the launcher icon's menu – those only when they come through their alias, which
     * no other app can start, and not again when the app is reopened from the recent apps.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        // Opened for something particular (shortcut, widget, link, shared text): "What's new" waits.
        if (intent.action != Intent.ACTION_MAIN) viewModel.whatsNew.holdBack()
        if (intent.component?.className == LauncherShortcuts.ALIAS) {
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
            when (intent.action) {
                LauncherShortcuts.ACTION_DIAL_NOW ->
                    intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let(viewModel::dialFromShortcut)
                LauncherShortcuts.ACTION_PICKER -> viewModel.openPickerFromShortcut()
                LauncherShortcuts.ACTION_WIDGET_SETUP -> intent.widgetId()?.let(viewModel.widget::open)
                LauncherShortcuts.ACTION_WIDGET_DIAL -> intent.widgetId()?.let { id ->
                    viewModel.widget.showEntry(id, intent.getIntExtra(LauncherShortcuts.EXTRA_WIDGET_ENTRY, -1))
                }
            }
            return
        }
        // Text shared from another app (e.g. an email signature): the phone number in it.
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { viewModel.takeNumbersFrom(it, fromClipboard = false) }
            return
        }
        val data: Uri = intent.data ?: return
        if (data.scheme == "tel") {
            data.schemeSpecificPart?.let(viewModel::prefill)
        }
    }
}
