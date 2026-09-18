package io.github.zyrouge.symphony.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.download.DownloadStatus
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.ViewContext

/**
 * A song's download affordance: idle cloud-download icon, a circular progress
 * ring while downloading, or a filled check once it's downloaded. Only shown
 * for remote (streamed) songs — a locally-scanned device song is already
 * "downloaded" by definition.
 */
@Composable
fun DownloadIconButton(
    context: ViewContext,
    song: Song,
    tint: Color = LocalContentColor.current,
) {
    if (!song.id.startsWith("vybe_")) return

    val states by context.symphony.downloader.states.collectAsState()
    val state = states[song.id]
    val downloaded = context.symphony.downloader.isDownloaded(song.id)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            context.symphony.downloader.download(song)
        } else {
            Toast.makeText(
                context.activity,
                "Storage permission is needed to download songs",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun startDownload() {
        val required = context.symphony.permission.getStoragePermissions()
        if (required.isEmpty() || context.symphony.permission.hasStoragePermissions(context.activity)) {
            context.symphony.downloader.download(song)
        } else {
            permissionLauncher.launch(required.toTypedArray())
        }
    }

    when {
        downloaded -> {
            IconButton(onClick = { /* already downloaded */ }) {
                Icon(
                    Icons.Filled.CloudDone,
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        state?.status == DownloadStatus.QUEUED || state?.status == DownloadStatus.DOWNLOADING -> {
            IconButton(onClick = { /* downloading */ }) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { if (state.progress > 0f) state.progress else 0.05f },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        else -> {
            IconButton(onClick = { startDownload() }) {
                Icon(
                    Icons.Filled.Download,
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = tint,
                )
            }
        }
    }
}
