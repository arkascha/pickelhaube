package org.rustygnome.pickelhaube.tr064

import java.io.IOException

/** A phone of the box, called from another one of its phones by its internal [number] ("**611"). */
data class InternalPhone(val name: String, val number: String, val kind: Kind) {
    enum class Kind { ANALOGUE, DECT, IP, ALL }
}

/**
 * The box' phones with their internal numbers, for internal calls:
 * - analogue phones from the box' phone ports ([FritzDialer.listPhones]): "FON1: …" is `**1`, "FON2: …" `**2`;
 * - DECT handsets from `X_AVM-DE_Dect` `GetGenericDectEntry`, which tells their name and id but not
 *   their number: the n-th handset is `**61(n-1)`, `**610` to `**615`, as the box numbers them;
 * - IP phones (also FRITZ!App Fon) from `X_VoIP` `X_AVM-DE_GetClient3`, which tells their number;
 * - all phones at once, `**9`.
 * Each part is read on its own: one the box does not answer (user rights, older FRITZ!OS) is left out.
 */
class FritzInternalPhones(config: FritzConfig, authToken: String? = null) {

    private val client = Tr064Client(config, authToken)

    /** The phones, [ports] being the box' phone ports as [FritzDialer.listPhones] gave them. */
    fun load(ports: List<String>): List<InternalPhone> {
        val analogue = ports.mapNotNull { port ->
            analogueNumber(port)?.let { InternalPhone(FritzDialer.displayName(port), it, InternalPhone.Kind.ANALOGUE) }
        }
        val dect = orEmpty { dectHandsets() }
        val ip = orEmpty { ipPhones() }
        val all = if (analogue.size + dect.size + ip.size > 1) listOf(InternalPhone("", ALL_PHONES, InternalPhone.Kind.ALL)) else emptyList()
        return analogue + dect + ip + all
    }

    private fun dectHandsets(): List<InternalPhone> {
        val count = client.call(DECT_SERVICE, DECT_CONTROL_URL, "GetNumberOfDectEntries")["NewNumberOfEntries"]?.toIntOrNull() ?: 0
        return (0 until count).mapNotNull { index ->
            val entry = client.call(DECT_SERVICE, DECT_CONTROL_URL, "GetGenericDectEntry", mapOf("NewIndex" to index.toString()))
            if (entry["NewActive"] == "0") return@mapNotNull null
            val number = dectNumber(entry["NewID"]) ?: return@mapNotNull null
            InternalPhone(entry["NewName"].orEmpty().trim().ifEmpty { number }, number, InternalPhone.Kind.DECT)
        }
    }

    private fun ipPhones(): List<InternalPhone> {
        val count = voip("X_AVM-DE_GetNumberOfClients")["NewX_AVM-DE_NumberOfClients"]?.toIntOrNull() ?: 0
        return (0 until count).mapNotNull { index ->
            val client = voip("X_AVM-DE_GetClient3", mapOf("NewX_AVM-DE_ClientIndex" to index.toString()))
            val number = ipNumber(client["NewX_AVM-DE_InternalNumber"]) ?: return@mapNotNull null
            InternalPhone(client["NewX_AVM-DE_PhoneName"].orEmpty().trim().ifEmpty { number }, number, InternalPhone.Kind.IP)
        }
    }

    private fun voip(action: String, args: Map<String, String> = emptyMap()) =
        client.call(FritzDialer.SERVICE_TYPE, FritzDialer.CONTROL_URL, action, args)

    private fun orEmpty(load: () -> List<InternalPhone>): List<InternalPhone> = try {
        load()
    } catch (_: IOException) {
        emptyList()
    }

    companion object {
        const val DECT_SERVICE = "urn:dslforum-org:service:X_AVM-DE_Dect:1"
        const val DECT_CONTROL_URL = "/upnp/control/x_dect"

        /** Rings all phones of the box. */
        const val ALL_PHONES = "**9"

        private val FON_PORT = Regex("""^\s*FON\s*(\d)\s*:""", RegexOption.IGNORE_CASE)

        /** Internal numbers as dialled on a phone of the box: "**" and digits – not its speed dials (**7xx). */
        private val INTERNAL = Regex("""^\*\*(?!7\d\d$)\d{1,3}$""")

        /** "FON1: Hallway" → `**1`; null for other ports. */
        fun analogueNumber(port: String): String? = FON_PORT.find(port)?.groupValues?.get(1)?.let { "**$it" }

        /** The DECT handset with id 1 to 6 → `**610` to `**615`. */
        fun dectNumber(id: String?): String? = id?.trim()?.toIntOrNull()?.takeIf { it in 1..6 }?.let { "**61${it - 1}" }

        /** An IP phone's internal number as the box gives it ("620", "**620") → `**620`. */
        fun ipNumber(raw: String?): String? {
            val digits = raw?.trim()?.removePrefix("**")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
            return "**$digits"
        }

        /** Whether [number] calls a phone of the box. */
        fun isInternal(number: String): Boolean = INTERNAL.matches(number)
    }
}
