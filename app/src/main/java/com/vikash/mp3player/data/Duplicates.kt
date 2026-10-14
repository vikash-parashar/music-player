package com.vikash.mp3player.data

import kotlin.math.abs

/**
 * Finds the same song saved more than once: same title and artist (ignoring case, punctuation and
 * tags like "(1)", "copy" or "320kbps") and a length within a few seconds.
 */
object Duplicates {

    private const val LENGTH_SLACK_MS = 3_000L
    private val NOISE_IN_BRACKETS = Regex("""[(\[{]\s*(\d+|copy|\d+\s*kbps|kbps|hq|hd|mp3|official( audio| video)?|audio|full song|lyrics?)\s*[)\]}]""")
    private val NOISE_WORDS = Regex("""\b(\d+\s*kbps|copy)\b""")
    private val NOT_LETTERS = Regex("""[^\p{L}\p{N}]+""")

    fun normalize(text: String): String =
        text.lowercase()
            .replace(NOISE_IN_BRACKETS, " ")
            .replace(NOISE_WORDS, " ")
            .replace(NOT_LETTERS, " ")
            .trim()

    /** Each group holds two or more copies, best first (biggest file, which usually means best quality). */
    fun find(songs: List<Song>): List<List<Song>> {
        val groups = mutableListOf<List<Song>>()
        songs.filter { !it.isOnline }
            .groupBy { normalize(it.title) + "|" + normalize(it.artist) }
            .filterKeys { !it.startsWith("|") }
            .values
            .filter { it.size > 1 }
            .forEach { sameName ->
                // Same name can still mean a different version (live, remix): split by length.
                val left = sameName.sortedBy { it.durationMs }.toMutableList()
                while (left.isNotEmpty()) {
                    val first = left.removeAt(0)
                    val cluster = mutableListOf(first)
                    val iter = left.iterator()
                    while (iter.hasNext()) {
                        val s = iter.next()
                        if (abs(s.durationMs - first.durationMs) <= LENGTH_SLACK_MS) { cluster += s; iter.remove() }
                    }
                    if (cluster.size > 1) groups += cluster.sortedByDescending { it.sizeBytes }
                }
            }
        return groups.sortedBy { it.first().title.lowercase() }
    }
}
