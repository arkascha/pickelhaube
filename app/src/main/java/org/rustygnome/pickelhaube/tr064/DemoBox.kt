package org.rustygnome.pickelhaube.tr064

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A FRITZ!Box that exists only inside the app, to try Pickelhaube without one – and for the review of
 * the app store, whose reviewers have no box at hand. It answers at the host [HOST] with user [USERNAME]
 * and password [PASSWORD], like a real box: its device description, the TR-064 actions the app uses
 * (answered in [Interceptor], in place of the network), the downloads of its phone books and call list,
 * and its call monitor. A call placed through it rings nowhere: it is picked up, connected and ended
 * after a few seconds, and then appears in its call list. Changes (speed dials, call forwardings, new
 * entries) last until the app is closed.
 *
 * The numbers are from the ranges the Bundesnetzagentur keeps free for films, so they reach nobody.
 */
object DemoBox {

    const val HOST = "demo"
    const val USERNAME = "demo"
    const val PASSWORD = "demo"

    /** Whether [host] is the demo box. */
    fun isDemo(host: String): Boolean = host.trim().equals(HOST, ignoreCase = true)

    private const val UDN = "uuid:75802409-bccb-40e7-8e6c-000000de0001"
    private const val MODEL = "FRITZ!Box 7590 AX"
    private const val SOFTWARE_VERSION = "256.08.20"
    private const val SERIAL_NUMBER = "0000000DE001"
    private const val OWN_NUMBER = "03023125300"
    private const val OFFICE_NUMBER = "03023125301"

    /** How long the box takes to answer – long enough to see that something is loading. */
    private const val LATENCY_MILLIS = 150L

    /** The steps of a call: the phone rings until picked up, the other side answers, they talk. */
    private const val RING_MILLIS = 3_000L
    private const val ANSWER_MILLIS = 3_000L
    private const val TALK_MILLIS = 8_000L

    /** The device description every box serves at `/tr64desc.xml` (see [BoxDiscovery]). */
    fun description(): String = """<?xml version="1.0"?>
        <root xmlns="urn:dslforum-org:device-1-0">
        <systemVersion><HW>256</HW><Major>256</Major><Display>$SOFTWARE_VERSION</Display></systemVersion>
        <device>
        <friendlyName>$MODEL (Demo)</friendlyName>
        <modelName>$MODEL</modelName>
        <UDN>$UDN</UDN>
        <serialNumber>$SERIAL_NUMBER</serialNumber>
        </device>
        </root>"""

    // --- the box' contents ------------------------------------------------------------

    private class Book(val name: String, val entries: MutableList<String>)

    private class Call(
        val type: Int,
        val number: String,
        val name: String?,
        val time: Long,
        val seconds: Int,
        /** The own number it went out from, or came in on. */
        val own: String = OWN_NUMBER,
    )

    private val CODES = Regex("""^(\*31#|\*12\d#)+""")

    private class Deflection(
        val id: Int,
        var enabled: Boolean,
        val type: String,
        val number: String,
        val toNumber: String,
        val mode: String,
    )

    /** Port names as the box gives them: the prefix tells the kind of phone (see [FritzDialer.displayName]). */
    private val phones = listOf("FON1: Hallway", "ISDN: Kitchen", "ISDN: Living room", "ISDN: Study", "DECT: All handsets")
    private val DECT_HANDSETS = listOf("Kitchen", "Living room", "Study")

    private var dialPhone = phones[1]
    private var nextUid = 100

    /**
     * The phone books. Like a real box (seen on a 7530 AX), it numbers them by their place in its
     * list, so one added (`AddPhonebook`, here put in by name) can move the others to new ids.
     */
    private val bookList: MutableList<Book> = mutableListOf(
        Book(
            "Family & friends",
            mutableListOf(
                contact("Anna Becker", home("03023125101"), mobile("017139200101", "01")),
                contact("Ben Hoffmann", mobile("017604069002")),
                contact("Clara Wagner", home("08999998103"), mobile("017139200103")),
                contact("Dad", home("03023125104", "02"), mobile("017604069004")),
                contact("Emma Schulz", mobile("017139200105")),
                contact("Felix Neumann", home("03023125106")),
                contact("Grandma", home("08999998107", "03")),
                contact("Hannah Klein", mobile("017604069008")),
                contact("Jonas Richter", home("03023125109"), mobile("017139200109")),
                contact("Lena Fischer", mobile("017604069010")),
                contact("Mum", home("03023125104"), mobile("017139200111", "04")),
                contact("Noah Wolf", mobile("017604069012")),
                contact("Paul Schröder", home("08999998113")),
                contact("Sophie Braun", mobile("017139200114")),
            ),
        ),
        Book(
            "Services",
            mutableListOf(
                contact("Dr. Weber, dentist", work("03023125201")),
                contact("Garage Krüger", work("08999998202")),
                contact("Pizzeria Da Marco", work("03023125203", "05")),
                contact("Vet Dr. Lang", work("03023125204")),
            ),
        ),
    )

    /** Phone book [id] – its place in [bookList]. */
    private val books: Map<Int, Book> get() = bookList.withIndex().associate { (i, book) -> i to book }

    private val calls: MutableList<Call> = System.currentTimeMillis().let { now ->
        val min = 60_000L
        val hour = 60 * min
        val day = 24 * hour
        mutableListOf(
            Call(3, "017139200111", "Mum", now - 12 * min, 340),
            Call(3, "03023125203", "Pizzeria Da Marco", now - 2 * hour, 75),
            Call(1, "017604069002", "Ben Hoffmann", now - 5 * hour, 610),
            Call(2, "08999998107", "Grandma", now - day - 3 * hour, 0),
            Call(3, "03023125201", "Dr. Weber, dentist", now - day - 6 * hour, 120, own = OFFICE_NUMBER),
            Call(1, "08999998350", null, now - 3 * day, 45, own = OFFICE_NUMBER),
            Call(1, "03023125104", "Dad", now - 4 * day, 1260),
            Call(3, "017604069010", "Lena Fischer", now - 9 * day, 300),
        )
    }

    private val deflections = listOf(
        Deflection(0, false, "toVoIP", OWN_NUMBER, "017139200111", "eDelayedOrBusy"),
        Deflection(1, false, "fromAll", "", "017139200111", "eParallelCall"),
        Deflection(2, false, "fromAnonymous", "", "08999998300", "eImmediately"),
    )

    private fun number(type: String, number: String, speedDial: String?) = Triple(type, number, speedDial)
    private fun home(n: String, speedDial: String? = null) = number("home", n, speedDial)
    private fun mobile(n: String, speedDial: String? = null) = number("mobile", n, speedDial)
    private fun work(n: String, speedDial: String? = null) = number("work", n, speedDial)

    /** An entry as the box stores and hands it out (see [FritzPhonebooks.parseContacts]). */
    private fun contact(name: String, vararg numbers: Triple<String, String, String?>): String = buildString {
        append("<contact><category>0</category><person><realName>").append(Tr064Client.escapeXml(name))
        append("</realName></person><telephony nid=\"").append(numbers.size).append("\">")
        numbers.forEachIndexed { i, (type, n, speedDial) ->
            append("<number type=\"").append(type).append('"')
            speedDial?.let { append(" quickdial=\"").append(it).append('"') }
            append(" prio=\"").append(if (i == 0) 1 else 0).append("\" id=\"").append(i).append("\">")
            append(n).append("</number>")
        }
        append("</telephony><uniqueid>").append(nextUid++).append("</uniqueid></contact>")
    }

    private fun uidOf(entry: String): String? = UNIQUE_ID.find(entry)?.groupValues?.get(1)

    private val UNIQUE_ID = Regex("""<uniqueid>\s*([^<\s]+)\s*</uniqueid>""")
    private val PROLOG = Regex("""<\?xml[^>]*\?>""")

    private fun phonebookExport(book: Book): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?><phonebooks><phonebook owner=\"0\" name=\"")
        append(Tr064Client.escapeXml(book.name)).append("\">")
        book.entries.forEach { append(it) }
        append("</phonebook></phonebooks>")
    }

    private fun callListExport(): String = buildString {
        val format = SimpleDateFormat("dd.MM.yy HH:mm", Locale.ROOT)
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?><root>")
        calls.sortedByDescending { it.time }.forEach { c ->
            append("<Call><Type>").append(c.type).append("</Type>")
            if (c.type == 3) {
                append("<Caller>").append(c.own).append("</Caller><Called>").append(c.number).append("</Called>")
            } else {
                append("<Caller>").append(c.number).append("</Caller><Called>").append(c.own).append("</Called>")
            }
            append("<Name>").append(Tr064Client.escapeXml(c.name.orEmpty())).append("</Name>")
            append("<Date>").append(format.format(Date(c.time))).append("</Date>")
            append("<Duration>").append(c.seconds / 60).append(':').append((c.seconds % 60).toString().padStart(2, '0'))
            append("</Duration></Call>")
        }
        append("</root>")
    }

    private fun deflectionList(): String = buildString {
        append("<List>")
        deflections.forEach { d ->
            append("<Item><DeflectionId>").append(d.id).append("</DeflectionId>")
            append("<Enable>").append(if (d.enabled) 1 else 0).append("</Enable>")
            append("<Type>").append(d.type).append("</Type><Number>").append(d.number).append("</Number>")
            append("<DeflectionToNumber>").append(d.toNumber).append("</DeflectionToNumber>")
            append("<Mode>").append(d.mode).append("</Mode></Item>")
        }
        append("</List>")
    }

    /** Whose [number] is, as the phone books tell. */
    private fun nameFor(number: String): String? = books.values.asSequence()
        .flatMap { book -> FritzPhonebooks.parseContacts(phonebookExport(book), book.name) }
        .firstOrNull { c -> c.numbers.any { it.number == number } }?.name

    // --- TR-064 -----------------------------------------------------------------------

    /** A SOAP fault: the box' [code] and its description. */
    private class Fault(val code: Int, val description: String) : Exception(description)

    /** Actions the box answers without login. */
    private val ANONYMOUS = setOf("GetSecurityPort", "X_AVM-DE_GetUserList")

    private val XML = "text/xml; charset=utf-8".toMediaType()
    private val ARGUMENT = Regex("""<(New[\w-]+)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Answers the requests of a [Tr064Client] for the demo box instead of the network: SOAP actions
     * and the downloads of phone books and call list, for the credentials in [config].
     */
    class Interceptor(private val config: FritzConfig) : okhttp3.Interceptor {

        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val request = chain.request()
            Thread.sleep(LATENCY_MILLIS)
            val loggedIn = config.username == USERNAME && config.password == PASSWORD
            val (code, body) = if (request.method == "GET") {
                if (loggedIn) download(request.url.encodedPath) else 401 to ""
            } else {
                val action = request.header("SOAPACTION").orEmpty().trim('"')
                val service = action.substringBefore('#')
                val name = action.substringAfter('#')
                val content = Buffer().also { request.body?.writeTo(it) }.readUtf8()
                val arguments = ARGUMENT.findAll(content).associate { it.groupValues[1] to unescapeXml(it.groupValues[2]) }
                when {
                    !loggedIn && name !in ANONYMOUS -> 401 to ""
                    else -> try {
                        200 to envelope(service, name, synchronized(DemoBox) { answer(name, arguments) })
                    } catch (f: Fault) {
                        500 to fault(f)
                    }
                }
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(body.toResponseBody(XML))
                .build()
        }
    }

    private fun download(path: String): Pair<Int, String> = synchronized(this) {
        when {
            path.startsWith("/phonebook/") -> path.substringAfterLast('/').toIntOrNull()?.let { id ->
                books[id]?.let { 200 to phonebookExport(it) }
            } ?: (404 to "")
            path == "/calllist" -> 200 to callListExport()
            else -> 404 to ""
        }
    }

    private fun answer(action: String, args: Map<String, String>): Map<String, String> = when (action) {
        "GetSecurityPort" -> mapOf("NewSecurityPort" to FritzConfig.HTTPS_PORT.toString())
        "GetInfo" -> mapOf(
            "NewManufacturerName" to "AVM",
            "NewModelName" to MODEL,
            "NewSoftwareVersion" to SOFTWARE_VERSION,
            "NewSerialNumber" to SERIAL_NUMBER,
        )
        "X_AVM-DE_GetUserList" -> mapOf("NewX_AVM-DE_UserList" to "<List><Username last_user=\"1\">$USERNAME</Username></List>")
        "X_AVM-DE_GetCurrentUser" -> mapOf(
            "NewX_AVM-DE_CurrentUsername" to USERNAME,
            "NewX_AVM-DE_CurrentUserRights" to "<rights><path>BoxAdmin</path><access>readwrite</access>" +
                "<path>Phone</path><access>readwrite</access><path>NAS</path><access>none</access>" +
                "<path>HomeAuto</path><access>none</access></rights>",
        )
        "X_AVM-DE_GetVoIPCommonCountryCode" -> mapOf("NewX_AVM-DE_LKZ" to "49", "NewX_AVM-DE_LKZPrefix" to "00")
        "X_AVM-DE_GetVoIPCommonAreaCode" -> mapOf("NewX_AVM-DE_OKZ" to "30", "NewX_AVM-DE_OKZPrefix" to "0")
        "X_AVM-DE_GetPhonePort" -> {
            val index = args["NewIndex"]?.toIntOrNull() ?: 0
            mapOf("NewX_AVM-DE_PhoneName" to (phones.getOrNull(index - 1) ?: throw Fault(713, "SpecifiedArrayIndexInvalid")))
        }
        // The DECT handsets among [phones], and an IP phone, with their internal numbers.
        "GetNumberOfDectEntries" -> mapOf("NewNumberOfEntries" to DECT_HANDSETS.size.toString())
        "GetGenericDectEntry" -> {
            val index = args["NewIndex"]?.toIntOrNull() ?: -1
            val name = DECT_HANDSETS.getOrNull(index) ?: throw Fault(713, "SpecifiedArrayIndexInvalid")
            mapOf("NewID" to (index + 1).toString(), "NewActive" to "1", "NewName" to name, "NewModel" to "0x0C")
        }
        "X_AVM-DE_GetNumberOfClients" -> mapOf("NewX_AVM-DE_NumberOfClients" to "1")
        "X_AVM-DE_GetClient3" -> {
            if (args["NewX_AVM-DE_ClientIndex"] != "0") throw Fault(713, "SpecifiedArrayIndexInvalid")
            mapOf("NewX_AVM-DE_PhoneName" to "Softphone", "NewX_AVM-DE_InternalNumber" to "620")
        }
        "X_AVM-DE_DialGetConfig" -> mapOf("NewX_AVM-DE_PhoneName" to dialPhone)
        "X_AVM-DE_DialSetConfig" -> {
            dialPhone = args["NewX_AVM-DE_PhoneName"]?.takeIf { it in phones } ?: throw Fault(402, "Invalid Args")
            emptyMap()
        }
        "X_AVM-DE_DialNumber" -> {
            placeCall(args["NewX_AVM-DE_PhoneNumber"].orEmpty().ifEmpty { throw Fault(402, "Invalid Args") })
            emptyMap()
        }
        "X_AVM-DE_DialHangup" -> {
            hangUp()
            emptyMap()
        }
        "X_AVM-DE_GetNumbers" -> mapOf(
            "NewNumberList" to "<List><Item><Number>$OWN_NUMBER</Number><Type>eVoIP</Type><Index>0</Index><Name></Name></Item>" +
                "<Item><Number>$OFFICE_NUMBER</Number><Type>eVoIP</Type><Index>1</Index><Name>Office</Name></Item></List>",
        )
        "X_AVM-DE_GetVoIPStatus" -> mapOf(
            "NewX_AVM-DE_VoIPStatus" to if (args["NewVoIPAccountIndex"] in setOf("0", "1")) "registered" else throw Fault(713, "SpecifiedArrayIndexInvalid"),
        )
        "AddPhonebook" -> {
            val name = args["NewPhonebookName"].orEmpty().ifBlank { throw Fault(402, "Invalid Args") }
            bookList += Book(name, mutableListOf())
            bookList.sortBy { it.name.lowercase() }
            emptyMap()
        }
        "GetPhonebookList" -> mapOf("NewPhonebookList" to books.keys.joinToString(","))
        "GetPhonebook" -> {
            val id = args["NewPhonebookID"]?.toIntOrNull()
            val book = books[id] ?: throw Fault(713, "SpecifiedArrayIndexInvalid")
            mapOf(
                "NewPhonebookName" to book.name,
                "NewPhonebookExtraID" to "",
                "NewPhonebookURL" to "http://$HOST:${FritzConfig.HTTP_PORT}/phonebook/$id?sid=demo",
            )
        }
        "GetPhonebookEntryUID" -> {
            val book = books[args["NewPhonebookID"]?.toIntOrNull()] ?: throw Fault(713, "SpecifiedArrayIndexInvalid")
            val entry = book.entries.firstOrNull { uidOf(it) == args["NewPhonebookEntryUniqueID"] }
                ?: throw Fault(714, "NoSuchEntryInArray")
            mapOf("NewPhonebookEntryData" to entry)
        }
        "SetPhonebookEntryUID" -> {
            val book = books[args["NewPhonebookID"]?.toIntOrNull()] ?: throw Fault(713, "SpecifiedArrayIndexInvalid")
            var entry = PROLOG.replace(args["NewPhonebookEntryData"].orEmpty(), "").trim()
            val uid = uidOf(entry)
            val index = book.entries.indexOfFirst { uidOf(it) == uid }
            if (uid != null && index >= 0) {
                book.entries[index] = entry
                mapOf("NewPhonebookEntryUniqueID" to uid)
            } else {
                val newUid = (nextUid++).toString()
                entry = entry.replace(UNIQUE_ID, "").replace("</contact>", "<uniqueid>$newUid</uniqueid></contact>")
                book.entries += entry
                mapOf("NewPhonebookEntryUniqueID" to newUid)
            }
        }
        "DeletePhonebookEntryUID" -> {
            val book = books[args["NewPhonebookID"]?.toIntOrNull()] ?: throw Fault(713, "SpecifiedArrayIndexInvalid")
            if (!book.entries.removeAll { uidOf(it) == args["NewPhonebookEntryUniqueID"] }) throw Fault(714, "NoSuchEntryInArray")
            emptyMap()
        }
        "GetCallList" -> mapOf("NewCallListURL" to "http://$HOST:${FritzConfig.HTTP_PORT}/calllist?sid=demo")
        "GetDeflections" -> mapOf("NewNumberOfDeflections" to deflections.size.toString(), "NewDeflectionList" to deflectionList())
        "SetDeflectionEnable" -> {
            val deflection = deflections.firstOrNull { it.id == args["NewDeflectionId"]?.toIntOrNull() }
                ?: throw Fault(713, "SpecifiedArrayIndexInvalid")
            deflection.enabled = args["NewEnable"] == "1"
            emptyMap()
        }
        else -> throw Fault(401, "Invalid Action")
    }

    private fun envelope(service: String, action: String, values: Map<String, String>): String = buildString {
        append("<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
        append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
        append("<u:").append(action).append("Response xmlns:u=\"").append(service).append("\">")
        values.forEach { (name, value) -> append('<').append(name).append('>').append(Tr064Client.escapeXml(value)).append("</").append(name).append('>') }
        append("</u:").append(action).append("Response></s:Body></s:Envelope>")
    }

    private fun fault(f: Fault): String =
        "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault>" +
            "<faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>" +
            "<UPnPError xmlns=\"urn:dslforum-org:control-1-0\"><errorCode>${f.code}</errorCode>" +
            "<errorDescription>${f.description}</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"

    private fun unescapeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    // --- the call monitor -------------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val monitor = MutableSharedFlow<CallEvent>(extraBufferCapacity = 16)
    private var call: Job? = null
    private var nextConnection = 0

    /** What the box' call monitor reports (see [CallMonitor.events]): always on. */
    fun monitorEvents(onListening: () -> Unit): Flow<CallEvent> = flow {
        onListening()
        emitAll(monitor)
    }

    /**
     * Dial help for [number]: the dial-help phone rings, is picked up, the box dials, the other side
     * answers and, after a short talk, hangs up – as reported on the call monitor.
     */
    private fun placeCall(dialled: String) {
        call?.cancel()
        // The codes in front, as the box takes them: number withheld (*31#), over which own number (*12x#).
        val codes = CODES.find(dialled)?.value.orEmpty()
        val number = dialled.removePrefix(codes)
        val (from, line) = if ("*122#" in codes) OFFICE_NUMBER to "SIP1" else OWN_NUMBER to "SIP0"
        val connection = (nextConnection++).toString()
        call = scope.launch {
            delay(RING_MILLIS)
            monitor.emit(CallEvent.Call(connection, extension = "11", caller = from, called = number, line = line))
            delay(ANSWER_MILLIS)
            monitor.emit(CallEvent.Connect(connection, extension = "11", number = number))
            delay(TALK_MILLIS)
            val seconds = (TALK_MILLIS / 1000).toInt()
            // Internal calls are not in the call list (as far as is known).
            if (!FritzInternalPhones.isInternal(number)) synchronized(DemoBox) {
                calls += Call(3, number, nameFor(number), System.currentTimeMillis(), seconds, own = from)
            }
            monitor.emit(CallEvent.Disconnect(connection, seconds))
        }
    }

    /** Cancels a call still ringing or being set up. */
    private fun hangUp() {
        call?.cancel()
        call = null
        monitor.tryEmit(CallEvent.Disconnect((nextConnection - 1).toString(), 0))
    }
}
