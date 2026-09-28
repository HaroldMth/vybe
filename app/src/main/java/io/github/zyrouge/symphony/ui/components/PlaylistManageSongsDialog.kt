package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.delay

@Composable
fun PlaylistManageSongsDialog(
    context: ViewContext,
    selectedSongIds: List<String>,
    onDone: (List<String>) -> Unit,
) {
    val allSongIds by context.symphony.groove.song.all.collectAsState()
    val nSelectedSongIds = remember { selectedSongIds.toMutableStateList() }
    var terms by remember { mutableStateOf("") }
    val songIds by remember(allSongIds, terms, selectedSongIds) {
        derivedStateOf {
            context.symphony.groove.song.search(allSongIds, terms, limit = -1)
                .map { it.entity }
                .sortedBy { !selectedSongIds.contains(it) }
        }
    }

    // The picker used to only search songs already sitting in the in-memory
    // cache (whatever home/charts/search happened to load), so you couldn't
    // add anything from the actual catalog. Debounce-search the Vybe API and
    // ingest the hits; groove.song.all updates reactively and the list above
    // picks them up.
    LaunchedEffect(terms) {
        val query = terms.trim()
        if (query.length < 2) return@LaunchedEffect
        delay(400)
        try {
            context.symphony.vybeApi.search(query)?.let {
                context.symphony.groove.catalog.ingestSearch(it)
            }
        } catch (err: Exception) {
            Logger.error("PlaylistManageSongs", "remote search failed", err)
        }
    }

    ScaffoldDialog(
        onDismissRequest = {
            onDone(nSelectedSongIds.toList())
        },
        title = {
            Text(context.symphony.t.ManageSongs)
        },
        titleLeading = {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(CircleShape)
                    .clickable {
                        onDone(selectedSongIds)
                    },
            ) {
                Icon(
                    Icons.Filled.Close,
                    null,
                    modifier = Modifier.padding(8.dp)
                )
            }
        },
        titleTrailing = {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(CircleShape)
                    .clickable {
                        onDone(nSelectedSongIds.toList())
                    },
            ) {
                Icon(
                    Icons.Filled.Done,
                    null,
                    modifier = Modifier.padding(8.dp)
                )
            }
        },
        content = {
            Column {
                TextField(
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).let {
                        TextFieldDefaults.colors(
                            focusedContainerColor = it,
                            unfocusedContainerColor = it,
                        )
                    },
                    placeholder = {
                        Text(context.symphony.t.SearchYourMusic)
                    },
                    value = terms,
                    onValueChange = {
                        terms = it
                    },
                )
                when {
                    songIds.isEmpty() -> Box(modifier = Modifier.padding(0.dp, 12.dp)) {
                        SubtleCaptionText(context.symphony.t.DamnThisIsSoEmpty)
                    }

                    else -> BoxWithConstraints {
                        LazyColumn(
                            modifier = Modifier
                                .height(this@BoxWithConstraints.maxHeight)
                                .padding(bottom = 4.dp)
                        ) {
                            items(songIds, key = { it }) { songId ->
                                context.symphony.groove.song.get(songId)?.let { song ->
                                    Box(modifier = Modifier.animateItem()) {
                                    SongCard(
                                        context,
                                        song = song,
                                        thumbnailLabel = when {
                                            nSelectedSongIds.contains(song.id) -> ({
                                                Icon(
                                                    Icons.Filled.Check,
                                                    null,
                                                    modifier = Modifier.size(12.dp),
                                                )
                                            })

                                            else -> null
                                        },
                                        disableHeartIcon = true,
                                    ) {
                                        when {
                                            nSelectedSongIds.contains(song.id) -> {
                                                nSelectedSongIds.remove(song.id)
                                            }

                                            else -> nSelectedSongIds.add(song.id)
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
    )
}
