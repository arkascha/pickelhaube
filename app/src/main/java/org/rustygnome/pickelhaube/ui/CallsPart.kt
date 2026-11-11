package org.rustygnome.pickelhaube.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.DeviceCallLog
import org.rustygnome.pickelhaube.data.MissedCalls
import org.rustygnome.pickelhaube.tr064.FritzDialer

/**
 * Calls: the box' call list for "Recent" and its missed calls not looked at yet, this phone's own
 * calls, and whether the box' internet numbers are registered.
 */
internal class CallsPart(private val host: DialerHost) {

    private val state get() = host.state
    private val settings get() = host.settings

    fun reloadRecents() {
        loadPhoneCalls()
        if (settings.activeProfile()?.callListEnabled == false) return
        if (state.value.busy) return host.runWhenIdle("recents") { if (state.value.showPicker) reloadRecents() }
        host.runOnBox(uiText(R.string.recents_loading)) { dialer ->
            val calls = dialer.loadRecentCalls()
            state.update { it.copy(boxCalls = calls, boxCallsLoaded = true) }
            markMissedSeen()
            UiText.Plural(R.plurals.status_calls_loaded, calls.size)
        }
    }

    /**
     * Reads the box' call list in the background – when the app comes up, or the box in use changed
     * –, to count its missed calls not looked at yet (and to have it ready for "Recent"). The first
     * time for a box, its list counts as seen: only calls after that are new.
     */
    fun preloadMissed() = host.runWhenIdle("missedCalls") {
        val profile = settings.activeProfile() ?: return@runWhenIdle
        if (state.value.noBoxReachable || !profile.callListEnabled) return@runWhenIdle
        val config = state.value.config
        host.scope.launch {
            val calls = withContext(Dispatchers.IO) { runCatching { FritzDialer(config).loadRecentCalls() }.getOrNull() }
            // Unless the box in use changed meanwhile.
            if (calls == null || state.value.config != config) return@launch
            val seen = settings.missedSeenUntil(profile.id)
                ?: MissedCalls.seenUntil(calls, 0).also { settings.setMissedSeenUntil(profile.id, it) }
            // With the phone's calls, as one returned from the mobile settles a missed one as well.
            val phoneCalls = if (state.value.callLogEnabled) {
                withContext(Dispatchers.IO) { runCatching { DeviceCallLog.load(host.app) }.getOrNull() }
            } else null
            val all = calls + phoneCalls.orEmpty()
            val missed = MissedCalls.newSince(all, seen, host.numberKeys()).size
            state.update { it.copy(boxCalls = calls, boxCallsLoaded = true, newMissedCalls = missed) }
            markMissedSeen()
        }
    }

    /**
     * Asks the box in the background – when the app comes up, or the box in use changed – for its own
     * numbers, and whether its internet numbers are registered with their provider, so the dialer can
     * warn before a call fails for that, and offer to call from another of them. Nothing is shown if the box does not say (rights, older FRITZ!OS).
     */
    fun preloadLines() = host.runWhenIdle("lines") {
        if (state.value.noBoxReachable) return@runWhenIdle
        val config = state.value.config
        host.scope.launch {
            val numbers = withContext(Dispatchers.IO) { runCatching { FritzDialer(config).loadNumbers() }.getOrNull() }
            // Unless the box in use changed meanwhile.
            if (numbers != null && state.value.config == config) {
                state.update { it.copy(lines = numbers.lines, ownNumbers = numbers.numbers) }
            }
        }
    }

    /** The warning about lines not registered was tapped: ask the box again. */
    fun checkLines() {
        preloadLines()
        host.startQueuedRequests()
    }

    /** "Recent" shows the box' calls: they count as seen from now on (marked as new while it stays open). */
    fun markMissedSeen() {
        val shown = state.value
        if (!shown.showPicker || shown.pickerTab != PickerTab.RECENT || !shown.boxCallsLoaded) return
        val id = settings.activeProfileId ?: return
        val seen = settings.missedSeenUntil(id) ?: 0L
        settings.setMissedSeenUntil(id, MissedCalls.seenUntil(shown.boxCalls, seen))
        state.update { it.copy(newMissedCalls = 0, newCallsSince = it.newCallsSince ?: seen) }
    }

    /** (Re)reads the phone's calls, if the feature is on. A failure is shown, not thrown. */
    fun loadPhoneCalls() {
        if (!state.value.callLogEnabled) return
        val app = host.app
        host.scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DeviceCallLog.load(app) } }
            state.update {
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
    fun enablePhoneCalls(granted: Boolean) {
        if (!granted) {
            state.update {
                it.copy(callLogProblem = uiText(R.string.call_log_denied))
            }
            return
        }
        val app = host.app
        host.scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DeviceCallLog.load(app) } }
            val calls = result.getOrNull()
            settings.callLogEnabled = calls != null
            state.update {
                it.copy(
                    callLogEnabled = calls != null,
                    phoneCalls = calls,
                    callLogProblem = result.exceptionOrNull()?.let(::describeCallLogProblem),
                )
            }
        }
    }

    fun disablePhoneCalls() {
        settings.callLogEnabled = false
        state.update { it.copy(callLogEnabled = false, phoneCalls = null, callLogProblem = null) }
    }

    /** No access / no call log say enough as they are; other failures add their (technical) message. */
    private fun describeCallLogProblem(error: Throwable): UiText =
        error.message?.takeIf { error !is SecurityException && error !is IllegalStateException }
            ?.let { uiText(R.string.call_log_failed, it) }
            ?: uiText(R.string.call_log_failed_plain)
}
