package com.vikash.mp3player.ui.skin

import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.data.UiSkin

/**
 * How the tactile parts are drawn (paper cards, keys rising out of them with a red/green ring).
 * Older looks use [Paper] with their own colours.
 */
enum class TactileLook { Paper, Clay, Led, Metal, Frost, Keys, Jelly, Sticker, Bubble, Arcade }

/** How cards, the dock and the mini player are drawn in a look. */
enum class SurfaceStyle { Material, Hairline, Glass, Raised, Clay, Brutal, Tile, Metal, Jelly }

/**
 * Everything a screen needs to draw itself in the chosen look. Read it with [LocalSkin]; colours
 * that Material components use (buttons, text, dialogs) also come through MaterialTheme.
 */
@Immutable
data class SkinSpec(
    val skin: UiSkin,
    val dark: Boolean,
    val style: SurfaceStyle,
    /** Screen background: a brush, or null for the colour scheme's background. */
    val backdrop: Brush?,
    /** Neo brutalism's dotted paper. */
    val dots: Color = Color.Transparent,
    val card: Color,
    val cardBrush: Brush? = null,
    val edge: Color,
    val corner: Dp,
    val shadowDark: Color = Color.Black.copy(alpha = 0.2f),
    val shadowLight: Color = Color.White,
    /** Push buttons: the square base, the ink on it, and the accent dome. */
    val tactile: Color,
    val tactileInk: Color,
    val tactileAccent: Color,
    val onTactileAccent: Color = Color.White,
    /** Hue of the 3D switch's lit side. */
    val accentHue: Float,
    val haptic: Int,
    val hapticsOn: Boolean = true,
    /** Two soft ticks per tap, one as a key goes down and one as it comes back up (Neumorphic). */
    val pressHaptics: Boolean = false,
    val look: TactileLook = TactileLook.Paper,
) {
    val isClassic get() = skin == UiSkin.Classic
    val shape: Shape get() = RoundedCornerShape(corner)
}

val LocalSkin = staticCompositionLocalOf { skinSpec(UiSkin.Classic, dark = false) }

private const val TICK = HapticFeedbackConstants.CLOCK_TICK
private const val TAP = HapticFeedbackConstants.KEYBOARD_TAP
private const val KEY = HapticFeedbackConstants.VIRTUAL_KEY
private val CONTEXT = HapticFeedbackConstants.CONTEXT_CLICK
private val CONFIRM = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY

fun skinSpec(skin: UiSkin, dark: Boolean, hapticsOn: Boolean = true): SkinSpec = when (skin) {
    UiSkin.Classic -> SkinSpec(
        skin, dark, SurfaceStyle.Material, null, card = Color.Unspecified, edge = Color.Unspecified, corner = 16.dp,
        tactile = Color(0xFFCCD0D4), tactileInk = Color(0x73000000), tactileAccent = Color(0xFFE11D48),
        accentHue = 350f, haptic = TICK, hapticsOn = false,
    )
    UiSkin.Paper -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Raised, Brush.verticalGradient(listOf(Color(0xFF1E1A22), Color(0xFF1E1A22))),
        card = Color(0xFF2A2530), edge = Color.Transparent, corner = 26.dp,
        shadowDark = Color(0xFF120F15), shadowLight = Color(0xFF3A3440),
        tactile = Color(0xFF2E2934), tactileInk = Color(0xFFD9D2E0), tactileAccent = Color(0xFF7C3AED),
        accentHue = 270f, haptic = TICK, hapticsOn = hapticsOn, pressHaptics = true, look = TactileLook.Paper,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Raised, Brush.verticalGradient(listOf(Color(0xFFF6ECF4), Color(0xFFF6ECF4))),
        card = Color(0xFFF1E9F1), edge = Color.Transparent, corner = 26.dp,
        shadowDark = Color(0xFFB9A6BA), shadowLight = Color.White,
        tactile = Color(0xFFEAE0EA), tactileInk = Color(0xFF5E5470), tactileAccent = Color(0xFF6D28D9),
        accentHue = 270f, haptic = TICK, hapticsOn = hapticsOn, pressHaptics = true, look = TactileLook.Paper,
    )
    // Night look: always dark, the ring around each key glows like an LED.
    UiSkin.Midnight -> SkinSpec(
        skin, true, SurfaceStyle.Raised, Brush.verticalGradient(listOf(Color(0xFF16171C), Color(0xFF16171C))),
        card = Color(0xFF1F2027), edge = Color.Transparent, corner = 24.dp,
        shadowDark = Color(0xAA000000), shadowLight = Color(0x0FFFFFFF),
        tactile = Color(0xFF2A2C35), tactileInk = Color(0xFFC9CBD6), tactileAccent = Color(0xFF7C3AED),
        accentHue = 270f, haptic = TICK, hapticsOn = hapticsOn, pressHaptics = true, look = TactileLook.Led,
    )
    UiSkin.Keys -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Tile, Brush.verticalGradient(listOf(Color(0xFF24201A), Color(0xFF24201A))),
        card = Color(0xFF332D24), edge = Color(0xFF4A4134), corner = 14.dp, shadowDark = Color(0xFF15120E),
        tactile = Color(0xFF3B342A), tactileInk = Color(0xFFE9DFC9), tactileAccent = Color(0xFFEA580C),
        accentHue = 25f, haptic = KEY, hapticsOn = hapticsOn, look = TactileLook.Keys,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Tile, Brush.verticalGradient(listOf(Color(0xFFEFE6D2), Color(0xFFEFE6D2))),
        card = Color(0xFFFBF5E6), edge = Color(0xFFD8CBAE), corner = 14.dp, shadowDark = Color(0xFFC9BB9A),
        tactile = Color(0xFFF1E7CF), tactileInk = Color(0xFF4A4036), tactileAccent = Color(0xFFC2410C),
        accentHue = 25f, haptic = KEY, hapticsOn = hapticsOn, look = TactileLook.Keys,
    )
    // Glossy Jelly: shiny candy keys that squish when pressed.
    UiSkin.Jelly -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Jelly, Brush.verticalGradient(listOf(Color(0xFF241523), Color(0xFF1A1020))),
        card = Color(0xFF331F31), edge = Color.Transparent, corner = 28.dp,
        shadowDark = Color(0xAA000000), shadowLight = Color(0x14FFFFFF),
        tactile = Color(0xFF3D2639), tactileInk = Color(0xFFFBD5E5), tactileAccent = Color(0xFFE11D48),
        accentHue = 345f, haptic = CONTEXT, hapticsOn = hapticsOn, pressHaptics = true, look = TactileLook.Jelly,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Jelly, Brush.verticalGradient(listOf(Color(0xFFFDF2F8), Color(0xFFFCE7F3))),
        card = Color(0xFFFFF7FB), edge = Color.Transparent, corner = 28.dp,
        shadowDark = Color(0x55BE123C), shadowLight = Color.White,
        tactile = Color(0xFFFFE4EF), tactileInk = Color(0xFF881337), tactileAccent = Color(0xFFE11D48),
        accentHue = 345f, haptic = CONTEXT, hapticsOn = hapticsOn, pressHaptics = true, look = TactileLook.Jelly,
    )
    // Cartoon Sticker: thick black outline and a hard offset shadow, like a comic.
    UiSkin.Sticker -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Brutal, Brush.verticalGradient(listOf(Color(0xFF1C1A16), Color(0xFF1C1A16))),
        card = Color(0xFF2A2721), edge = Color(0xFFFDE047), corner = 22.dp, shadowDark = Color(0xFFFDE047),
        tactile = Color(0xFF3A362E), tactileInk = Color(0xFFFFF7D6), tactileAccent = Color(0xFFFB7185), onTactileAccent = Color(0xFF111111),
        accentHue = 350f, haptic = KEY, hapticsOn = hapticsOn, look = TactileLook.Sticker,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Brutal, Brush.verticalGradient(listOf(Color(0xFFFEF3C7), Color(0xFFFEF3C7))),
        card = Color.White, edge = Color(0xFF111111), corner = 22.dp, shadowDark = Color(0xFF111111),
        tactile = Color(0xFFFFFBEB), tactileInk = Color(0xFF111111), tactileAccent = Color(0xFFFB7185), onTactileAccent = Color(0xFF111111),
        accentHue = 350f, haptic = KEY, hapticsOn = hapticsOn, look = TactileLook.Sticker,
    )
    // Soap Bubble: see-through rainbow bubbles that squash sideways.
    UiSkin.Bubble -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Glass, Brush.verticalGradient(listOf(Color(0xFF0B1E2E), Color(0xFF1B1433))),
        card = Color.White.copy(alpha = 0.10f), edge = Color.White.copy(alpha = 0.22f), corner = 26.dp,
        tactile = Color(0xFF1A2A3C), tactileInk = Color(0xFFE0F2FE), tactileAccent = Color(0xFF0EA5E9),
        accentHue = 200f, haptic = TAP, hapticsOn = hapticsOn, look = TactileLook.Bubble,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Glass, Brush.verticalGradient(listOf(Color(0xFFE0F2FE), Color(0xFFFAE8FF))),
        card = Color.White.copy(alpha = 0.55f), edge = Color.White.copy(alpha = 0.85f), corner = 26.dp,
        tactile = Color(0xFFF0F9FF), tactileInk = Color(0xFF0C4A6E), tactileAccent = Color(0xFF0284C7),
        accentHue = 200f, haptic = TAP, hapticsOn = hapticsOn, look = TactileLook.Bubble,
    )
    // Arcade: chunky game-machine buttons that drop down with a solid click. Always dark.
    UiSkin.Arcade -> SkinSpec(
        skin, true, SurfaceStyle.Tile, Brush.verticalGradient(listOf(Color(0xFF1A1033), Color(0xFF120A26))),
        card = Color(0xFF241747), edge = Color(0xFF3B2A6B), corner = 18.dp, shadowDark = Color(0xFF0A0617),
        tactile = Color(0xFF2B1B4F), tactileInk = Color(0xFFEDE9FE), tactileAccent = Color(0xFFE11D48),
        accentHue = 345f, haptic = KEY, hapticsOn = hapticsOn, look = TactileLook.Arcade,
    )
    UiSkin.Minimal -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Hairline, null, card = Color.Transparent, edge = Color(0xFF2A2A2A), corner = 6.dp,
        tactile = Color(0xFF2A2A2C), tactileInk = Color(0xCCFFFFFF), tactileAccent = Color(0xFFF5F5F5), onTactileAccent = Color(0xFF111111),
        accentHue = 350f, haptic = TICK, hapticsOn = hapticsOn,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Hairline, null, card = Color.Transparent, edge = Color(0xFFE8E8E8), corner = 6.dp,
        tactile = Color(0xFFE6E6E8), tactileInk = Color(0x8C000000), tactileAccent = Color(0xFF111111),
        accentHue = 350f, haptic = TICK, hapticsOn = hapticsOn,
    )
    UiSkin.Glass -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Glass,
        Brush.linearGradient(listOf(Color(0xFF9F1239), Color(0xFF4C1D95), Color(0xFF1E1B4B), Color(0xFF075985))),
        card = Color.White.copy(alpha = 0.13f), edge = Color.White.copy(alpha = 0.28f), corner = 20.dp,
        tactile = Color(0xFFD6D0EA), tactileInk = Color(0xFFEDEBFF), tactileAccent = Color(0xFF8B5CF6),
        accentHue = 270f, haptic = TAP, hapticsOn = hapticsOn, look = TactileLook.Frost,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Glass,
        Brush.linearGradient(listOf(Color(0xFFB9E6FF), Color(0xFFD9C8FF), Color(0xFFFFD3E6))),
        card = Color.White.copy(alpha = 0.45f), edge = Color.White.copy(alpha = 0.75f), corner = 24.dp,
        tactile = Color(0xFFE4DEF5), tactileInk = Color(0xFF2E3560), tactileAccent = Color(0xFF6D28D9),
        accentHue = 270f, haptic = TAP, hapticsOn = hapticsOn, look = TactileLook.Frost,
    )
    // Lit from the top left: raised parts get a light edge up-left and a soft shade down-right, and
    // every raised surface has a faint diagonal sheen so it reads as moulded, not flat.
    UiSkin.Neu -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Raised, null, card = Color(0xFF2A2D34),
        cardBrush = Brush.linearGradient(listOf(Color(0xFF30333B), Color(0xFF25282E))),
        edge = Color.Transparent, corner = 22.dp,
        shadowDark = Color(0xFF1A1C21), shadowLight = Color(0xFF3A3E47),
        tactile = Color(0xFF2A2D34), tactileInk = Color(0xB3E2E6EE), tactileAccent = Color(0xFF2E3138), onTactileAccent = Color(0xFFFB7185),
        accentHue = 350f, haptic = TICK, hapticsOn = hapticsOn, pressHaptics = true,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Raised, null, card = Color(0xFFE0E5EC),
        cardBrush = Brush.linearGradient(listOf(Color(0xFFE9EEF5), Color(0xFFD4DAE2))),
        edge = Color.Transparent, corner = 22.dp,
        shadowDark = Color(0xFFB8C0CC), shadowLight = Color.White,
        tactile = Color(0xFFE0E5EC), tactileInk = Color(0xFF5B6778), tactileAccent = Color(0xFFE6EBF1), onTactileAccent = Color(0xFFE11D48),
        accentHue = 350f, haptic = TICK, hapticsOn = hapticsOn, pressHaptics = true,
    )
    UiSkin.Clay -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Clay, Brush.verticalGradient(listOf(Color(0xFF2E211C), Color(0xFF231915))),
        card = Color(0xFF3A2A24), edge = Color.Transparent, corner = 32.dp, shadowDark = Color(0x88000000),
        tactile = Color(0xFF45332B), tactileInk = Color(0xFFF5DCCD), tactileAccent = Color(0xFFFF7A3D),
        accentHue = 20f, haptic = CONTEXT, hapticsOn = hapticsOn, look = TactileLook.Clay,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Clay, Brush.verticalGradient(listOf(Color(0xFFFFE3D3), Color(0xFFFFD0BE))),
        card = Color(0xFFFFF4EC), edge = Color.Transparent, corner = 32.dp, shadowDark = Color(0x55E58C6A),
        tactile = Color(0xFFFFF6F0), tactileInk = Color(0xFF8A5A45), tactileAccent = Color(0xFFF0705E),
        accentHue = 20f, haptic = CONTEXT, hapticsOn = hapticsOn, look = TactileLook.Clay,
    )
    UiSkin.Brutal -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Brutal, null, dots = Color.White.copy(alpha = 0.12f),
        card = Color(0xFF27272A), edge = Color(0xFFFAFAFA), corner = 6.dp, shadowDark = Color(0xFFA3E635),
        tactile = Color(0xFF3F3F46), tactileInk = Color(0xE6FFFFFF), tactileAccent = Color(0xFFFACC15), onTactileAccent = Color.Black,
        accentHue = 220f, haptic = CONFIRM, hapticsOn = hapticsOn,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Brutal, null, dots = Color.Black.copy(alpha = 0.13f),
        card = Color.White, edge = Color.Black, corner = 6.dp, shadowDark = Color.Black,
        tactile = Color(0xFFF4F4F4), tactileInk = Color(0xCC000000), tactileAccent = Color(0xFFFACC15), onTactileAccent = Color.Black,
        accentHue = 220f, haptic = CONFIRM, hapticsOn = hapticsOn,
    )
    UiSkin.Bento -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Tile, null, card = Color(0xFF17171F), edge = Color(0xFF24242E), corner = 18.dp,
        tactile = Color(0xFF3A3A46), tactileInk = Color(0xCCFFFFFF), tactileAccent = Color(0xFFF97316),
        accentHue = 22f, haptic = TICK, hapticsOn = hapticsOn,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Tile, null, card = Color.White, edge = Color(0xFFE4E4E7), corner = 18.dp,
        tactile = Color(0xFFE7E7EA), tactileInk = Color(0x8C000000), tactileAccent = Color(0xFFF97316),
        accentHue = 22f, haptic = TICK, hapticsOn = hapticsOn,
    )
    UiSkin.HiFi -> if (dark) SkinSpec(
        skin, true, SurfaceStyle.Metal, Brush.verticalGradient(listOf(Color(0xFF3A2615), Color(0xFF1E130A))),
        card = Color(0xFF2B1D12), cardBrush = Brush.verticalGradient(listOf(Color(0xFF3A2818), Color(0xFF24170D))),
        edge = Color(0xFF5C4026), corner = 10.dp, shadowDark = Color(0x8C000000),
        tactile = Color(0xFFCFCFCF), tactileInk = Color(0xFF2A1A0C), tactileAccent = Color(0xFFD97706),
        accentHue = 35f, haptic = KEY, hapticsOn = hapticsOn, look = TactileLook.Metal,
    ) else SkinSpec(
        skin, false, SurfaceStyle.Metal, Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFFC9CED6))),
        card = Color(0xFFE2E2E2), cardBrush = Brush.verticalGradient(listOf(Color(0xFFF2F2F2), Color(0xFFC4C4C4))),
        edge = Color(0xFF8A8A8A), corner = 10.dp, shadowDark = Color(0x59000000),
        tactile = Color(0xFFD4D4D4), tactileInk = Color(0xFF3A4048), tactileAccent = Color(0xFFB45309),
        accentHue = 35f, haptic = KEY, hapticsOn = hapticsOn, look = TactileLook.Metal,
    )
}

// ---------- Material colours, shapes and type per look ----------

private fun scheme(
    dark: Boolean, accent: Color, onAccent: Color, bg: Color, raised: Color, dialog: Color, ink: Color, muted: Color, outline: Color,
): ColorScheme = if (dark) darkColorScheme(
    primary = accent, onPrimary = onAccent, primaryContainer = raised, onPrimaryContainer = ink,
    secondary = accent, secondaryContainer = accent, onSecondaryContainer = onAccent, tertiary = accent,
    background = bg, onBackground = ink, surface = bg, onSurface = ink,
    surfaceVariant = raised, onSurfaceVariant = muted, surfaceContainer = dialog, surfaceContainerHigh = dialog,
    surfaceContainerHighest = dialog, surfaceContainerLow = raised, outline = outline, outlineVariant = outline,
) else lightColorScheme(
    primary = accent, onPrimary = onAccent, primaryContainer = raised, onPrimaryContainer = ink,
    secondary = accent, secondaryContainer = accent, onSecondaryContainer = onAccent, tertiary = accent,
    background = bg, onBackground = ink, surface = bg, onSurface = ink,
    surfaceVariant = raised, onSurfaceVariant = muted, surfaceContainer = dialog, surfaceContainerHigh = dialog,
    surfaceContainerHighest = dialog, surfaceContainerLow = raised, outline = outline, outlineVariant = outline,
)

/** Colour scheme for a Pro look, or null for Classic (which keeps the brand / colour themes). */
fun skinColorScheme(skin: UiSkin, dark: Boolean): ColorScheme? = when (skin) {
    UiSkin.Classic -> null
    UiSkin.Paper -> if (dark) scheme(true, Color(0xFFA78BFA), Color(0xFF1E1530), Color(0xFF1E1A22), Color(0xFF2A2530), Color(0xFF2E2934), Color(0xFFEEE8F4), Color(0xFFA69DB2), Color(0xFF3E3746))
        else scheme(false, Color(0xFF6D28D9), Color.White, Color(0xFFF6ECF4), Color(0xFFF1E9F1), Color(0xFFF8F2F7), Color(0xFF1E1530), Color(0xFF6E6480), Color(0xFFD9C6D6))
    UiSkin.Midnight -> scheme(true, Color(0xFFA78BFA), Color(0xFF16171C), Color(0xFF16171C), Color(0xFF1F2027), Color(0xFF22232B), Color(0xFFECEDF3), Color(0xFF9A9CAA), Color(0xFF2E3038))
    UiSkin.Keys -> if (dark) scheme(true, Color(0xFFFB923C), Color(0xFF24201A), Color(0xFF24201A), Color(0xFF332D24), Color(0xFF3B342A), Color(0xFFF1E7CF), Color(0xFFB3A68D), Color(0xFF4A4134))
        else scheme(false, Color(0xFFC2410C), Color.White, Color(0xFFEFE6D2), Color(0xFFFBF5E6), Color(0xFFFBF5E6), Color(0xFF2B2620), Color(0xFF6E6455), Color(0xFFD8CBAE))
    UiSkin.Jelly -> if (dark) scheme(true, Color(0xFFFB7185), Color(0xFF1A1020), Color(0xFF1A1020), Color(0xFF331F31), Color(0xFF3A2438), Color(0xFFFCE7F3), Color(0xFFC9A3B8), Color(0xFF4A2F46))
        else scheme(false, Color(0xFFE11D48), Color.White, Color(0xFFFDF2F8), Color(0xFFFFF7FB), Color(0xFFFFF7FB), Color(0xFF4C0519), Color(0xFF9F5A75), Color(0xFFF9C9DC))
    UiSkin.Sticker -> if (dark) scheme(true, Color(0xFFFDE047), Color(0xFF111111), Color(0xFF1C1A16), Color(0xFF2A2721), Color(0xFF2A2721), Color(0xFFFFF7D6), Color(0xFFD6CFB3), Color(0xFFFDE047))
        else scheme(false, Color(0xFFE11D48), Color.White, Color(0xFFFEF3C7), Color.White, Color.White, Color(0xFF111111), Color(0xFF44403C), Color(0xFF111111))
    UiSkin.Bubble -> if (dark) scheme(true, Color(0xFF7DD3FC), Color(0xFF0B1E2E), Color(0xFF0B1E2E), Color(0x1AFFFFFF), Color(0xFF16263A), Color(0xFFE0F2FE), Color(0xFF9CC3DA), Color(0x38FFFFFF))
        else scheme(false, Color(0xFF0284C7), Color.White, Color(0xFFE0F2FE), Color(0x8CFFFFFF), Color(0xFFF5FBFF), Color(0xFF0C4A6E), Color(0xFF4B6B80), Color(0x330C4A6E))
    UiSkin.Arcade -> scheme(true, Color(0xFFFF4D6D), Color.White, Color(0xFF1A1033), Color(0xFF241747), Color(0xFF2B1B4F), Color(0xFFEDE9FE), Color(0xFFA99BD1), Color(0xFF3B2A6B))
    UiSkin.Minimal -> if (dark) scheme(true, Color(0xFFF5F5F5), Color(0xFF111111), Color(0xFF0A0A0A), Color(0xFF161616), Color(0xFF171717), Color(0xFFF5F5F5), Color(0xFF8A8A8A), Color(0xFF2A2A2A))
        else scheme(false, Color(0xFF111111), Color.White, Color(0xFFFAFAFA), Color(0xFFF2F2F2), Color.White, Color(0xFF111111), Color(0xFF8A8A8A), Color(0xFFE5E5E5))
    UiSkin.Glass -> if (dark) scheme(true, Color.White, Color(0xFF4C1D95), Color(0xFF2A1F5C), Color(0x33FFFFFF), Color(0xFF2E2563), Color.White, Color(0xB3FFFFFF), Color(0x47FFFFFF))
        else scheme(false, Color(0xFF6D28D9), Color.White, Color(0xFFF3EEFF), Color(0x80FFFFFF), Color(0xFFF7F4FF), Color(0xFF1E1B4B), Color(0xFF5B5675), Color(0x331E1B4B))
    UiSkin.Neu -> if (dark) scheme(true, Color(0xFFFB7185), Color(0xFF2A2D34), Color(0xFF2A2D34), Color(0xFF2E3138), Color(0xFF30333B), Color(0xFFE2E6EE), Color(0xFF9AA3B2), Color(0xFF3A3E47))
        else scheme(false, Color(0xFFE11D48), Color.White, Color(0xFFE0E5EC), Color(0xFFE6EBF1), Color(0xFFE6EBF1), Color(0xFF3D4A5C), Color(0xFF7D8AA0), Color(0xFFC5CED9))
    UiSkin.Clay -> if (dark) scheme(true, Color(0xFFFF7A3D), Color.White, Color(0xFF2A1E1A), Color(0xFF3A2A24), Color(0xFF3A2A24), Color(0xFFFBEDE4), Color(0xFFC9A897), Color(0xFF5A4238))
        else scheme(false, Color(0xFFF0705E), Color.White, Color(0xFFFFE3D3), Color(0xFFFFF4EC), Color(0xFFFFF4EC), Color(0xFF4A2A1E), Color(0xFF8A6A5C), Color(0xFFF0C6B0))
    UiSkin.Brutal -> if (dark) scheme(true, Color(0xFFFACC15), Color.Black, Color(0xFF18181B), Color(0xFF27272A), Color(0xFF27272A), Color(0xFFFAFAFA), Color(0xFFD4D4D8), Color(0xFFFAFAFA))
        else scheme(false, Color(0xFF2563EB), Color.White, Color(0xFFD9F99D), Color.White, Color.White, Color.Black, Color(0xFF333333), Color.Black)
    UiSkin.Bento -> if (dark) scheme(true, Color(0xFFF97316), Color.White, Color(0xFF0B0B10), Color(0xFF17171F), Color(0xFF17171F), Color(0xFFF4F4F5), Color(0xFF8B8B98), Color(0xFF24242E))
        else scheme(false, Color(0xFFEA580C), Color.White, Color(0xFFF4F4F5), Color.White, Color.White, Color(0xFF18181B), Color(0xFF71717A), Color(0xFFE4E4E7))
    UiSkin.HiFi -> if (dark) scheme(true, Color(0xFFF59E0B), Color(0xFF1D1209), Color(0xFF2A1B0F), Color(0xFF3A2818), Color(0xFF33231A), Color(0xFFF5E6C8), Color(0xFFC2A77A), Color(0xFF5C4026))
        else scheme(false, Color(0xFFB45309), Color.White, Color(0xFFDDE1E6), Color(0xFFEDEDED), Color(0xFFF2F2F2), Color(0xFF1F2937), Color(0xFF52525B), Color(0xFF9CA3AF))
}

fun skinShapes(skin: UiSkin): Shapes? {
    val r = when (skin) {
        UiSkin.Classic -> return null
        UiSkin.Minimal -> listOf(2, 4, 6, 8, 12)
        UiSkin.Glass, UiSkin.Neu, UiSkin.Paper, UiSkin.Midnight -> listOf(12, 16, 20, 24, 28)
        UiSkin.Keys -> listOf(6, 8, 12, 14, 16)
        UiSkin.Jelly, UiSkin.Bubble -> listOf(16, 20, 24, 28, 32)
        UiSkin.Sticker, UiSkin.Arcade -> listOf(10, 14, 18, 22, 24)
        UiSkin.Clay -> listOf(14, 20, 26, 30, 36)
        UiSkin.Brutal -> listOf(4, 4, 6, 6, 8)
        UiSkin.Bento -> listOf(10, 14, 18, 22, 26)
        UiSkin.HiFi -> listOf(4, 6, 10, 12, 16)
    }
    return Shapes(
        RoundedCornerShape(r[0].dp), RoundedCornerShape(r[1].dp), RoundedCornerShape(r[2].dp),
        RoundedCornerShape(r[3].dp), RoundedCornerShape(r[4].dp),
    )
}

private fun Typography.map(f: (TextStyle) -> TextStyle) = copy(
    displayLarge = f(displayLarge), displayMedium = f(displayMedium), displaySmall = f(displaySmall),
    headlineLarge = f(headlineLarge), headlineMedium = f(headlineMedium), headlineSmall = f(headlineSmall),
    titleLarge = f(titleLarge), titleMedium = f(titleMedium), titleSmall = f(titleSmall),
    bodyLarge = f(bodyLarge), bodyMedium = f(bodyMedium), bodySmall = f(bodySmall),
    labelLarge = f(labelLarge), labelMedium = f(labelMedium), labelSmall = f(labelSmall),
)

private fun Typography.headings(f: (TextStyle) -> TextStyle) = copy(
    headlineLarge = f(headlineLarge), headlineMedium = f(headlineMedium), headlineSmall = f(headlineSmall),
    titleLarge = f(titleLarge), titleMedium = f(titleMedium),
)

private val condensed by lazy { FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)) }
private val light by lazy { FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL)) }

/** Type for a Pro look (system fonts only, so nothing is downloaded), or null for Classic. */
fun skinTypography(skin: UiSkin, base: Typography): Typography? = when (skin) {
    UiSkin.Classic -> null
    UiSkin.Minimal -> base.headings { it.copy(fontFamily = light, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp) }
    UiSkin.Glass -> base.headings { it.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp) }
    UiSkin.Neu -> base.headings { it.copy(fontWeight = FontWeight.Medium) }
    UiSkin.Paper -> base.headings { it.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp) }
    UiSkin.Midnight -> base.headings { it.copy(fontWeight = FontWeight.SemiBold) }
    UiSkin.Keys -> base.map { it.copy(fontFamily = FontFamily.Monospace) }
        .headings { it.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
    UiSkin.Clay -> base.headings { it.copy(fontWeight = FontWeight.ExtraBold) }
    UiSkin.Jelly, UiSkin.Bubble -> base.headings { it.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp) }
    UiSkin.Sticker -> base.headings { it.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp) }
        .copy(labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Black))
    UiSkin.Arcade -> base.headings { it.copy(fontFamily = condensed, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp) }
    UiSkin.Brutal -> base.headings { it.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.4).sp) }
        .copy(labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 0.6.sp))
    UiSkin.Bento -> base.headings { it.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp) }
    UiSkin.HiFi -> base.map { it.copy(fontFamily = condensed) }
        .headings { it.copy(fontFamily = condensed, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
}

// ---------- drawing helpers ----------

/** Paints the look's screen background (gradient, dotted paper, or the plain theme colour). */
@Composable
fun Modifier.skinBackdrop(): Modifier {
    val s = LocalSkin.current
    val bg = MaterialTheme.colorScheme.background
    return drawBehind {
        if (s.backdrop != null) drawRect(s.backdrop) else drawRect(bg)
        // Glossy Jelly: soft candy bubbles floating behind everything.
        if (s.look == TactileLook.Jelly) {
            val a = if (s.dark) 0.16f else 0.55f
            drawCircle(Color(0xFFF9A8D4).copy(alpha = a), size.width * 0.32f, Offset(size.width * 0.12f, size.height * 0.06f))
            drawCircle(Color(0xFFC4B5FD).copy(alpha = a), size.width * 0.22f, Offset(size.width * 0.98f, size.height * 0.30f))
            drawCircle(Color(0xFF86EFAC).copy(alpha = a * 0.8f), size.width * 0.18f, Offset(size.width * 0.02f, size.height * 0.68f))
            drawCircle(Color(0xFFFDE68A).copy(alpha = a * 0.8f), size.width * 0.15f, Offset(size.width * 0.9f, size.height * 0.88f))
        }
        if (s.dots.alpha > 0f) {
            val step = 14.dp.toPx()
            val r = 1.2.dp.toPx()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) { drawCircle(s.dots, r, Offset(x, y)); x += step }
                y += step
            }
        }
    }
}

/** Scaffold / screen colour: see-through in the Pro looks so [skinBackdrop] shows. */
@Composable
fun skinScreenColor(): Color = if (LocalSkin.current.isClassic) MaterialTheme.colorScheme.background else Color.Transparent

/** Card, dock and mini-player treatment for the current look. */
@Composable
fun Modifier.skinSurface(shape: Shape = LocalSkin.current.shape): Modifier {
    val s = LocalSkin.current
    return when (s.style) {
        SurfaceStyle.Material -> this.background(MaterialTheme.colorScheme.surfaceVariant, shape)
        SurfaceStyle.Hairline -> this.background(MaterialTheme.colorScheme.background, shape).border(1.dp, s.edge, shape)
        SurfaceStyle.Glass -> this
            .dropShadow(shape, Color.Black.copy(alpha = if (s.dark) 0.18f else 0.08f), offsetY = 8.dp, blur = 24.dp)
            .background(s.card, shape).border(1.dp, s.edge, shape)
        SurfaceStyle.Raised -> this
            .dropShadow(shape, s.shadowDark, 9.dp, 9.dp, 18.dp)
            .dropShadow(shape, s.shadowLight, (-9).dp, (-9).dp, 18.dp)
            .background(s.cardBrush ?: Brush.linearGradient(listOf(s.card, s.card)), shape)
        SurfaceStyle.Clay -> this
            .dropShadow(shape, s.shadowDark, offsetY = 12.dp, blur = 22.dp)
            .background(s.card, shape)
            .innerShadow(shape, Color.White.copy(alpha = if (s.dark) 0.12f else 0.9f), 5.dp, 5.dp, 10.dp)
            .innerShadow(shape, Color.Black.copy(alpha = if (s.dark) 0.3f else 0.1f), (-5).dp, (-5).dp, 10.dp)
        SurfaceStyle.Brutal -> this
            .dropShadow(shape, s.shadowDark, 4.dp, 4.dp)
            .background(s.card, shape).border(3.dp, s.edge, shape)
        SurfaceStyle.Tile -> this.background(s.card, shape).border(1.dp, s.edge, shape)
        // Glossy jelly: soft pink shadow, a shine along the top and a little colour at the bottom.
        SurfaceStyle.Jelly -> this
            .dropShadow(shape, s.shadowDark, offsetY = 10.dp, blur = 22.dp)
            .background(s.card, shape)
            .innerShadow(shape, Color.White.copy(alpha = if (s.dark) 0.10f else 0.95f), offsetY = 3.dp, blur = 6.dp)
            .innerShadow(shape, s.tactileAccent.copy(alpha = if (s.dark) 0.18f else 0.10f), offsetY = (-3).dp, blur = 8.dp)
        SurfaceStyle.Metal -> this
            .dropShadow(shape, s.shadowDark, offsetY = 4.dp, blur = 10.dp)
            .background(s.cardBrush ?: Brush.linearGradient(listOf(s.card, s.card)), shape)
            .border(1.dp, s.edge, shape)
            .innerShadow(shape, Color.White.copy(alpha = if (s.dark) 0.12f else 0.8f), offsetY = 1.5.dp, blur = 1.dp)
    }
}

private val PRESS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
private val RELEASE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) HapticFeedbackConstants.KEYBOARD_RELEASE else -1

/**
 * Neumorphic keys: a soft tick when the finger presses the key down and a lighter one when it
 * lets go, so it feels like a real button. Returns true when this look uses it (then skip the
 * single tap buzz).
 */
@Composable
fun SkinPressHaptics(pressed: Boolean): Boolean {
    val s = LocalSkin.current
    val view = LocalView.current
    val on = s.hapticsOn && s.pressHaptics
    val was = remember { booleanArrayOf(false) }
    androidx.compose.runtime.LaunchedEffect(pressed, on) {
        if (!on) return@LaunchedEffect
        if (pressed) view.performHapticFeedback(PRESS)
        else if (was[0] && RELEASE >= 0) view.performHapticFeedback(RELEASE)
        was[0] = pressed
    }
    return s.pressHaptics
}

/** A tap buzz in the current look's feel; does nothing in Classic or when turned off. */
@Composable
fun rememberSkinHaptic(): () -> Unit {
    val s = LocalSkin.current
    val view = LocalView.current
    return remember(s.haptic, s.hapticsOn, view) {
        { if (s.hapticsOn) view.performHapticFeedback(s.haptic) }
    }
}
