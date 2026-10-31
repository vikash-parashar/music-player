package com.vikash.mp3player.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.File
import com.vikash.mp3player.data.NetworkShares
import java.util.EnumSet

/**
 * Where the player reads bytes from: `smb://` songs come from a network folder, everything else
 * (phone files, stories, internet radio over http or https) from Media3's default source.
 */
@OptIn(UnstableApi::class)
class StreamSourceFactory(private val context: Context) : DataSource.Factory {
    private val default = DefaultDataSource.Factory(
        context,
        DefaultHttpDataSource.Factory()
            .setUserAgent("Ampli/1.0 (Android music player)")
            // Radio stations often redirect http -> https (or back); follow them.
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000),
    )

    override fun createDataSource(): DataSource = RoutingDataSource(default.createDataSource()) { SmbDataSource(context.applicationContext) }
}

@OptIn(UnstableApi::class)
private class RoutingDataSource(private val fallback: DataSource, private val smb: () -> DataSource) : DataSource {
    private val listeners = ArrayList<TransferListener>()
    private var smbSource: DataSource? = null
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        fallback.addTransferListener(transferListener)
        smbSource?.addTransferListener(transferListener)
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme.equals("smb", ignoreCase = true)) {
            smbSource ?: smb().also { s -> listeners.forEach(s::addTransferListener); smbSource = s }
        } else fallback
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active!!.read(buffer, offset, length)
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
    override fun close() {
        try { active?.close() } finally { active = null }
    }
}

/** Reads one file from a saved network folder; seeks are plain offset reads, so scrubbing is quick. */
@OptIn(UnstableApi::class)
class SmbDataSource(private val context: Context) : BaseDataSource(/* isNetwork = */ true) {
    private var uri: Uri? = null
    private var file: File? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val shareId = dataSpec.uri.authority.orEmpty()
        val share = NetworkShares.find(context, shareId)
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        val path = dataSpec.uri.pathSegments.joinToString("\\")
        val f = try {
            NetworkShares.Pool.withShare(share) { disk ->
                disk.openFile(
                    path,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )
            }
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        file = f
        val size = runCatching { f.fileInformation.standardInformation.endOfFile }.getOrDefault(C.LENGTH_UNSET.toLong())
        position = dataSpec.position
        remaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            size >= 0 -> (size - position).coerceAtLeast(0)
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val want = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        // One SMB read request per call, capped well under every server's max read size.
        val n = try {
            file!!.read(buffer, position, offset, minOf(want, MAX_READ))
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        if (n <= 0) return C.RESULT_END_OF_INPUT
        position += n
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        runCatching { file?.close() }
        file = null
        if (opened) { opened = false; transferEnded() }
    }

    private companion object { const val MAX_READ = 64 * 1024 }
}
