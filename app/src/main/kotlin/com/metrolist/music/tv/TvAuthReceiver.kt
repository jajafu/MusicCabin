/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.metrolist.innertube.utils.parseCookieString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Credentials pushed from an already logged-in phone to authorize this TV. */
data class TvAuthBundle(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
    val accountName: String,
    val accountEmail: String,
    val channelHandle: String,
)

/**
 * Serves a one-shot login handoff while the TV account page is open.
 * Modeled on FramePhotoReceiver: plain LAN socket, foreground-only, no cloud relay.
 * The 6-digit pairing code shown on the TV is required in the JSON body, and the
 * cookie is validated for a login marker before [onAuth] runs.
 */
internal class TvAuthReceiver(
    private val context: Context,
    private val expectedCode: () -> String?,
    private val onAuth: (TvAuthBundle) -> Boolean,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val failures = AtomicInteger(0)
    @Volatile private var server: ServerSocket? = null
    @Volatile private var activeClient: Socket? = null

    fun start(): String {
        val address = privateAddress(context) ?: throw IOException("No local network address")
        val socket = ServerSocket(PORT, 4, address)
        server = socket
        executor.execute {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: IOException) { break }
                activeClient = client
                client.use { runCatching { handle(it) } }
                activeClient = null
            }
        }
        return "http://${address.hostAddress}:$PORT/"
    }

    override fun close() {
        runCatching { server?.close() }
        server = null
        runCatching { activeClient?.close() }
        executor.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 30_000
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val request = readLine(input, MAX_LINE)?.split(' ') ?: return
        if (request.size != 3 || request[2] != "HTTP/1.1") return
        val headers = mutableMapOf<String, String>()
        var headerCount = 0
        while (true) {
            val line = readLine(input, MAX_LINE) ?: return
            if (line.isEmpty()) break
            if (++headerCount > MAX_HEADERS) return
            val split = line.indexOf(':')
            if (split <= 0) return
            headers[line.substring(0, split).lowercase()] = line.substring(split + 1).trim()
        }
        if (request[0] == "GET" && request[1] == "/") {
            respond(output, 200, INFO_PAGE.toByteArray(StandardCharsets.UTF_8), "text/html; charset=utf-8")
            return
        }
        if (request[0] != "POST" || request[1] != "/auth") {
            respond(output, 404, "Not found")
            return
        }
        if (failures.get() >= MAX_ATTEMPTS) {
            respond(output, 429, "Too many attempts")
            return
        }
        val length = headers["content-length"]?.toIntOrNull()
        if (headers["content-type"]?.substringBefore(';')?.trim() != "application/json" ||
            length == null || length !in 1..MAX_BODY
        ) {
            fail(output, "Invalid request")
            return
        }
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) {
                fail(output, "Truncated request")
                return
            }
            read += count
        }
        val bundle = runCatching { parseBundle(String(body, StandardCharsets.UTF_8)) }.getOrNull()
        val code = expectedCode()
        if (bundle == null || code.isNullOrBlank() || bundle.first != code) {
            fail(output, "Invalid pairing code")
            return
        }
        if (!isLoginCookie(bundle.second.cookie)) {
            fail(output, "No login session in request")
            return
        }
        val accepted = runCatching { onAuth(bundle.second) }.getOrDefault(false)
        if (accepted) {
            respond(output, 200, "Authorized")
        } else {
            fail(output, "Could not save login")
        }
    }

    private fun fail(output: BufferedOutputStream, message: String) {
        failures.incrementAndGet()
        val status = if (failures.get() >= MAX_ATTEMPTS) 429 else 400
        respond(output, status, message)
    }

    private fun parseBundle(raw: String): Pair<String, TvAuthBundle> {
        val json = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
        fun field(name: String) = json[name]?.jsonPrimitive?.content.orEmpty()
        return field("code") to TvAuthBundle(
            cookie = field("cookie"),
            visitorData = field("visitorData"),
            dataSyncId = field("dataSyncId"),
            authUser = field("authUser").filter(Char::isDigit).ifBlank { "0" },
            accountName = field("accountName"),
            accountEmail = field("accountEmail"),
            channelHandle = field("channelHandle"),
        )
    }

    private fun isLoginCookie(cookie: String): Boolean {
        if (cookie.length > MAX_BODY) return false
        return runCatching { "SAPISID" in parseCookieString(cookie) }.getOrDefault(false)
    }

    private fun respond(output: BufferedOutputStream, status: Int, message: String) =
        respond(output, status, message.toByteArray(StandardCharsets.UTF_8), "text/plain; charset=utf-8")

    private fun respond(output: BufferedOutputStream, status: Int, body: ByteArray, contentType: String) {
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"
            429 -> "Too Many Requests"; else -> "Internal Server Error"
        }
        output.write(("HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun readLine(input: BufferedInputStream, maximum: Int): String? {
        val bytes = ArrayList<Byte>()
        while (bytes.size < maximum) {
            val next = input.read()
            if (next < 0) return null
            if (next == 10) {
                val count = bytes.size - if (bytes.lastOrNull() == 13.toByte()) 1 else 0
                return String(bytes.take(count).toByteArray(), StandardCharsets.US_ASCII)
            }
            bytes.add(next.toByte())
        }
        return null
    }

    companion object {
        const val PORT = 38748
        const val MAX_BODY = 32 * 1024
        private const val MAX_LINE = 8 * 1024
        private const val MAX_HEADERS = 32
        private const val MAX_ATTEMPTS = 10

        private val INFO_PAGE = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body><h1>MusicCabin TV login</h1>
            <p>This address only accepts a login pushed from the MusicCabin app.
            On your phone, open the account menu, choose Authorize TV login,
            then enter the TV address and the 6-digit code shown on the TV.</p>
            </body></html>
        """.trimIndent()

        private fun privateAddress(context: Context): Inet4Address? {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lanNetworks = manager.allNetworks.filter { network ->
                val capabilities = manager.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            }
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = lanNetworks.flatMap { network ->
                manager.getLinkProperties(network)?.linkAddresses.orEmpty().map { it.address }
            } + interfaces.filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }
            return candidates.filterIsInstance<Inet4Address>().firstOrNull { address ->
                val bytes = address.address.map { it.toInt() and 0xff }
                bytes[0] == 10 || (bytes[0] == 172 && bytes[1] in 16..31) ||
                    (bytes[0] == 192 && bytes[1] == 168)
            }
        }
    }
}
