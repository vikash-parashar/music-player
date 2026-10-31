package com.vikash.mp3player.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vikash.mp3player.R
import com.vikash.mp3player.data.RadioRepository
import com.vikash.mp3player.data.RadioStation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** What the station list shows: top stations in a country, favourites, one genre, or a search. */
sealed interface RadioMode {
    data object Top : RadioMode
    data object Favourites : RadioMode
    data class Genre(val tag: String) : RadioMode
    data class Search(val query: String) : RadioMode
}

data class RadioState(
    val mode: RadioMode = RadioMode.Top,
    val query: String = "",
    val loading: Boolean = false,
    val stations: List<RadioStation> = emptyList(),
    val failed: Boolean = false,
)

class RadioViewModel(app: Application) : AndroidViewModel(app) {
    /** The phone's country, so India sees Indian stations and the US sees American ones. */
    val country: String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "IN"

    private val _state = MutableStateFlow(RadioState())
    val state: StateFlow<RadioState> = _state.asStateFlow()
    val favourites = RadioRepository.favourites
    private var job: Job? = null

    init {
        RadioRepository.loadFavourites(app)
        show(RadioMode.Top)
    }

    fun show(mode: RadioMode) {
        _state.update { it.copy(mode = mode, query = if (mode is RadioMode.Search) mode.query else "") }
        job?.cancel()
        if (mode is RadioMode.Favourites) { _state.update { it.copy(loading = false, failed = false) }; return }
        job = viewModelScope.launch {
            if (mode is RadioMode.Search) {
                if (mode.query.isBlank()) { show(RadioMode.Top); return@launch }
                delay(450) // wait for typing to pause
            }
            _state.update { it.copy(loading = true, failed = false) }
            runCatching {
                when (mode) {
                    RadioMode.Top -> RadioRepository.top(country)
                    is RadioMode.Genre -> RadioRepository.byGenre(mode.tag)
                    is RadioMode.Search -> RadioRepository.search(mode.query)
                    RadioMode.Favourites -> emptyList()
                }
            }.onSuccess { list -> _state.update { it.copy(stations = list, loading = false) } }
                .onFailure { _state.update { it.copy(loading = false, failed = true) } }
        }
    }

    fun toggleFavourite(station: RadioStation) = RadioRepository.toggleFavourite(getApplication(), station)

    fun played(station: RadioStation) { viewModelScope.launch { RadioRepository.countClick(station) } }
}

/** Discover > Radio: thousands of live stations, free for everyone. */
@Composable
fun RadioSection(currentId: String?, actions: DiscoverActions) {
    val vm: RadioViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val countryName = Locale("", vm.country).displayCountry.ifBlank { vm.country }
    val favIds = favourites.map { it.id }.toSet()
    val shown = if (state.mode == RadioMode.Favourites) favourites else state.stations

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { vm.show(RadioMode.Search(it)) },
            placeholder = { Text(stringResource(R.string.radio_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { vm.show(RadioMode.Top) }) {
                    Icon(Icons.Default.Clear, stringResource(R.string.common_clear))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(state.mode == RadioMode.Top, onClick = { vm.show(RadioMode.Top) },
                label = { Text(stringResource(R.string.radio_top_in, countryName)) })
            FilterChip(state.mode == RadioMode.Favourites, onClick = { vm.show(RadioMode.Favourites) },
                label = { Text(stringResource(R.string.radio_favourites)) },
                leadingIcon = { Icon(Icons.Default.Favorite, null, Modifier.size(16.dp)) })
            RadioRepository.genres.forEach { g ->
                FilterChip((state.mode as? RadioMode.Genre)?.tag == g, onClick = { vm.show(RadioMode.Genre(g)) },
                    label = { Text(g.replaceFirstChar { it.titlecase() }) })
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        when {
            state.failed && state.mode != RadioMode.Favourites -> EmptyMessage(stringResource(R.string.discover_offline))
            state.mode == RadioMode.Favourites && favourites.isEmpty() -> EmptyMessage(stringResource(R.string.radio_no_favourites))
            !state.loading && shown.isEmpty() -> EmptyMessage(stringResource(R.string.radio_no_results))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { station ->
                    StationRow(
                        station = station,
                        playing = currentId?.endsWith(station.id) == true,
                        favourite = station.id in favIds,
                        onPlay = {
                            actions.play(listOf(station.toSong()), 0, "Radio")
                            vm.played(station)
                        },
                        onFavourite = { vm.toggleFavourite(station) },
                    )
                }
                item {
                    Text(stringResource(R.string.radio_credit), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StationRow(station: RadioStation, playing: Boolean, favourite: Boolean, onPlay: () -> Unit, onFavourite: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(station.toSong().artworkUri, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    Icon(Icons.Default.Equalizer, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    station.name.trim(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            val quality = listOfNotNull(
                station.country.takeIf { it.isNotBlank() },
                station.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
                station.codec.takeIf { it.isNotBlank() && it != "UNKNOWN" },
            ).joinToString(" · ")
            Text(quality, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (station.tagList.isNotEmpty()) Text(
                station.tagList.take(4).joinToString(", "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onFavourite) {
            Icon(
                if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                stringResource(if (favourite) R.string.radio_unfavourite else R.string.radio_favourite),
                tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
