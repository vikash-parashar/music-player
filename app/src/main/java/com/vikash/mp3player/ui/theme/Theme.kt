package com.vikash.mp3player.ui.theme

import com.vikash.mp3player.data.ColorProfile
import com.vikash.mp3player.data.ThemeMode
import com.vikash.mp3player.data.UiSkin
import com.vikash.mp3player.ui.skin.LocalSkin
import com.vikash.mp3player.ui.skin.skinColorScheme
import com.vikash.mp3player.ui.skin.skinShapes
import com.vikash.mp3player.ui.skin.skinSpec
import com.vikash.mp3player.ui.skin.skinTypography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ColorScheme
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** Ampli brand colors, taken from the logo gradient. */
object Ampli {
    val Slate = Color(0xFF334155)
    val Rose = Color(0xFFE11D48)
    val RoseDeep = Color(0xFFBE123C)
    val Night = Color(0xFF0F0D14)
    val NightRaised = Color(0xFF1C1820)
    val Blush = Color(0xFFFDA4AF)

    /**
     * Hero cards, call-to-action pills and artwork placeholders. Luxury look: deep ink warming
     * into ember, so white text and gold accents sit on it well. (The app icon keeps slate to rose.)
     */
    val Gradient: Brush = Brush.linearGradient(listOf(Color(0xFF1B1512), Color(0xFF4A1F12), Color(0xFFC2410C)))
}

/**
 * The "minimal luxury" look: ink-black (day) or ivory (night) controls, a warm coral glow behind
 * whatever is active, and gold for the thing that's playing or selected.
 */
object Lux {
    val Ink = Color(0xFF16120F)
    val Ivory = Color(0xFFF4EFEA)
    val Glow = Color(0xFFFF5A4E)
    val Ember = Color(0xFFFF7A3C)
    val Gold = Color(0xFFF59E0B)
    val GoldDeep = Color(0xFFEA580C)
    val GoldBrush: Brush = Brush.horizontalGradient(listOf(Gold, GoldDeep))
}

private val DarkColors = darkColorScheme(
    primary = Lux.Ivory,
    onPrimary = Lux.Ink,
    primaryContainer = Color(0xFF3A2416),
    onPrimaryContainer = Color(0xFFFFE9DC),
    secondary = Color(0xFFBDB3AA),
    secondaryContainer = Lux.Ivory,
    onSecondaryContainer = Lux.Ink,
    tertiary = Lux.Gold,
    background = Color(0xFF0D0B0A),
    onBackground = Lux.Ivory,
    surface = Color(0xFF0D0B0A),
    onSurface = Lux.Ivory,
    surfaceVariant = Color(0xFF1A1715),
    onSurfaceVariant = Color(0xFF9A9087),
    surfaceContainer = Color(0xFF171412),
    surfaceContainerHigh = Color(0xFF221E1B),
    outline = Color(0xFF3A332E),
    outlineVariant = Color(0xFF2A2522),
)

private val LightColors = lightColorScheme(
    primary = Lux.Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE9DC),
    onPrimaryContainer = Color(0xFF3A1A0A),
    secondary = Color(0xFF6F6760),
    secondaryContainer = Lux.Ink,
    onSecondaryContainer = Color.White,
    tertiary = Lux.GoldDeep,
    background = Color(0xFFF7F4F0),
    onBackground = Lux.Ink,
    surface = Color(0xFFF7F4F0),
    onSurface = Lux.Ink,
    surfaceVariant = Color(0xFFEFEAE4),
    onSurfaceVariant = Color(0xFF7A7169),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFD9D1C9),
    outlineVariant = Color(0xFFE9E3DD),
)

/**
 * One Pro colour theme: an accent and a tinted background for day, and the same for night.
 * Everything else in the Material scheme is worked out from those, so every screen follows it.
 */
private class Palette(
    val dayAccent: Color, val dayBg: Color, val dayRaised: Color, val dayInk: Color,
    val nightAccent: Color, val nightBg: Color, val nightRaised: Color, val nightInk: Color,
    val dayContainer: Color, val nightContainer: Color,
) {
    fun light(): ColorScheme = lightColorScheme(
        primary = dayAccent, onPrimary = Color.White,
        primaryContainer = dayContainer, onPrimaryContainer = dayInk,
        secondary = dayAccent.copy(alpha = 0.85f), secondaryContainer = dayRaised, onSecondaryContainer = dayInk,
        tertiary = dayAccent,
        background = dayBg, onBackground = dayInk, surface = dayBg, onSurface = dayInk,
        surfaceVariant = dayRaised, surfaceContainer = dayRaised, surfaceContainerHigh = dayContainer,
    )

    fun dark(): ColorScheme = darkColorScheme(
        primary = nightAccent, onPrimary = nightBg,
        primaryContainer = nightContainer, onPrimaryContainer = Color.White,
        secondary = nightAccent.copy(alpha = 0.85f), secondaryContainer = nightRaised, onSecondaryContainer = nightInk,
        tertiary = nightAccent,
        background = nightBg, onBackground = nightInk, surface = nightBg, onSurface = nightInk,
        surfaceVariant = nightRaised, onSurfaceVariant = nightInk.copy(alpha = 0.7f),
        surfaceContainer = nightRaised, surfaceContainerHigh = nightContainer,
        outline = nightInk.copy(alpha = 0.3f),
    )
}

private fun palette(profile: ColorProfile): Palette? = when (profile) {
    ColorProfile.Ampli -> null
    ColorProfile.SkyBlue -> Palette(
        Color(0xFF0284C7), Color(0xFFF0F9FF), Color(0xFFE0F2FE), Color(0xFF0C1E2B),
        Color(0xFF7DD3FC), Color(0xFF07131C), Color(0xFF0E2130), Color(0xFFE6F4FD),
        Color(0xFFBAE6FD), Color(0xFF0C4A6E),
    )
    ColorProfile.MilkyWhite -> Palette(
        Color(0xFF9A6B3F), Color(0xFFFFFBF3), Color(0xFFF7EFE2), Color(0xFF2A2118),
        Color(0xFFE8C9A4), Color(0xFF15120E), Color(0xFF221D17), Color(0xFFF6EEE3),
        Color(0xFFF0E1CB), Color(0xFF5C4128),
    )
    ColorProfile.Electric -> Palette(
        Color(0xFF7C3AED), Color(0xFFF8F5FF), Color(0xFFEDE7FE), Color(0xFF1A1033),
        Color(0xFF22D3EE), Color(0xFF09061A), Color(0xFF15102C), Color(0xFFEDE9FE),
        Color(0xFFDDD0FD), Color(0xFF4C1D95),
    )
    ColorProfile.Snow -> Palette(
        Color(0xFF4F7DD8), Color(0xFFFBFDFF), Color(0xFFEEF3FA), Color(0xFF16202E),
        Color(0xFFBFD4F6), Color(0xFF0B1018), Color(0xFF151C27), Color(0xFFF1F5FB),
        Color(0xFFDCE7F8), Color(0xFF2C4675),
    )
    ColorProfile.Water -> Palette(
        Color(0xFF0D9488), Color(0xFFF0FDFA), Color(0xFFDDF7F2), Color(0xFF0B2420),
        Color(0xFF5EEAD4), Color(0xFF041615), Color(0xFF0B2422), Color(0xFFE2FBF6),
        Color(0xFFB7EEE4), Color(0xFF115E59),
    )
    ColorProfile.Air -> Palette(
        Color(0xFF6366F1), Color(0xFFF7F8FF), Color(0xFFECEEFE), Color(0xFF181A33),
        Color(0xFFA5B4FC), Color(0xFF0C0E1A), Color(0xFF161A2E), Color(0xFFEEF0FF),
        Color(0xFFDDE1FE), Color(0xFF3730A3),
    )
}

/** The swatch shown in Settings: the profile's accent and day background. */
fun profileSwatch(profile: ColorProfile): Pair<Color, Color> =
    palette(profile)?.let { it.dayAccent to it.dayBg } ?: (Lux.Ink to Color(0xFFF7F4F0))

private val AmpliTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * Brand colors by default so the app looks like Ampli. Follows the phone unless the user picks Day
 * or Night in Settings. Pro adds colour themes ([ColorProfile]), pure black (AMOLED) and wallpaper
 * colors (Android 12+, which win over a colour theme).
 */
@Composable
fun Mp3PlayerTheme(
    mode: ThemeMode = ThemeMode.System,
    amoledBlack: Boolean = false,
    wallpaperColors: Boolean = false,
    profile: ColorProfile = ColorProfile.Ampli,
    skin: UiSkin = UiSkin.Classic,
    skinHaptics: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    // A Pro app style brings its own colours; colour themes and wallpaper colours apply to Classic.
    var scheme = skinColorScheme(skin, dark) ?: when {
        wallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> palette(profile)?.dark() ?: DarkColors
        else -> palette(profile)?.light() ?: LightColors
    }
    if (dark && amoledBlack && skin == UiSkin.Classic) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF111111),
            surfaceContainer = Color(0xFF0B0B0B),
            surfaceContainerHigh = Color(0xFF161616),
        )
    }
    val shapes = skinShapes(skin)
    MaterialTheme(
        colorScheme = scheme,
        typography = skinTypography(skin, AmpliTypography) ?: AmpliTypography,
        shapes = shapes ?: MaterialTheme.shapes,
    ) {
        CompositionLocalProvider(LocalSkin provides skinSpec(skin, dark, skinHaptics), content = content)
    }
}
