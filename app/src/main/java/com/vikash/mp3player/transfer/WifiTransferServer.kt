package com.vikash.mp3player.transfer

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.Executors
import android.os.StatFs
import kotlin.concurrent.thread

/**
 * "Send songs from your computer": a tiny web server on the phone. Open the address on any
 * laptop on the same Wi-Fi, drop music files on the page, and they land in Music/Ampli and
 * show up in the Library. No cable, no app on the computer, nothing goes over the internet.
 *
 * Only runs while the transfer screen is open. The address carries a random code so other
 * people on the same Wi-Fi can't guess it.
 */
class WifiTransferServer(private val context: Context) {

    private val token = UUID.randomUUID().toString().replace("-", "").take(10)
    // A few uploads at once is plenty; extra connections wait their turn.
    // Made fresh on each start and shut down on stop, so no threads stay behind after the screen closes.
    private var pool: java.util.concurrent.ExecutorService? = null
    private var server: ServerSocket? = null
    @Volatile private var lastActivity = 0L
    private val active = java.util.concurrent.atomic.AtomicInteger(0)

    /** Milliseconds since the last request, or 0 while a request (an upload) is running. */
    fun idleMillis(): Long = if (active.get() > 0) 0L else android.os.SystemClock.elapsedRealtime() - lastActivity

    private val _received = MutableStateFlow<List<String>>(emptyList())
    /** File names received so far, newest first. */
    val received: StateFlow<List<String>> = _received.asStateFlow()

    /** Starts listening and returns the address to type on the computer, or null without Wi-Fi. */
    fun start(): String? {
        stop()
        val ip = wifiAddress() ?: return null
        val socket = ServerSocket().apply { reuseAddress = true }
        runCatching { socket.bind(InetSocketAddress(PORT)) }.onFailure { socket.bind(InetSocketAddress(0)) }
        server = socket
        val workers = Executors.newFixedThreadPool(4).also { pool = it }
        lastActivity = android.os.SystemClock.elapsedRealtime()
        thread(name = "ampli-transfer", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                lastActivity = android.os.SystemClock.elapsedRealtime()
                runCatching {
                    workers.execute {
                        active.incrementAndGet()
                        try { client.use { runCatching { handle(it) } } } finally {
                            active.decrementAndGet()
                            lastActivity = android.os.SystemClock.elapsedRealtime()
                        }
                    }
                }.onFailure { runCatching { client.close() } }
            }
        }
        return "http://$ip:${socket.localPort}/$token"
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        // Lets an upload that is already running finish, then the threads end.
        pool?.shutdown()
        pool = null
    }

    // ------------------------------------------------------------------ HTTP

    private fun handle(client: Socket) {
        client.soTimeout = 60_000
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
        if (parts.size < 2) return respond(out, 400, "text/plain", "Bad request")
        val method = parts[0]
        val path = parts[1].substringBefore('?')
        val query = parts[1].substringAfter('?', "")

        when {
            method == "GET" && (path == "/$token" || path == "/$token/") ->
                respond(out, 200, "text/html; charset=utf-8", page(token))
            method == "PUT" && path == "/$token/upload" -> {
                val name = query.split('&').firstOrNull { it.startsWith("name=") }
                    ?.substringAfter("name=")?.let { URLDecoder.decode(it, "UTF-8") }
                    ?.let(::safeName)
                val length = headers["content-length"]?.toLongOrNull()
                when {
                    name == null || length == null -> respond(out, 400, "application/json", """{"ok":false,"error":"Missing file"}""")
                    length > MAX_FILE_BYTES ->
                        respond(out, 413, "application/json", """{"ok":false,"error":"File is too big (500 MB max)"}""")
                    length + SPARE_BYTES > freeBytes() ->
                        respond(out, 507, "application/json", """{"ok":false,"error":"Not enough free space on the phone"}""")
                    name.substringAfterLast('.', "").lowercase() !in AUDIO_EXTENSIONS ->
                        respond(out, 415, "application/json", """{"ok":false,"error":"Only music files can be sent"}""")
                    else -> {
                        val ok = save(name, input, length)
                        if (ok) _received.value = listOf(name) + _received.value
                        respond(out, if (ok) 200 else 500, "application/json", """{"ok":$ok}""")
                    }
                }
            }
            else -> respond(out, 404, "text/plain", "Not found. Check the address shown in Ampli.")
        }
    }

    /** Reads one header line byte by byte, so the body that follows isn't swallowed by a buffer. */
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

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray()
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 413 -> "Payload Too Large"; 507 -> "Insufficient Storage"; 415 -> "Unsupported Media Type"; else -> "Error" }
        out.write("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    // ------------------------------------------------------------------ saving

    private fun freeBytes(): Long = runCatching {
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    /** Streams [length] bytes into Music/Ampli. Returns false (and cleans up) on any failure. */
    private fun save(name: String, input: InputStream, length: Long): Boolean {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase()) ?: "audio/mpeg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Ampli")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: return false
            val ok = runCatching {
                resolver.openOutputStream(uri)?.use { copy(input, it, length) } ?: false
            }.getOrDefault(false)
            if (ok) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } else {
                resolver.delete(uri, null, null)
            }
            ok
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Ampli").apply { mkdirs() }
            val file = File(dir, name)
            val ok = runCatching { FileOutputStream(file).use { copy(input, it, length) } }.getOrDefault(false)
            if (ok) MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
            else file.delete()
            ok
        }
    }

    private fun copy(input: InputStream, out: OutputStream, length: Long): Boolean {
        val buf = ByteArray(64 * 1024)
        var left = length
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n == -1) return false
            out.write(buf, 0, n)
            left -= n
        }
        return true
    }

    private fun safeName(raw: String): String? =
        raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150).takeIf { it.isNotEmpty() && it != "." && it != ".." }

    /** The phone's IPv4 address on the current Wi-Fi (or hotspot/Ethernet) network. */
    private fun wifiAddress(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    private companion object {
        const val PORT = 8080
        const val MAX_FILE_BYTES = 500L * 1024 * 1024
        /** Room left over so a transfer never fills the phone completely. */
        const val SPARE_BYTES = 200L * 1024 * 1024
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "amr", "mid", "midi")
    }
}
