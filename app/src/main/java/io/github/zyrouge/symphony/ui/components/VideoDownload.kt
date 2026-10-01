package io.github.zyrouge.symphony.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.download.DownloadStatus
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.Haptic
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.helpers.haptic

/**
 * The video player's download affordance.
 *
 * Unlike audio-only screens — where the download button plainly means "save the audio" —
 * a song in the video player can have an audio copy, a video copy, both or neither. So
 * this button reflects the *video* download's state and, on tap, asks which copy to save
 * in a sheet whose two rows each show their own state ("Already downloaded" for audio,
 * an idle download icon for a video that isn't saved yet, and so on).
 */
@Composable
fun VideoDownloadButton(
    context: ViewContext,
    song: Song,
    tint: Color = Color.White,
) {
    val videoStates by context.symphony.downloader.videoStates.collectAsState()
    val downloaded = context.symphony.downloader.isVideoDownloaded(song.id)
    var showChooser by remember { mutableStateOf(false) }

    val state = videoStates[song.id]
    val busy = state?.status == DownloadStatus.QUEUED ||
        state?.status == DownloadStatus.DOWNLOADING
    val kind = when {
        downloaded -> VideoDownloadKind.Done
        busy -> VideoDownloadKind.Busy
        else -> VideoDownloadKind.Idle
    }

    // Buzz once when a video download you watched start actually finishes.
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(kind) {
        if (kind == VideoDownloadKind.Busy) wasBusy = true
        if (kind == VideoDownloadKind.Done && wasBusy) {
            wasBusy = false
            context.haptic(Haptic.Success)
        }
    }

    IconButton(onClick = {
        context.haptic(Haptic.Toggle)
        showChooser = true
    }) {
        Crossfade(
            targetState = kind,
            animationSpec = tween(220),
            label = "video-download-button-state",
        ) { target ->
            when (target) {
                VideoDownloadKind.Done -> Icon(
                    Icons.Filled.CloudDone,
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                VideoDownloadKind.Busy -> Box(contentAlignment = Alignment.Center) {
                    val raw = state?.progress ?: 0f
                    val goal = if (raw > 0f) raw else 0.05f
                    val animated by animateFloatAsState(
                        targetValue = goal,
                        animationSpec = tween(350),
                        label = "video-download-progress",
                    )
                    CircularProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                VideoDownloadKind.Idle -> Icon(
                    Icons.Filled.Download,
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = tint,
                )
            }
        }
    }

    if (showChooser) {
        VideoDownloadSheet(
            context = context,
            song = song,
            onDismissRequest = { showChooser = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoDownloadSheet(
    context: ViewContext,
    song: Song,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val audioStates by context.symphony.downloader.states.collectAsState()
    val videoStates by context.symphony.downloader.videoStates.collectAsState()
    // The player may already have resolved the video stream; passing it along avoids a
    // second lookup when the user downloads while a video is playing.
    val videoState by context.symphony.videoMode.state.collectAsState()

    // A locally-scanned song's audio is already on the device, so it counts as saved.
    val audioOnDevice = !song.id.startsWith("vybe_") ||
        context.symphony.downloader.isDownloaded(song.id)
    val audioFailed = audioStates[song.id]?.status == DownloadStatus.FAILED
    val audioBusy = audioStates[song.id]?.status == DownloadStatus.QUEUED ||
        audioStates[song.id]?.status == DownloadStatus.DOWNLOADING

    val videoDownloaded = context.symphony.downloader.isVideoDownloaded(song.id)
    val videoFailed = videoStates[song.id]?.status == DownloadStatus.FAILED
    val videoBusy = videoStates[song.id]?.status == DownloadStatus.QUEUED ||
        videoStates[song.id]?.status == DownloadStatus.DOWNLOADING

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            context.symphony.downloader.download(song)
        } else {
            showStoragePermissionToast(context)
        }
    }
    val videoPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            context.symphony.downloader.downloadVideo(song, videoState.stream)
        } else {
            showStoragePermissionToast(context)
        }
    }

    fun startAudio() {
        if (audioOnDevice || audioBusy) return
        context.haptic(Haptic.Toggle)
        if (hasStoragePermission(context)) context.symphony.downloader.download(song)
        else audioPermissionLauncher.launch(
            context.symphony.permission.getStoragePermissions().toTypedArray()
        )
    }

    fun startVideo() {
        if (videoDownloaded || videoBusy) return
        context.haptic(Haptic.Toggle)
        if (hasStoragePermission(context)) {
            context.symphony.downloader.downloadVideo(song, videoState.stream)
        } else {
            videoPermissionLauncher.launch(
                context.symphony.permission.getStoragePermissions().toTypedArray()
            )
        }
    }

    ModalBottomSheet(
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
    ) {
        Text(
            "Download",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        // ── Audio ────────────────────────────────────────────────────────────────
        ListItem(
            modifier = Modifier.clickable(enabled = !audioOnDevice && !audioBusy) {
                startAudio()
                onDismissRequest()
            },
            leadingContent = { Icon(Icons.Filled.MusicNote, null) },
            headlineContent = { Text("Audio") },
            supportingContent = {
                Text(
                    when {
                        audioOnDevice -> "Already downloaded"
                        audioBusy -> "Downloading…"
                        audioFailed -> "Download failed — tap to retry"
                        else -> "Save the audio for offline listening"
                    }
                )
            },
            trailingContent = { DownloadRowTrailing(
                done = audioOnDevice,
                busy = audioBusy,
                progress = audioStates[song.id]?.progress ?: 0f,
            ) },
        )

        // ── Video ────────────────────────────────────────────────────────────────
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !videoDownloaded && !videoBusy) {
                    startVideo()
                    onDismissRequest()
                },
            leadingContent = { Icon(Icons.Filled.Movie, null) },
            headlineContent = { Text("Video") },
            supportingContent = {
                Text(
                    when {
                        videoDownloaded -> "Downloaded"
                        videoBusy -> "Downloading…"
                        videoFailed -> "Download failed — tap to retry"
                        else -> "Save the video for offline playback"
                    }
                )
            },
            trailingContent = { DownloadRowTrailing(
                done = videoDownloaded,
                busy = videoBusy,
                progress = videoStates[song.id]?.progress ?: 0f,
            ) },
        )
    }
}

@Composable
private fun DownloadRowTrailing(done: Boolean, busy: Boolean, progress: Float) {
    when {
        done -> Icon(
            Icons.Filled.CloudDone,
            null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        busy -> {
            val animated by animateFloatAsState(
                targetValue = if (progress > 0f) progress else 0.05f,
                animationSpec = tween(350),
                label = "download-row-progress",
            )
            CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        else -> Icon(
            Icons.Filled.Download,
            null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun hasStoragePermission(context: ViewContext): Boolean {
    val required = context.symphony.permission.getStoragePermissions()
    return required.isEmpty() || context.symphony.permission.hasStoragePermissions(context.activity)
}

private fun showStoragePermissionToast(context: ViewContext) {
    Toast.makeText(
        context.activity,
        "Storage permission is needed to download songs",
        Toast.LENGTH_SHORT,
    ).show()
}

private enum class VideoDownloadKind { Idle, Busy, Done }
