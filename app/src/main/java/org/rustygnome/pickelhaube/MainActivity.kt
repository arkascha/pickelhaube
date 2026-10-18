package org.rustygnome.pickelhaube

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.rustygnome.pickelhaube.tr064.CertificateInfo
import org.rustygnome.pickelhaube.ui.DialerScreen
import org.rustygnome.pickelhaube.ui.TrustCertificateDialog
import org.rustygnome.pickelhaube.ui.ViewCertificateDialog
import org.rustygnome.pickelhaube.ui.DialerViewModel
import org.rustygnome.pickelhaube.ui.ErrorDialog
import org.rustygnome.pickelhaube.ui.PhonebookScreen
import org.rustygnome.pickelhaube.ui.SettingsScreen
import org.rustygnome.pickelhaube.ui.SecondFactorDialog
import org.rustygnome.pickelhaube.ui.theme.PickelhaubeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DialerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            PickelhaubeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val trustedFingerprint = remember(state.config.trustedCertDer) {
                    state.config.trustedCertDer?.let(CertificateInfo::fromDerBase64)?.sha256
                }
                if (state.showPhonebook && !state.showSettings) {
                    BackHandler { viewModel.closePhonebook() }
                    PhonebookScreen(
                        routerBooks = state.phonebooks,
                        deviceContacts = state.deviceContacts,
                        loaded = state.phonebooksLoaded,
                        busy = state.busy,
                        onPick = viewModel::pickNumber,
                        onReload = viewModel::reloadPhonebooks,
                        onBack = viewModel::closePhonebook,
                        onDeviceContactsAllowed = viewModel::loadDeviceContacts,
                    )
                } else if (state.showSettings) {
                    val canGoBack = state.settingsSaved
                    BackHandler(enabled = canGoBack) { viewModel.closeSettings() }
                    SettingsScreen(
                        initial = state.config,
                        canGoBack = canGoBack,
                        trustedFingerprint = trustedFingerprint,
                        busy = state.busy,
                        status = state.status,
                        onSave = viewModel::saveSettings,
                        onBack = viewModel::closeSettings,
                        onShowCertificate = viewModel::showTrustedCertificate,
                        onForgetCertificate = viewModel::forgetCertificate,
                    )
                } else {
                    DialerScreen(
                        state = state,
                        onNumberChange = viewModel::onNumberChange,
                        onDigit = viewModel::appendDigit,
                        onBackspace = viewModel::backspace,
                        onDial = viewModel::dial,
                        onHangup = viewModel::hangup,
                        onSelectPhone = viewModel::selectPhone,
                        onRefresh = viewModel::refreshPhones,
                        onOpenSettings = viewModel::openSettings,
                        onOpenPhonebook = viewModel::openPhonebook,
                    )
                }

                // Errors block all use of the app: shown modally above whichever screen is open.
                state.error?.let { error ->
                    ErrorDialog(
                        error = error,
                        canRetry = state.canRetry,
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Pre-fills the number when opened from a tel: link (e.g. tapping a number in Contacts). */
    private fun handleIntent(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme == "tel") {
            data.schemeSpecificPart?.let(viewModel::prefill)
        }
    }
}
