package com.vikash.mp3player.data

import androidx.annotation.StringRes
import com.vikash.mp3player.R

/**
 * The whole-app look. Classic is the free look everyone has today; the rest come with Pro (free
 * users can try one for [com.vikash.mp3player.ui.skin.SkinTrial.SECONDS] seconds).
 */
enum class UiSkin(val id: String, @StringRes val labelRes: Int) {
    Classic("classic", R.string.skin_classic),
    // Tactile themes (2026-11-02): paper cards, buttons rising out of them with a red/green ring.
    Paper("paper", R.string.skin_paper),
    Clay("clay", R.string.skin_clay),
    Midnight("midnight", R.string.skin_midnight),
    HiFi("hifi", R.string.skin_hifi),
    Glass("glass", R.string.skin_glass),
    Keys("keys", R.string.skin_keys),
    Minimal("minimal", R.string.skin_minimal),
    Neu("neu", R.string.skin_neu),
    Brutal("brutal", R.string.skin_brutal),
    Bento("bento", R.string.skin_bento);

    companion object {
        fun from(id: String?): UiSkin = entries.firstOrNull { it.id == id } ?: Classic
    }
}
