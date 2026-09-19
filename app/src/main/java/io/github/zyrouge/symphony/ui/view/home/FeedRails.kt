package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.services.home.FeedSection
import io.github.zyrouge.symphony.ui.components.PlaylistTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.helpers.navigateSafe
import io.github.zyrouge.symphony.ui.theme.ThemeColors
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.ui.view.GenreViewRoute

/**
 * Renders one [FeedSection] of the home feed. Each kind of content gets its own
 * layout so the page doesn't read as the same rail repeated: ranked cards for
 * charts, plain cards, a compact list, round avatars for people, playlist tiles
 * and colour chips for genres.
 */
@Composable
fun FeedSectionView(context: ViewContext, section: FeedSection) {
    Column {
        Spacer(modifier = Modifier.height(28.dp))
        FeedHeading(
            context,
            title = section.title,
            leadSongId = (section as? FeedSection.Songs)?.leadSongId,
        )
        Spacer(modifier = Modifier.height(12.dp))
        when (section) {
            is FeedSection.Songs -> when (section.style) {
                FeedSection.Songs.Style.Ranked -> SongCardsRail(context, section.songIds, ranked = true)
                FeedSection.Songs.Style.Cards -> SongCardsRail(context, section.songIds, ranked = false)
                FeedSection.Songs.Style.Rows -> SongRows(context, section.songIds)
            }

            is FeedSection.Albums -> AlbumsRail(context, section.albumIds)
            is FeedSection.Artists -> ArtistsRail(context, section.artistNames)
            is FeedSection.Playlists -> PlaylistsRail(context, section.playlistIds)
            is FeedSection.Genres -> GenreChips(context, section.genreNames)
        }
    }
}

@Composable
private fun FeedHeading(context: ViewContext, title: String, leadSongId: String?) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lead = leadSongId?.let { context.symphony.groove.song.get(it) }
        if (lead != null) {
            AsyncImage(
                lead.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HorizontalRail(spacing: Int, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.dp),
    ) {
        Spacer(modifier = Modifier.width(6.dp))
        content()
        Spacer(modifier = Modifier.width(6.dp))
    }
}

/** Clicking a card plays that song and then keeps going with related songs (endless queue). */
@Composable
private fun SongCardsRail(context: ViewContext, songIds: List<String>, ranked: Boolean) {
    HorizontalRail(spacing = 14) {
        songIds.take(12).forEachIndexed { index, songId ->
            val song = context.symphony.groove.song.get(songId) ?: return@forEachIndexed
            Column(
                modifier = Modifier
                    .width(128.dp)
                    .clickable { context.symphony.radio.shorty.playQueue(songId) }
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
                    if (ranked) {
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
    }
}

/** Compact vertical list, used for the "Because you played ..." row. */
@Composable
private fun SongRows(context: ViewContext, songIds: List<String>) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        songIds.take(5).forEach { songId ->
            val song = context.symphony.groove.song.get(songId) ?: return@forEach
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { context.symphony.radio.shorty.playQueue(songId) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    song.createArtworkImageRequest(context.symphony).build(),
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
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
        }
    }
}

@Composable
private fun AlbumsRail(context: ViewContext, albumIds: List<String>) {
    val albums = remember(albumIds) { context.symphony.groove.album.get(albumIds) }
    HorizontalRail(spacing = 12) {
        albums.forEach { album ->
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
}

/** People get round avatars. */
@Composable
private fun ArtistsRail(context: ViewContext, artistNames: List<String>) {
    val artists = remember(artistNames) {
        context.symphony.groove.artist.get(artistNames)
            .map { it.name to it.createArtworkImageRequest(context.symphony).build() }
    }
    HorizontalRail(spacing = 16) {
        artists.forEach { (artistName, artistImage) ->
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .clickable {
                        context.navController.navigateSafe(ArtistViewRoute(artistName))
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
    }
}

@Composable
private fun PlaylistsRail(context: ViewContext, playlistIds: List<String>) {
    HorizontalRail(spacing = 12) {
        playlistIds.forEach { playlistId ->
            context.symphony.groove.playlist.get(playlistId)?.let { playlist ->
                Box(modifier = Modifier.width(140.dp)) {
                    PlaylistTile(context, playlist)
                }
            }
        }
    }
}

/** Genre reads as mood, mood reads as colour: reuses the app's own accent palette. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreChips(context: ViewContext, genreNames: List<String>) {
    val genres = remember(genreNames) {
        genreNames.mapNotNull { context.symphony.groove.genre.get(it) }
    }
    val palette = remember { ThemeColors.PrimaryColorsMap.values.toList() }
    FlowRow(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        genres.forEach { genre ->
            val color = palette[Math.floorMod(genre.name.hashCode(), palette.size)]
            Surface(
                modifier = Modifier.clickable {
                    context.navController.navigateSafe(GenreViewRoute(genre.name))
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
