package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.NowPlayingData
import io.github.zyrouge.symphony.ui.view.NowPlayingStates
import io.github.zyrouge.symphony.ui.view.QueueViewRoute

/**
 * Flat action-bar replacing the old CenterAlignedTopAppBar.
 * Layout: [↓ collapse]  [≡ queue]  [♥ favorite]  [↓ download]
 */
@Composable
fun NowPlayingAppBar(context: ViewContext, data: NowPlayingData, states: NowPlayingStates) {
    val favoriteSongIds by context.symphony.groove.playlist.favorites.collectAsState()
    val isFavorite by remember(data) {
        derivedStateOf { favoriteSongIds.contains(data.song.id) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Collapse / down
        IconButton(onClick = { context.navController.popBackStack() }) {
            Icon(
                Icons.Filled.ExpandMore,
                null,
                modifier = Modifier.size(32.dp),
                tint = Color.White,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Queue
        IconButton(onClick = { context.navController.navigate(QueueViewRoute) }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                null,
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }

        // Favorite
        IconButton(
            onClick = {
                context.symphony.groove.playlist.run {
                    if (isFavorite) unfavorite(data.song.id)
                    else favorite(data.song.id)
                }
            }
        ) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                null,
                modifier = Modifier.size(24.dp),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
            )
        }

        // Download (Phase 7 — wired once DownloadManager exists)
        IconButton(onClick = { /* TODO Phase 7 */ }) {
            Icon(
                Icons.Filled.Download,
                null,
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
fun NowPlayingLandscapeAppBar(context: ViewContext, data: NowPlayingData, states: NowPlayingStates) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { context.navController.popBackStack() }) {
            Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(32.dp), tint = Color.White)
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { context.navController.navigate(QueueViewRoute) }) {
            Icon(Icons.AutoMirrored.Filled.Sort, null, tint = Color.White)
        }
    }
}
