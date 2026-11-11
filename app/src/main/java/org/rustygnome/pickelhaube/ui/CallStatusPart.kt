package org.rustygnome.pickelhaube.ui

import android.os.SystemClock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.rustygnome.pickelhaube.tr064.CallMonitor
import org.rustygnome.pickelhaube.tr064.FritzInternalPhones

/** How long a started dial-help call counts as "cancellable" (the handset rings about this long). */
private const val DIAL_ACTIVE_MILLIS = 30_000L

/** How long the box rings the phone for dial help before giving up (a little longer, to be safe). */
private const val PICK_UP_TIMEOUT_MILLIS = 45_000L

/** How long the end of a call stays shown. */
private const val CALL_END_SHOWN_MILLIS = 20_000L

/**
 * The call just placed, as the dialer shows it: "pick up", then – with the box' call monitor –
 * dialling, connected and how it ended; and the setting that switches following calls on and off.
 */
internal class CallStatusPart(private val host: DialerHost) {

    private val state get() = host.state
    private val settings get() = host.settings

    /** Ends [UiState.dialActive] after [DIAL_ACTIVE_MILLIS]. */
    private var dialTimeout: Job? = null

    /** Without the call monitor: the call counts as cancellable (and "pick up" as shown) for a while. */
    fun markDialActive() {
        dialTimeout?.cancel()
        state.update { it.copy(dialActive = true) }
        dialTimeout = host.scope.launch {
            delay(DIAL_ACTIVE_MILLIS)
            state.update { it.copy(dialActive = false, callProgress = it.callProgress.takeUnless { p -> p is CallProgress.PickUp }) }
        }
    }

    fun clearDialActive() {
        dialTimeout?.cancel()
        dialTimeout = null
        state.update { it.copy(dialActive = false) }
    }

    /** Follows the call placed last on the box' call monitor. */
    private var monitorJob: Job? = null

    /** Clears the end of a call from the dialer after [CALL_END_SHOWN_MILLIS]. */
    private var callEndJob: Job? = null

    /**
     * Shows that the box rings [phone] to dial [number], and – if the feature is on – follows the call
     * on the call monitor of the box in use: dialling once picked up (or not picked up in time), then
     * connected, ended or not answered. Only the box' report of dialling [number] counts; the other
     * calls of the household are ignored. A box with the monitor off is noted for the settings and,
     * the first time, the dialer says how to switch it on. [extension]: the digits to type once
     * connected, shown all along.
     */
    fun follow(number: String, phone: String?, extension: String?) {
        stopFollowing()
        state.update { it.copy(callProgress = CallProgress.PickUp(phone, number, extension = extension)) }
        if (!state.value.callStatusEnabled) return
        // Not known yet whether the call monitor reports internal calls: rather than saying
        // "not picked up" after a while, such a call is not followed – as without the monitor.
        if (FritzInternalPhones.isInternal(number)) return
        val profile = settings.activeProfile() ?: return
        val boxHost = state.value.config.host
        val key = host.numberKeys()
        monitorJob = host.scope.launch {
            val call = this
            var listening = false
            val follower = CallFollower(number, key)
            val pickUpTimeout = launch {
                delay(PICK_UP_TIMEOUT_MILLIS)
                showCallEnd(CallProgress.NotPickedUp)
                call.cancel()
            }
            try {
                CallMonitor.events(boxHost) {
                    listening = true
                    noteCallMonitor(profile.id, true)
                    // From now on the monitor says how long the call can be cancelled.
                    dialTimeout?.cancel()
                }.first { event ->
                    when (val step = follower.take(event)) {
                        is CallFollower.Step.Dialling -> {
                            pickUpTimeout.cancel()
                            state.update { it.copy(callProgress = CallProgress.Dialling(number, step.from, extension)) }
                            false
                        }
                        CallFollower.Step.Connected -> {
                            clearDialActive()
                            state.update {
                                it.copy(callProgress = CallProgress.Connected(number, SystemClock.elapsedRealtime(), extension))
                            }
                            false
                        }
                        is CallFollower.Step.Ended -> {
                            showCallEnd(step.progress)
                            true
                        }
                        CallFollower.Step.Ignored -> false
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
                        state.update {
                            val shown = it.callProgress
                            if (shown is CallProgress.PickUp) it.copy(callProgress = shown.copy(monitorOff = true)) else it
                        }
                    }
                } else {
                    // Lost during the call (network change, box restarted): nothing more is known.
                    clearDialActive()
                    state.update { it.copy(callProgress = null) }
                }
            }
        }
    }

    fun stopFollowing() {
        monitorJob?.cancel()
        monitorJob = null
        callEndJob?.cancel()
        callEndJob = null
    }

    /** The call is over: shows how it ended for a while. */
    private fun showCallEnd(end: CallProgress) {
        clearDialActive()
        state.update { it.copy(callProgress = end) }
        callEndJob?.cancel()
        callEndJob = host.scope.launch {
            delay(CALL_END_SHOWN_MILLIS)
            state.update { if (it.callProgress == end) it.copy(callProgress = null) else it }
        }
    }

    private fun noteCallMonitor(profileId: String, available: Boolean) {
        settings.updateCallMonitor(profileId, available)
        state.update { it.copy(profiles = settings.profiles()) }
    }

    /** Switches following the calls on the box' call monitor on or off. */
    fun setEnabled(enabled: Boolean) {
        settings.callStatusEnabled = enabled
        if (!enabled) {
            stopFollowing()
            clearDialActive()
        }
        state.update { it.copy(callStatusEnabled = enabled, callProgress = if (enabled) it.callProgress else null) }
        if (enabled) checkMonitors()
    }

    /**
     * Asks the boxes that answered the last detection whether their call monitor is on (the others
     * keep what was known: they could not tell).
     */
    fun checkMonitors() {
        if (state.value.checkingCallMonitors) return
        val seen = state.value.boxesSeen.orEmpty()
        val profiles = settings.profiles().filter { it.id in seen }
        if (profiles.isEmpty()) return
        state.update { it.copy(checkingCallMonitors = true) }
        host.scope.launch {
            profiles.map { p -> async { p.id to CallMonitor.isAvailable(p.config.host) } }.awaitAll()
                .forEach { (id, available) -> settings.updateCallMonitor(id, available) }
            state.update { it.copy(profiles = settings.profiles(), checkingCallMonitors = false) }
        }
    }
}
