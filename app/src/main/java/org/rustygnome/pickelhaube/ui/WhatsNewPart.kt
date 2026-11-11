package org.rustygnome.pickelhaube.ui

import kotlinx.coroutines.flow.update
import org.rustygnome.pickelhaube.BuildConfig
import org.rustygnome.pickelhaube.data.WhatsNew

/** The "What's new" note: once after an update, and again on request from the settings. */
internal class WhatsNewPart(private val host: DialerHost) {

    private val state get() = host.state
    private val settings get() = host.settings

    /** After an update, the note about what is new – shown over the dialer when there is room (see [WhatsNew]). */
    fun check() {
        val current = BuildConfig.VERSION_CODE
        val versions = WhatsNew.toShow(settings.lastSeenVersion, current, settings.isConfigured())
        if (versions.isEmpty()) {
            settings.lastSeenVersion = current
        } else {
            state.update { it.copy(whatsNew = versions, whatsNewRequested = false) }
        }
    }

    /**
     * The app was opened for something particular – a launcher shortcut, a widget, a tel: link, a
     * shared text: the note waits for the next ordinary start.
     */
    fun holdBack() {
        if (state.value.whatsNewRequested) return
        state.update { it.copy(whatsNew = null) }
    }

    /** "What's new" in the settings: the note of the current version again. */
    fun show() {
        val latest = WhatsNew.latest(BuildConfig.VERSION_CODE) ?: return
        state.update { it.copy(whatsNew = listOf(latest), whatsNewRequested = true) }
    }

    /** The note was read: not shown again for this version. */
    fun dismiss() {
        settings.lastSeenVersion = BuildConfig.VERSION_CODE
        state.update { it.copy(whatsNew = null, whatsNewRequested = false) }
    }
}
