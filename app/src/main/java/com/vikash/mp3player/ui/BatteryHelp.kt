package com.vikash.mp3player.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.ui.skin.SkinButton

/**
 * OnePlus, Xiaomi, Vivo, Oppo and Realme stop background apps to save battery, which cuts the music
 * when the screen goes off. This asks once, on those brands only, to exempt Ampli. It opens the
 * system list instead of using REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which Play restricts.
 */
object BatteryHelp {
    private const val PREFS = "ampli_battery_help"
    private const val KEY_DONE = "asked"
    private val BRANDS = setOf("oneplus", "xiaomi", "redmi", "poco", "vivo", "iqoo", "oppo", "realme")

    private fun brand(): String = (Build.MANUFACTURER ?: "").lowercase()

    fun shouldShow(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (brand() !in BRANDS) return false
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun markDone(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()

    /** The brand's own "allow background" switch lives on the app info page; the battery list is the fallback. */
    fun openSettings(context: Context) {
        val tries = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        for (i in tries) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            } catch (e: ActivityNotFoundException) {
            } catch (e: SecurityException) {
            }
        }
    }

    fun brandName(): String = when (brand()) {
        "oneplus" -> "OnePlus"
        "xiaomi", "redmi", "poco" -> "Xiaomi"
        "vivo", "iqoo" -> "Vivo"
        "oppo" -> "Oppo"
        "realme" -> "Realme"
        else -> Build.MANUFACTURER ?: ""
    }
}

@Composable
fun BatteryHelpDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.battery_help_title)) },
        text = {
            Column {
                Text(stringResource(R.string.battery_help_body, BatteryHelp.brandName()), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.battery_help_steps), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            SkinButton(onClick = { BatteryHelp.openSettings(context); onDone() }) {
                Text(stringResource(R.string.battery_help_allow))
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.battery_help_later)) } },
    )
}
