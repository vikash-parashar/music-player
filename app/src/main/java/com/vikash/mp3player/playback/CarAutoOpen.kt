package com.vikash.mp3player.playback

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vikash.mp3player.MainActivity
import com.vikash.mp3player.R
import com.vikash.mp3player.ui.CarLaunch

/**
 * "Car mode when my car connects": when the Bluetooth device the listener picked as their car
 * connects, open Car mode. Android blocks apps from jumping on screen from the background, so
 * when Ampli isn't visible we post a tap-to-open notification instead. The device name comes from
 * AudioDeviceInfo.productName, which needs no Bluetooth permission.
 */
object CarAutoOpen {
    private const val PREFS = "ampli_prefs"
    private const val KEY_ON = "car_auto_open"
    private const val KEY_CAR = "car_auto_open_device"
    private const val KEY_SEEN = "car_auto_open_seen"
    private const val CHANNEL = "car_connected"
    private const val NOTIFICATION_ID = 4711

    /** True while MainActivity is resumed (set from its onResume/onPause). */
    @Volatile var appVisible = false

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ON, false)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ON, on).apply()

    fun car(context: Context): String? = prefs(context).getString(KEY_CAR, null)
    fun setCar(context: Context, name: String) = prefs(context).edit().putString(KEY_CAR, name).apply()

    /** Bluetooth audio devices this phone connected while Ampli was running, newest first. */
    fun seen(context: Context): List<String> =
        prefs(context).getString(KEY_SEEN, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    /** Bluetooth audio device names connected right now. */
    fun connectedNow(context: Context): List<String> {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        return audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isBluetoothAudio() }
            .mapNotNull { it.productName?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
    }

    /** Remembers a Bluetooth audio device's name, so it can be picked in Settings. */
    fun remember(context: Context, device: AudioDeviceInfo) {
        val name = device.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val list = (listOf(name) + seen(context).filter { it != name }).take(8)
        prefs(context).edit().putString(KEY_SEEN, list.joinToString("\n")).apply()
    }

    /** True when [device] is the picked car and the setting is on. */
    fun isCar(context: Context, device: AudioDeviceInfo): Boolean {
        if (!isEnabled(context)) return false
        val picked = car(context) ?: return false
        return device.productName?.toString()?.trim() == picked
    }

    fun AudioDeviceInfo.isBluetoothAudio(): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    private fun carModeIntent(context: Context) = Intent(context, MainActivity::class.java)
        .setAction(CarLaunch.ACTION_CAR_MODE)
        // Playback already started; Car mode only needs to open.
        .putExtra(CarLaunch.EXTRA_AUTOPLAY, false)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Opens Car mode now if Ampli is on screen, otherwise posts the tap-to-open notification. */
    fun open(context: Context, carName: String) {
        if (appVisible) {
            runCatching { context.startActivity(carModeIntent(context)) }
            return
        }
        notify(context, carName)
    }

    @android.annotation.SuppressLint("MissingPermission") // checked just below
    private fun notify(context: Context, carName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.car_auto_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val tap = PendingIntent.getActivity(
            context, NOTIFICATION_ID, carModeIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ampli)
            .setContentTitle(context.getString(R.string.car_auto_notif_title, carName))
            .setContentText(context.getString(R.string.car_auto_notif_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}
