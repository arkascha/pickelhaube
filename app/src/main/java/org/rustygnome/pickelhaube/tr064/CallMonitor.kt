package org.rustygnome.pickelhaube.tr064

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One line of the box' call monitor; [connection] tells the calls apart while they last. */
sealed interface CallEvent {
    val connection: String

    /** An incoming call from [caller] to the own number [called]. */
    data class Ring(override val connection: String, val caller: String, val called: String) : CallEvent

    /** The box dials [called] from phone [extension], with the own number [caller] on [line] (e.g. `SIP0`). */
    data class Call(
        override val connection: String,
        val extension: String,
        val caller: String,
        val called: String,
        val line: String,
    ) : CallEvent

    /** The other side answered (or phone [extension] took an incoming call). */
    data class Connect(override val connection: String, val extension: String, val number: String) : CallEvent

    /** The call ended after [seconds] talking (0 when it never connected). */
    data class Disconnect(override val connection: String, val seconds: Int) : CallEvent
}

/**
 * The box' call monitor: a plain TCP connection to port 1012, on which the box reports every call as
 * it happens, one line per event – without login and unencrypted. It is off until switched on with
 * `#96*5*` on a phone connected to the box (`#96*4*` switches it off again); then the box accepts the
 * connection, else it refuses it.
 */
object CallMonitor {

    const val PORT = 1012

    private const val CONNECT_TIMEOUT_MILLIS = 2_000

    /**
     * The events of the box at [host], until the flow is cancelled (which closes the connection) or the
     * box closes it. [onListening] is called once connected; a box with the monitor off ends the flow
     * with an [IOException].
     */
    fun events(host: String, onListening: () -> Unit = {}): Flow<CallEvent> = callbackFlow {
        val socket = Socket()
        launch(Dispatchers.IO) {
            try {
                socket.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MILLIS)
                onListening()
                socket.getInputStream().bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    parse(line)?.let { trySendBlocking(it) }
                }
                close()
            } catch (e: IOException) {
                close(e)
            }
        }
        awaitClose { runCatching { socket.close() } }
    }

    /** Whether the box at [host] accepts call monitor connections (connects and closes right away). */
    suspend fun isAvailable(host: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { Socket().use { it.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MILLIS) } }.isSuccess
    }

    /**
     * One line of the monitor, e.g. `27.10.26 14:03:11;CALL;0;10;4930555;0301234567;SIP0;`: date and time,
     * event, connection, then the event's fields. Null for anything else.
     */
    fun parse(line: String): CallEvent? {
        val f = line.trim().split(';')
        if (f.size < 4) return null
        val id = f[2]
        return when (f[1]) {
            "RING" -> if (f.size >= 5) CallEvent.Ring(id, caller = f[3], called = f[4]) else null
            "CALL" -> if (f.size >= 6) {
                CallEvent.Call(id, extension = f[3], caller = f[4], called = f[5], line = f.getOrElse(6) { "" })
            } else null
            "CONNECT" -> if (f.size >= 5) CallEvent.Connect(id, extension = f[3], number = f[4]) else null
            "DISCONNECT" -> f[3].toIntOrNull()?.let { CallEvent.Disconnect(id, it) }
            else -> null
        }
    }
}
