package com.vikash.mp3player.data

/**
 * Colour of the main buttons (Open, Scan, Play...) in the Pro looks, picked in Settings.
 * [Theme] keeps the look's own colour. Colours are ARGB.
 */
enum class KeyColor(val id: String, val argb: Long, val light: Long) {
    Theme("theme", 0, 0),
    Purple("purple", 0xFF6D28D9, 0xFF9B6CF7),
    Rose("rose", 0xFFE11D48, 0xFFFB7185),
    Blue("blue", 0xFF2563EB, 0xFF60A5FA),
    Teal("teal", 0xFF0F766E, 0xFF2DD4BF),
    Orange("orange", 0xFFEA580C, 0xFFFB923C),
    Indigo("indigo", 0xFF3730A3, 0xFF818CF8),
    Gold("gold", 0xFFB7791F, 0xFFF6C453),
    Black("black", 0xFF111827, 0xFF4B5563);

    companion object {
        fun from(id: String?): KeyColor = entries.firstOrNull { it.id == id } ?: Theme
    }
}
