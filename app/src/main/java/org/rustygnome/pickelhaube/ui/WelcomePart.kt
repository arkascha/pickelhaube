package org.rustygnome.pickelhaube.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pickelhaube.tr064.BoxDiscovery
import org.rustygnome.pickelhaube.tr064.DemoBox
import org.rustygnome.pickelhaube.tr064.FritzConfig

/**
 * The welcome tour: on first start, or started again from the settings to add another box. Its
 * connect step saves the box the way the connection page does (see [ConnectionsPart.save]).
 */
internal class WelcomePart(private val host: DialerHost, private val connections: ConnectionsPart) {

    private val state get() = host.state
    private val settings get() = host.settings

    /** Starts the welcome tour again from the settings: its connect step adds another box. */
    fun start() {
        connections.add()
        state.update { it.copy(showWelcome = true, welcomeBox = null, welcomeConnected = false) }
    }

    private var findJob: Job? = null

    /**
     * Asks, without logging in, which box answers at [host] – for the tour's "find your box" step –
     * and the users it has, for the next one.
     */
    fun find(host: String) {
        val trimmed = host.trim()
        findJob?.cancel()
        if (trimmed.isEmpty()) return state.update { it.copy(welcomeBox = null) }
        state.update { it.copy(welcomeBox = WelcomeBox.Searching(trimmed)) }
        findJob = this.host.scope.launch {
            val identity = withContext(Dispatchers.IO) { BoxDiscovery.identify(trimmed) }
            val box = if (identity == null) {
                WelcomeBox.NotFound(trimmed)
            } else {
                val setUpAs = settings.profiles().firstOrNull { it.deviceId == identity.udn }?.name
                WelcomeBox.Found(trimmed, identity.friendlyName, identity.modelName, setUpAs)
            }
            state.update { it.copy(welcomeBox = box) }
        }
        connections.lookUpUsers(FritzConfig(host = trimmed))
    }

    /** The tour's connect step: saves and connects the box entered, as "Save & connect" does. */
    fun connect(name: String, host: String, user: String, password: String) {
        val config = FritzConfig(host = host.trim(), username = user.trim(), password = password)
        state.update { it.copy(editingName = name) }
        connections.save(
            config, name, phonebooksEnabled = true, callListEnabled = true, speedDialsEnabled = true, internalCallsEnabled = false,
        )
    }

    /** The tour's demo link: the demo box, connected right away. */
    fun connectDemo() = connect("Demo", DemoBox.HOST, DemoBox.USERNAME, DemoBox.PASSWORD)

    /** The error dialog of a rejected login, in the tour: closes, and the tour goes back to the password. */
    fun changePassword() {
        host.dismissError()
        state.update { it.copy(welcomeChangePassword = true) }
    }

    /** The tour went back to the password, as asked by [changePassword]. */
    fun passwordChangeShown() = state.update { it.copy(welcomeChangePassword = false) }

    /**
     * Leaves the tour without having connected: to the connection page – with what was entered
     * ([toForm], e.g. after a failure) or empty (skipped) – on first start; back to the settings
     * when started from there, unless [toForm].
     */
    fun leave(toForm: Boolean = false) {
        findJob?.cancel()
        val firstStart = !settings.isConfigured()
        state.update {
            it.copy(
                showWelcome = false,
                welcomeBox = null,
                welcomeConnected = false,
                showSettings = true,
                showConnectionSettings = firstStart || toForm,
                editingProfileId = null,
                editingName = if (toForm) it.editingName else "",
                config = if (toForm) it.config else FritzConfig(),
                saveCheck = null,
            )
        }
    }

    /** The tour's last page: on to the dialer. */
    fun finish() = state.update {
        it.copy(
            showWelcome = false,
            welcomeBox = null,
            welcomeConnected = false,
            showSettings = false,
            showConnectionSettings = false,
        )
    }
}
