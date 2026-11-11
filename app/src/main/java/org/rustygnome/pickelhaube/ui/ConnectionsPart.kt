package org.rustygnome.pickelhaube.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.HostCheck
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts
import org.rustygnome.pickelhaube.tr064.BoxDiscovery
import org.rustygnome.pickelhaube.tr064.BoxUsers
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.FritzDialer

/**
 * The connection page: adding, editing and deleting a box – what answers at the address entered, its
 * users and HTTPS port, and saving only once the box accepted the login (or, asked, without that).
 */
internal class ConnectionsPart(private val host: DialerHost) {

    private val state get() = host.state
    private val settings get() = host.settings

    /** Connection page for a new FritzBox. */
    fun add() = state.update {
        it.copy(
            showConnectionSettings = true,
            editingProfileId = null,
            editingName = "",
            config = FritzConfig(),
            boxUsers = emptyList(),
            securityPort = null,
            hostNote = null,
            saveCheck = null,
        )
    }

    /** Connection page for an existing FritzBox. */
    fun edit(id: String) {
        val profile = settings.profiles().firstOrNull { it.id == id } ?: return
        state.update {
            it.copy(
                showConnectionSettings = true,
                editingProfileId = id,
                editingName = profile.name,
                config = profile.config,
                boxUsers = emptyList(),
                securityPort = null,
                hostNote = null,
                saveCheck = null,
            )
        }
        refreshUserRights(profile)
    }

    private var rightsJob: Job? = null

    /**
     * Asks the box again what [profile]'s user may do, so the connection page shows the rights as
     * they are now – they may have been changed on the box since they were last read.
     */
    private fun refreshUserRights(profile: BoxProfile) {
        rightsJob?.cancel()
        rightsJob = host.scope.launch {
            val rights = withContext(Dispatchers.IO) { readUserRights(FritzDialer(profile.config)) } ?: return@launch
            settings.updateUserRights(profile.id, rights)
            state.update { it.copy(profiles = settings.profiles()) }
        }
    }

    private var usersJob: Job? = null

    /**
     * Asks the box at the address entered on the connection page ([edited]: host, port, HTTPS) for
     * its users. Only a suggestion, so a failure – e.g. an older FRITZ!OS, or no box there – just
     * leaves the list empty.
     */
    fun lookUpUsers(edited: FritzConfig) {
        usersJob?.cancel()
        state.update { it.copy(boxUsers = emptyList(), hostNote = null) }
        val config = edited.copy(trustedCertDer = state.value.config.trustedCertDer)
        usersJob = host.scope.launch {
            // Another box there would offer its own users – and they would mean nothing for this one.
            val check = checkHost(config.host)
            if (!state.value.showConnectionSettings) return@launch
            if (check is HostCheck.OtherBox || check is HostCheck.AlreadySetUp) {
                state.update { it.copy(hostNote = HostNote(config.host, check)) }
            }
            if (check is HostCheck.OtherBox) return@launch
            val users = withContext(Dispatchers.IO) { runCatching { BoxUsers.list(config) }.getOrNull() }
            if (users != null && state.value.showConnectionSettings) state.update { it.copy(boxUsers = users) }
        }
    }

    private var securityPortJob: Job? = null

    /** Asks the box at [host] (entered on the connection page) on which port it talks HTTPS. */
    fun lookUpSecurityPort(host: String) {
        securityPortJob?.cancel()
        securityPortJob = this.host.scope.launch {
            if (checkHost(host) is HostCheck.OtherBox) return@launch
            val port = withContext(Dispatchers.IO) { BoxDiscovery.securityPort(host) } ?: return@launch
            if (state.value.showConnectionSettings) state.update { it.copy(securityPort = SecurityPort(host, port)) }
        }
    }

    /** Which box answers at [host], compared with the connection being edited and the others (see [HostCheck]). */
    private suspend fun checkHost(host: String): HostCheck {
        val identity = withContext(Dispatchers.IO) { BoxDiscovery.identify(host) }
        val profiles = settings.profiles()
        return HostCheck.of(identity, profiles.firstOrNull { it.id == state.value.editingProfileId }, profiles)
    }

    /** Leaves the connection page without saving: back to the connection in use. */
    fun close() {
        host.verifyingSettings = false
        pendingSave = null
        state.update {
            it.copy(
                showConnectionSettings = false,
                editingProfileId = null,
                hostNote = null,
                saveCheck = null,
                config = activeConfig(),
                error = null,
            )
        }
    }

    /**
     * Tries the edited connection against the router. Only if that works it is saved – and made
     * the active one, since the box evidently is reachable from here; otherwise the error is shown.
     * [phonebooksEnabled], [callListEnabled], [speedDialsEnabled]: whether this box' phone books, call
     * list and speed dials are used; [internalCallsEnabled]: whether its phones are offered for
     * internal calls.
     */
    fun save(
        edited: FritzConfig,
        name: String,
        phonebooksEnabled: Boolean,
        callListEnabled: Boolean,
        speedDialsEnabled: Boolean,
        internalCallsEnabled: Boolean,
    ) {
        if (state.value.busy) return
        val request = SaveRequest(
            edited.copy(trustedCertDer = state.value.config.trustedCertDer),
            name, phonebooksEnabled, callListEnabled, speedDialsEnabled, internalCallsEnabled,
        )
        // First, without logging in, which box answers there: logging in to another one would hand it
        // this box' credentials, and take over its certificate and device id.
        state.update { it.copy(busy = true, status = uiText(R.string.status_connecting, request.config.host), error = null) }
        host.scope.launch {
            val check = checkHost(request.config.host)
            state.update { it.copy(busy = false, status = null) }
            if (!state.value.showConnectionSettings) return@launch
            if (check == HostCheck.Expected) {
                saveChecked(request)
            } else {
                pendingSave = request
                val editing = settings.profiles().firstOrNull { it.id == state.value.editingProfileId }
                val shown = request.name.trim().ifEmpty { editing?.name ?: request.config.host }
                state.update { it.copy(saveCheck = SaveCheck(request.config.host, shown, check)) }
            }
        }
    }

    /** The connection page's entries, kept while [SaveCheck] waits for a decision. */
    private data class SaveRequest(
        val config: FritzConfig,
        val name: String,
        val phonebooksEnabled: Boolean,
        val callListEnabled: Boolean,
        val speedDialsEnabled: Boolean,
        val internalCallsEnabled: Boolean,
    )

    private var pendingSave: SaveRequest? = null

    /** [SaveCheck] answered with "save anyway": checked against the box there after all (a new connection only). */
    fun saveAnyway() {
        val request = pendingSave ?: return
        dismissSaveCheck()
        saveChecked(request)
    }

    /**
     * [SaveCheck] answered with "save without checking": the entries are stored as they are, while
     * all the box told about itself before – device id, details, certificate – stays. The box in use
     * stays as well: this one was not found here.
     */
    fun saveUnchecked() {
        val request = pendingSave ?: return
        dismissSaveCheck()
        val previous = settings.profiles().firstOrNull { it.id == state.value.editingProfileId } ?: return
        val profile = previous.copy(
            name = request.name.trim().ifEmpty { previous.name },
            config = request.config,
            // Rights read before belong to the user of then.
            userRights = previous.userRights?.takeIf { previous.config.username == request.config.username },
            phonebooksEnabled = request.phonebooksEnabled,
            callListEnabled = request.callListEnabled,
            speedDialsEnabled = request.speedDialsEnabled,
            internalCallsEnabled = request.internalCallsEnabled,
        )
        settings.saveProfile(profile)
        LauncherShortcuts.publish(host.app)
        val active = settings.activeProfileId == profile.id
        state.update {
            it.copy(
                profiles = settings.profiles(),
                editingProfileId = null,
                showSettings = it.settingsSaved,
                showConnectionSettings = false,
                config = activeConfig(),
            )
        }
        // Chosen by hand while elsewhere: its changed entries are to be used from now on.
        if (active) host.activateProfile(profile.id)
    }

    fun dismissSaveCheck() {
        pendingSave = null
        state.update { it.copy(saveCheck = null) }
    }

    private fun saveChecked(request: SaveRequest) {
        val config = request.config
        val name = request.name
        val phonebooksEnabled = request.phonebooksEnabled
        val callListEnabled = request.callListEnabled
        val speedDialsEnabled = request.speedDialsEnabled
        val editingId = state.value.editingProfileId
        host.verifyingSettings = true
        state.update { it.copy(config = config, error = null) }
        host.runOnBox(uiText(R.string.status_connecting, config.host)) { dialer ->
            val message = host.loadPhones(dialer, request.internalCallsEnabled)
            // Learn the box' device id, so it can be recognised automatically later.
            val identity = BoxDiscovery.identify(config.host)
            val info = readBoxInfo(dialer)
            val codes = readDialingCodes(dialer)
            val rights = readUserRights(dialer)
            val previous = settings.profiles().firstOrNull { it.id == editingId }
            val profile = BoxProfile(
                id = editingId ?: java.util.UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { identity?.friendlyName ?: previous?.name ?: config.host },
                config = state.value.config,
                deviceId = identity?.udn ?: previous?.deviceId,
                model = info?.model ?: identity?.modelName ?: previous?.model,
                // Without the rights for GetInfo, the public device description tells them as well.
                softwareVersion = info?.softwareVersion ?: identity?.softwareVersion ?: previous?.softwareVersion,
                serialNumber = info?.serialNumber ?: identity?.serialNumber ?: previous?.serialNumber,
                dialingCodes = codes ?: previous?.dialingCodes,
                // Rights read before belong to the user of then.
                userRights = rights ?: previous?.userRights?.takeIf { previous.config.username == config.username },
                phonebooksEnabled = phonebooksEnabled,
                // Without the right the call list could only fail: off, the connection page says why.
                callListEnabled = callListEnabled && rights?.phone != false,
                speedDialsEnabled = speedDialsEnabled,
                internalCallsEnabled = request.internalCallsEnabled,
            )
            settings.saveProfile(profile)
            settings.activeProfileId = profile.id
            host.verifyingSettings = false
            LauncherShortcuts.publish(host.app)
            // It just answered, so it is the one reachable – under the name it has now.
            LauncherShortcuts.updateBox(host.app, profile)
            // First setup: straight to the dialer. Later changes: back to the settings overview.
            state.update {
                it.copy(
                    profiles = settings.profiles(),
                    activeProfileId = profile.id,
                    // It just answered its device description (if not: it is not a FritzBox we can recognise).
                    activeBoxSeen = identity != null,
                    boxesSeen = if (identity != null) it.boxesSeen.orEmpty() + profile.id else it.boxesSeen,
                    editingProfileId = null,
                    networkNotice = null,
                    phonebooks = emptyList(),
                    phonebooksLoaded = false,
                    boxCalls = emptyList(),
                    newMissedCalls = 0,
                    lines = emptyList(),
                    ownNumbers = emptyList(),
                    boxCallsLoaded = false,
                    deflections = null,
                    showSettings = it.settingsSaved,
                    showConnectionSettings = false,
                    settingsSaved = true,
                    // Connected from the welcome tour: it goes on to its last page.
                    welcomeConnected = it.showWelcome,
                )
            }
            message
        }
        host.preloadFromBox()
    }

    fun delete(id: String) {
        val wasActive = settings.activeProfileId == id
        settings.deleteProfile(id)
        val remaining = settings.profiles()
        state.update {
            it.copy(
                profiles = remaining,
                activeProfileId = settings.activeProfileId,
                boxesSeen = it.boxesSeen?.minus(id),
                settingsSaved = remaining.isNotEmpty(),
                showConnectionSettings = if (it.editingProfileId == id) false else it.showConnectionSettings,
                editingProfileId = if (it.editingProfileId == id) null else it.editingProfileId,
            )
        }
        when {
            remaining.isEmpty() -> {
                LauncherShortcuts.removeAll(host.app)
                state.update { it.copy(phones = emptyList(), internalPhones = emptyList(), selectedPhone = null, config = FritzConfig(), deflections = null) }
                add()
            }
            wasActive -> host.activateProfile(remaining.first().id)
        }
    }

    private fun activeConfig(): FritzConfig = settings.activeProfile()?.config ?: FritzConfig()
}
