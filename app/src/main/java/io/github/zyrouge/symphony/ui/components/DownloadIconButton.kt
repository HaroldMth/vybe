package io.github.zyrouge.symphony.ui.components

import android.widget.Toast
import io.github.zyrouge.symphony.ui.helpers.haptic
import io.github.zyrouge.symphony.ui.helpers.Haptic
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
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
        context.haptic(Haptic.Toggle)
        val required = context.symphony.permission.getStoragePermissions()
        if (required.isEmpty() || context.symphony.permission.hasStoragePermissions(context.activity)) {
            context.symphony.downloader.download(song)
        } else {
            permissionLauncher.launch(required.toTypedArray())
        }
    }

    val kind = when {
        downloaded -> DownloadKind.Done
        state?.status == DownloadStatus.QUEUED ||
            state?.status == DownloadStatus.DOWNLOADING -> DownloadKind.Busy
        else -> DownloadKind.Idle
    }

    // Buzz once when a download you watched start actually finishes.
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(kind) {
        if (kind == DownloadKind.Busy) wasBusy = true
        if (kind == DownloadKind.Done && wasBusy) {
            wasBusy = false
            context.haptic(Haptic.Success)
        }
    }

    // Glide between the states (cloud -> ring -> check) instead of snapping.
    Crossfade(
        targetState = kind,
        animationSpec = tween(220),
        label = "download-button-state",
    ) { target ->
        when (target) {
            DownloadKind.Done -> IconButton(onClick = { /* already downloaded */ }) {
                Icon(
                    Icons.Filled.CloudDone,
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            DownloadKind.Busy -> IconButton(onClick = { /* downloading */ }) {
                Box(contentAlignment = Alignment.Center) {
                    // Progress arrives in coarse steps; tween between them so
                    // the ring sweeps smoothly instead of jumping.
                    val raw = state?.progress ?: 0f
                    val goal = if (raw > 0f) raw else 0.05f
                    val animated by animateFloatAsState(
                        targetValue = goal,
                        animationSpec = tween(350),
                        label = "download-progress",
                    )
                    CircularProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            DownloadKind.Idle -> IconButton(onClick = { startDownload() }) {
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

private enum class DownloadKind { Idle, Busy, Done }
