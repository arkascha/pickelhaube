package org.rustygnome.pickelhaube.ui

import org.rustygnome.pickelhaube.tr064.BoxInfo
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.Tr064Exception
import org.rustygnome.pickelhaube.tr064.UserRights

/** Model, FRITZ!OS version and serial number: optional, so a failure (e.g. missing rights) is ignored. */
internal fun readBoxInfo(dialer: FritzDialer): BoxInfo? = runCatching { dialer.deviceInfo() }.getOrNull()

/** Country and area code: only for comparing numbers, so a failure is ignored as well. */
internal fun readDialingCodes(dialer: FritzDialer): DialingCodes? = runCatching { dialer.dialingCodes() }.getOrNull()

/**
 * The user's rights: for explaining refusals, so a failure is ignored as well – except a refusal
 * to tell them, which makes the ones read before unknown: none (not shown) instead of outdated.
 */
internal fun readUserRights(dialer: FritzDialer): UserRights? = try {
    dialer.userRights()
} catch (e: Tr064Exception) {
    if (e.errorCode == Tr064Exception.NOT_AUTHORIZED) UserRights(null, emptyMap()) else null
} catch (_: Exception) {
    null
}
