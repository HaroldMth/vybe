package io.github.zyrouge.symphony.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.repositories.PlaylistRepository
import io.github.zyrouge.symphony.ui.components.AnimatedNowPlayingBottomBar
import io.github.zyrouge.symphony.ui.components.IconTextBody
import io.github.zyrouge.symphony.ui.components.PlaylistDropdownMenu
import io.github.zyrouge.symphony.ui.components.PlaylistManageSongsDialog
import io.github.zyrouge.symphony.ui.components.SongList
import io.github.zyrouge.symphony.ui.components.SongListType
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.theme.ThemeColors
import io.github.zyrouge.symphony.utils.mutate
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistViewRoute(val playlistId: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistView(context: ViewContext, route: PlaylistViewRoute) {
    val allPlaylistIds by context.symphony.groove.playlist.all.collectAsState()
    val updateId by context.symphony.groove.playlist.updateId.collectAsState()
    var updateCounter by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(PlaylistRepository.isRemoteId(route.playlistId)) }
    LaunchedEffect(route.playlistId) {
        if (PlaylistRepository.isRemoteId(route.playlistId)) {
            loading = true
            context.symphony.groove.catalog.ensurePlaylist(route.playlistId)
            loading = false
        }
    }
    val playlist by remember(route.playlistId, updateId) {
        derivedStateOf { context.symphony.groove.playlist.get(route.playlistId) }
    }
    val songIds by remember(playlist) {
        derivedStateOf { playlist?.getSongIds(context.symphony) ?: emptyList() }
    }
    val isViable by remember(allPlaylistIds, route.playlistId, updateId) {
        derivedStateOf { context.symphony.groove.playlist.get(route.playlistId) != null }
    }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showManageSongs by remember { mutableStateOf(false) }
    val isFavoritesPlaylist by remember(playlist) {
        derivedStateOf {
            playlist?.let { context.symphony.groove.playlist.isFavoritesPlaylist(it) } == true
        }
    }

    val incrementUpdateCounter = {
        updateCounter = if (updateCounter > 25) 0 else updateCounter + 1
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { context.navController.popBackStack() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                title = {
                    TopAppBarMinimalTitle {
                        Text(
                            context.symphony.t.Playlist
                                    + (playlist?.let { " - ${it.title}" } ?: "")
                        )
                    }
                },
                actions = {
                    if (isViable) {
                        IconButton(
                            onClick = {
                                showOptionsMenu = true
                            }
                        ) {
                            Icon(Icons.Filled.MoreVert, null)
                            PlaylistDropdownMenu(
                                context,
                                playlist!!,
                                expanded = showOptionsMenu,
                                onSongsChanged = {
                                    incrementUpdateCounter()
                                },
                                onRename = {
                                    incrementUpdateCounter()
                                },
                                onDelete = {
                                    context.navController.popBackStack()
                                },
                                onDismissRequest = {
                                    showOptionsMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                when {
                    loading && !isViable -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    // Fresh, user-made playlist: point straight at adding songs
                    // instead of leaving an empty list with no obvious next step.
                    isViable && songIds.isEmpty() && playlist?.let {
                        it.isNotLocal && !context.symphony.groove.playlist.isRemotePlaylist(it)
                    } == true -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            null,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text("This playlist is empty")
                        Spacer(modifier = Modifier.height(12.dp))
                        ElevatedButton(onClick = { showManageSongs = true }) {
                            Icon(Icons.Filled.Add, null)
                            Text("  Add songs")
                        }
                    }

                    isViable -> SongList(
                        context,
                        songIds = songIds,
                        type = SongListType.Playlist,
                        disableHeartIcon = isFavoritesPlaylist,
                        trailingOptionsContent = { _, song, onDismissRequest ->
                            playlist?.takeIf {
                                it.isNotLocal && !context.symphony.groove.playlist.isRemotePlaylist(it)
                            }?.let {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.DeleteForever,
                                            null,
                                            tint = ThemeColors.Red,
                                        )
                                    },
                                    text = {
                                        Text(context.symphony.t.RemoveFromPlaylist)
                                    },
                                    onClick = {
                                        onDismissRequest()
                                        context.symphony.groove.playlist.update(
                                            it.id,
                                            songIds.mutate { remove(song.id) },
                                        )
                                    }
                                )
                            }
                        },
                    )

                    else -> UnknownPlaylist(context, route.playlistId)
                }
            }
        },
        bottomBar = {
            AnimatedNowPlayingBottomBar(context)
        }
    )

    if (showManageSongs) {
        playlist?.let { current ->
            PlaylistManageSongsDialog(
                context,
                selectedSongIds = songIds,
                onDone = { ids ->
                    showManageSongs = false
                    context.symphony.groove.playlist.update(current.id, ids)
                    incrementUpdateCounter()
                },
            )
        }
    }
}

@Composable
private fun UnknownPlaylist(context: ViewContext, playlistId: String) {
    IconTextBody(
        icon = { modifier ->
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                null,
                modifier = modifier
            )
        },
        content = {
            Text(context.symphony.t.UnknownPlaylistX(playlistId))
        }
    )
}
