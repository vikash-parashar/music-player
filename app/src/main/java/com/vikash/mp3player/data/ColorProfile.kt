package com.vikash.mp3player.data

/**
 * The app's colour theme. [Ampli] (rose) is free; the rest are Pro. Each one has a day and a night
 * version, so it follows the Day / Night setting like the brand theme does.
 */
enum class ColorProfile(val id: String) {
    Ampli("ampli"),
    SkyBlue("sky"),
    MilkyWhite("milky"),
    Electric("electric"),
    Snow("snow"),
    Water("water"),
    Air("air"),
    Saffron("saffron"),
    Mango("mango"),
    Coral("coral"),
    Cherry("cherry"),
    Forest("forest"),
    Lime("lime"),
    Bubblegum("bubblegum"),
    MidnightGold("midnight_gold"),
    Graphite("graphite");

    companion object {
        fun from(id: String?): ColorProfile = entries.firstOrNull { it.id == id } ?: Ampli
    }
}
