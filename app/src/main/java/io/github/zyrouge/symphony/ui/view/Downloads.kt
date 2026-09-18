package io.github.zyrouge.symphony.ui.view

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.zyrouge.symphony.services.download.DownloadStatus
import io.github.zyrouge.symphony.ui.components.SongList
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.serialization.Serializable

@Serializable
object DownloadsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsView(context: ViewContext) {
    val states by context.symphony.downloader.states.collectAsState()
    val downloadedSongIds = states
        .filterValues { it.status == DownloadStatus.COMPLETED }
        .keys
        .toList()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        androidx.compose.material3.Text("Downloads")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { context.navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
            )
        },
        content = { contentPadding ->
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.padding(contentPadding)
            ) {
                SongList(
                    context,
                    songIds = downloadedSongIds,
                )
            }
        }
    )
}
