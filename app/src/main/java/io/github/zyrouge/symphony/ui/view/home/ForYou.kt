package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.services.api.VybeHomeData
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.services.groove.repositories.PlaylistRepository
import io.github.zyrouge.symphony.services.radio.Radio
import io.github.zyrouge.symphony.ui.components.IconTextBody
import io.github.zyrouge.symphony.ui.components.PlaylistTile
import io.github.zyrouge.symphony.ui.components.PulsingBarsLoader
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.theme.ThemeColors
import io.github.zyrouge.symphony.ui.view.AlbumArtistViewRoute
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.ui.view.GenreViewRoute
import java.util.Calendar

enum class ForYou(val label: (context: ViewContext) -> String) {
    Albums(label = { it.symphony.t.SuggestedAlbums }),
    Artists(label = { it.symphony.t.SuggestedArtists }),
    AlbumArtists(label = { it.symphony.t.SuggestedAlbumArtists })
}

/**
 * Home / For You.
 *
 * Design concept: one hero moment (today's top track, full-bleed, played with
 * one tap) followed by content rails, each with a card treatment that fits
 * what it's showing rather than one repeated square-grid pattern — trending
 * songs are numbered (they're a real ranking), artists and album artists are
 * round (they're people), genres are solid color chips pulled from the app's
 * own accent palette (genre reads as mood, mood reads as color), and albums
 * stay square (album art). The screen keeps only one loud element — the hero
 * — and stays quiet everywhere else.
 */
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
    val heroSong = remember(trendingSongIds) {
        trendingSongIds.firstOrNull()?.let { context.symphony.groove.song.get(it) }
    }

    when {
        homeLoading && trendingSongIds.isEmpty() && songIds.isEmpty() -> RailLoading()
        trendingSongIds.isNotEmpty() || songIds.isNotEmpty() -> {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Spacer(modifier = Modifier.height(8.dp))
                Greeting(context)
                Spacer(modifier = Modifier.height(16.dp))

                if (heroSong != null) {
                    HeroCard(
                        context = context,
                        song = heroSong,
                        enabled = !songsIsUpdating,
                        onPlay = { context.symphony.radio.shorty.playQueue(trendingSongIds) },
                        onShuffle = {
                            context.symphony.radio.shorty.playQueue(trendingSongIds, shuffle = true)
                        },
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }

                when {
                    homeLoading -> {
                        SectionHeading(context.symphony.t.RecentlyAddedSongs)
                        Spacer(modifier = Modifier.height(12.dp))
                        RailLoading()
                    }

                    trendingSongIds.isEmpty() -> {
                        SectionHeading(context.symphony.t.RecentlyAddedSongs)
                        Spacer(modifier = Modifier.height(12.dp))
                        RailEmpty(context)
                    }

                    else -> {
                        SectionHeading(context.symphony.t.RecentlyAddedSongs)
                        Spacer(modifier = Modifier.height(12.dp))
                        TrendingRail(context, trendingSongIds)
                    }
                }

                val newReleaseIds = homeData?.newReleases?.map { it.id }.orEmpty()
                if (newReleaseIds.isNotEmpty()) {
                    SuggestedAlbums(
                        context,
                        isLoading = homeLoading,
                        albumIds = newReleaseIds.take(8),
                    )
                }

                val homeArtistNames = homeData?.artists?.map { it.name }.orEmpty()
                if (homeArtistNames.isNotEmpty()) {
                    SuggestedArtists(
                        context,
                        label = context.symphony.t.SuggestedArtists,
                        isLoading = homeLoading,
                        artistNames = homeArtistNames.take(8),
                    )
                }

                val homePlaylists = homeData?.playlists.orEmpty()
                if (homePlaylists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionHeading(context.symphony.t.Playlists)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Spacer(modifier = Modifier.width(4.dp))
                        homePlaylists.take(8).forEach {
                            val playlistId = PlaylistRepository.remoteId(it.id)
                            context.symphony.groove.playlist.get(playlistId)?.let { playlist ->
                                Box(modifier = Modifier.width(140.dp)) {
                                    PlaylistTile(context, playlist)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }

                val homeGenres = homeData?.genres?.map { it.name }.orEmpty()
                if (homeGenres.isNotEmpty()) {
                    SuggestedGenres(
                        context,
                        isLoading = homeLoading,
                        genreNames = homeGenres.take(10),
                    )
                }

                val contents by context.symphony.settings.forYouContents.flow.collectAsState()
                val randomAlbums by remember(albumsIsUpdating, albumIds) {
                    derivedStateOf { albumIds.shuffled().take(8) }
                }
                val randomArtists by remember(artistsIsUpdating, artistNames) {
                    derivedStateOf { artistNames.shuffled().take(8) }
                }
                val randomAlbumArtists by remember(albumArtistsIsUpdating, albumArtistNames) {
                    derivedStateOf { albumArtistNames.shuffled().take(8) }
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

                        ForYou.AlbumArtists -> SuggestedArtists(
                            context,
                            label = context.symphony.t.SuggestedAlbumArtists,
                            isLoading = albumArtistsIsUpdating,
                            artistNames = randomAlbumArtists,
                            asAlbumArtists = true,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        else -> IconTextBody(
            icon = { modifier -> Icon(Icons.Filled.MusicNote, null, modifier = modifier) },
            content = { Text(context.symphony.t.DamnThisIsSoEmpty) },
        )
    }
}

@Composable
private fun Greeting(context: ViewContext) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 5 -> "Still up?"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "Late night listening"
    }
    Text(
        greeting,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        ),
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

/**
 * The one loud element on the page: today's top track, full-bleed, one tap
 * to play. Everything else on the screen is deliberately quieter than this.
 */
@Composable
private fun HeroCard(
    context: ViewContext,
    song: Song,
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(enabled = enabled) { onPlay() }
        ) {
            AsyncImage(
                song.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.75f),
                            ),
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    "Playing now",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.75f),
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artists.isNotEmpty()) {
                    Text(
                        song.artists.joinToString(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.8f),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(56.dp)
                    .clickable(enabled = enabled) { onPlay() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) { onShuffle() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Shuffle,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                context.symphony.t.ShufflePlay,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                ),
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

/** Trending songs are a real ranking, so a rank badge is earned here. */
@Composable
private fun TrendingRail(context: ViewContext, songIds: List<String>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(modifier = Modifier.width(6.dp))
        songIds.take(10).forEachIndexed { index, songId ->
            val song = context.symphony.groove.song.get(songId) ?: return@forEachIndexed
            Column(
                modifier = Modifier
                    .width(128.dp)
                    .clickable {
                        context.symphony.radio.shorty.playQueue(
                            songIds,
                            options = Radio.PlayOptions(index = index),
                        )
                    }
            ) {
                Box {
                    AsyncImage(
                        song.createArtworkImageRequest(context.symphony).build(),
                        null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Surface(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(24.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                (index + 1).toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artists.isNotEmpty()) {
                    Text(
                        song.artists.joinToString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
    }
}

@Composable
private fun RailLoading() {
    Box(
        modifier = Modifier
            .height((LocalConfiguration.current.screenHeightDp * 0.2f).dp)
            .fillMaxWidth()
            .padding(0.dp, 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        PulsingBarsLoader()
    }
}

@Composable
private fun RailEmpty(context: ViewContext) {
    val height = (LocalConfiguration.current.screenHeightDp * 0.12f).dp
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
private fun <T> StatedRail(
    context: ViewContext,
    isLoading: Boolean,
    items: List<T>,
    content: @Composable (T) -> Unit,
) {
    when {
        isLoading -> RailLoading()
        items.isEmpty() -> RailEmpty(context)
        else -> Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.width(6.dp))
            items.forEach { content(it) }
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

/** Album art is inherently square — this is the one rail that stays square. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedAlbums(
    context: ViewContext,
    isLoading: Boolean,
    albumIds: List<String>,
) {
    val albums by remember(albumIds) {
        derivedStateOf { context.symphony.groove.album.get(albumIds) }
    }

    Spacer(modifier = Modifier.height(28.dp))
    SectionHeading(context.symphony.t.SuggestedAlbums)
    Spacer(modifier = Modifier.height(12.dp))
    StatedRail(context, isLoading, albums) { album ->
        Column(
            modifier = Modifier
                .width(128.dp)
                .clickable {
                    context.navController.navigate(AlbumViewRoute(album.id))
                }
        ) {
            AsyncImage(
                album.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** People get round avatars — artists and album artists both use this. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedArtists(
    context: ViewContext,
    label: String,
    isLoading: Boolean,
    artistNames: List<String>,
    asAlbumArtists: Boolean = false,
) {
    val artists by remember(artistNames, asAlbumArtists) {
        derivedStateOf {
            if (asAlbumArtists) context.symphony.groove.albumArtist.get(artistNames).map { it.name to it.createArtworkImageRequest(context.symphony).build() }
            else context.symphony.groove.artist.get(artistNames).map { it.name to it.createArtworkImageRequest(context.symphony).build() }
        }
    }

    Spacer(modifier = Modifier.height(28.dp))
    SectionHeading(label)
    Spacer(modifier = Modifier.height(12.dp))
    when {
        isLoading -> RailLoading()
        artists.isEmpty() -> RailEmpty(context)
        else -> Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.width(6.dp))
            artists.forEach { (artistName, artistImage) ->
                Column(
                    modifier = Modifier
                        .width(88.dp)
                        .clickable {
                            if (asAlbumArtists) {
                                context.navController.navigate(AlbumArtistViewRoute(artistName))
                            } else {
                                context.navController.navigate(ArtistViewRoute(artistName))
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        artistImage,
                        null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        artistName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

/** Genre reads as mood, mood reads as color — reuses the app's own accent palette. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SuggestedGenres(
    context: ViewContext,
    isLoading: Boolean,
    genreNames: List<String>,
) {
    val genres by remember(genreNames) {
        derivedStateOf { genreNames.mapNotNull { context.symphony.groove.genre.get(it) } }
    }
    val palette = remember { ThemeColors.PrimaryColorsMap.values.toList() }

    Spacer(modifier = Modifier.height(28.dp))
    SectionHeading(context.symphony.t.Genres)
    Spacer(modifier = Modifier.height(12.dp))
    when {
        isLoading -> RailLoading()
        genres.isEmpty() -> RailEmpty(context)
        else -> FlowRow(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            genres.forEach { genre ->
                val color = palette[Math.floorMod(genre.name.hashCode(), palette.size)]
                Surface(
                    modifier = Modifier.clickable {
                        context.navController.navigate(GenreViewRoute(genre.name))
                    },
                    shape = RoundedCornerShape(50),
                    color = color.copy(alpha = 0.16f),
                ) {
                    Text(
                        genre.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = color,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
