package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.zyrouge.symphony.services.video.VideoMode
import io.github.zyrouge.symphony.ui.components.DownloadIconButton
import io.github.zyrouge.symphony.ui.components.VideoDownloadButton
import io.github.zyrouge.symphony.ui.helpers.FadeTransition
import io.github.zyrouge.symphony.ui.helpers.Haptic
import io.github.zyrouge.symphony.ui.helpers.ScreenOrientation
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.helpers.haptic
import io.github.zyrouge.symphony.ui.view.NowPlayingData
import io.github.zyrouge.symphony.ui.view.NowPlayingStates
import kotlin.math.roundToInt

private const val MORPH_MS = 520
private val CoverRadius = 20.dp

/**
 * Portrait Now Playing. One layout that is both the audio screen and the video screen,
 * with a single value ([progress], 0 = audio, 1 = video) driving the move between them:
 *
 *  - the square cover grows into a full-bleed 16:9 stage (padding and rounding go to 0)
 *  - the title / seek bar / controls block rides directly under the stage
 *  - the top bar turns into an overlay on the video and hides when you tap it
 *  - the Lyrics / Up next panel fades in underneath, in the space the cover gave up
 *
 * The video's TextureView stays mounted the whole time, so nothing restarts or flashes.
 */
@Composable
internal fun NowPlayingPortraitBody(
    context: ViewContext,
    data: NowPlayingData,
    states: NowPlayingStates,
) {
    val videoMode = context.symphony.videoMode
    val videoState by videoMode.state.collectAsState()
    val engaged = videoState.engaged
    val ready = videoState.phase == VideoMode.Phase.Ready
    val overlay = rememberVideoOverlayState()

    // Every time video mode starts, begin with the controls showing.
    LaunchedEffect(engaged) {
        if (engaged) overlay.visible = true
    }

    val progressState = animateFloatAsState(
        targetValue = if (engaged) 1f else 0f,
        animationSpec = tween(MORPH_MS, easing = FastOutSlowInEasing),
        label = "now-playing-video-morph",
    )
    val progress: () -> Float = { progressState.value }
    val panelVisible by remember { derivedStateOf { progressState.value > 0.02f } }
    val audioVisible by remember { derivedStateOf { progressState.value < 0.999f } }
    val overlayShown = overlay.shown(ready, data.isPlaying)

    NowPlayingMorphLayout(
        progress = progress,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        appBar = {
            NowPlayingMorphAppBar(
                context = context,
                data = data,
                states = states,
                progress = progress,
                overlayShown = overlayShown,
                onInteract = { overlay.poke() },
            )
        },
        stage = {
            NowPlayingMorphStage(
                context = context,
                data = data,
                states = states,
                progress = progress,
                engaged = engaged,
                audioVisible = audioVisible,
                overlay = overlay,
            )
        },
        info = {
            NowPlayingBodyContent(context, data, states, progress)
        },
        panel = {
            if (panelVisible) {
                NowPlayingVideoPanel(context, data)
            }
        },
    )
}

/**
 * Places four slots. The info block always sits directly below the stage; in audio mode
 * the stage is "everything above the info", in video mode it is a 16:9 box under the
 * status bar. Blending the two positions with [progress] is the whole animation, and it
 * happens in the layout pass, so no frame of it recomposes anything.
 */
@Composable
private fun NowPlayingMorphLayout(
    progress: () -> Float,
    modifier: Modifier,
    appBar: @Composable () -> Unit,
    stage: @Composable () -> Unit,
    info: @Composable () -> Unit,
    panel: @Composable () -> Unit,
) {
    val insetTop = WindowInsets.statusBars.getTop(LocalDensity.current)

    Layout(
        modifier = modifier,
        content = {
            Box { appBar() }
            Box { stage() }
            Box { info() }
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = ((progress() - 0.5f) * 2f).coerceIn(0f, 1f)
                },
            ) { panel() }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val p = progress().coerceIn(0f, 1f)

        val loose = Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = h)
        val bar = measurables[0].measure(loose)
        val info = measurables[2].measure(loose)

        val videoHeight = (w * 9f / 16f).roundToInt()
        val infoY = lerp(h - info.height, insetTop + videoHeight, p)
        val stageTop = lerp(bar.height, insetTop, p)
        val stageHeight = (infoY - stageTop).coerceAtLeast(0)
        val stage = measurables[1].measure(Constraints.fixed(w, stageHeight))

        val panelTop = infoY + info.height
        // Measure the panel at its final (video-mode) height, not the animated one. It
        // holds a lyrics/queue LazyColumn, and tying its height to the morphing info block
        // remeasured and re-laid out that list on every frame of the morph. Only its
        // placement and alpha change now, so there is nothing to re-measure.
        val panelHeight = (h - (insetTop + videoHeight + info.height)).coerceAtLeast(0)
        val panel = measurables[3].measure(Constraints.fixed(w, panelHeight))

        layout(w, h) {
            stage.place(0, stageTop)
            info.place(0, infoY)
            panel.place(0, panelTop)
            // Last, so it draws over the video.
            bar.place(0, 0)
        }
    }
}

/**
 * The cover / video rectangle. Its size, position and corner radius are all blended from
 * [progress] in a layout modifier:
 *
 *   audio   -> a rounded square (or the tall lyrics card when lyrics are on the cover)
 *   video   -> the full width and height the layout gives the stage, square corners
 *
 * Artwork (or lyrics) is underneath and the video fades in on top of it. The video's
 * poster is the same artwork, so the hand-off between them is invisible.
 */
@Composable
private fun NowPlayingMorphStage(
    context: ViewContext,
    data: NowPlayingData,
    states: NowPlayingStates,
    progress: () -> Float,
    engaged: Boolean,
    audioVisible: Boolean,
    overlay: VideoOverlayState,
) {
    val showLyrics by states.showLyrics.collectAsState()
    // Lyrics belong to the panel while video is on, so they only take the cover in audio mode.
    val lyricsOnCover = showLyrics && !engaged
    val lyricsAmount by animateFloatAsState(
        targetValue = if (lyricsOnCover) 1f else 0f,
        animationSpec = tween(300),
        label = "now-playing-stage-lyrics",
    )

    val density = LocalDensity.current
    val cornerPx = with(density) { CoverRadius.toPx() }
    val padX = with(density) { defaultHorizontalPadding.roundToPx() }
    val padBottom = with(density) { 20.dp.roundToPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val cw = constraints.maxWidth
                    val ch = constraints.maxHeight
                    val p = progress().coerceIn(0f, 1f)
                    val la = lyricsAmount

                    // The audio rectangle: a square, growing into the tall lyrics card.
                    val side = minOf(cw - 2 * padX, ch - padBottom).coerceAtLeast(0).toFloat()
                    val audioW = lerp(side, (cw - 2 * padX).coerceAtLeast(0).toFloat(), la)
                    val audioH = lerp(side, (ch - padBottom).coerceAtLeast(0).toFloat(), la)
                    val audioY = (ch - padBottom - audioH) / 2f

                    val w = lerp(audioW, cw.toFloat(), p).roundToInt().coerceAtLeast(0)
                    val h = lerp(audioH, ch.toFloat(), p).roundToInt().coerceAtLeast(0)
                    val y = lerp(audioY, 0f, p).roundToInt()

                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(cw, ch) { placeable.place((cw - w) / 2, y) }
                }
                .graphicsLayer {
                    shape = RoundedCornerShape(cornerPx * (1f - progress().coerceIn(0f, 1f)))
                    clip = true
                },
        ) {
            if (audioVisible) {
                AnimatedContent(
                    label = "now-playing-stage-audio",
                    modifier = Modifier.fillMaxSize(),
                    targetState = lyricsOnCover,
                    transitionSpec = {
                        FadeTransition.enterTransition()
                            .togetherWith(FadeTransition.exitTransition())
                    },
                ) { lyrics ->
                    if (lyrics) {
                        NowPlayingBodyCoverLyrics(context, ScreenOrientation.PORTRAIT)
                    } else {
                        NowPlayingBodyCoverArtwork(context, data.song, fillBounds = true)
                    }
                }
            }

            AnimatedVisibility(
                visible = engaged,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250)),
            ) {
                val poster = remember(data.song.id) {
                    data.song.createArtworkImageRequest(context.symphony).build()
                }
                VideoCard(
                    context = context,
                    poster = poster,
                    modifier = Modifier.fillMaxSize(),
                    stage = true,
                    overlay = overlay,
                )
            }
        }
    }
}

/** Favourite, download and more, shown beside the title once video mode is on. */
@Composable
internal fun VideoTitleActions(
    context: ViewContext,
    data: NowPlayingData,
    states: NowPlayingStates,
) {
    val favoriteSongIds by context.symphony.groove.playlist.favorites.collectAsState()
    val isFavorite = favoriteSongIds.contains(data.song.id)

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
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
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        // In video mode the download button asks audio vs video (each has its own state);
        // audio-only screens keep using the plain DownloadIconButton.
        VideoDownloadButton(context, data.song, tint = Color.White)
        IconButton(
            onClick = { states.showExtraOptions.value = !states.showExtraOptions.value },
        ) {
            Icon(Icons.Outlined.MoreHoriz, null, tint = Color.White)
        }
    }
}

/**
 * Shrinks a block's width to [fraction] of its natural width, fading and clipping it as it
 * goes, so a neighbour (the title) takes the space smoothly.
 */
internal fun Modifier.revealWidth(fraction: () -> Float): Modifier = this
    .graphicsLayer {
        alpha = fraction().coerceIn(0f, 1f)
        clip = true
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0))
        val width = (placeable.width * fraction().coerceIn(0f, 1f)).roundToInt()
        layout(width, placeable.height) {
            // Anchored to the right edge, so it slides out toward the screen edge.
            placeable.placeRelative(width - placeable.width, 0)
        }
    }

/** Same idea as [revealWidth], for height. */
internal fun Modifier.verticalReveal(fraction: () -> Float): Modifier = this
    .graphicsLayer {
        alpha = fraction().coerceIn(0f, 1f)
        clip = true
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * fraction().coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, height) {
            placeable.placeRelative(0, 0)
        }
    }
