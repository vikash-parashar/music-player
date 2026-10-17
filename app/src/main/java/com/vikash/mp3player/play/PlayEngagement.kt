package com.vikash.mp3player.play

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Google Play's in-app rating card. Only works for Play installs; elsewhere it quietly does nothing. */
object PlayReview {
    fun ask(activity: Activity, onAsked: () -> Unit) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful && !activity.isFinishing && !activity.isDestroyed) {
                onAsked()
                manager.launchReviewFlow(activity, task.result)
            }
        }
    }
}

/**
 * Flexible in-app updates: Play downloads the new version in the background while the music keeps
 * playing, then the app shows "Update ready, Restart". Sideloaded test builds get no update info.
 */
object AppUpdates {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var manager: AppUpdateManager? = null
    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) _ready.value = true
    }

    private fun manager(context: Context): AppUpdateManager =
        manager ?: AppUpdateManagerFactory.create(context.applicationContext).also {
            it.registerListener(listener)
            manager = it
        }

    fun check(context: Context, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val m = manager(context)
        m.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                _ready.value = true
            } else if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                runCatching {
                    m.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
                }
            }
        }
    }

    /** Called on resume: an update may have finished downloading while the app was in the background. */
    fun refresh() {
        manager?.appUpdateInfo?.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) _ready.value = true
        }
    }

    fun complete() {
        manager?.completeUpdate()
    }
}
