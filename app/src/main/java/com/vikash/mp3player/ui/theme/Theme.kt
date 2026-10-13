package com.vikash.mp3player.ui.theme

import com.vikash.mp3player.data.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
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

    /** Same diagonal gradient as the app icon. */
    val Gradient: Brush = Brush.linearGradient(listOf(Slate, Rose))
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFDA4AF),
    onPrimary = Color(0xFF4C0519),
    primaryContainer = Ampli.RoseDeep,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFCBD5E1),
    secondaryContainer = Color(0xFF2B2F3A),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Ampli.Rose,
    background = Ampli.Night,
    onBackground = Color(0xFFF2EEF0),
    surface = Ampli.Night,
    onSurface = Color(0xFFF2EEF0),
    surfaceVariant = Ampli.NightRaised,
    onSurfaceVariant = Color(0xFFB8AEB3),
    surfaceContainer = Ampli.NightRaised,
    surfaceContainerHigh = Color(0xFF27222B),
    outline = Color(0xFF4A4248),
)

private val LightColors = lightColorScheme(
    primary = Ampli.RoseDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4E6),
    onPrimaryContainer = Color(0xFF4C0519),
    secondary = Color(0xFF475569),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Ampli.Rose,
    background = Color(0xFFFFFAFA),
    surface = Color(0xFFFFFAFA),
    surfaceVariant = Color(0xFFF3ECEE),
    surfaceContainer = Color(0xFFF7F1F2),
)

private val AmpliTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Brand colors everywhere (no wallpaper colors) so the app always looks like Ampli. Follows the phone unless the user picks Day or Night in Settings. */
@Composable
fun Mp3PlayerTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AmpliTypography,
        content = content,
    )
}
