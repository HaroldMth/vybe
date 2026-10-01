package io.github.zyrouge.symphony.ui.view.nowPlaying

import io.github.zyrouge.symphony.ui.helpers.haptic
import io.github.zyrouge.symphony.ui.helpers.Haptic
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
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
import io.github.zyrouge.symphony.services.video.VideoMode
import io.github.zyrouge.symphony.services.video.VideoText
import io.github.zyrouge.symphony.ui.components.DownloadIconButton
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.NowPlayingData
import io.github.zyrouge.symphony.ui.view.NowPlayingStates
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Flat action-bar replacing the old CenterAlignedTopAppBar.
 * Layout: [↓ collapse]  [⋯ extra options]  [♥ favorite]  [⬇ download]
 * Queue, shuffle and repeat now live only in the main control row / bottom bar
 * so nothing is duplicated on screen.
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

        // Extra options (moved up from the bottom bar)
        IconButton(onClick = { states.showExtraOptions.value = !states.showExtraOptions.value }) {
            Icon(
                Icons.Outlined.MoreHoriz,
                null,
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }

        // Favorite
        IconButton(
            onClick = {
                context.haptic(Haptic.Toggle)
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

        // Video mode (YouTube) toggle
        val videoState by context.symphony.videoMode.state.collectAsState()
        IconButton(
            onClick = {
                context.haptic(Haptic.Toggle)
                context.symphony.videoMode.toggleForSong(data.song.id)
            }
        ) {
            YouTubeToggleIcon(
                active = videoState.engaged,
                loading = videoState.phase == VideoMode.Phase.Loading,
                description = VideoText.VIDEO_MODE,
            )
        }

        // Download
        DownloadIconButton(context, data.song, tint = Color.White)
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
        IconButton(onClick = { states.showExtraOptions.value = !states.showExtraOptions.value }) {
            Icon(Icons.Outlined.MoreHoriz, null, tint = Color.White)
        }
    }
}

/**
 * The portrait top bar for the morphing player. In audio mode it is the familiar
 * `˅ ... ⋯ ♡ ▶ ⤓` row. In video mode it becomes an overlay on top of the video,
 * `˅ ... ▶ ⛶`, over a soft scrim, and fades away with the video's other controls
 * (favourite, download and more move down next to the title).
 *
 * [progress] is 0 for audio and 1 for video; [overlayShown] is whether the video's
 * controls are currently visible.
 */
@Composable
fun NowPlayingMorphAppBar(
    context: ViewContext,
    data: NowPlayingData,
    states: NowPlayingStates,
    progress: () -> Float,
    overlayShown: Boolean,
    onInteract: () -> Unit,
) {
    val videoMode = context.symphony.videoMode
    val videoState by videoMode.state.collectAsState()
    val fill by videoMode.fillMode.collectAsState()
    val favoriteSongIds by context.symphony.groove.playlist.favorites.collectAsState()
    val isFavorite by remember(data) {
        derivedStateOf { favoriteSongIds.contains(data.song.id) }
    }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (overlayShown) 1f else 0f,
        animationSpec = tween(if (overlayShown) 200 else 350),
        label = "video-overlay-alpha",
    )
    // Which of the two icon groups can be tapped (the other is fading out).
    val audioActive by remember { derivedStateOf { progress() < 0.5f } }
    val videoActive = !audioActive && overlayShown
    // A disabled button still swallows taps, so anything that isn't visible must be out of
    // the tree entirely, not just faded or disabled. Otherwise invisible video icons sit
    // on top of the audio icons (and over the video itself) and eat the touches.
    val overlayVisible = overlayAlpha > 0.01f

    Box(modifier = Modifier.fillMaxWidth()) {
        // Scrim so white icons stay legible on bright video frames.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = progress() * overlayAlpha }
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                    ),
                ),
        )
        // The video starts below the status bar; black fills the inset above it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .graphicsLayer { alpha = progress() }
                .background(Color.Black),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Collapse / down: in both modes, same place.
            if (audioActive || overlayVisible) IconButton(
                onClick = { context.navController.popBackStack() },
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - progress() * (1f - overlayAlpha)
                },
            ) {
                Icon(
                    Icons.Filled.ExpandMore,
                    null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
            }

            Box(modifier = Modifier.weight(1f).height(48.dp)) {
                // Audio: ⋯ ♡ ▶ ⤓. Fades out over the first half of the morph and is then
                // removed, so it can never catch a tap meant for the video underneath.
                if (audioActive) Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer { alpha = (1f - progress() * 2f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = audioActive,
                        onClick = { states.showExtraOptions.value = !states.showExtraOptions.value },
                    ) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        enabled = audioActive,
                        onClick = {
                            context.haptic(Haptic.Toggle)
                            context.symphony.groove.playlist.run {
                                if (isFavorite) unfavorite(data.song.id)
                                else favorite(data.song.id)
                            }
                        },
                    ) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    IconButton(
                        enabled = audioActive,
                        onClick = {
                            context.haptic(Haptic.Toggle)
                            videoMode.toggleForSong(data.song.id)
                        },
                    ) {
                        YouTubeToggleIcon(
                            active = videoState.engaged,
                            loading = videoState.phase == VideoMode.Phase.Loading,
                            description = VideoText.VIDEO_MODE,
                        )
                    }
                    DownloadIconButton(context, data.song, tint = Color.White)
                }

                // Video: ▶ ⛶. Fades in over the second half, and is gone entirely while the
                // overlay is hidden so taps reach the video.
                if (!audioActive && overlayVisible) Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer {
                            alpha = ((progress() - 0.5f) * 2f).coerceIn(0f, 1f) * overlayAlpha
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = videoActive,
                        onClick = {
                            context.haptic(Haptic.Toggle)
                            videoMode.toggleForSong(data.song.id)
                        },
                    ) {
                        YouTubeToggleIcon(
                            active = videoState.engaged,
                            loading = videoState.phase == VideoMode.Phase.Loading,
                            description = VideoText.VIDEO_MODE,
                        )
                    }
                    IconButton(
                        enabled = videoActive,
                        onClick = {
                            context.haptic(Haptic.Toggle)
                            videoMode.toggleFill()
                            onInteract()
                        },
                    ) {
                        Icon(
                            if (fill) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (fill) VideoText.FIT else VideoText.FILL,
                            modifier = Modifier.size(26.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
