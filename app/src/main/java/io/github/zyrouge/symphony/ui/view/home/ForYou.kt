package io.github.zyrouge.symphony.ui.view.home

import io.github.zyrouge.symphony.ui.helpers.navigateSafe
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import io.github.zyrouge.symphony.services.home.FeedSection
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.components.IconTextBody
import io.github.zyrouge.symphony.ui.components.PulsingBarsLoader
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.AlbumArtistViewRoute
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
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
    val feed = context.symphony.homeFeed
    val songsIsUpdating by context.symphony.groove.song.isUpdating.collectAsState()
    val songIds by context.symphony.groove.song.all.collectAsState()
    val sections by feed.sections.collectAsState()
    val refreshing by feed.isRefreshing.collectAsState()
    val loadingMore by feed.isLoadingMore.collectAsState()
    val hasLoaded by feed.hasLoaded.collectAsState()
    val heroId by feed.heroSongId.collectAsState()
    val trendingIds by feed.trendingSongIds.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        feed.start()
    }

    // Endless feed: whenever the last few items are on screen, ask for more sections.
    LaunchedEffect(listState, sections.size, refreshing) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }.collect { nearEnd ->
            if (nearEnd) {
                feed.loadMore()
            }
        }
    }

    // Local library songs are the fallback when the API is unreachable.
    val localFallback = remember(songIds) { songIds.take(12) }
    val heroSong = remember(heroId, localFallback) {
        (heroId ?: localFallback.firstOrNull())?.let { context.symphony.groove.song.get(it) }
    }
    val heroQueue = if (trendingIds.isNotEmpty()) trendingIds else localFallback

    when {
        !hasLoaded && sections.isEmpty() && heroSong == null -> RailLoading()

        hasLoaded && sections.isEmpty() && heroSong == null -> IconTextBody(
            icon = { modifier -> Icon(Icons.Filled.MusicNote, null, modifier = modifier) },
            content = { Text(context.symphony.t.DamnThisIsSoEmpty) },
        )

        else -> PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { feed.refresh() },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "greeting") {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        GreetingRow(context, onRefresh = { feed.refresh() })
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (heroSong != null) {
                    item(key = "hero") {
                        HeroCard(
                            context = context,
                            song = heroSong,
                            enabled = !songsIsUpdating,
                            // Playing a single song starts an endless queue of related songs.
                            onPlay = { context.symphony.radio.shorty.playQueue(heroSong.id) },
                            onShuffle = {
                                context.symphony.radio.shorty.playQueue(heroQueue, shuffle = true)
                            },
                        )
                    }
                }

                itemsIndexed(sections, key = { index, section -> "$index-${section.key}" }) { index, section ->
                    Column {
                        FeedSectionView(context, section)
                        if (index == LIBRARY_RAILS_AFTER) {
                            LibraryRails(context)
                        }
                    }
                }

                if (sections.isEmpty() && hasLoaded && localFallback.isNotEmpty()) {
                    item(key = "local") {
                        FeedSectionView(
                            context,
                            FeedSection.Songs(
                                key = "local",
                                title = "From your library",
                                songIds = localFallback,
                                style = FeedSection.Songs.Style.Cards,
                            ),
                        )
                    }
                }

                item(key = "footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loadingMore || (refreshing && sections.isEmpty())) {
                            PulsingBarsLoader()
                        }
                    }
                }
            }
        }
    }
}

/** The user's own library suggestions (configurable in settings), shown once in the feed. */
@Composable
private fun LibraryRails(context: ViewContext) {
    val albumIds by context.symphony.groove.album.all.collectAsState()
    val artistNames by context.symphony.groove.artist.all.collectAsState()
    val albumArtistNames by context.symphony.groove.albumArtist.all.collectAsState()
    val albumsIsUpdating by context.symphony.groove.album.isUpdating.collectAsState()
    val artistsIsUpdating by context.symphony.groove.artist.isUpdating.collectAsState()
    val albumArtistsIsUpdating by context.symphony.groove.albumArtist.isUpdating.collectAsState()
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
    Column {
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
    }
}

private const val LIBRARY_RAILS_AFTER = 4

@Composable
private fun GreetingRow(context: ViewContext, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Greeting(context)
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, null)
        }
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
                    "Featured",
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
                    context.navController.navigateSafe(AlbumViewRoute(album.id))
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
                                context.navController.navigateSafe(AlbumArtistViewRoute(artistName))
                            } else {
                                context.navController.navigateSafe(ArtistViewRoute(artistName))
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
