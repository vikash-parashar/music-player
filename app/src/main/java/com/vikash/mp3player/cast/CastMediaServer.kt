package com.vikash.mp3player.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A Chromecast can't open files on the phone, so while casting the phone serves the songs in the
 * queue over the home Wi-Fi. Only files the queue registered are served, at addresses with a
 * random code, and the server stops when casting stops.
 */
class CastMediaServer(private val context: Context) {

    private val token = UUID.randomUUID().toString().take(8)
    private val files = ConcurrentHashMap<String, Uri>()
    private val keys = ConcurrentHashMap<Uri, String>()
    private val nextKey = AtomicInteger(1)
    private var server: ServerSocket? = null
    private var base: String? = null

    /** The Wi-Fi address the Chromecast can fetch [uri] from, or null when the phone has no Wi-Fi. */
    @Synchronized
    fun urlFor(uri: Uri): String? {
        val root = base ?: start() ?: return null
        val key = keys.getOrPut(uri) { nextKey.getAndIncrement().toString().also { files[it] = uri } }
        return "$root/$key"
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        base = null
        files.clear()
        keys.clear()
    }

    private fun start(): String? {
        val ip = wifiAddress() ?: return null
        val socket = ServerSocket().apply { reuseAddress = true }
        runCatching { socket.bind(InetSocketAddress(0)) }.onFailure { return null }
        server = socket
        thread(name = "ampli-cast", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { client.use { runCatching { handle(it) } } }
            }
        }
        return "http://$ip:${socket.localPort}/$token".also { base = it }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 30_000
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        val requestLine = readLine(input) ?: return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val parts = requestLine.split(' ')
        if (parts.size < 2 || (parts[0] != "GET" && parts[0] != "HEAD")) return status(out, 405, "Method Not Allowed")
        val path = parts[1].substringBefore('?').trim('/').split('/')
        if (path.size != 2 || path[0] != token) return status(out, 404, "Not Found")
        val uri = files[path[1]] ?: return status(out, 404, "Not Found")
        val resolver = context.contentResolver
        val type = runCatching { resolver.getType(uri) }.getOrNull() ?: "application/octet-stream"
        val length = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L

        // Seeking on the TV asks for a byte range.
        val range = headers["range"]?.let { Regex("""bytes=(\d*)-(\d*)""").find(it) }
        if (range != null && length > 0) {
            val (a, b) = range.destructured
            val start = a.toLongOrNull() ?: (length - (b.toLongOrNull() ?: 0)).coerceAtLeast(0)
            val end = if (a.isEmpty()) length - 1 else (b.toLongOrNull() ?: (length - 1)).coerceAtMost(length - 1)
            if (start >= length || start > end) {
                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$length\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            out.write(
                ("HTTP/1.1 206 Partial Content\r\nContent-Type: $type\r\nAccept-Ranges: bytes\r\n" +
                    "Content-Range: bytes $start-$end/$length\r\nContent-Length: ${end - start + 1}\r\n" +
                    "Access-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").toByteArray()
            )
            if (parts[0] == "GET") resolver.openInputStream(uri)?.use { copy(it, out, start, end - start + 1) }
            return
        }
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nAccept-Ranges: bytes\r\n" +
                (if (length > 0) "Content-Length: $length\r\n" else "") +
                "Access-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").toByteArray()
        )
        if (parts[0] == "GET") resolver.openInputStream(uri)?.use { copy(it, out, 0, Long.MAX_VALUE) }
    }

    private fun copy(input: InputStream, out: OutputStream, skip: Long, count: Long) {
        var toSkip = skip
        while (toSkip > 0) {
            val n = input.skip(toSkip)
            if (n <= 0) { if (input.read() == -1) return; toSkip-- } else toSkip -= n
        }
        val buf = ByteArray(64 * 1024)
        var left = count
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n == -1) break
            out.write(buf, 0, n)
            left -= n
        }
        out.flush()
    }

    private fun status(out: OutputStream, code: Int, text: String) {
        out.write("HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
            buf.write(b)
            if (buf.size() > 8192) return null
        }
    }

    private fun wifiAddress(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }
}
