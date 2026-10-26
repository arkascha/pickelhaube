package org.rustygnome.pickelhaube.tr064

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

/**
 * A call deflection set up on the box (Telephony › Call Handling › Call Forwarding). [type] says
 * which calls ("fromAll", "fromNumber", "fromAnonymous", "fromPB", "fromNotInPhonebook", "toMSN",
 * "toPOTS", "toVoIP", …), with [number] the caller's or own number it is about; [mode] when they
 * are forwarded ("eImmediately", "eBusy", "eParallelCall", …) and [toNumber] where to.
 */
data class CallDeflection(
    val id: Int,
    val enabled: Boolean,
    val type: String,
    val number: String,
    val toNumber: String,
    val mode: String,
)

/** The one of [deflections] a single switch (the quick settings tile) is for: [chosen], else the first. */
fun List<CallDeflection>.forTile(chosen: Int?): CallDeflection? = firstOrNull { it.id == chosen } ?: minByOrNull { it.id }

/**
 * The box' call deflections via the TR-064 X_AVM-DE_OnTel service. They can only be switched on
 * and off this way – set up, changed or deleted only on the box itself.
 */
class FritzDeflections(config: FritzConfig, authToken: String? = null) {

    private val client = Tr064Client(config, authToken)

    @Throws(IOException::class)
    fun load(): List<CallDeflection> =
        parse(call("GetDeflections")["NewDeflectionList"].orEmpty())

    @Throws(IOException::class)
    fun setEnabled(id: Int, enabled: Boolean) {
        call("SetDeflectionEnable", mapOf("NewDeflectionId" to id.toString(), "NewEnable" to if (enabled) "1" else "0"))
    }

    private fun call(action: String, args: Map<String, String> = emptyMap()) =
        client.call(FritzPhonebooks.SERVICE_TYPE, FritzPhonebooks.CONTROL_URL, action, args)

    companion object {
        /**
         * Parses `<List><Item><DeflectionId>0</DeflectionId><Enable>1</Enable><Type>toVoIP</Type>
         * <Number>…</Number><DeflectionToNumber>…</DeflectionToNumber><Mode>eParallelCall</Mode>…</Item></List>`.
         */
        internal fun parse(xml: String): List<CallDeflection> {
            if (xml.isBlank()) return emptyList()
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))
            val deflections = mutableListOf<CallDeflection>()
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
                            values["DeflectionId"]?.toIntOrNull()?.let { id ->
                                deflections += CallDeflection(
                                    id = id,
                                    enabled = values["Enable"] == "1",
                                    type = values["Type"].orEmpty(),
                                    number = values["Number"].orEmpty(),
                                    toNumber = values["DeflectionToNumber"].orEmpty(),
                                    mode = values["Mode"].orEmpty(),
                                )
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
            return deflections
        }
    }
}
