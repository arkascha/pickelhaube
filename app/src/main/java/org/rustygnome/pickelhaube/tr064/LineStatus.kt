package org.rustygnome.pickelhaube.tr064

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

/** One of the box' internet numbers (VoIP account) and whether it is registered with its provider. */
data class VoipLine(
    /** The box' account index (`NewVoIPAccountIndex`). */
    val index: Int,
    val number: String,
    /** As the box says: "disabled", "not registered", "registered", "connected" or "unknown". */
    val status: String,
) {
    /** Switched on, but not registered: calls over it fail. Disabled lines and an unknown state are no finding. */
    val down: Boolean get() = status.trim().equals(STATUS_NOT_REGISTERED, ignoreCase = true)

    companion object {
        const val STATUS_NOT_REGISTERED = "not registered"
    }
}

/**
 * Whether the box' internet numbers are registered with their provider (TR-064 `X_VoIP`): the
 * numbers from `X_AVM-DE_GetNumbers`, each VoIP one's state from `X_AVM-DE_GetVoIPStatus`.
 * `X_AVM-DE_GetVoIPAccounts` is deliberately not used – it hands out the accounts' credentials.
 */
class FritzLines(config: FritzConfig, authToken: String? = null) {

    private val client = Tr064Client(config, authToken)

    /** The box' VoIP lines with their state; a line whose state cannot be read is left out. */
    @Throws(IOException::class)
    fun load(): List<VoipLine> =
        voipNumbers(call("X_AVM-DE_GetNumbers")["NewNumberList"].orEmpty()).mapNotNull { (index, number) ->
            val status = try {
                call("X_AVM-DE_GetVoIPStatus", mapOf("NewVoIPAccountIndex" to index.toString()))["NewX_AVM-DE_VoIPStatus"]
            } catch (_: Tr064Exception) {
                null
            }
            status?.let { VoipLine(index, number, it) }
        }

    private fun call(action: String, args: Map<String, String> = emptyMap()) =
        client.call(FritzDialer.SERVICE_TYPE, FritzDialer.CONTROL_URL, action, args)

    companion object {
        /**
         * The VoIP numbers (account index to number) of `<List><Item><Number>030123456</Number>
         * <Type>eVoIP</Type><Index>0</Index><Name></Name></Item>…</List>` – POTS, ISDN and mobile
         * numbers have no registration.
         */
        internal fun voipNumbers(xml: String): List<Pair<Int, String>> {
            if (xml.isBlank()) return emptyList()
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))
            val numbers = mutableListOf<Pair<Int, String>>()
            var item: MutableMap<String, String>? = null
            val text = StringBuilder()
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "Item") item = HashMap()
                        text.setLength(0)
                    }
                    XmlPullParser.TEXT -> text.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        val values = item
                        if (parser.name == "Item" && values != null) {
                            val index = values["Index"]?.toIntOrNull()
                            val number = values["Number"].orEmpty()
                            if (index != null && number.isNotEmpty() && values["Type"].equals("eVoIP", ignoreCase = true)) {
                                numbers += index to number
                            }
                            item = null
                        } else {
                            values?.put(parser.name, text.toString().trim())
                        }
                        text.setLength(0)
                    }
                }
                parser.next()
            }
            return numbers
        }
    }
}
