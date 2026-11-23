package org.rustygnome.pickelhaube

import org.rustygnome.pickelhaube.ui.onlineCopies
import org.rustygnome.pickelhaube.ui.boxesToAsk
import org.rustygnome.pickelhaube.ui.Page
import org.rustygnome.pickelhaube.ui.pageTransition
import org.rustygnome.pickelhaube.ui.page
import org.rustygnome.pickelhaube.ui.isOpen
import org.rustygnome.pickelhaube.ui.numberKey
import org.rustygnome.pickelhaube.ui.deviceCountryIso
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pickelhaube.contacts.ContactActions
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import org.rustygnome.pickelhaube.ui.phoneNumberFormat
import org.rustygnome.pickelhaube.ui.LocalNumberDisplay
import org.rustygnome.pickelhaube.ui.NumberDisplay
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContent
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
        // "Call via FRITZ!Box" in the contacts: keep its rows following the contacts.
        lifecycleScope.launch(Dispatchers.IO) { ContactActions.ensureScheduled(applicationContext) }

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
                    // Which page shows is decided in one place (UiState.page); the others stay open underneath.
                    // Each page drawn with the state it had: one sliding away keeps showing what it showed.
                    AnimatedContent(
                        targetState = state,
                        contentKey = { it.page },
                        transitionSpec = { pageTransition(initialState.page, targetState.page) },
                        label = "page",
                    ) { shown ->
                        when (shown.page) {
                            Page.WELCOME -> {
                                val firstStart = shown.profiles.isEmpty()
                                WelcomeScreen(
                                    firstStart = firstStart,
                                    box = shown.welcomeBox,
                                    boxUsers = shown.boxUsers,
                                    busy = shown.busy,
                                    connected = shown.welcomeConnected,
                                    changePasswordRequest = shown.welcomeChangePassword,
                                    onChangePasswordShown = viewModel.welcome::passwordChangeShown,
                                    loginRejected = shown.loginRejected,
                                    ringPhone = shown.selectedPhone,
                                    ringPhoneOnBox = shown.dialPhoneOnBox,
                                    saveCheck = shown.saveCheck,
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
                                            busy = shown.transferBusy,
                                            pendingImport = shown.pendingImport,
                                            importError = shown.importError,
                                            onOpenImport = viewModel.transfer::openFile,
                                            onImport = ::importSettings,
                                            onCancelImport = viewModel.transfer::cancelImport,
                                        )
                                    },
                                )
                            }
                            Page.DEFLECTIONS -> {
                                BackHandler { viewModel.deflections.close() }
                                DeflectionsScreen(
                                    boxName = shown.activeProfile?.name,
                                    deflections = shown.deflections,
                                    busy = shown.busy,
                                    tileDeflection = shown.tileDeflection,
                                    onChooseTile = { viewModel.deflections.chooseTile(it.id) },
                                    onToggle = { deflection, enabled -> viewModel.deflections.setEnabled(deflection.id, enabled) },
                                    onReload = viewModel.deflections::load,
                                    onBack = viewModel.deflections::close,
                                )
                            }
                            Page.LANGUAGE -> {
                                BackHandler { viewModel.closeLanguage() }
                                LanguageScreen(onBack = viewModel::closeLanguage)
                            }
                            Page.PICKER -> {
                                val routerBooksEnabled = shown.activeProfile?.phonebooksEnabled ?: true
                                // The box' phone books a contact can be handed over to: only while it is there.
                                val boxBooks = if (shown.phonebooksLoaded && !shown.noBoxReachable) shown.phonebooks else emptyList()
                                val boxSpeedDialsEnabled = shown.activeProfile?.speedDialsEnabled ?: true
                                val boxSpeedDials = remember(shown.phonebooks) { SpeedDials.fromPhonebooks(shown.phonebooks) }
                                // The box' speed dials as far as they can be set up now.
                                val editableBoxSpeedDials = boxSpeedDials.takeIf { boxSpeedDialsEnabled && boxBooks.any { !it.online } }
                                // Entries the box' own phone books have as copies of online contacts: they may be deleted.
                                val context = LocalContext.current
                                val dialingCodes = shown.activeProfile?.dialingCodes
                                val onlineCopies = remember(boxBooks, dialingCodes) {
                                    val countryIso = deviceCountryIso(context)
                                    onlineCopies(boxBooks) { numberKey(it, countryIso, dialingCodes) }
                                }
                                // A number's call button – not while picking one for a widget or a speed dial.
                                val callFromPicker: ((String) -> Unit)? =
                                    if (shown.addingToWidget || shown.assigningSpeedDial != null) null else viewModel::callFromPicker
                                // While setting up a speed dial, "back" only cancels that.
                                BackHandler {
                                    if (shown.assigningSpeedDial != null) viewModel.speedDials.cancelAssigning() else viewModel.picker.close()
                                }
                                val session = shown.pickerSession
                                // Opened anew: what was kept of the last time is let go.
                                LaunchedEffect(session) { if (session > 1) pickerStates.removeState("picker-${session - 1}") }
                                pickerStates.SaveableStateProvider("picker-$session") {
                                NumberPickerScreen(
                                    tab = shown.pickerTab,
                                    undoOffer = shown.undoOffer,
                                    onUndo = viewModel.speedDials::undo,
                                    onUndoGone = { viewModel.speedDials.dropUndo(it) },
                                quietError = shown.quietError,
                                onRetry = viewModel.boxActions::retry,
                                onQuietErrorGone = viewModel.boxActions::dismissError,
                                    onTabChange = viewModel.picker::selectTab,
                                    busy = shown.busy,
                                    onReload = viewModel.picker::reload,
                                    onBack = viewModel.picker::close,
                                    recents = {
                                        RecentsTab(
                                            boxCalls = shown.boxCalls,
                                            boxCallsLoaded = shown.boxCallsLoaded,
                                            newSince = shown.newCallsSince,
                                            missedOnly = shown.recentsMissedOnly,
                                            onMissedOnlyChange = viewModel.picker::setRecentsMissedOnly,
                                            boxName = shown.activeProfile?.name,
                                            dialingCodes = shown.activeProfile?.dialingCodes,
                                            ownNumbers = shown.ownNumbers,
                                            contacts = remember(shown.phonebooks, routerBooksEnabled, shown.deviceContacts, shown.deviceContactsEnabled) {
                                                val books = (if (routerBooksEnabled) shown.phonebooks else emptyList()) +
                                                    listOfNotNull(shown.deviceContacts?.takeIf { shown.deviceContactsEnabled })
                                                books.flatMap { it.contacts }
                                            },
                                            boxCallsEnabled = shown.activeProfile?.callListEnabled ?: true,
                                            phoneCalls = shown.phoneCalls,
                                            callLogEnabled = shown.callLogEnabled,
                                            callLogProblem = shown.callLogProblem,
                                            busy = shown.busy,
                                            onPick = viewModel.picker::pickNumber,
                                            onCall = callFromPicker,
                                            callEnabled = !shown.noBoxReachable,
                                            boxBooks = boxBooks,
                                            onHandOver = viewModel.picker::handOverToBox,
                                            phoneSpeedDials = shown.phoneSpeedDials.takeIf { shown.phoneSpeedDialsEnabled },
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
                                            routerBooks = if (routerBooksEnabled) shown.phonebooks else emptyList(),
                                            routerBooksEnabled = routerBooksEnabled,
                                            deviceContacts = shown.deviceContacts,
                                            deviceContactsEnabled = shown.deviceContactsEnabled,
                                            dialingCodes = shown.activeProfile?.dialingCodes,
                                            loaded = shown.phonebooksLoaded,
                                            busy = shown.busy,
                                            onPick = viewModel.picker::pickContactNumber,
                                            onCall = callFromPicker?.let { call -> { _: String, number: String -> call(number) } },
                                            callEnabled = !shown.noBoxReachable,
                                            phoneSpeedDials = shown.phoneSpeedDials.takeIf { shown.phoneSpeedDialsEnabled },
                                            onSetSpeedDial = viewModel.speedDials::setPhone,
                                            assigningSpeedDial = shown.assigningSpeedDial,
                                            boxSpeedDials = editableBoxSpeedDials,
                                            onSetBoxSpeedDial = viewModel.speedDials::setBox,
                                            onlineCopies = onlineCopies,
                                            onDeleteCopy = viewModel.speedDials::deleteOnlineCopy,
                                            onCancelAssigning = viewModel.speedDials::cancelAssigning,
                                            boxBooks = boxBooks,
                                            boxName = shown.activeProfile?.name,
                                            onHandOver = viewModel.picker::handOverToBox,
                                            onDeviceContactsAllowed = viewModel.picker::loadDeviceContacts,
                                            onDisableDeviceContacts = { viewModel.picker.setDeviceContactsEnabled(false) },
                                            askForDeviceContacts = shown.askForDeviceContacts,
                                            onDeviceContactsAnswer = viewModel.picker::answerDeviceContactsQuestion,
                                        )
                                    },
                                    speedDials = {
                                        SpeedDialTab(
                                            phoneSpeedDials = shown.phoneSpeedDials.takeIf { shown.phoneSpeedDialsEnabled },
                                            boxSpeedDials = boxSpeedDials.takeIf { boxSpeedDialsEnabled },
                                            boxName = shown.activeProfile?.name,
                                            boxLoaded = shown.phonebooksLoaded,
                                            busy = shown.busy,
                                            onPick = { if (shown.addingToWidget) viewModel.widget.add(it.name, it.number) else viewModel.numberInput.useSpeedDial(it) },
                                            onCall = callFromPicker?.let { { speedDial: SpeedDial -> viewModel.callSpeedDialFromPicker(speedDial) } },
                                            callEnabled = !shown.noBoxReachable,
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
                                            phones = shown.internalPhones,
                                            busy = shown.busy,
                                            onPick = viewModel.numberInput::pickInternalPhone,
                                            onCall = callFromPicker?.let { { number: String, name: String -> viewModel.callInternalFromPicker(number, name) } },
                                            callEnabled = !shown.noBoxReachable,
                                            ringPhone = shown.selectedPhone,
                                        )
                                    },
                                    showInternal = shown.activeProfile?.internalCallsEnabled == true,
                                )
                                }
                            }
                            Page.WIDGET_SETUP -> {
                                // Shown only while there is one (see UiState.page).
                                val setup = shown.widgetSetup!!
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
                            }
                            Page.CONNECTION -> {
                                val canGoBack = shown.settingsSaved
                                BackHandler(enabled = canGoBack) { viewModel.connections.close() }
                                // A fresh form for every box being edited (or added).
                                key(shown.editingProfileId ?: "new") {
                                    val edited = shown.profiles.firstOrNull { it.id == shown.editingProfileId }
                                    ConnectionSettingsScreen(
                                        initial = shown.config,
                                        canGoBack = canGoBack,
                                        hasTrustedCert = shown.config.trustedCertDer != null,
                                        busy = shown.busy,
                                        status = shown.status,
                                        initialName = shown.editingName,
                                        isNew = shown.editingProfileId == null,
                                        initialPhonebooksEnabled = edited?.phonebooksEnabled ?: true,
                                        initialCallListEnabled = edited?.callListEnabled ?: true,
                                        initialSpeedDialsEnabled = edited?.speedDialsEnabled ?: true,
                                        initialInternalCallsEnabled = edited?.internalCallsEnabled ?: false,
                                        boxInfo = edited?.info,
                                        dialingCodes = edited?.dialingCodes,
                                        userRights = edited?.userRights,
                                        boxUsers = shown.boxUsers,
                                        securityPort = shown.securityPort,
                                        onLookUpSecurityPort = viewModel.connections::lookUpSecurityPort,
                                        onLookUpUsers = viewModel.connections::lookUpUsers,
                                        hostNote = shown.hostNote,
                                        saveCheck = shown.saveCheck,
                                        onSaveUnchecked = viewModel.connections::saveUnchecked,
                                        onSaveAnyway = viewModel.connections::saveAnyway,
                                        onDismissSaveCheck = viewModel.connections::dismissSaveCheck,
                                        onSave = viewModel.connections::save,
                                        onBack = viewModel.connections::close,
                                        onDelete = shown.editingProfileId?.let { id -> { viewModel.connections.delete(id) } },
                                        onShowCertificate = viewModel.certificates::show,
                                        onForgetCertificate = viewModel.certificates::forget,
                                        // Nothing set up yet (e.g. just installed): the settings may come from a file instead.
                                        importOffer = if (shown.profiles.isEmpty()) {
                                            {
                                                SettingsImportOffer(
                                                    busy = shown.transferBusy,
                                                    pendingImport = shown.pendingImport,
                                                    importError = shown.importError,
                                                    onOpenImport = viewModel.transfer::openFile,
                                                    onImport = ::importSettings,
                                                    onCancelImport = viewModel.transfer::cancelImport,
                                                )
                                            }
                                        } else null,
                                    )
                                }
                            }
                            Page.SETTINGS -> {
                                BackHandler { viewModel.closeSettings() }
                                SettingsScreen(
                                    profiles = shown.profiles,
                                    activeProfileId = shown.activeProfileId,
                                    activeIssue = shown.activeBoxIssue,
                                    boxesSeen = shown.boxesSeen,
                                    callMonitorsCheckable = shown.boxesToAsk.isNotEmpty(),
                                    noBoxReachable = shown.noBoxReachable,
                                    busy = shown.busy,
                                    networkNotice = shown.networkNotice,
                                    onEditProfile = viewModel.connections::edit,
                                    onActivateProfile = viewModel::activateProfile,
                                    onAddProfile = viewModel.connections::add,
                                    onStartWelcomeTour = viewModel.welcome::start,
                                    onShowWhatsNew = viewModel.whatsNew::show,
                                    onBack = viewModel::closeSettings,
                                    onOpenLanguage = viewModel::openLanguage,
                                    deviceContactsEnabled = shown.deviceContactsEnabled,
                                    onDeviceContactsChange = viewModel.picker::setDeviceContactsEnabled,
                                    callLogEnabled = shown.callLogEnabled,
                                    callLogProblem = shown.callLogProblem,
                                    onEnableCallLog = viewModel.calls::enablePhoneCalls,
                                    onDisableCallLog = viewModel.calls::disablePhoneCalls,
                                    phoneSpeedDialsEnabled = shown.phoneSpeedDialsEnabled,
                                    onPhoneSpeedDialsChange = viewModel.speedDials::setPhoneEnabled,
                                    keyTonesEnabled = shown.keyTonesEnabled,
                                    onKeyTonesChange = viewModel::setKeyTonesEnabled,
                                    callStatusEnabled = shown.callStatusEnabled,
                                    onCallStatusChange = viewModel.callStatus::setEnabled,
                                    checkingCallMonitors = shown.checkingCallMonitors,
                                    onCheckCallMonitors = viewModel.callStatus::checkMonitors,
                                    transferBusy = shown.transferBusy,
                                    pendingImport = shown.pendingImport,
                                    importError = shown.importError,
                                    onExportSettings = viewModel.transfer::export,
                                    onOpenSettingsFile = viewModel.transfer::openFile,
                                    onImportSettings = ::importSettings,
                                    onCancelImport = viewModel.transfer::cancelImport,
                                )
                            }
                            Page.DIALER -> {
                                // A number just picked: "back" leads to the picker again, as it was left.
                                BackHandler(enabled = shown.backToPicker) { viewModel.picker.reopen() }
                                DialerScreen(
                                    state = shown,
                                    onNumberChange = viewModel.numberInput::onNumberChange,
                                    onFieldChange = viewModel.numberInput::editField,
                                    onRetry = viewModel.boxActions::retry,
                                    onDigit = viewModel.numberInput::appendDigit,
                                    onLongDigit = viewModel.numberInput::longPressDigit,
                                    onBackspace = viewModel.numberInput::backspace,
                                    onDial = { viewModel.dial() },
                                    onDialHidden = { viewModel.dial(withhold = true) },
                                    onHangup = viewModel::hangup,
                                    onSelectPhone = viewModel::selectPhone,
                                    onRefresh = viewModel::refreshPhones,
                                    onOpenSettings = viewModel::openSettings,
                                    onOpenPicker = { viewModel.picker.open() },
                                    onShowMissedCalls = { viewModel.picker.open(missedOnly = true) },
                                    onPaste = { viewModel.numberInput.takeNumbersFrom(it, fromClipboard = true) },
                                    onChooseNumber = viewModel.numberInput::chooseNumber,
                                    onDismissNumberChoice = viewModel.numberInput::dismissNumberChoice,
                                    onOpenDeflections = viewModel.deflections::open,
                                    onCheckLines = viewModel.calls::checkLines,
                                    onDialFrom = { viewModel.dial(from = it) },
                                    onSuggestion = viewModel.numberInput::takeSuggestion,
                                    onPullRefresh = viewModel::refresh,
                                )
                            }
                        }
                    }
                }

                // Errors block all use of the app: shown modally above whichever screen is open.
                state.error?.let { error ->
                    ErrorDialog(
                        error = error,
                        canRetry = state.canRetry,
                        // Within the settings (e.g. a box chosen by hand there) the user is where they would lead.
                        offerSettings = !state.isOpen(Page.SETTINGS),
                        onRetry = viewModel.boxActions::retry,
                        onOpenSettings = viewModel::openSettingsFromError,
                        // In the welcome tour, a rejected login leads back to the password instead of just closing.
                        onDismiss = if (state.page == Page.WELCOME && state.loginRejected) viewModel.welcome::changePassword else viewModel.boxActions::dismissError,
                        dismissLabel = stringResource(
                            if (state.page == Page.WELCOME && state.loginRejected) R.string.welcome_change_password else R.string.close,
                        ),
                    )
                }
                state.secondFactor?.let { challenge ->
                    SecondFactorDialog(challenge = challenge, onCancel = viewModel.boxActions::cancelSecondFactor)
                }
                state.certificatePrompt?.let { prompt ->
                    TrustCertificateDialog(
                        prompt = prompt,
                        onTrust = viewModel.boxActions::trustCertificate,
                        onReject = viewModel.boxActions::rejectCertificate,
                    )
                }
                // After an update, once: over the dialer when nothing else is open; asked for in the settings, there.
                state.whatsNew?.let { versions ->
                    val onDialer = state.page == Page.DIALER
                    val nothingElse = state.error == null && state.secondFactor == null && state.certificatePrompt == null
                    if (nothingElse && (onDialer || state.whatsNewRequested)) {
                        WhatsNewDialog(versions, onDismiss = viewModel.whatsNew::dismiss)
                    }
                }
                state.viewedCertificate?.let { info ->
                    ViewCertificateDialog(
                        info = info,
                        onClose = viewModel.certificates::close,
                        onForget = viewModel.certificates::forget,
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
        if (!isChangingConfigurations) viewModel.picker.onLeftApp()
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
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { viewModel.numberInput.takeNumbersFrom(it, fromClipboard = false) }
            return
        }
        val data: Uri = intent.data ?: return
        if (data.scheme == "tel") {
            data.schemeSpecificPart?.let(viewModel.numberInput::prefill)
        }
        // A "Call via FRITZ!Box" row tapped in the contacts app: its number, read from the row (if it is one).
        if (data.scheme == "content") {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { ContactActions.numberOf(applicationContext, data) }?.let(viewModel.numberInput::prefill)
            }
        }
    }
}
