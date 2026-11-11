package org.rustygnome.pickelhaube.ui

import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.tr064.BoxIdentity
import org.rustygnome.pickelhaube.tr064.BoxInfo
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.UserRights

/** The connection page's entries, kept while [SaveCheck] waits for a decision. */
internal data class SaveRequest(
    val config: FritzConfig,
    val name: String,
    val phonebooksEnabled: Boolean,
    val callListEnabled: Boolean,
    val speedDialsEnabled: Boolean,
    val internalCallsEnabled: Boolean,
)

/**
 * What the box told about itself while connecting: its public device description ([identity]), and
 * – logged in – its details, dialing codes and the user's rights; each null where it did not say.
 */
internal data class BoxReport(
    val identity: BoxIdentity?,
    val info: BoxInfo?,
    val codes: DialingCodes?,
    val rights: UserRights?,
)

/**
 * The connection [id] as saved once the box accepted the login: the [request]'s entries with [config]
 * (the entered one, with the certificate trusted meanwhile), and what the box just told ([report]) –
 * where it did not say, what was known of the connection before ([previous]).
 */
internal fun checkedProfile(id: String, request: SaveRequest, config: FritzConfig, previous: BoxProfile?, report: BoxReport): BoxProfile {
    val (identity, info, codes, rights) = report
    return BoxProfile(
        id = id,
        name = request.name.trim().ifEmpty { identity?.friendlyName ?: previous?.name ?: request.config.host },
        config = config,
        deviceId = identity?.udn ?: previous?.deviceId,
        model = info?.model ?: identity?.modelName ?: previous?.model,
        // Without the rights for GetInfo, the public device description tells them as well.
        softwareVersion = info?.softwareVersion ?: identity?.softwareVersion ?: previous?.softwareVersion,
        serialNumber = info?.serialNumber ?: identity?.serialNumber ?: previous?.serialNumber,
        dialingCodes = codes ?: previous?.dialingCodes,
        // Rights read before belong to the user of then.
        userRights = rights ?: previous?.userRights?.takeIf { previous.config.username == request.config.username },
        phonebooksEnabled = request.phonebooksEnabled,
        // Without the right the call list could only fail: off, the connection page says why.
        callListEnabled = request.callListEnabled && rights?.phone != false,
        speedDialsEnabled = request.speedDialsEnabled,
        internalCallsEnabled = request.internalCallsEnabled,
    )
}

/**
 * The connection [previous] with the [request]'s entries, saved without checking: all the box told
 * about itself before – device id, details, certificate – stays.
 */
internal fun uncheckedProfile(previous: BoxProfile, request: SaveRequest): BoxProfile = previous.copy(
    name = request.name.trim().ifEmpty { previous.name },
    config = request.config,
    // Rights read before belong to the user of then.
    userRights = previous.userRights?.takeIf { previous.config.username == request.config.username },
    phonebooksEnabled = request.phonebooksEnabled,
    callListEnabled = request.callListEnabled,
    speedDialsEnabled = request.speedDialsEnabled,
    internalCallsEnabled = request.internalCallsEnabled,
)
