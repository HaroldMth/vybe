package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.services.api.VybeHomeData
import io.github.zyrouge.symphony.services.groove.repositories.PlaylistRepository
import io.github.zyrouge.symphony.services.radio.Radio
import io.github.zyrouge.symphony.ui.components.IconTextBody
import io.github.zyrouge.symphony.ui.components.PlaylistTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.AlbumArtistViewRoute
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.ui.view.GenreViewRoute

enum class ForYou(val label: (context: ViewContext) -> String) {
    Albums(label = { it.symphony.t.SuggestedAlbums }),
    Artists(label = { it.symphony.t.SuggestedArtists }),
    AlbumArtists(label = { it.symphony.t.SuggestedAlbumArtists })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForYouView(context: ViewContext) {
    val songsIsUpdating by context.symphony.groove.song.isUpdating.collectAsState()
    val songIds by context.symphony.groove.song.all.collectAsState()
    val albumIds by context.symphony.groove.album.all.collectAsState()
    val artistNames by context.symphony.groove.artist.all.collectAsState()
    val albumArtistNames by context.symphony.groove.albumArtist.all.collectAsState()
    val albumsIsUpdating by context.symphony.groove.album.isUpdating.collectAsState()
    val artistsIsUpdating by context.symphony.groove.artist.isUpdating.collectAsState()
    val albumArtistsIsUpdating by context.symphony.groove.albumArtist.isUpdating.collectAsState()
    var homeData by remember { mutableStateOf<VybeHomeData?>(null) }
    var homeLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val data = context.symphony.vybeApi.getHome()
            if (data != null) {
                context.symphony.groove.catalog.ingestHome(data)
                homeData = data
            }
        } catch (err: Exception) {
            io.github.zyrouge.symphony.utils.Logger.error("ForYouView", "home fetch failed", err)
        } finally {
            homeLoading = false
        }
    }

    val trendingSongIds = remember(homeData, songIds) {
        homeData?.trending?.map { "vybe_${it.id}" } ?: songIds.take(12)
    }

    when {
        homeLoading && trendingSongIds.isEmpty() && songIds.isEmpty() -> SixGridLoading()
        trendingSongIds.isNotEmpty() || songIds.isNotEmpty() -> {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.padding(20.dp, 0.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ForYouButton(
                            icon = Icons.Filled.PlayArrow,
                            text = { Text(context.symphony.t.PlayAll) },
                            enabled = !songsIsUpdating && trendingSongIds.isNotEmpty(),
                            onClick = {
                                context.symphony.radio.shorty.playQueue(trendingSongIds)
                            },
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        ForYouButton(
                            icon = Icons.Filled.Shuffle,
                            text = { Text(context.symphony.t.ShufflePlay) },
                            enabled = !songsIsUpdating && trendingSongIds.isNotEmpty(),
                            onClick = {
                                context.symphony.radio.shorty.playQueue(
                                    trendingSongIds,
                                    shuffle = true,
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                SideHeading { Text(context.symphony.t.RecentlyAddedSongs) }
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    homeLoading -> SixGridLoading()
                    trendingSongIds.isEmpty() -> SixGridEmpty(context)
                    else -> BoxWithConstraints {
                        val tileWidth = this@BoxWithConstraints.maxWidth.times(0.7f)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Spacer(modifier = Modifier.width(12.dp))
                            trendingSongIds.take(8).forEachIndexed { i, songId ->
                                val tileHeight = 96.dp
                                val backgroundColor = MaterialTheme.colorScheme.surface
                                val song = context.symphony.groove.song.get(songId)
                                    ?: return@forEachIndexed

                                ElevatedCard(
                                    modifier = Modifier
                                        .width(tileWidth)
                                        .height(tileHeight),
                                    onClick = {
                                        context.symphony.radio.shorty.playQueue(
                                            trendingSongIds,
                                            options = Radio.PlayOptions(index = i),
                                        )
                                    }
                                ) {
                                    Box {
                                        AsyncImage(
                                            song.createArtworkImageRequest(context.symphony)
                                                .build(),
                                            null,
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier.matchParentSize(),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            backgroundColor.copy(alpha = 0.2f),
                                                            backgroundColor.copy(alpha = 0.7f),
                                                            backgroundColor.copy(alpha = 0.8f),
                                                        ),
                                                    )
                                                )
                                        )
                                        Row(modifier = Modifier.padding(8.dp)) {
                                            Box {
                                                AsyncImage(
                                                    song.createArtworkImageRequest(context.symphony)
                                                        .build(),
                                                    null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .aspectRatio(1f)
                                                        .fillMaxHeight()
                                                        .clip(RoundedCornerShape(4.dp)),
                                                )
                                                Box(
                                                    modifier = Modifier.matchParentSize(),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                backgroundColor.copy(alpha = 0.25f),
                                                                CircleShape,
                                                            )
                                                            .padding(1.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.PlayArrow,
                                                            null,
                                                            modifier = Modifier.size(20.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Text(
                                                    song.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (song.artists.isNotEmpty()) {
                                                    Text(
                                                        song.artists.joinToString(),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                }

                val newReleaseIds = homeData?.newReleases?.map { it.id }.orEmpty()
                if (newReleaseIds.isNotEmpty()) {
                    SuggestedAlbums(
                        context,
                        isLoading = homeLoading,
                        albumIds = newReleaseIds.take(6),
                    )
                }

                val homeArtistNames = homeData?.artists?.map { it.name }.orEmpty()
                if (homeArtistNames.isNotEmpty()) {
                    SuggestedArtists(
                        context,
                        label = context.symphony.t.SuggestedArtists,
                        isLoading = homeLoading,
                        artistNames = homeArtistNames.take(6),
                    )
                }

                val homePlaylists = homeData?.playlists.orEmpty()
                if (homePlaylists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SideHeading { Text(context.symphony.t.Playlists) }
                    Spacer(modifier = Modifier.height(12.dp))
                    StatedSixGrid(
                        context,
                        isLoading = homeLoading,
                        items = homePlaylists.take(6).map {
                            PlaylistRepository.remoteId(it.id)
                        }
                    ) { playlistId ->
                        context.symphony.groove.playlist.get(playlistId)?.let { playlist ->
                            PlaylistTile(context, playlist)
                        }
                    }
                }

                // NOTE: previously used GenreGrid(context, homeGenres.take(8)) here.
                // GenreGrid wraps a LazyVerticalGrid internally (via ResponsiveGrid),
                // and nesting a lazy grid inside this screen's outer
                // Column(Modifier.verticalScroll(...)) crashes immediately with:
                // "Vertically scrollable component was measured with an infinity
                // maximum height constraints". Using a small non-lazy grid instead,
                // same pattern as SuggestedAlbums/SuggestedArtists above.
                val homeGenres = homeData?.genres?.map { it.name }.orEmpty()
                if (homeGenres.isNotEmpty()) {
                    SuggestedGenres(
                        context,
                        isLoading = homeLoading,
                        genreNames = homeGenres.take(6),
                    )
                }

                val contents by context.symphony.settings.forYouContents.flow.collectAsState()
                val randomAlbums by remember(albumsIsUpdating, albumIds) {
                    derivedStateOf {
                        albumIds.shuffled().take(6)
                    }
                }
                val randomArtists by remember(artistsIsUpdating, artistNames) {
                    derivedStateOf {
                        artistNames.shuffled().take(6)
                    }
                }
                val randomAlbumArtists by remember(albumArtistsIsUpdating, albumArtistNames) {
                    derivedStateOf {
                        albumArtistNames.shuffled().take(6)
                    }
                }
                contents.forEach {
                    when (it) {
                        ForYou.Albums -> SuggestedAlbums(
                            context,
                            isLoading = albumsIsUpdating,
                            albumIds = randomAlbums,
                        )

                        ForYou.Artists -> SuggestedArtists(
                            context,
                            label = context.symphony.t.SuggestedArtists,
                            isLoading = artistsIsUpdating,
                            artistNames = randomArtists,
                        )

                        ForYou.AlbumArtists -> SuggestedAlbumArtists(
                            context,
                            label = context.symphony.t.SuggestedAlbumArtists,
                            isLoading = albumArtistsIsUpdating,
                            albumArtistNames = randomAlbumArtists,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        else -> IconTextBody(
            icon = { modifier ->
                Icon(
                    Icons.Filled.MusicNote,
                    null,
                    modifier = modifier,
                )
            },
            content = { Text(context.symphony.t.DamnThisIsSoEmpty) },
        )
    }
}

@Composable
private fun SideHeading(text: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(20.dp, 0.dp)) {
        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
            text()
        }
    }
}

@Composable
private fun ForYouButton(
    icon: ImageVector,
    text: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ElevatedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            text()
        }
    }
}

@Composable
private fun SixGridLoading() {
    Box(
        modifier = Modifier
            .height((LocalConfiguration.current.screenHeightDp * 0.2f).dp)
            .fillMaxWidth()
            .padding(0.dp, 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        io.github.zyrouge.symphony.ui.components.PulsingBarsLoader()
    }
}

@Composable
private fun SixGridEmpty(context: ViewContext) {
    val height = (LocalConfiguration.current.screenHeightDp * 0.15f).dp
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .padding(0.dp, 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            context.symphony.t.DamnThisIsSoEmpty,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        )
    }
}

@Composable
private fun <T> StatedSixGrid(
    context: ViewContext,
    isLoading: Boolean,
    items: List<T>,
    content: @Composable (T) -> Unit,
) {
    when {
        isLoading -> SixGridLoading()
        items.isEmpty() -> SixGridEmpty(context)
        else -> SixGrid(items) {
            content(it)
        }
    }
}

@Composable
private fun <T> SixGrid(
    items: List<T>,
    content: @Composable (T) -> Unit,
) {
    val gap = 12.dp
    Row(
        modifier = Modifier.padding(20.dp, 0.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        (0..2).forEach { i ->
            val item = items.getOrNull(i)
            Box(modifier = Modifier.weight(1f)) {
                item?.let { content(it) }
            }
        }
    }
    if (items.size > 3) {
        Spacer(modifier = Modifier.height(gap))
        Row(
            modifier = Modifier.padding(20.dp, 0.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            (3..5).forEach { i ->
                val item = items.getOrNull(i)
                Box(modifier = Modifier.weight(1f)) {
                    item?.let { content(it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedAlbums(
    context: ViewContext,
    isLoading: Boolean,
    albumIds: List<String>,
) {
    val albums by remember(albumIds) {
        derivedStateOf {
            context.symphony.groove.album.get(albumIds)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    SideHeading {
        Text(context.symphony.t.SuggestedAlbums)
    }
    Spacer(modifier = Modifier.height(12.dp))
    StatedSixGrid(context, isLoading, albums) { album ->
        Card(
            onClick = {
                context.navController.navigate(AlbumViewRoute(album.id))
            }
        ) {
            AsyncImage(
                album.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedArtists(
    context: ViewContext,
    label: String,
    isLoading: Boolean,
    artistNames: List<String>,
) {
    val artists by remember(artistNames) {
        derivedStateOf {
            context.symphony.groove.artist.get(artistNames)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    SideHeading {
        Text(label)
    }
    Spacer(modifier = Modifier.height(12.dp))
    StatedSixGrid(context, isLoading, artists) { artist ->
        Card(
            onClick = {
                context.navController.navigate(ArtistViewRoute(artist.name))
            }
        ) {
            AsyncImage(
                artist.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedAlbumArtists(
    context: ViewContext,
    label: String,
    isLoading: Boolean,
    albumArtistNames: List<String>,
) {
    val albumArtists by remember(albumArtistNames) {
        derivedStateOf {
            context.symphony.groove.albumArtist.get(albumArtistNames)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    SideHeading {
        Text(label)
    }
    Spacer(modifier = Modifier.height(12.dp))
    StatedSixGrid(context, isLoading, albumArtists) { albumArtist ->
        Card(
            onClick = {
                context.navController.navigate(AlbumArtistViewRoute(albumArtist.name))
            }
        ) {
            AsyncImage(
                albumArtist.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

// Lightweight, non-lazy genre preview grid for the For You screen.
// Deliberately does NOT reuse GenreGrid (ui/components/GenreGrid.kt), which wraps
// a LazyVerticalGrid via ResponsiveGrid — that crashes when nested inside this
// screen's Column(Modifier.verticalScroll(...)) because a lazy grid needs a
// bounded height to measure against and the scrollable Column gives it infinity.
// GenreGrid is fine as-is for its own full-page/dedicated-route usage; just don't
// call it from inside another scrollable container.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedGenres(
    context: ViewContext,
    isLoading: Boolean,
    genreNames: List<String>,
) {
    val genres by remember(genreNames) {
        derivedStateOf {
            genreNames.mapNotNull { context.symphony.groove.genre.get(it) }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    SideHeading {
        Text(context.symphony.t.Genres)
    }
    Spacer(modifier = Modifier.height(12.dp))
    StatedSixGrid(context, isLoading, genres) { genre ->
        Card(
            onClick = {
                context.navController.navigate(GenreViewRoute(genre.name))
            }
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    genre.name,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
