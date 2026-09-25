package com.metrolist.music.tv

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.metrolist.music.photo.isFileWithinRoot
import com.metrolist.music.photo.tvPhotoImportsDirectory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/** Serves the phone picker and accepts photos only while the TV send settings are open. */
internal class TvPhotoReceiver(
    private val context: Context,
    private val onPhoto: (File) -> Unit,
) : AutoCloseable {
    private val directory = importsDirectory(context)
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var activeClient: Socket? = null
    private val page by lazy { context.assets.open("tv_photo_sender.html").use { it.readBytes() } }

    fun start(): String {
        page
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
        val request = readLine(input, 4096)?.split(' ') ?: return
        if (request.size != 3 || request[2] != "HTTP/1.1") return
        val headers = mutableMapOf<String, String>()
        var headerCount = 0
        while (true) {
            val line = readLine(input, 4096) ?: return
            if (line.isEmpty()) break
            if (++headerCount > 32) return
            val split = line.indexOf(':')
            if (split <= 0) return
            headers[line.substring(0, split).lowercase()] = line.substring(split + 1).trim()
        }
        if (request[0] == "GET" && request[1] == "/") {
            respond(output, 200, page, "text/html; charset=utf-8")
            return
        }
        if (request[0] != "POST" || request[1] != "/photo") {
            respond(output, 404, "Not found")
            return
        }
        val expectedOrigin = "http://${socket.localAddress.hostAddress}:$PORT"
        if (headers["origin"] != expectedOrigin) {
            respond(output, 403, "Forbidden")
            return
        }
        val length = headers["content-length"]?.toIntOrNull()
        if (headers["content-type"] != "image/jpeg" || length == null || length !in 1..MAX_BYTES) {
            respond(output, 400, "Invalid image")
            return
        }
        if (!directory.isDirectory && !directory.mkdirs()) {
            respond(output, 500, "Cannot create photo folder")
            return
        }
        val name = runCatching {
            URLDecoder.decode(headers["x-photo-name"].orEmpty(), StandardCharsets.UTF_8.name())
        }.getOrDefault("photo")
            .substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
            .trim().take(48).ifBlank { "photo" }.let { if (it.length < 3) "photo" else it }
        var staged: File? = null
        var saved = false
        try {
            val file = File.createTempFile("$name-", ".jpg", directory).also { staged = it }
            if (!isFileWithinRoot(file, directory)) throw IOException("Invalid storage path")
            file.outputStream().use { sink ->
                var remaining = length
                val buffer = ByteArray(16 * 1024)
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count < 0) throw IOException("Truncated upload")
                    sink.write(buffer, 0, count)
                    remaining -= count
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth !in 1..MAX_EDGE || bounds.outHeight !in 1..MAX_EDGE) {
                respond(output, 400, "Invalid dimensions")
                return
            }
            onPhoto(file)
            saved = true
            respond(output, 200, "Saved")
        } catch (_: Exception) {
            respond(output, 500, "Could not save photo")
        } finally {
            if (!saved) staged?.delete()
        }
    }

    private fun respond(output: BufferedOutputStream, status: Int, message: String) =
        respond(output, status, message.toByteArray(StandardCharsets.UTF_8), "text/plain; charset=utf-8")

    private fun respond(output: BufferedOutputStream, status: Int, body: ByteArray, contentType: String) {
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
            404 -> "Not Found"; else -> "Internal Server Error"
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
        const val PORT = 38747
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_EDGE = 4096
        fun importsDirectory(context: Context) = tvPhotoImportsDirectory(context)
        fun isImportedUri(context: Context, uri: String): Boolean {
            val parsed = Uri.parse(uri)
            val file = parsed.path?.let(::File) ?: return false
            return parsed.scheme == "file" && isFileWithinRoot(file, importsDirectory(context))
        }

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
