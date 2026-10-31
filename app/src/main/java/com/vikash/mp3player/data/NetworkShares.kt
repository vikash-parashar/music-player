package com.vikash.mp3player.data

import android.content.Context
import android.net.Uri
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.security.bc.BCSecurityProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A shared folder on a PC or NAS (SMB 2/3, i.e. Windows file sharing, Samba, most NAS boxes). */
@Serializable
data class NetworkShare(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val host: String,
    val share: String,
    /** Optional sub-folder inside the share to open first, with "/" separators. */
    val path: String = "",
    /** Blank = guest login. */
    val user: String = "",
    val password: String = "",
    val domain: String = "",
)

/** One folder or audio file inside a share. */
data class NetworkEntry(val name: String, val path: String, val isFolder: Boolean, val sizeBytes: Long)

/**
 * Saved network folders and the SMB connections behind them. Songs from a share play through
 * `smb://<share id>/<path>` URIs that [com.vikash.mp3player.playback.SmbDataSource] opens.
 * Logins are kept in the app's private storage, which Android encrypts on modern phones and
 * which is excluded from backups (see backup_rules / data_extraction_rules).
 */
object NetworkShares {
    private const val PREFS = "network_shares"
    private const val KEY = "shares"
    private val json = Json { ignoreUnknownKeys = true }

    private val _shares = MutableStateFlow<List<NetworkShare>>(emptyList())
    val shares: StateFlow<List<NetworkShare>> = _shares.asStateFlow()
    @Volatile private var loaded = false

    val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "alac", "aiff", "aif", "ape", "mka", "dsf", "amr")

    fun load(context: Context) {
        if (loaded) return
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _shares.value = raw?.let { runCatching { json.decodeFromString<List<NetworkShare>>(it) }.getOrNull() }.orEmpty()
        loaded = true
    }

    fun find(context: Context, id: String): NetworkShare? { load(context); return _shares.value.firstOrNull { it.id == id } }

    fun save(context: Context, share: NetworkShare) {
        load(context)
        _shares.value = _shares.value.filterNot { it.id == share.id } + share
        persist(context)
    }

    fun remove(context: Context, id: String) {
        load(context)
        _shares.value = _shares.value.filterNot { it.id == id }
        persist(context)
        Pool.drop(id)
    }

    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(_shares.value)).apply()
    }

    /**
     * Parses what people paste from Windows Explorer or a NAS app: `\\192.168.1.5\Music\Hindi`,
     * `smb://nas.local/Music`, or `192.168.1.5/Music`. Returns host, share and sub-path.
     */
    fun parseAddress(input: String): Triple<String, String, String>? {
        val clean = input.trim().removePrefix("smb://").removePrefix("SMB://").replace('\\', '/').trim('/')
        val parts = clean.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        return Triple(parts[0], parts[1], parts.drop(2).joinToString("/"))
    }

    /** Lists folders and audio files at [path] (relative to the share root), folders first. */
    fun list(context: Context, share: NetworkShare, path: String): List<NetworkEntry> =
        Pool.withShare(share) { disk ->
            disk.list(path.replace('/', '\\'))
                .asSequence()
                .filter { it.fileName != "." && it.fileName != ".." && !it.fileName.startsWith(".") }
                .mapNotNull { info ->
                    val folder = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                    val ext = info.fileName.substringAfterLast('.', "").lowercase()
                    if (!folder && ext !in audioExtensions) null
                    else NetworkEntry(info.fileName, if (path.isBlank()) info.fileName else "$path/${info.fileName}", folder, info.endOfFile)
                }
                .sortedWith(compareBy<NetworkEntry>({ !it.isFolder }, { it.name.lowercase() }))
                .toList()
        }

    /** Tries to log in and open the share; null on success, otherwise the error to show. */
    fun test(share: NetworkShare): String? = runCatching { Pool.withShare(share) { it.list("") }; null }
        .getOrElse { it.message ?: it.javaClass.simpleName }

    fun songUri(share: NetworkShare, path: String): Uri = Uri.Builder().scheme("smb").authority(share.id)
        .apply { path.split('/').filter { it.isNotEmpty() }.forEach { appendPath(it) } }.build()

    fun toSong(share: NetworkShare, entry: NetworkEntry): Song {
        val base = entry.name.substringBeforeLast('.')
        // "Artist - Title.mp3" is the common naming; otherwise the file name is the title.
        val (artist, title) = base.split(" - ", limit = 2).let { if (it.size == 2) it[0].trim() to it[1].trim() else share.name to base }
        return Song(
            id = "smb:${share.id}:${entry.path}",
            uri = songUri(share, entry.path),
            title = title,
            artist = artist,
            album = entry.path.substringBeforeLast('/', share.share),
            folder = "${share.name}/${entry.path.substringBeforeLast('/', "")}".trimEnd('/'),
            durationMs = 0,
            artworkUri = null,
            isOnline = true,
            sizeBytes = entry.sizeBytes,
        )
    }

    /** One logged-in session per share, reused across files and seeks; reconnects when dropped. */
    object Pool {
        private val client by lazy {
            SMBClient(
                SmbConfig.builder()
                    .withSecurityProvider(BCSecurityProvider()) // Android lacks MD4, which NTLM needs
                    .withTimeout(20, TimeUnit.SECONDS)
                    .withSoTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
        }

        private class Live(val connection: Connection, val session: Session, val disk: DiskShare)
        private val live = HashMap<String, Live>()

        @Synchronized
        fun <T> withShare(share: NetworkShare, block: (DiskShare) -> T): T {
            val current = live[share.id]?.takeIf { it.connection.isConnected && it.disk.isConnected }
            val l = current ?: open(share).also { live[share.id] = it }
            return try {
                block(l.disk)
            } catch (e: Exception) {
                // A stale connection (Wi-Fi changed, PC slept): drop it and try once more.
                drop(share.id)
                val fresh = open(share).also { live[share.id] = it }
                block(fresh.disk)
            }
        }

        private fun open(share: NetworkShare): Live {
            val host = share.host.substringBefore(':')
            val port = share.host.substringAfter(':', "").toIntOrNull()
            val connection = if (port != null) client.connect(host, port) else client.connect(host)
            val auth = if (share.user.isBlank()) AuthenticationContext.guest()
            else AuthenticationContext(share.user, share.password.toCharArray(), share.domain.ifBlank { null })
            val session = connection.authenticate(auth)
            val disk = session.connectShare(share.share) as? DiskShare
                ?: run { connection.close(); error("${share.share} is not a folder share") }
            return Live(connection, session, disk)
        }

        @Synchronized
        fun drop(id: String) {
            live.remove(id)?.let { runCatching { it.disk.close() }; runCatching { it.connection.close() } }
        }
    }
}
