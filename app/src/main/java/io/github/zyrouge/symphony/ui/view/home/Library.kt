package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.services.download.DownloadStatus
import io.github.zyrouge.symphony.ui.components.NewPlaylistDialog
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.ui.view.DownloadsViewRoute
import io.github.zyrouge.symphony.ui.view.PlaylistViewRoute

@Composable
fun LibraryView(context: ViewContext) {
    val favoriteSongIds by context.symphony.groove.playlist.favorites.collectAsState()
    val allPlaylists by context.symphony.groove.playlist.all.collectAsState()
    val downloadStates by context.symphony.downloader.states.collectAsState()
    val downloadedCount by remember(downloadStates) {
        derivedStateOf { downloadStates.values.count { it.status == DownloadStatus.COMPLETED } }
    }

    val playlists by remember(allPlaylists) {
        derivedStateOf {
            allPlaylists.mapNotNull { context.symphony.groove.playlist.get(it) }
                .filter { !it.isLocal || it.songPaths.isNotEmpty() }
                .take(8)
        }
    }

    var showPlaylistCreator by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // ── Downloads ─────────────────────────────────────────────────────────
        item {
            LibrarySectionHeader(
                icon = Icons.Filled.Download,
                title = "Downloads",
                actionLabel = null,
                onAction = null,
            )
            ElevatedCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clickable {
                        context.navController.navigate(DownloadsViewRoute)
                    },
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Downloaded Songs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            if (downloadedCount > 0) "$downloadedCount downloaded" else "Saved to Music/Vybe",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Favorites ─────────────────────────────────────────────────────────
        if (favoriteSongIds.isNotEmpty()) {
            item {
                LibrarySectionHeader(
                    icon = Icons.Filled.Favorite,
                    title = "Favorites",
                    actionLabel = "See all",
                    onAction = null, // TODO: navigate to favorites playlist
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(favoriteSongIds.take(12).toList()) { songId ->
                        context.symphony.groove.song.get(songId)?.let { song ->
                            SongCompactTile(context, songId = songId, title = song.title, subtitle = song.artists.joinToString(), artworkRequest = song.createArtworkImageRequest(context.symphony).build()) {
                                context.symphony.radio.shorty.playQueue(songId)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // ── Playlists ─────────────────────────────────────────────────────────
        item {
            LibrarySectionHeader(
                icon = Icons.Filled.QueueMusic,
                title = context.symphony.t.Playlists,
                actionLabel = "New",
                onAction = { showPlaylistCreator = true },
            )
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No playlists yet",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(playlists) { playlist ->
                        PlaylistCompactTile(
                            context,
                            title = playlist.title,
                            subtitle = "${playlist.songPaths.size} songs",
                            artworkRequest = playlist.createArtworkImageRequest(context.symphony).build(),
                        ) {
                            context.navController.navigate(PlaylistViewRoute(playlist.id))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPlaylistCreator) {
        NewPlaylistDialog(
            context,
            onDone = { playlist ->
                showPlaylistCreator = false
                context.symphony.groove.playlist.add(playlist)
            },
            onDismissRequest = { showPlaylistCreator = false },
        )
    }
}

@Composable
private fun LibrarySectionHeader(
    icon: ImageVector,
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction) {
                if (actionLabel == "New") Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SongCompactTile(
    context: ViewContext,
    songId: String,
    title: String,
    subtitle: String,
    artworkRequest: Any,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            artworkRequest,
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistCompactTile(
    context: ViewContext,
    title: String,
    subtitle: String,
    artworkRequest: Any,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(130.dp),
        ) {
            AsyncImage(
                artworkRequest,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
