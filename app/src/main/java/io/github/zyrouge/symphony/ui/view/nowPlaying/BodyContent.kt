package io.github.zyrouge.symphony.ui.view.nowPlaying

import io.github.zyrouge.symphony.ui.helpers.navigateSafe
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.radio.RadioQueue
import io.github.zyrouge.symphony.ui.components.SongDropdownMenu
import io.github.zyrouge.symphony.ui.helpers.FadeTransition
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.ui.view.NowPlayingData
import io.github.zyrouge.symphony.ui.view.NowPlayingDefaults
import io.github.zyrouge.symphony.ui.view.NowPlayingLyricsLayout
import io.github.zyrouge.symphony.ui.view.NowPlayingStates
import io.github.zyrouge.symphony.utils.DurationUtils

/**
 * Bottom section of the Now Playing screen:
 *   Song title + LYRICS pill
 *   Artist name
 *   Seek bar
 *   Controls row: [⇌] [⏮] [▶] [⏭] [↺]
 *   Extra options moved to BottomBar's … menu
 */
@Composable
fun NowPlayingBodyContent(context: ViewContext, data: NowPlayingData, states: NowPlayingStates) {
    val showLyrics by states.showLyrics.collectAsState()

    data.run {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            // ── Song title + LYRICS pill ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = defaultHorizontalPadding),
                verticalAlignment = Alignment.Top,
            ) {
                AnimatedContent(
                    label = "now-playing-body-title",
                    modifier = Modifier.weight(1f),
                    targetState = song,
                    transitionSpec = {
                        FadeTransition.enterTransition()
                            .togetherWith(FadeTransition.exitTransition())
                    },
                ) { targetStateSong ->
                    Column {
                        Text(
                            targetStateSong.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (targetStateSong.artists.isNotEmpty()) {
                            Row {
                                targetStateSong.artists.forEachIndexed { i, artist ->
                                    Text(
                                        artist,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White.copy(alpha = 0.7f),
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.pointerInput(Unit) {
                                            detectTapGestures { _ ->
                                                context.navController.navigateSafe(ArtistViewRoute(artist))
                                            }
                                        },
                                    )
                                    if (i != targetStateSong.artists.size - 1) {
                                        Text(
                                            ", ",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.White.copy(alpha = 0.7f),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // LYRICS pill button
                Surface(
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .border(
                            1.dp,
                            if (showLyrics) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.4f),
                            RoundedCornerShape(50),
                        )
                        .then(
                            Modifier.pointerInput(Unit) {
                                detectTapGestures {
                                    when (lyricsLayout) {
                                        NowPlayingLyricsLayout.ReplaceArtwork -> {
                                            val nShow = !states.showLyrics.value
                                            states.showLyrics.value = nShow
                                            NowPlayingDefaults.showLyrics = nShow
                                        }
                                        NowPlayingLyricsLayout.SeparatePage -> {
                                            context.navController.navigate(
                                                io.github.zyrouge.symphony.ui.view.LyricsViewRoute
                                            )
                                        }
                                    }
                                }
                            }
                        ),
                    color = if (showLyrics)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else Color.Transparent,
                ) {
                    Text(
                        "LYRICS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (showLyrics) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(
                                1f,
                                androidx.compose.ui.unit.TextUnitType.Sp
                            ),
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Seek bar ──────────────────────────────────────────────────────
            NowPlayingSeekBar(context)

            Spacer(modifier = Modifier.height(8.dp))

            // ── Controls: [⇌] [⏮] [▶] [⏭] [↺] ─────────────────────────────
            NowPlayingVybeControls(context, data)

            Spacer(modifier = Modifier.height(4.dp))

            // ── Bottom bar (extra options: queue count, ···) ──────────────────
            NowPlayingBodyBottomBar(context, data, states)
        }
    }
}

/**
 * Vybe-style control row matching the reference screenshot:
 * Shuffle | SkipPrev | PlayPause(large) | SkipNext | Repeat
 */
@Composable
fun NowPlayingVybeControls(context: ViewContext, data: NowPlayingData) {
    data.run {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = defaultHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shuffle
            IconButton(onClick = { context.symphony.radio.queue.toggleShuffleMode() }) {
                Icon(
                    Icons.Filled.Shuffle,
                    null,
                    modifier = Modifier.size(22.dp),
                    tint = if (currentShuffleMode) MaterialTheme.colorScheme.primary
                    else Color.White.copy(alpha = 0.7f),
                )
            }

            // Skip previous
            IconButton(onClick = { context.symphony.radio.shorty.previous() }) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
            }

            // Play / pause — large white circle
            IconButton(
                onClick = { context.symphony.radio.shorty.playPause() },
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White, CircleShape),
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.Black,
                )
            }

            // Skip next
            IconButton(onClick = { context.symphony.radio.shorty.skip() }) {
                Icon(
                    Icons.Filled.SkipNext,
                    null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
            }

            // Repeat
            IconButton(onClick = { context.symphony.radio.queue.toggleLoopMode() }) {
                Icon(
                    when (currentLoopMode) {
                        RadioQueue.LoopMode.Song -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    },
                    null,
                    modifier = Modifier.size(22.dp),
                    tint = when (currentLoopMode) {
                        RadioQueue.LoopMode.None -> Color.White.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

// ── SeekBar ───────────────────────────────────────────────────────────────────

@Composable
fun NowPlayingSeekBar(context: ViewContext) {
    val playbackPosition by context.symphony.radio.observatory.playbackPosition.collectAsState()

    Row(
        modifier = Modifier.padding(horizontal = defaultHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var seekRatio by remember { mutableStateOf<Float?>(null) }

        NowPlayingPlaybackPositionText(
            seekRatio?.let { it * playbackPosition.total }?.toLong() ?: playbackPosition.played,
            Alignment.CenterStart,
        )
        Box(modifier = Modifier.weight(1f)) {
            NowPlayingSeekBarSlider(
                ratio = playbackPosition.ratio,
                onSeekStart = { seekRatio = 0f },
                onSeek = { seekRatio = it },
                onSeekEnd = {
                    context.symphony.radio.seek((it * playbackPosition.total).toLong())
                    seekRatio = null
                },
                onSeekCancel = { seekRatio = null },
            )
        }
        NowPlayingPlaybackPositionText(
            playbackPosition.total,
            Alignment.CenterEnd,
        )
    }
}

@Composable
private fun NowPlayingSeekBarSlider(
    ratio: Float,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    onSeekCancel: () -> Unit,
) {
    val sliderHeight = 12.dp
    val thumbSize = 14.dp
    val thumbSizeHalf = thumbSize / 2
    val trackHeight = 4.dp

    var dragging by remember { mutableStateOf(false) }
    var dragRatio by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(sliderHeight),
        contentAlignment = Alignment.Center,
    ) {
        val sliderWidth = maxWidth

        Box(
            modifier = Modifier
                .height(sliderHeight)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSeekEnd((offset.x / sliderWidth.toPx()).coerceIn(0f..1f))
                    }
                }
                .pointerInput(Unit) {
                    var offsetX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            offsetX = offset.x
                            dragging = true
                            onSeekStart()
                        },
                        onDragEnd = {
                            onSeekEnd(dragRatio)
                            offsetX = 0f
                            dragging = false
                            dragRatio = 0f
                        },
                        onDragCancel = {
                            onSeekCancel()
                            offsetX = 0f
                            dragging = false
                            dragRatio = 0f
                        },
                        onHorizontalDrag = { pointer, dragAmount ->
                            pointer.consume()
                            offsetX += dragAmount
                            dragRatio = (offsetX / sliderWidth.toPx()).coerceIn(0f..1f)
                            onSeek(dragRatio)
                        },
                    )
                }
        )
        // Track background
        Box(
            modifier = Modifier
                .padding(thumbSizeHalf, 0.dp)
                .height(trackHeight)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(thumbSizeHalf))
        ) {
            // Track fill
            Box(
                modifier = Modifier
                    .height(trackHeight)
                    .fillMaxWidth(if (dragging) dragRatio else ratio)
                    .background(Color.White, RoundedCornerShape(thumbSizeHalf))
            )
        }
        // Thumb
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .offset(
                        sliderWidth
                            .minus(thumbSizeHalf.times(2))
                            .times(if (dragging) dragRatio else ratio),
                        0.dp
                    )
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun NowPlayingPlaybackPositionText(duration: Long, alignment: Alignment) {
    val durationFormatted = DurationUtils.formatMs(duration)
    Box(contentAlignment = alignment) {
        Text(
            "0".repeat(durationFormatted.length),
            style = MaterialTheme.typography.labelMedium.copy(color = Color.Transparent),
        )
        Text(
            durationFormatted,
            style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.7f)),
        )
    }
}

// Legacy compact controls (kept for landscape / settings variants)
@Composable
fun NowPlayingCompactControls(context: ViewContext, data: NowPlayingData, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(defaultHorizontalPadding, 0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NowPlayingControlButton(
            NowPlayingControlButtonStyle(NowPlayingControlButtonColor.Primary),
            icon = if (!data.isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            onClick = { context.symphony.radio.shorty.playPause() }
        )
        NowPlayingControlButton(
            NowPlayingControlButtonStyle(NowPlayingControlButtonColor.Surface),
            icon = Icons.Filled.SkipPrevious,
            onClick = { context.symphony.radio.shorty.previous() }
        )
        NowPlayingControlButton(
            NowPlayingControlButtonStyle(NowPlayingControlButtonColor.Surface),
            icon = Icons.Filled.SkipNext,
            onClick = { context.symphony.radio.shorty.skip() }
        )
    }
}

@Composable
fun NowPlayingTraditionalControls(context: ViewContext, data: NowPlayingData) {
    NowPlayingVybeControls(context, data)
}

private enum class NowPlayingControlButtonColor { Primary, Surface, Transparent }
private enum class NowPlayingControlButtonSize { Default, Large }
private data class NowPlayingControlButtonStyle(
    val color: NowPlayingControlButtonColor,
    val size: NowPlayingControlButtonSize = NowPlayingControlButtonSize.Default,
)

@Composable
private fun NowPlayingControlButton(
    style: NowPlayingControlButtonStyle,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val backgroundColor = when (style.color) {
        NowPlayingControlButtonColor.Primary -> MaterialTheme.colorScheme.primary
        NowPlayingControlButtonColor.Surface -> MaterialTheme.colorScheme.surfaceVariant
        NowPlayingControlButtonColor.Transparent -> Color.Transparent
    }
    val contentColor = when (style.color) {
        NowPlayingControlButtonColor.Primary -> MaterialTheme.colorScheme.onPrimary
        else -> LocalContentColor.current
    }
    val iconSize = when (style.size) {
        NowPlayingControlButtonSize.Default -> 24.dp
        NowPlayingControlButtonSize.Large -> 32.dp
    }
    IconButton(
        modifier = Modifier.background(backgroundColor, CircleShape),
        onClick = onClick,
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}
