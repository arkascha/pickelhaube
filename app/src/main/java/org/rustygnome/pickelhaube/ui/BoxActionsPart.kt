package org.rustygnome.pickelhaube.ui

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.tr064.AuthenticationException
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.Tr064Exception
import org.rustygnome.pickelhaube.tr064.UntrustedCertificateException

/** How long to wait for the user to confirm on the box before giving up. */
private const val SECOND_FACTOR_TIMEOUT_MILLIS = 120_000L

/**
 * Everything done on the box goes through here ([run]): one action at a time, the ones tapped
 * meanwhile queued, its errors shown (quietly or in full, with "Retry"), the box' certificate asked
 * about (and the interrupted action resumed once trusted, see [CertificatesPart] for the pinning),
 * and a confirmation on the box (second factor) waited for.
 */
internal class BoxActionsPart(private val host: DialerHost, private val certificates: CertificatesPart) {

    private val state get() = host.state
    private val settings get() = host.settings

    /** Action interrupted by an untrusted certificate; re-run once the user trusts it. */
    private var pendingAction: Pair<UiText, BoxAction>? = null

    /** The last action that failed, for "Retry". */
    private var failedAction: Pair<UiText, BoxAction>? = null

    /** The action running is "try again" after a quiet note: should it fail as well, then in full. */
    private var retryingQuietly = false

    /**
     * Box requests that came in while another one was running (one per [runWhenIdle] key, so
     * repeated requests are not repeated). They start one after the other once the running one
     * succeeded – not after a failure, whose error dialog or certificate question they would replace.
     */
    private val whenIdle = LinkedHashMap<String, () -> Unit>()

    /**
     * Actions tapped while the box was busy (another action, or reading in the background) – they
     * used to be dropped, so the tap seemed to do nothing. They run one after the other, each once
     * (the same one tapped twice is queued once), before the requests of [whenIdle]; also after a
     * failure, but only once its error has been read ([dismissError]) and not while the box' certificate
     * is in question.
     */
    private val queuedActions = ArrayDeque<Pair<UiText, BoxAction>>()

    private var secondFactorJob: Job? = null

    fun runWhenIdle(key: String, action: () -> Unit) {
        whenIdle[key] = action
    }

    /** The next of [queuedActions], if the box is free for it; true if one was started. */
    private fun startQueuedAction(): Boolean {
        val shown = state.value
        if (shown.busy || shown.error != null || shown.certificatePrompt != null) return false
        val (progress, block) = queuedActions.removeFirstOrNull() ?: return false
        run(progress, block)
        return true
    }

    fun startQueuedRequests() {
        if (startQueuedAction()) return
        while (whenIdle.isNotEmpty() && !state.value.busy) {
            val key = whenIdle.keys.first()
            whenIdle.remove(key)?.invoke()
        }
    }

    // --- errors -----------------------------------------------------------------------

    fun dismissError() {
        failedAction = null
        state.update { it.copy(error = null, canRetry = false, quietError = null) }
        // Tapped while the failed one was running: its turn now that the error has been read.
        startQueuedAction()
    }

    fun retry() {
        val action = failedAction ?: return
        // Tried again after a quiet note: failing once more, it is shown in full.
        retryingQuietly = state.value.quietError != null
        dismissError()
        run(action.first, action.second)
    }

    // --- the certificate prompt -------------------------------------------------------

    /** User accepted the presented certificate: pin it (see [CertificatesPart]) and retry what was interrupted. */
    fun trustCertificate() {
        if (!certificates.trustPresented()) return
        val action = pendingAction
        pendingAction = null
        if (action != null) run(action.first, action.second) else startQueuedAction()
    }

    fun rejectCertificate() {
        failedAction = pendingAction
        pendingAction = null
        // Without the box' certificate, nothing tapped meanwhile can reach it either.
        queuedActions.clear()
        state.update {
            it.copy(
                certificatePrompt = null,
                status = null,
                canRetry = failedAction != null,
                error = BoxError(
                    uiText(R.string.error_cert_rejected_title),
                    uiText(R.string.error_cert_rejected),
                    settingsRelated = true,
                ),
            )
        }
    }

    // --- running an action ------------------------------------------------------------

    /** Runs [block] on the box in use, with [progress] shown – or queues it while the box is busy. */
    fun run(progress: UiText, block: BoxAction) {
        val waiting = state.value
        if (waiting.busy || waiting.certificatePrompt != null) {
            if (queuedActions.none { it.first == progress }) queuedActions.addLast(progress to block)
            return
        }
        failedAction = null
        state.update { it.copy(busy = true, status = progress, error = null, canRetry = false, quietError = null, loginRejected = false) }
        host.scope.launch {
            val config = state.value.config
            val result = runCatching {
                withContext(Dispatchers.IO) { block(FritzDialer(config)) }
            }
            val error = result.exceptionOrNull()
            when {
                error == null -> state.update {
                    // It answered, so it is reachable after all (e.g. a reload while none was found).
                    it.copy(busy = false, status = result.getOrNull(), activeBoxWorking = true, activeBoxSeen = true)
                }

                error is Tr064Exception && error.secondFactorRequired ->
                    confirmAndRepeat(progress, block, config)

                error is UntrustedCertificateException -> {
                    pendingAction = progress to block
                    state.update {
                        it.copy(
                            busy = false,
                            status = uiText(R.string.status_waiting_cert),
                            certificatePrompt = CertificatePrompt(error.certificate, error.previouslyTrusted),
                        )
                    }
                }

                else -> {
                    failedAction = progress to block
                    // Refused: ask the box what the user may do, to name the missing right.
                    val rights = if (error is Tr064Exception && error.errorCode == Tr064Exception.NOT_AUTHORIZED) {
                        withContext(Dispatchers.IO) { readUserRights(FritzDialer(config)) }
                    } else null
                    // They have changed, apparently: keep them for the connection page as well.
                    if (rights != null && !host.verifyingSettings) {
                        settings.activeProfile()?.takeIf { it.config.host == config.host && it.config.username == config.username }
                            ?.let { profile ->
                                settings.updateUserRights(profile.id, rights)
                                state.update { it.copy(profiles = settings.profiles()) }
                            }
                    }
                    val described = describeError(error, config, rights)
                    // Likely to pass: quietly, in the dialer or the picker – unless "try again" after it failed the
                    // same way, then in full, with what to check. Elsewhere (settings, the tour, …) always in full.
                    val shown = state.value
                    val dialerOrPicker = !shown.showSettings && !shown.showWelcome && !shown.showDeflections && shown.widgetSetup == null
                    val quiet = described.passing && !retryingQuietly && !host.verifyingSettings && dialerOrPicker
                    state.update {
                        it.copy(
                            busy = false,
                            status = null,
                            error = described.takeUnless { quiet },
                            quietError = described.takeIf { quiet },
                            canRetry = true,
                            loginRejected = error is AuthenticationException,
                            // A failed check of edited settings says nothing about the box in use.
                            activeBoxWorking = if (host.verifyingSettings) it.activeBoxWorking else false,
                        )
                    }
                }
            }
            retryingQuietly = false
            if (error == null) startQueuedRequests()
        }
    }

    // --- second factor ----------------------------------------------------------------

    /**
     * The box refused [block] with 866: start a second-factor confirmation, show how to
     * confirm (dialog), wait for it, then repeat [block] with the confirmation token.
     */
    private fun confirmAndRepeat(progress: UiText, block: BoxAction, config: FritzConfig) {
        state.update { it.copy(busy = true, status = uiText(R.string.status_waiting_confirmation)) }
        secondFactorJob = host.scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val auth = FritzDialer(config)
                    val challenge = auth.startSecondFactor()
                    state.update { it.copy(secondFactor = challenge) }

                    val deadline = System.currentTimeMillis() + SECOND_FACTOR_TIMEOUT_MILLIS
                    var authState = FritzDialer.STATE_WAITING
                    while (authState == FritzDialer.STATE_WAITING && System.currentTimeMillis() < deadline) {
                        delay(1_000)
                        authState = auth.secondFactorState()
                    }
                    if (authState == FritzDialer.STATE_WAITING) {
                        runCatching { auth.stopSecondFactor() }
                        throw SecondFactorNotConfirmedException()
                    }
                    // Whatever the final state is called, the repeated action tells whether
                    // the confirmation was accepted (it fails with 866 again if not).
                    state.update { it.copy(secondFactor = null, status = progress) }
                    block(FritzDialer(config, challenge.token))
                }
            }
            val error = result.exceptionOrNull()
            if (error is CancellationException) return@launch
            state.update { it.copy(secondFactor = null, busy = false) }
            if (error == null) {
                state.update { it.copy(status = result.getOrNull(), activeBoxWorking = true) }
                startQueuedRequests()
            } else {
                failedAction = progress to block
                val shown = if (error is Tr064Exception && error.secondFactorRequired) {
                    SecondFactorNotConfirmedException()
                } else error
                state.update {
                    it.copy(
                        status = null,
                        error = describeError(shown, config),
                        canRetry = true,
                        loginRejected = shown is AuthenticationException,
                        activeBoxWorking = if (host.verifyingSettings) it.activeBoxWorking else false,
                    )
                }
            }
        }
    }

    /** User cancelled the confirmation dialog. */
    fun cancelSecondFactor() {
        secondFactorJob?.cancel()
        secondFactorJob = null
        val config = state.value.config
        host.scope.launch(Dispatchers.IO) {
            runCatching { FritzDialer(config).stopSecondFactor() }
        }
        state.update { it.copy(secondFactor = null, busy = false, status = null) }
        startQueuedAction()
    }
}
