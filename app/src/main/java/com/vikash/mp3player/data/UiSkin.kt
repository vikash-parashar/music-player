package com.vikash.mp3player.data

import androidx.annotation.StringRes
import com.vikash.mp3player.R

/**
 * The whole-app look. Classic is the free look everyone has today; the rest come with Pro (free
 * users can try one for [com.vikash.mp3player.ui.skin.SkinTrial.SECONDS] seconds).
 */
enum class UiSkin(val id: String, @StringRes val labelRes: Int) {
    Classic("classic", R.string.skin_classic),
    Minimal("minimal", R.string.skin_minimal),
    Glass("glass", R.string.skin_glass),
    Neu("neu", R.string.skin_neu),
    Clay("clay", R.string.skin_clay),
    Brutal("brutal", R.string.skin_brutal),
    Bento("bento", R.string.skin_bento),
    HiFi("hifi", R.string.skin_hifi);

    companion object {
        fun from(id: String?): UiSkin = entries.firstOrNull { it.id == id } ?: Classic
    }
}
