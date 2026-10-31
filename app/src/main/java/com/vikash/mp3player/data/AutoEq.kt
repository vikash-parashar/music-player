package com.vikash.mp3player.data

import android.content.Context
import org.json.JSONArray

/**
 * Headphone correction curves from the AutoEq project (github.com/jaakkopasanen/AutoEq, MIT),
 * reduced to Ampli's 10 equaliser bands. Each curve is averaged over one octave around the band
 * centre and shifted so its loudest band sits at 0 dB, so applying one never clips.
 *
 * Measurements come from oratory1990, crinacle, Rtings and other reviewers credited in the
 * AutoEq repository; [Profile.source] keeps the reviewer's name for the list.
 */
object AutoEq {
    data class Profile(val name: String, val source: String, val gains: FloatArray)

    @Volatile private var cache: List<Profile>? = null

    fun profiles(context: Context): List<Profile> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    private fun load(context: Context): List<Profile> = runCatching {
        val text = context.assets.open("autoeq.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val g = o.getJSONArray("g")
            Profile(o.getString("n"), o.optString("s"), FloatArray(g.length()) { g.getDouble(it).toFloat() })
        }
    }.getOrDefault(emptyList())

    /** Case-insensitive match on every word, so "sony xm5" finds "Sony WH-1000XM5". */
    fun search(all: List<Profile>, query: String): List<Profile> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return all
        return all.filter { p -> val n = p.name.lowercase(); words.all { n.contains(it) } }
    }
}
