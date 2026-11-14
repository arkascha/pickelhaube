package org.rustygnome.pickelhaube.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** What kind of network the phone is in: to say why no FRITZ!Box answers, and what may help. */
enum class NetworkKind {
    /** No network at all. */
    NONE,

    /** Mobile data only: away from home, and no VPN. */
    MOBILE,

    /** A Wi-Fi or a cable – at home it would be the box' own; here none of the boxes answered in it. */
    LOCAL,

    /** A VPN is on: the boxes are only reached if it leads into their network. */
    VPN;

    companion object {
        /** The phone's network now (the default network, which a VPN takes over while on). */
        fun current(context: Context): NetworkKind {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NONE
            val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            return of(
                connected = caps != null,
                vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
                cellularOnly = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            )
        }

        /** A VPN counts before the network beneath it: through it, the box may well be reached. */
        internal fun of(connected: Boolean, vpn: Boolean, cellularOnly: Boolean): NetworkKind = when {
            !connected -> NONE
            vpn -> VPN
            cellularOnly -> MOBILE
            else -> LOCAL
        }
    }
}
