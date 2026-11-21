package org.rustygnome.pickelhaube.ui

import kotlinx.coroutines.flow.update
import org.rustygnome.pickelhaube.tr064.CertificateInfo

/**
 * The box' self-signed certificate: pinned when the user trusts the presented one, shown and
 * forgotten from the connection page. What was interrupted by the prompt is the view model's
 * business (see [BoxActionsPart.trustCertificate], [BoxActionsPart.rejectCertificate]).
 */
internal class CertificatesPart(private val host: DialerHost) {

    private val state get() = host.state
    private val settings get() = host.settings

    /** Pins the presented certificate and closes the prompt; false if none was presented. */
    fun trustPresented(): Boolean {
        val prompt = state.value.certificatePrompt ?: return false
        val config = state.value.config.copy(trustedCertDer = prompt.certificate.derBase64)
        // While verifying new settings, everything is persisted together once the login works.
        if (!host.verifyingSettings) persistedProfileId()?.let { settings.updateTrustedCert(it, config.trustedCertDer) }
        state.update { it.copy(config = config, certificatePrompt = null) }
        return true
    }

    fun show() {
        val info = state.value.config.trustedCertDer?.let(CertificateInfo::fromDerBase64) ?: return
        state.update { it.copy(viewedCertificate = info) }
    }

    fun close() = state.update { it.copy(viewedCertificate = null) }

    fun forget() {
        val config = state.value.config.copy(trustedCertDer = null)
        if (!host.verifyingSettings) persistedProfileId()?.let { settings.updateTrustedCert(it, null) }
        state.update { it.copy(config = config, viewedCertificate = null) }
    }

    /** The stored connection a certificate decision belongs to: the edited one, else the active one. */
    private fun persistedProfileId(): String? =
        if (state.value.showConnectionSettings) state.value.editingProfileId else settings.activeProfileId
}
