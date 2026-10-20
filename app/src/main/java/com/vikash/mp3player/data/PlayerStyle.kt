package com.vikash.mp3player.data

/** The picture at the top of Now Playing. Record, Cassette, Boombox and Jukebox are free; the rest need Pro. */
enum class PlayerStyle(val id: String, val pro: Boolean) {
    Record("record", false),
    Cassette("cassette", false),
    Boombox("boombox", false),
    Jukebox("jukebox", false),
    ReelToReel("reel", true),
    Neon("neon", true),
    Lyrics("lyrics", true),
    Radio("radio", true),
    ClickWheel("wheel", true);

    companion object {
        fun from(id: String?): PlayerStyle = entries.firstOrNull { it.id == id } ?: Record
    }
}
