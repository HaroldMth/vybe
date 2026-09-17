package io.github.zyrouge.symphony.ui.view

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.components.AlbumArtistDropdownMenu
import io.github.zyrouge.symphony.ui.components.AlbumDropdownMenu
import io.github.zyrouge.symphony.ui.components.ArtistDropdownMenu
import io.github.zyrouge.symphony.ui.components.GenericGrooveCard
import io.github.zyrouge.symphony.ui.components.IconTextBody
import io.github.zyrouge.symphony.ui.components.PlaylistDropdownMenu
import io.github.zyrouge.symphony.ui.components.SongCard
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.joinToStringIfNotEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Embedded search view for use as a nav tab (no back-navigation button).
 * The search state is remembered across tab switches via rememberSaveable.
 */

private data class EmbeddedSearchResult(
    val songIds: List<String> = emptyList(),
    val artistNames: List<String> = emptyList(),
    val albumIds: List<String> = emptyList(),
    val albumArtistNames: List<String> = emptyList(),
    val genreNames: List<String> = emptyList(),
    val playlistIds: List<String> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedSearchView(context: ViewContext) {
    val coroutineScope = rememberCoroutineScope()
    var terms by rememberSaveable { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<EmbeddedSearchResult?>(null) }
    var selectedChip by rememberSaveable { mutableStateOf<Groove.Kind?>(null) }

    fun isChipSelected(kind: Groove.Kind) = selectedChip == null || selectedChip == kind

    var currentTermsRoutine: Job? = null
    fun setTerms(nTerms: String) {
        terms = nTerms
        isSearching = true
        currentTermsRoutine?.cancel()
        currentTermsRoutine = coroutineScope.launch {
            withContext(Dispatchers.IO) {
                delay(250)
                val songIds = mutableListOf<String>()
                val artistNames = mutableListOf<String>()
                val albumIds = mutableListOf<String>()
                val albumArtistNames = mutableListOf<String>()
                val genreNames = mutableListOf<String>()
                val playlistIds = mutableListOf<String>()

                if (nTerms.isNotEmpty()) {
                    val remoteData = context.symphony.vybeApi.search(nTerms)
                    if (remoteData != null) {
                        val ingested = context.symphony.groove.catalog.ingestSearch(remoteData)
                        if (isChipSelected(Groove.Kind.SONG)) songIds.addAll(ingested.songIds)
                        if (isChipSelected(Groove.Kind.ARTIST)) artistNames.addAll(ingested.artistNames)
                        if (isChipSelected(Groove.Kind.ALBUM)) albumIds.addAll(ingested.albumIds)
                        if (isChipSelected(Groove.Kind.PLAYLIST)) playlistIds.addAll(ingested.playlistIds)
                    }
                    results = EmbeddedSearchResult(
                        songIds = songIds,
                        artistNames = artistNames,
                        albumIds = albumIds,
                        albumArtistNames = albumArtistNames,
                        genreNames = genreNames,
                        playlistIds = playlistIds,
                    )
                }
                isSearching = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field (no back button — it's a tab)
        TextField(
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            singleLine = true,
            value = terms,
            onValueChange = { setTerms(it) },
            placeholder = { Text(context.symphony.t.SearchYourMusic) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (terms.isNotEmpty()) {
                    IconButton(onClick = { setTerms("") }) {
                        Icon(Icons.Filled.Close, null)
                    }
                }
            }
        )

        // Filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.width(4.dp))
            FilterChip(
                selected = selectedChip == null,
                label = { Text(context.symphony.t.All) },
                onClick = { selectedChip = null; setTerms(terms) }
            )
            Groove.Kind.entries.forEach { kind ->
                FilterChip(
                    selected = selectedChip == kind,
                    label = { Text(kind.tabLabel(context)) },
                    onClick = { selectedChip = kind; setTerms(terms) }
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Results
        Box(modifier = Modifier.fillMaxSize()) {
            if (terms.isEmpty()) {
                // Empty state — browse hint
                Box(modifier = Modifier.align(Alignment.Center)) {
                    IconTextBody(
                        icon = { mod -> Icon(Icons.Filled.Search, null, modifier = mod) },
                        content = { Text("Search for songs, artists, albums…") }
                    )
                }
            } else {
                results?.run {
                    val hasSongs = isChipSelected(Groove.Kind.SONG) && songIds.isNotEmpty()
                    val hasArtists = isChipSelected(Groove.Kind.ARTIST) && artistNames.isNotEmpty()
                    val hasAlbums = isChipSelected(Groove.Kind.ALBUM) && albumIds.isNotEmpty()
                    val hasAlbumArtists = isChipSelected(Groove.Kind.ALBUM_ARTIST) && albumArtistNames.isNotEmpty()
                    val hasPlaylists = isChipSelected(Groove.Kind.PLAYLIST) && playlistIds.isNotEmpty()
                    val hasGenres = isChipSelected(Groove.Kind.GENRE) && genreNames.isNotEmpty()
                    val hasNoResults = !hasSongs && !hasArtists && !hasAlbums && !hasAlbumArtists && !hasPlaylists && !hasGenres

                    when {
                        isSearching -> Box(modifier = Modifier.align(Alignment.Center)) {
                            IconTextBody(
                                icon = { mod -> Icon(Icons.Filled.Search, null, modifier = mod) },
                                content = { Text(context.symphony.t.FilteringResults) }
                            )
                        }
                        hasNoResults -> Box(modifier = Modifier.align(Alignment.Center)) {
                            IconTextBody(
                                icon = { mod -> Icon(Icons.Filled.PriorityHigh, null, modifier = mod) },
                                content = { Text(context.symphony.t.NoResultsFound) }
                            )
                        }
                        else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            if (hasSongs) {
                                EmbeddedSearchHeading(context.symphony.t.Songs)
                                songIds.forEach { songId ->
                                    context.symphony.groove.song.get(songId)?.let { song ->
                                        SongCard(context, song) {
                                            context.symphony.radio.shorty.playQueue(song.id)
                                        }
                                    }
                                }
                            }
                            if (hasArtists) {
                                EmbeddedSearchHeading(context.symphony.t.Artists)
                                artistNames.forEach { name ->
                                    context.symphony.groove.artist.get(name)?.let { artist ->
                                        GenericGrooveCard(
                                            image = artist.createArtworkImageRequest(context.symphony).build(),
                                            title = { Text(artist.name) },
                                            subtitle = artist.numberOfTracks
                                                .takeIf { it > 0 }
                                                ?.let { { Text("${it} songs") } },
                                            options = { exp, dis ->
                                                ArtistDropdownMenu(context, artist, expanded = exp, onDismissRequest = dis)
                                            },
                                            onClick = { context.navController.navigate(ArtistViewRoute(artist.name)) }
                                        )
                                    }
                                }
                            }
                            if (hasAlbums) {
                                EmbeddedSearchHeading(context.symphony.t.Albums)
                                albumIds.forEach { albumId ->
                                    context.symphony.groove.album.get(albumId)?.let { album ->
                                        GenericGrooveCard(
                                            image = album.createArtworkImageRequest(context.symphony).build(),
                                            title = { Text(album.name) },
                                            subtitle = album.artists.joinToStringIfNotEmpty()?.let { { Text(it) } },
                                            options = { exp, dis ->
                                                AlbumDropdownMenu(context, album, expanded = exp, onDismissRequest = dis)
                                            },
                                            onClick = { context.navController.navigate(AlbumViewRoute(album.id)) }
                                        )
                                    }
                                }
                            }
                            if (hasAlbumArtists) {
                                EmbeddedSearchHeading(context.symphony.t.AlbumArtists)
                                albumArtistNames.forEach { name ->
                                    context.symphony.groove.albumArtist.get(name)?.let { albumArtist ->
                                        GenericGrooveCard(
                                            image = albumArtist.createArtworkImageRequest(context.symphony).build(),
                                            title = { Text(albumArtist.name) },
                                            options = { exp, dis ->
                                                AlbumArtistDropdownMenu(context, albumArtist, expanded = exp, onDismissRequest = dis)
                                            },
                                            onClick = { context.navController.navigate(AlbumArtistViewRoute(albumArtist.name)) }
                                        )
                                    }
                                }
                            }
                            if (hasPlaylists) {
                                EmbeddedSearchHeading(context.symphony.t.Playlists)
                                playlistIds.forEach { playlistId ->
                                    context.symphony.groove.playlist.get(playlistId)?.let { playlist ->
                                        GenericGrooveCard(
                                            image = playlist.createArtworkImageRequest(context.symphony).build(),
                                            title = { Text(playlist.title) },
                                            options = { exp, dis ->
                                                PlaylistDropdownMenu(context, playlist, expanded = exp, onDismissRequest = dis)
                                            },
                                            onClick = { context.navController.navigate(PlaylistViewRoute(playlist.id)) }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                } ?: if (isSearching) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        IconTextBody(
                            icon = { mod -> Icon(Icons.Filled.Search, null, modifier = mod) },
                            content = { Text(context.symphony.t.FilteringResults) }
                        )
                    }
                } else {}
            }
        }
    }
}

@Composable
private fun EmbeddedSearchHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp)
    )
}

private fun Groove.Kind.tabLabel(context: ViewContext) = when (this) {
    Groove.Kind.SONG -> context.symphony.t.Songs
    Groove.Kind.ALBUM -> context.symphony.t.Albums
    Groove.Kind.ARTIST -> context.symphony.t.Artists
    Groove.Kind.ALBUM_ARTIST -> context.symphony.t.AlbumArtists
    Groove.Kind.GENRE -> context.symphony.t.Genres
    Groove.Kind.PLAYLIST -> context.symphony.t.Playlists
}
