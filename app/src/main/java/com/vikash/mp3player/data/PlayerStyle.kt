package com.vikash.mp3player.data

/**
 * The picture at the top of Now Playing. Car (the default) and Record are free; the rest need Pro
 * (free users get a short preview).
 */
enum class PlayerStyle(val id: String, val pro: Boolean) {
    Car("car", false),
    Record("record", false),
    Cassette("cassette", true),
    Boombox("boombox", true),
    Jukebox("jukebox", true),
    ReelToReel("reel", true),
    Neon("neon", true),
    Lyrics("lyrics", true),
    Radio("radio", true),
    ClickWheel("wheel", true);

    companion object {
        fun from(id: String?): PlayerStyle = entries.firstOrNull { it.id == id } ?: Car
    }
}
