package com.vikash.mp3player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Podcasts the user follows, newest first, saved as JSON in the app's files. */
class PodcastFollows(context: Context) {

    private val file = File(context.filesDir, "podcasts.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PodcastShow.serializer())

    private val _shows = MutableStateFlow(load())
    val shows: StateFlow<List<PodcastShow>> = _shows.asStateFlow()

    fun isFollowing(feedUrl: String) = _shows.value.any { it.feedUrl == feedUrl }

    /** Returns true when the show is now followed. */
    fun toggle(show: PodcastShow): Boolean {
        val now = _shows.value
        val following = now.any { it.feedUrl == show.feedUrl }
        save(if (following) now.filterNot { it.feedUrl == show.feedUrl } else listOf(show) + now)
        return !following
    }

    private fun load(): List<PodcastShow> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    private fun save(list: List<PodcastShow>) {
        _shows.value = list
        runCatching { file.writeText(json.encodeToString(serializer, list)) }
    }
}
