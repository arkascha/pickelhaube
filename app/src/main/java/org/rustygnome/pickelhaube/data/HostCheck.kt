package org.rustygnome.pickelhaube.data

import org.rustygnome.pickelhaube.tr064.BoxIdentity

/**
 * Which box answers at the host of a connection that is being saved, compared with the boxes set
 * up. Most boxes are set up as "fritz.box", which always leads to the box of the current network: a
 * box set up for elsewhere must not be checked by logging in to that one – its credentials would go
 * to the wrong box, and that box' certificate and device id would be taken over.
 */
sealed interface HostCheck {

    /** The box being edited answers – or, for a new connection, a box not set up yet (or none). */
    data object Expected : HostCheck

    /** No box answers there: the box being edited cannot be checked from here. */
    data object NoAnswer : HostCheck

    /** Another box than the one being edited answers there, under [name]. */
    data class OtherBox(val name: String) : HostCheck

    /** For a new connection: the box there is set up already, as [name]. */
    data class AlreadySetUp(val name: String) : HostCheck

    companion object {

        /** [identity]: what answered at the host (null: nothing); [editing]: the connection being edited, null for a new one. */
        fun of(identity: BoxIdentity?, editing: BoxProfile?, profiles: List<BoxProfile>): HostCheck {
            val known = identity?.let { found -> profiles.firstOrNull { it.id != editing?.id && it.deviceId == found.udn } }
            return when {
                editing == null -> if (known != null) AlreadySetUp(known.name) else Expected
                identity == null -> NoAnswer
                editing.deviceId == identity.udn -> Expected
                // Never recognised yet, so any box not set up otherwise may be it.
                editing.deviceId == null && known == null -> Expected
                else -> OtherBox(known?.name ?: identity.friendlyName ?: identity.modelName ?: identity.host)
            }
        }
    }
}
