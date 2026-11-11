package org.rustygnome.pickelhaube.ui

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.BoxRecognition
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts
import org.rustygnome.pickelhaube.tr064.BoxIdentity

/** Second detection after one that found none of the configured boxes. */
private const val DETECT_RETRY_MILLIS = 4_000L

/** Coming to the foreground detects again only if the last detection is at least this old. */
private const val DETECT_MIN_INTERVAL_MILLIS = 10_000L

/**
 * Which of the configured boxes is the one of the network the phone is in: looked for at start, on
 * every network change and back in the foreground, and switched to when found.
 */
internal class NetworkPart(private val host: DialerHost) {

    private val state get() = host.state
    private val settings get() = host.settings

    private var detectJob: Job? = null

    /** The first detection and loading, at start. */
    private var startupJob: Job? = null

    /** When the last detection started ([SystemClock.elapsedRealtime]), to skip needless repeats. */
    private var lastDetection = 0L

    /** What a detection found: the configured box of this network, if any, and whether it switched to it. */
    private data class Detection(val match: BoxProfile?, val switched: Boolean)

    /**
     * Looks for the configured boxes in the current network (see [BoxRecognition.recognise]) and
     * switches to the one found. A connection without device id learns it only once logged in (see
     * verifyAndLearn).
     */
    private suspend fun detect(): Detection {
        lastDetection = SystemClock.elapsedRealtime()
        val profiles = settings.profiles()
        if (profiles.isEmpty()) return Detection(null, false)
        val recognition = BoxRecognition.recognise(host.app, profiles)
        val found = recognition.found
        val gateway = recognition.gateway
        val match = recognition.match
        lastFound = found
        val unknown = unknownGatewayBox(found, gateway, match)
        state.update {
            it.copy(
                profiles = profiles,
                activeBoxSeen = profiles.firstOrNull { p -> p.id == settings.activeProfileId }?.let(::seenInNetwork),
                // By device id only: a connection without one is not known to be the box at its host.
                boxesSeen = seenByDeviceId(profiles, found),
                // A request that failed in the previous network says nothing about this one.
                activeBoxWorking = null,
                networkNotice = unknown?.let { box ->
                    (box.friendlyName ?: box.modelName)?.let { uiText(R.string.notice_unknown_box, it) }
                        ?: uiText(R.string.notice_unknown_box_generic)
                },
            )
        }
        if (match != null && match.id != settings.activeProfileId) {
            host.activateProfile(match.id)
            LauncherShortcuts.updateBox(host.app, match)
            return Detection(match, true)
        }
        LauncherShortcuts.updateBox(host.app, state.value.activeProfile.takeIf { !state.value.noBoxReachable })
        return Detection(match, false)
    }

    /** Right after a network change the box often does not answer yet: one more try a little later. */
    private suspend fun detectAgainIfNotFound(detection: Detection) {
        if (detection.match != null) return
        delay(DETECT_RETRY_MILLIS)
        detect()
    }

    /** Detects the box again – unless that just happened, or is happening. */
    fun detectIfStale() {
        if (SystemClock.elapsedRealtime() - lastDetection < DETECT_MIN_INTERVAL_MILLIS) return
        if (detectJob?.isActive == true) return
        detectJob = host.scope.launch { detectAgainIfNotFound(detect()) }
    }

    /** The boxes that answered the last detection; null before the first one. */
    private var lastFound: List<BoxIdentity>? = null

    /**
     * Whether [profile]'s box answered the last detection – by its device id, or by its host for a
     * connection whose device id is not known yet. Null before the first detection.
     */
    fun seenInNetwork(profile: BoxProfile): Boolean? = lastFound?.let { profile.answeredAmong(it) }

    /** Re-detects the box whenever the phone's network changes (e.g. Wi-Fi at home vs. office). */
    fun watch() {
        val cm = host.app.getSystemService(ConnectivityManager::class.java) ?: return
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
        detectJob = host.scope.launch {
            delay(1_500) // let the network settle (DHCP, routes)
            detectAgainIfNotFound(detect())
        }
    }

    /** Detects the box of this network and, if that did not switch (and load), loads from the box in use. */
    fun detectAndLoad(): Job = host.scope.launch {
        val detection = detect()
        if (!detection.switched && !state.value.noBoxReachable) host.refreshPhones()
        detectAgainIfNotFound(detection)
    }

    /**
     * The first detection and loading, at start – not the [detectJob]: a network change right at the
     * start (Android reports the current network at once) restarts that, and would take the loading with it.
     */
    fun startup() {
        startupJob = detectAndLoad()
    }

    /** Until it is known which box is reachable from here: the start, and a detection running or due. */
    suspend fun awaitDetection() {
        startupJob?.join()
        detectIfStale()
        // A network change restarts the detection: until one has run to its end.
        while (detectJob?.isActive == true) detectJob?.join()
    }

    /** Stops watching the network: the view model is gone. */
    fun stopWatching() {
        val cm = host.app.getSystemService(ConnectivityManager::class.java)
        networkCallback?.let { cb -> runCatching { cm?.unregisterNetworkCallback(cb) } }
    }
}

/** Whether this box is among the [found] ones: by its device id, or by its host for a connection whose device id is not known yet. */
internal fun BoxProfile.answeredAmong(found: List<BoxIdentity>): Boolean = found.any { box ->
    if (deviceId != null) box.udn == deviceId
    else box.host.equals(config.host, ignoreCase = true)
}

/** The [profiles] (ids) among the [found] boxes by device id only: a connection without one is not known to be the box at its host. */
internal fun seenByDeviceId(profiles: List<BoxProfile>, found: List<BoxIdentity>): Set<String> =
    profiles.filter { p -> found.any { box -> box.udn == p.deviceId } }.map { p -> p.id }.toSet()

/** The box answering at the network's [gateway] when it is none of the configured ones ([match] null): to mention it. */
internal fun unknownGatewayBox(found: List<BoxIdentity>, gateway: String?, match: BoxProfile?): BoxIdentity? =
    if (match == null) found.firstOrNull { it.host == gateway } else null
