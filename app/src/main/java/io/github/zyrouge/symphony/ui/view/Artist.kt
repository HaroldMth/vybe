package io.github.zyrouge.symphony.ui.view

import io.github.zyrouge.symphony.ui.helpers.navigateSafe
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.ui.components.AlbumDropdownMenu
import io.github.zyrouge.symphony.ui.components.ArtistDropdownMenu
import io.github.zyrouge.symphony.ui.components.GenericGrooveCard
import io.github.zyrouge.symphony.ui.components.SongCard
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.serialization.Serializable

@Serializable
data class ArtistViewRoute(val artistName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistView(context: ViewContext, artistName: String) {
    var detailLoaded by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(artistName) {
        loading = true
        context.symphony.groove.catalog.ensureArtist(artistName)
        loading = false
        detailLoaded = true
    }

    // Collect live data from repositories
    val allArtists by context.symphony.groove.artist.all.collectAsState()
    val allAlbums by context.symphony.groove.album.all.collectAsState()
    val allSongs by context.symphony.groove.song.all.collectAsState()

    // Derived from live state
    val songIds = remember(allSongs, artistName) {
        context.symphony.groove.artist.getSongIds(artistName)
    }
    val albumIds = remember(allAlbums, artistName) {
        context.symphony.groove.artist.getAlbumIds(artistName)
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Songs", "Albums", "Similar")

    // Cover artwork request via repository
    val artworkUri = context.symphony.groove.artist.getArtworkUri(artistName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { context.navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { contentPadding ->
        if (loading && !detailLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentPadding.calculateTopPadding()),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // ── Hero ─────────────────────────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                    ) {
                        // Blurred background
                        AsyncImage(
                            artworkUri,
                            null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(20.dp)
                                .graphicsLayer { alpha = 0.7f },
                        )
                        // Gradient scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.75f))
                                    )
                                )
                        )
                        // Circle artist image
                        AsyncImage(
                            artworkUri,
                            null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(130.dp)
                                .clip(CircleShape)
                                .align(Alignment.Center)
                                .offset(y = (-24).dp),
                        )
                        // Artist info
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                        ) {
                            Text(
                                artistName,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                ),
                            )
                            val artist = context.symphony.groove.artist.get(artistName)
                            val meta = buildList {
                                artist?.numberOfAlbums?.takeIf { it > 0 }?.let { add("$it albums") }
                                artist?.numberOfTracks?.takeIf { it > 0 }?.let { add("$it songs") }
                            }.joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.8f),
                                    ),
                                )
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (songIds.isNotEmpty())
                                    context.symphony.radio.shorty.playQueue(songIds)
                            },
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play")
                        }
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (songIds.isNotEmpty())
                                    context.symphony.radio.shorty.playQueue(songIds, shuffle = true)
                            },
                        ) {
                            Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }
                }

                // ── Tabs ─────────────────────────────────────────────────────
                item {
                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) },
                            )
                        }
                    }
                }

                // ── Tab: Overview ─────────────────────────────────────────────
                if (selectedTab == 0) {
                    item {
                        Text(
                            "Top Songs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                        )
                    }
                    val topSongs = songIds.take(5)
                    items(topSongs.size) { idx ->
                        val songId = topSongs[idx]
                        context.symphony.groove.song.get(songId)?.let { song ->
                            SongCard(context, song) {
                                context.symphony.radio.shorty.playQueue(songIds)
                            }
                        }
                    }
                    if (albumIds.isNotEmpty()) {
                        item {
                            Text(
                                "Albums",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val topAlbums = albumIds.take(6)
                                items(topAlbums.size) { idx ->
                                    val albumId = topAlbums[idx]
                                    context.symphony.groove.album.get(albumId)?.let { album ->
                                        Column(
                                            modifier = Modifier
                                                .width(120.dp)
                                                .clickable { context.navController.navigateSafe(AlbumViewRoute(albumId)) },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            AsyncImage(
                                                album.createArtworkImageRequest(context.symphony).build(),
                                                null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(120.dp)
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                album.name,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            album.startYear?.let {
                                                Text(
                                                    it.toString(),
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // ── Tab: Songs ────────────────────────────────────────────────
                if (selectedTab == 1) {
                    if (songIds.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loading) CircularProgressIndicator() else Text("No songs found")
                            }
                        }
                    } else {
                        items(songIds.size) { idx ->
                            val songId = songIds[idx]
                            context.symphony.groove.song.get(songId)?.let { song ->
                                SongCard(context, song) {
                                    context.symphony.radio.shorty.playQueue(songIds)
                                }
                            }
                        }
                    }
                }

                // ── Tab: Albums ────────────────────────────────────────────────
                if (selectedTab == 2) {
                    if (albumIds.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loading) CircularProgressIndicator() else Text("No albums found")
                            }
                        }
                    } else {
                        items(albumIds.size) { idx ->
                            val albumId = albumIds[idx]
                            context.symphony.groove.album.get(albumId)?.let { album ->
                                GenericGrooveCard(
                                    image = album.createArtworkImageRequest(context.symphony).build(),
                                    title = { Text(album.name) },
                                    subtitle = album.startYear?.let { year -> { Text(year.toString()) } },
                                    options = { expanded, onDismissRequest ->
                                        AlbumDropdownMenu(context, album, expanded = expanded, onDismissRequest = onDismissRequest)
                                    },
                                    onClick = { context.navController.navigateSafe(AlbumViewRoute(albumId)) },
                                )
                            }
                        }
                    }
                }

                // ── Tab: Similar ─────────────────────────────────────────────
                if (selectedTab == 3) {
                    item {
                        val allArtistNames = allArtists.filter { it != artistName }.take(12)
                        if (allArtistNames.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loading) CircularProgressIndicator()
                                else Text("No similar artists found")
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(allArtistNames.size) { idx ->
                                    val relatedName = allArtistNames[idx]
                                    Column(
                                        modifier = Modifier
                                            .width(90.dp)
                                            .clickable {
                                                context.navController.navigateSafe(ArtistViewRoute(relatedName))
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        AsyncImage(
                                            context.symphony.groove.artist.getArtworkUri(relatedName),
                                            null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape),
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            relatedName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
