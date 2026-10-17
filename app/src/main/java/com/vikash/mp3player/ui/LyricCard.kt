package com.vikash.mp3player.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vikash.mp3player.R
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Looks for the lyric picture card. Rose is free; the rest come with Ampli Pro. */
enum class LyricCardStyle(@StringRes val label: Int, val pro: Boolean, val base: Long, val text: Long) {
    Rose(R.string.lyric_card_style_rose, false, 0xFFBE123C, 0xFFFFFFFF),
    Vinyl(R.string.lyric_card_style_vinyl, true, 0xFF0E0E10, 0xFFFDE68A),
    Paper(R.string.lyric_card_style_paper, true, 0xFFFDF6E3, 0xFF3B2A1A),
    Neon(R.string.lyric_card_style_neon, true, 0xFF0B0614, 0xFFF0ABFC),
}

private const val CARD_W = 1080
private const val CARD_H = 1350
private const val CARD_MARK = "AMPLI"
private const val PACKAGE_WHATSAPP = "com.whatsapp"
private const val PACKAGE_INSTAGRAM = "com.instagram.android"

/**
 * Draws the card on a plain android Canvas at a fixed 1080x1350 layout, so the shared picture
 * looks the same on every phone. [scale] shrinks it for the on-screen preview.
 */
fun renderLyricCard(look: LyricCardStyle, line: String, title: String, artist: String, scale: Float = 1f): Bitmap {
    val bmp = Bitmap.createBitmap((CARD_W * scale).toInt(), (CARD_H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val w = CARD_W.toFloat()
    val h = CARD_H.toFloat()
    val textColor = look.text.toInt()

    // Background.
    val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    when (look) {
        LyricCardStyle.Rose -> {
            bg.shader = RadialGradient(
                w * 0.3f, h * 0.22f, h * 1.05f,
                intArrayOf(0xFFFB7185.toInt(), 0xFFBE123C.toInt(), 0xFF4C0519.toInt()),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, w, h, bg)
        }
        LyricCardStyle.Vinyl -> {
            bg.color = look.base.toInt()
            c.drawRect(0f, 0f, w, h, bg)
            val cx = w * 0.86f
            val cy = h * 0.88f
            val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 2f
                color = 0x16FFFFFF
            }
            var r = 150f
            while (r < 1500f) {
                c.drawCircle(cx, cy, r, groove)
                r += 14f
            }
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE11D48.toInt() }
            c.drawCircle(cx, cy, 130f, label)
            label.color = look.base.toInt()
            c.drawCircle(cx, cy, 14f, label)
        }
        LyricCardStyle.Paper, LyricCardStyle.Neon -> {
            bg.color = look.base.toInt()
            c.drawRect(0f, 0f, w, h, bg)
        }
    }

    val side = 90f
    val boxWidth = (w - side * 2).toInt()

    // AMPLI mark, top-left.
    val markPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        alpha = 220
        textSize = 34f
        letterSpacing = 0.25f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    c.drawText(CARD_MARK, 72f, 72f - markPaint.ascent(), markPaint)

    // Song · Artist, centred at the bottom.
    val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        alpha = 200
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val footRaw = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" · ")
    val foot = TextUtils.ellipsize(footRaw, footPaint, boxWidth.toFloat(), TextUtils.TruncateAt.END).toString()
    c.drawText(foot, w / 2f, h - 84f - footPaint.descent(), footPaint)

    // The lyric line: as big as fits, centred in the middle area.
    val linePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        typeface = if (look == LyricCardStyle.Paper) Typeface.create("cursive", Typeface.BOLD)
        else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        if (look == LyricCardStyle.Neon) setShadowLayer(28f, 0f, 0f, 0xFFE879F9.toInt())
    }
    val top = 190f
    val bottom = h - 210f
    val maxHeight = bottom - top
    val text = line.trim().ifEmpty { "♪" }
    var size = 108f
    var layout: StaticLayout
    do {
        size -= 4f
        linePaint.textSize = size
        layout = StaticLayout.Builder.obtain(text, 0, text.length, linePaint, boxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.12f)
            .setIncludePad(false)
            .setMaxLines(if (size <= 44f) 12 else Int.MAX_VALUE)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    } while (layout.height > maxHeight && size > 44f)
    c.save()
    c.translate(side, top + (maxHeight - layout.height).coerceAtLeast(0f) / 2f)
    layout.draw(c)
    if (look == LyricCardStyle.Neon) layout.draw(c) // a second pass makes the glow brighter
    c.restore()
    return bmp
}

/** Writes the full-size card to the cache and returns a content Uri other apps can read. */
private fun cardUri(context: Context, bmp: Bitmap): Uri {
    val dir = File(context.cacheDir, "lyric_cards").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "ampli_lyric_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun shareImage(context: Context, uri: Uri, targetPackage: String?) {
    fun send(): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (targetPackage != null) {
        val direct = send().setPackage(targetPackage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
            return
        } catch (e: ActivityNotFoundException) {
            // App not installed: fall through to the normal share sheet.
        }
    }
    val chooser = Intent.createChooser(send(), context.getString(R.string.lyric_card_share_chooser))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
        .onFailure { Toast.makeText(context, R.string.lyric_card_no_app, Toast.LENGTH_SHORT).show() }
}

/** Saves into Pictures/Ampli. Returns false when this phone won't let us write there. */
private fun saveToPictures(context: Context, bmp: Bitmap): Boolean {
    val name = "Ampli_lyric_${System.currentTimeMillis()}.png"
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ampli")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
        }.getOrDefault(false)
        if (ok) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } else {
            resolver.delete(uri, null, null)
        }
        ok
    } else {
        // Android 9 and below: the manifest's WRITE_EXTERNAL_STORAGE (maxSdkVersion 28) covers this,
        // but only once the user has granted it.
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        runCatching {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Ampli")
            dir.mkdirs()
            val file = File(dir, name)
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            true
        }.getOrDefault(false)
    }
}

private enum class CardAction { WhatsApp, Instagram, Save }

/** Long-press a lyric line: pick a look and send the line as a picture. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LyricCardSheet(line: String, title: String, artist: String, pro: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var style by remember { mutableStateOf(LyricCardStyle.Rose) }
    var busy by remember { mutableStateOf(false) }
    val previews = remember(line, title, artist) { mutableStateMapOf<LyricCardStyle, ImageBitmap>() }

    LaunchedEffect(line, title, artist, style) {
        if (style !in previews) {
            val s = style
            previews[s] = withContext(Dispatchers.Default) { renderLyricCard(s, line, title, artist, 0.5f).asImageBitmap() }
        }
    }
    val placeholder by animateColorAsState(Color(style.base), label = "lyricCardBase")

    fun act(action: CardAction) {
        if (busy) return
        busy = true
        val s = style
        scope.launch {
            try {
                val bmp = withContext(Dispatchers.Default) { renderLyricCard(s, line, title, artist) }
                when (action) {
                    CardAction.WhatsApp, CardAction.Instagram -> {
                        val uri = withContext(Dispatchers.IO) { cardUri(context, bmp) }
                        shareImage(context, uri, if (action == CardAction.WhatsApp) PACKAGE_WHATSAPP else PACKAGE_INSTAGRAM)
                    }
                    CardAction.Save -> {
                        val saved = withContext(Dispatchers.IO) { saveToPictures(context, bmp) }
                        if (saved) {
                            Toast.makeText(context, R.string.lyric_card_saved, Toast.LENGTH_SHORT).show()
                        } else {
                            val uri = withContext(Dispatchers.IO) { cardUri(context, bmp) }
                            shareImage(context, uri, null)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, R.string.lyric_card_failed, Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.lyric_card_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                Modifier.fillMaxWidth(0.72f).aspectRatio(4f / 5f).clip(RoundedCornerShape(20.dp)).background(placeholder),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = style, label = "lyricCard") { s ->
                    val img = previews[s]
                    if (img != null) {
                        Image(
                            img,
                            contentDescription = stringResource(R.string.lyric_card_preview_desc),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(s.base)))
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LyricCardStyle.entries.forEach { s ->
                    FilterChip(
                        selected = style == s,
                        onClick = {
                            if (s.pro && !pro) {
                                style = LyricCardStyle.Rose
                                Toast.makeText(context, R.string.lyric_card_pro_toast, Toast.LENGTH_SHORT).show()
                            } else {
                                style = s
                            }
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(s.label))
                                if (s.pro) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.lyric_card_pro_badge),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Ampli.Rose)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Ampli.Rose.copy(alpha = 0.2f),
                        ),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                CardActionButton(Icons.AutoMirrored.Filled.Chat, R.string.lyric_card_whatsapp, !busy, Modifier.weight(1f)) { act(CardAction.WhatsApp) }
                CardActionButton(Icons.Default.PhotoCamera, R.string.lyric_card_instagram, !busy, Modifier.weight(1f)) { act(CardAction.Instagram) }
                CardActionButton(Icons.Default.Download, R.string.lyric_card_save, !busy, Modifier.weight(1f)) { act(CardAction.Save) }
            }
        }
    }
}

@Composable
private fun CardActionButton(icon: ImageVector, @StringRes label: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringResource(label),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
