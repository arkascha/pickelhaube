package org.rustygnome.pickelhaube.tr064

import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * One number of a contact, with the box' type tag ("home", "mobile", "work", …) – or, for the
 * phone's own contacts, Android's label for it. The UI turns it into a label. [speedDial] is the
 * box' speed dial for it ("00"–"99", dialled on a handset as `**7` and this), if one is set up.
 */
data class PhonebookNumber(val number: String, val type: String, val speedDial: String? = null)

data class PhonebookContact(
    val name: String,
    val numbers: List<PhonebookNumber>,
    /** Name of the phone book this contact comes from. */
    val book: String,
    /** For a contact of the box: the id of its phone book and its entry's unique id, to change it. */
    val bookId: Int? = null,
    val uid: String? = null,
)

data class Phonebook(
    val id: Int,
    val name: String,
    val contacts: List<PhonebookContact>,
    /**
     * An online phone book (Google, CardDAV, …) the box syncs from its provider: the box does not
     * let it be changed – no new entries, no speed dials.
     */
    val online: Boolean = false,
)

/**
 * Reads the phone books of the box via the TR-064 X_AVM-DE_OnTel service: the list of
 * phone book ids, then per id a download URL for an XML export of its contacts.
 * Online phone books configured on the box (e.g. CardDAV) are synced into the box and,
 * as far as known, show up in the same list.
 */
class FritzPhonebooks(config: FritzConfig, authToken: String? = null) {

    private val client = Tr064Client(config, authToken)

    @Throws(IOException::class)
    fun loadAll(): List<Phonebook> = listIds().map { load(it) }

    @Throws(IOException::class)
    fun listIds(): List<Int> =
        call("GetPhonebookList")["NewPhonebookList"].orEmpty()
            .split(',').mapNotNull { it.trim().toIntOrNull() }

    @Throws(IOException::class)
    fun load(id: Int): Phonebook {
        val info = call("GetPhonebook", mapOf("NewPhonebookId" to id.toString()))
        val name = info["NewPhonebookName"].orEmpty().ifBlank { "Phone book ${id + 1}" }
        val url = info["NewPhonebookURL"].orEmpty()
        if (url.isBlank()) return Phonebook(id, name, emptyList())
        val xml = client.download(url)
        return Phonebook(id, name, parseContacts(xml, name, id), isOnline(xml))
    }

    /** Adds [contact] with its numbers as a new entry to phone book [id] (`SetPhonebookEntryUID`). */
    @Throws(IOException::class)
    fun add(id: Int, contact: PhonebookContact) {
        call("SetPhonebookEntryUID", mapOf("NewPhonebookID" to id.toString(), "NewPhonebookEntryData" to entryXml(contact)))
    }

    /**
     * Gives speed dial [slot] to [number] of entry [uid] in phone book [id] and takes it from the
     * entry's other numbers; with [number] null it only takes it away. The rest of the entry stays.
     */
    @Throws(IOException::class)
    fun setSpeedDial(id: Int, uid: String, slot: String, number: String?) {
        val entry = call("GetPhonebookEntryUID", mapOf("NewPhonebookID" to id.toString(), "NewPhonebookEntryUniqueID" to uid))
        val xml = entry["NewPhonebookEntryData"].orEmpty().ifBlank { throw IOException("Phone book entry $uid not found") }
        call(
            "SetPhonebookEntryUID",
            mapOf("NewPhonebookID" to id.toString(), "NewPhonebookEntryData" to withSpeedDial(xml, uid, slot, number)),
        )
    }

    private fun call(action: String, args: Map<String, String> = emptyMap()) =
        client.call(SERVICE_TYPE, CONTROL_URL, action, args)

    companion object {
        const val SERVICE_TYPE = "urn:dslforum-org:service:X_AVM-DE_OnTel:1"
        const val CONTROL_URL = "/upnp/control/x_contact"

        /**
         * Parses the box' phone book export:
         * `<phonebooks><phonebook name="…"><contact><person><realName>…</realName></person>
         *  <telephony><number type="home" quickdial="12" …>030123</number>…</telephony></contact>…`
         */
        fun parseContacts(xml: String, bookName: String, bookId: Int? = null): List<PhonebookContact> {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))

            val contacts = mutableListOf<PhonebookContact>()
            var inContact = false
            var name = ""
            var uid: String? = null
            var numbers = mutableListOf<PhonebookNumber>()
            var numberType = ""
            var speedDial: String? = null
            val text = StringBuilder()

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "contact" -> {
                                inContact = true
                                name = ""
                                uid = null
                                numbers = mutableListOf()
                            }
                            "number" -> {
                                numberType = parser.getAttributeValue(null, "type").orEmpty()
                                speedDial = speedDialSlot(parser.getAttributeValue(null, "quickdial"))
                            }
                        }
                        text.setLength(0)
                    }
                    XmlPullParser.TEXT -> text.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        if (inContact) when (parser.name) {
                            "realName" -> name = text.toString().trim()
                            "uniqueid" -> uid = text.toString().trim().takeIf { it.isNotEmpty() }
                            "number" -> {
                                val n = text.toString().trim()
                                if (n.isNotEmpty()) numbers += PhonebookNumber(n, numberType, speedDial)
                            }
                            "contact" -> {
                                inContact = false
                                if (numbers.isNotEmpty()) {
                                    contacts += PhonebookContact(name.ifBlank { numbers.first().number }, numbers, bookName, bookId, uid)
                                }
                            }
                        }
                        text.setLength(0)
                    }
                }
                parser.next()
            }
            return contacts.sortedBy { it.name.lowercase() }
        }

        /**
         * A new phone book entry as the box takes it: `<contact><category>0</category><person>
         * <realName>…</realName></person><telephony><number type="home" prio="1" id="0">…</number>…`,
         * the first number the preferred one.
         */
        internal fun entryXml(contact: PhonebookContact): String = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?><contact><category>0</category><person><realName>")
            append(Tr064Client.escapeXml(contact.name))
            append("</realName></person><telephony nid=\"").append(contact.numbers.size).append("\">")
            contact.numbers.forEachIndexed { i, n ->
                append("<number type=\"").append(boxNumberType(n.type)).append('"')
                n.speedDial?.let { append(" quickdial=\"").append(it).append('"') }
                append(" prio=\"").append(if (i == 0) 1 else 0).append("\" id=\"").append(i).append("\">")
                append(Tr064Client.escapeXml(n.number)).append("</number>")
            }
            append("</telephony></contact>")
        }

        /** The box' type of a number: its own tags are kept, anything else (e.g. a custom label) is "other". */
        private fun boxNumberType(type: String): String =
            type.lowercase().takeIf { it in BOX_NUMBER_TYPES } ?: "other"

        private val BOX_NUMBER_TYPES = setOf("home", "mobile", "work", "fax_work", "intern", "memo", "other")

        /**
         * [xml], an entry as `GetPhonebookEntryUID` returns it, with speed dial [slot] at [number]
         * (matched as written in the entry) and at none of its other numbers; with [number] null
         * only taken away. Its unique id [uid] is made sure of, so the box changes this entry
         * rather than adding a new one.
         */
        internal fun withSpeedDial(xml: String, uid: String, slot: String, number: String?): String {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val contact = doc.documentElement
            if (contact.getElementsByTagName("uniqueid").length == 0) {
                contact.appendChild(doc.createElement("uniqueid").apply { textContent = uid })
            }
            val numbers = contact.getElementsByTagName("number")
            for (i in 0 until numbers.length) {
                val element = numbers.item(i) as Element
                when {
                    number != null && element.textContent.trim() == number.trim() -> element.setAttribute("quickdial", slot)
                    speedDialSlot(element.getAttribute("quickdial")) == slot -> element.removeAttribute("quickdial")
                }
            }
            val out = StringWriter()
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "utf-8")
            }.transform(DOMSource(doc), StreamResult(out))
            return out.toString()
        }

        /**
         * Whether the export is of an online phone book: the box' own ones have `owner="0"` in the
         * `<phonebook>` element, the ones synced from a provider an owner from 240 on.
         */
        internal fun isOnline(xml: String): Boolean =
            OWNER.find(xml)?.groupValues?.get(1)?.toIntOrNull()?.let { it >= FIRST_ONLINE_OWNER } ?: false

        private val OWNER = Regex("""<phonebook\b[^>]*\bowner="(\d+)"""")
        private const val FIRST_ONLINE_OWNER = 240

        /** The `quickdial` attribute as a two-digit speed dial ("5" → "05"); null if none is set. */
        fun speedDialSlot(raw: String?): String? =
            raw?.trim()?.takeIf { it.matches(Regex("\\d{1,2}")) }?.padStart(2, '0')
    }
}
