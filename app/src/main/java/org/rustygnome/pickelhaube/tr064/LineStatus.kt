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
 * One of the box' own numbers (Telephony › Own Numbers), as `X_AVM-DE_GetNumbers` lists them: [type]
 * "eVoIP" for an internet number, "ePOTS", "eISDN", "eGSM" … for the others; [name] as given on the box.
 */
data class OwnNumber(val index: Int, val number: String, val type: String, val name: String = "") {
    val voip: Boolean get() = type.equals("eVoIP", ignoreCase = true)

    /** How it is called: its name on the box, else the number. */
    val label: String get() = name.ifBlank { number }

    /**
     * The box' code that places a single call over this internet number, dialled before the number –
     * as the box shows it in the column "Selection" of its internet numbers: `*121#` for the first,
     * `*122#` for the second, …, `*120#` for the tenth; null for the others and other lines.
     */
    val selectCode: String?
        get() = if (!voip) null else when (index) {
            in 0..8 -> "*12${index + 1}#"
            9 -> "*120#"
            else -> null
        }

    /** [number] dialled over this own number – unless it is a code itself (internal numbers, service codes). */
    fun dialling(number: String): String =
        selectCode?.takeIf { number.firstOrNull()?.let { it.isDigit() || it == '+' } == true }?.plus(number) ?: number
}

/** The box' own numbers, and the state of its internet numbers. */
data class BoxNumbers(val numbers: List<OwnNumber>, val lines: List<VoipLine>)

/**
 * The box' own numbers and whether its internet numbers are registered with their provider (TR-064
 * `X_VoIP`): the numbers from `X_AVM-DE_GetNumbers`, each VoIP one's state from `X_AVM-DE_GetVoIPStatus`.
 * `X_AVM-DE_GetVoIPAccounts` is deliberately not used – it hands out the accounts' credentials.
 */
class FritzLines(config: FritzConfig, authToken: String? = null) {

    private val client = Tr064Client(config, authToken)

    /** The box' own numbers, and its VoIP lines with their state; a line whose state cannot be read is left out. */
    @Throws(IOException::class)
    fun load(): BoxNumbers {
        val numbers = parseNumbers(call("X_AVM-DE_GetNumbers")["NewNumberList"].orEmpty())
        val lines = numbers.filter { it.voip }.mapNotNull { own ->
            val status = try {
                call("X_AVM-DE_GetVoIPStatus", mapOf("NewVoIPAccountIndex" to own.index.toString()))["NewX_AVM-DE_VoIPStatus"]
            } catch (_: Tr064Exception) {
                null
            }
            status?.let { VoipLine(own.index, own.number, it) }
        }
        return BoxNumbers(numbers, lines)
    }

    private fun call(action: String, args: Map<String, String> = emptyMap()) =
        client.call(FritzDialer.SERVICE_TYPE, FritzDialer.CONTROL_URL, action, args)

    companion object {
        /** Parses `<List><Item><Number>030123456</Number><Type>eVoIP</Type><Index>0</Index><Name></Name></Item>…</List>`. */
        internal fun parseNumbers(xml: String): List<OwnNumber> {
            if (xml.isBlank()) return emptyList()
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))
            val numbers = mutableListOf<OwnNumber>()
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
                            if (index != null && number.isNotEmpty()) {
                                numbers += OwnNumber(index, number, values["Type"].orEmpty(), values["Name"].orEmpty())
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
