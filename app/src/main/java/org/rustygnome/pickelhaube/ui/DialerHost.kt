package org.rustygnome.pickelhaube.ui

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.rustygnome.pickelhaube.data.SettingsRepository
import org.rustygnome.pickelhaube.tr064.FritzDialer

/** Something to do on the box in use, with a message for the user when it worked. */
internal typealias BoxAction = suspend (FritzDialer) -> UiText

/**
 * What [DialerViewModel] shares with its parts – each one area of the app with its own class (e.g.
 * [WidgetPart]), reached from the screens as a property of the view model (`viewModel.widget`). They
 * all work on the one [state], as the screens show it.
 */
internal interface DialerHost {
    val app: Application
    val settings: SettingsRepository
    val state: MutableStateFlow<UiState>

    /** Lives as long as the view model. */
    val scope: CoroutineScope

    /**
     * True while settings entered on the connection page are being verified: the edited config is
     * only stored once the box accepted it.
     */
    var verifyingSettings: Boolean

    /** Back to the dialer, closing whatever page is open – not while no box is set up yet. */
    fun showDialer()

    /** Opens the number picker. */
    fun openPicker()

    /** Starts over with the settings stored now (after an import), as on a fresh start. */
    fun startOver()

    /** Closes the error dialog. */
    fun dismissError()

    /** Turns a number into the form it is compared by (see [numberKey]), with this phone's country and the box' dialing codes. */
    fun numberKeys(): (String) -> String

    /** Switches to the configured box [id]. */
    fun activateProfile(id: String)

    /** Loads the phones (and the rest) from the box in use. */
    fun refreshPhones()

    /** Runs [block] on the box in use, with [progress] shown – its errors, certificate questions and second factor handled. */
    fun runOnBox(progress: UiText, block: BoxAction)

    /** Reads the phones for dial help from [dialer] – and, if [internal], its phones for internal calls. */
    fun loadPhones(dialer: FritzDialer, internal: Boolean): UiText

    /** Loads in the background what else the box offers: speed dials, call deflections, missed calls, line status. */
    fun preloadFromBox()

    /** Runs [action] once no other box request is running – one per [key], so a repeated request is not repeated. */
    fun runWhenIdle(key: String, action: () -> Unit)

    /** Starts the requests that waited for [runWhenIdle], if nothing else is running. */
    fun startQueuedRequests()

    /** Reads the phone books of the box in use anew (once it is idle). */
    fun reloadRouterPhonebooks()
}
