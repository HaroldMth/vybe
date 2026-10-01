package io.github.zyrouge.symphony.ui.view.nowPlaying

import android.os.Build
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.video.VideoMode
import io.github.zyrouge.symphony.services.video.VideoText
import io.github.zyrouge.symphony.ui.components.KeepScreenAwake
import io.github.zyrouge.symphony.ui.components.PulsingBarsLoader
import io.github.zyrouge.symphony.ui.components.swipeable
import io.github.zyrouge.symphony.ui.helpers.Haptic
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.helpers.haptic
import kotlinx.coroutines.delay
import androidx.compose.runtime.Stable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(20.dp)
private const val CONTROLS_HIDE_MS = 2500L
private const val BUFFERING_DELAY_MS = 500L
private const val REVEAL_MS = 450

/**
 * Whether the video's overlay controls are showing. Hoisted out of [VideoCard] so the
 * player's top bar (which sits over the video in the full-bleed layout) hides and shows
 * together with the card's own chips.
 */
@Stable
class VideoOverlayState {
    var visible by mutableStateOf(true)
    var nonce by mutableIntStateOf(0)

    fun toggle() {
        visible = !visible
        nonce++
    }

    /** Restarts the auto-hide timer without changing visibility. */
    fun poke() {
        nonce++
    }

    /** Always shown while nothing is playing, so there is never a way to get stuck. */
    fun shown(ready: Boolean, isPlaying: Boolean) = !ready || visible || !isPlaying
}

@Composable
fun rememberVideoOverlayState() = remember { VideoOverlayState() }

/**
 * The square video card. Artwork sits on top until the first frame is actually drawn and
 * then crossfades away, so there is never a black flash. Everything it shows comes from
 * [VideoMode], so it can be torn down and rebuilt (lyrics toggle, rotation) without the
 * video restarting.
 */
@Composable
fun VideoCard(
    context: ViewContext,
    poster: Any?,
    modifier: Modifier = Modifier,
    canSwipe: Boolean = true,
    // Full-bleed player layout: no rounded card, glow, shadow or border, and no entrance
    // animation (the screen's own morph does that).
    stage: Boolean = false,
    overlay: VideoOverlayState = rememberVideoOverlayState(),
) {
    val videoMode = context.symphony.videoMode
    val state by videoMode.state.collectAsState()
    val hasFrame by videoMode.hasFrame.collectAsState()
    val isBuffering by videoMode.isBuffering.collectAsState()
    val aspect by videoMode.aspect.collectAsState()
    val fill by videoMode.fillMode.collectAsState()
    val isPlaying by context.symphony.radio.observatory.isPlaying.collectAsState()

    val ready = state.phase == VideoMode.Phase.Ready
    val revealed = ready && hasFrame

    // Soft entrance: the card eases in instead of popping.
    var entered by remember { mutableStateOf(stage) }
    LaunchedEffect(Unit) { entered = true }
    val enterScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "video-card-enter-scale",
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(320),
        label = "video-card-enter-alpha",
    )

    val videoAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(REVEAL_MS, easing = FastOutSlowInEasing),
        label = "video-alpha",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (revealed) 0.5f else 0f,
        animationSpec = tween(900),
        label = "video-glow",
    )

    // Controls: shown on tap, hidden a moment after playback starts, always visible while paused.
    LaunchedEffect(overlay.visible, overlay.nonce, isPlaying, ready) {
        if (overlay.visible && isPlaying && ready) {
            delay(CONTROLS_HIDE_MS)
            overlay.visible = false
        }
    }
    val showControls = ready && (overlay.visible || !isPlaying)

    // The buffering ring only appears if the stall lasts, so quick seeks stay clean.
    var showBuffering by remember { mutableStateOf(false) }
    LaunchedEffect(isBuffering, ready) {
        if (isBuffering && ready) {
            delay(BUFFERING_DELAY_MS)
            showBuffering = true
        } else {
            showBuffering = false
        }
    }

    // Transient "-10s / +10s" hint after a double tap.
    var seekHint by remember { mutableIntStateOf(0) }
    var seekHintNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekHintNonce) {
        if (seekHintNonce > 0) {
            delay(650)
            seekHint = 0
        }
    }

    if (ready && isPlaying) {
        KeepScreenAwake()
    }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = enterScale
            scaleY = enterScale
            alpha = enterAlpha
        },
    ) {
        // Ambient glow: a blurred copy of the artwork behind the card. Only on API 31+,
        // where blur exists; below that an unblurred copy would look like a bug.
        if (!stage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        alpha = glowAlpha
                    }
                    .blur(40.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (stage) Modifier
                    else Modifier.shadow(14.dp, CardShape, clip = false).clip(CardShape),
                )
                .background(Color.Black)
                .then(
                    if (stage) Modifier
                    else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.12f), CardShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (ready) {
                VideoSurface(
                    context = context,
                    aspect = aspect,
                    fill = fill,
                    alpha = videoAlpha,
                )
            }

            // Poster: the same artwork the player shows, blurred while the video is found.
            val loading = state.phase == VideoMode.Phase.Loading
            val posterBlur by animateDpAsState(
                targetValue = if (loading) 14.dp else 0.dp,
                animationSpec = tween(500),
                label = "video-poster-blur",
            )
            val posterAlpha by animateFloatAsState(
                targetValue = if (revealed) 0f else 1f,
                animationSpec = tween(REVEAL_MS, easing = FastOutSlowInEasing),
                label = "video-poster-alpha",
            )
            if (posterAlpha > 0.01f) {
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = posterAlpha }
                        .blur(posterBlur),
                )
            }

            // Bottom vignette for legibility of the chips and to sit the frame into the UI.
            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(400)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.38f)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            ),
                        ),
                )
            }

            when (state.phase) {
                VideoMode.Phase.Loading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PulsingBarsLoader()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (state.retrying) VideoText.RETRYING else VideoText.LOADING,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.8f),
                            ),
                        )
                    }
                }

                VideoMode.Phase.Error -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            when (state.message) {
                                VideoMode.MESSAGE_NOT_FOUND -> VideoText.NOT_FOUND
                                else -> VideoText.PLAYBACK_FAILED
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        )
                        state.detail?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.5f),
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        FilledTonalButton(onClick = {
                            context.haptic(Haptic.Toggle)
                            videoMode.retry()
                        }) {
                            Text(VideoText.TRY_AGAIN)
                        }
                    }
                }

                else -> Unit
            }

            AnimatedVisibility(
                visible = showBuffering,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                )
            }

            // Gestures: tap toggles the controls, double tap seeks (sides) or plays/pauses
            // (middle), horizontal swipe changes song like the artwork does.
            val seekBackSeconds = context.symphony.settings.seekBackDuration.value
            val seekForwardSeconds = context.symphony.settings.seekForwardDuration.value
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (canSwipe) {
                            Modifier.swipeable(
                                minimumDragAmount = 100f,
                                onSwipeLeft = {
                                    if (context.symphony.radio.canJumpToNext()) {
                                        context.haptic(Haptic.Toggle)
                                        context.symphony.radio.jumpToNext()
                                    }
                                },
                                onSwipeRight = {
                                    if (context.symphony.radio.canJumpToPrevious()) {
                                        context.haptic(Haptic.Toggle)
                                        context.symphony.radio.jumpToPrevious()
                                    }
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .pointerInput(ready) {
                        detectTapGestures(
                            onTap = {
                                if (ready) overlay.toggle()
                            },
                            onDoubleTap = { offset ->
                                if (!ready) return@detectTapGestures
                                val third = size.width / 3f
                                when {
                                    offset.x < third -> {
                                        context.haptic(Haptic.Toggle)
                                        context.symphony.radio.shorty.seekFromCurrent(-seekBackSeconds)
                                        seekHint = -seekBackSeconds
                                        seekHintNonce++
                                    }

                                    offset.x > third * 2 -> {
                                        context.haptic(Haptic.Toggle)
                                        context.symphony.radio.shorty.seekFromCurrent(seekForwardSeconds)
                                        seekHint = seekForwardSeconds
                                        seekHintNonce++
                                    }

                                    else -> {
                                        context.haptic(Haptic.Toggle)
                                        context.symphony.radio.shorty.playPause()
                                    }
                                }
                                overlay.poke()
                            },
                        )
                    },
            )

            AnimatedVisibility(
                visible = seekHint != 0,
                modifier = Modifier.align(if (seekHint < 0) Alignment.CenterStart else Alignment.CenterEnd),
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(300)),
            ) {
                Surface(
                    modifier = Modifier.padding(20.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f),
                ) {
                    Text(
                        text = (if (seekHint < 0) "\u2212" else "+") + "${kotlin.math.abs(seekHint)}s",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            // Chips: the quality (only when the API reports one) and the fill/fit switch.
            AnimatedVisibility(
                visible = showControls && !stage,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(400)),
            ) {
                Surface(
                    modifier = Modifier
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            context.haptic(Haptic.Toggle)
                            videoMode.toggleFill()
                            overlay.poke()
                        },
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f),
                ) {
                    Text(
                        text = if (fill) VideoText.FIT else VideoText.FILL,
                        style = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            val quality = state.stream?.quality?.takeIf { it.isNotBlank() }
            if (quality != null) {
                AnimatedVisibility(
                    visible = showControls,
                    modifier = Modifier.align(Alignment.BottomStart),
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(400)),
                ) {
                    Surface(
                        modifier = Modifier.padding(12.dp),
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.55f),
                    ) {
                        Text(
                            text = quality,
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A TextureView (not a SurfaceView), so the rounded corners, fades and morph animations
 * of whatever holds it apply to the video too. It is sized from the video's real aspect
 * ratio inside whatever rectangle it is given (a square card or the full-bleed 16:9
 * stage) and clipped by its parent, which is what makes "fill" actually fill.
 *
 * The size is worked out in a layout modifier instead of composition, so resizing it every
 * frame during the audio/video morph doesn't recompose anything.
 */
@Composable
private fun VideoSurface(
    context: ViewContext,
    aspect: Float,
    fill: Boolean,
    alpha: Float,
) {
    val fillAmount by animateFloatAsState(
        targetValue = if (fill) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "video-fill",
    )
    val ratio = if (aspect > 0f) aspect else 16f / 9f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { context.symphony.videoMode.attachTexture(it) }
            },
            onRelease = { context.symphony.videoMode.detachTexture(it) },
            modifier = Modifier
                .layout { measurable, constraints ->
                    val cw = constraints.maxWidth.toFloat()
                    val ch = constraints.maxHeight.toFloat()
                    val containerRatio = if (ch > 0f) cw / ch else ratio
                    val wider = ratio >= containerRatio
                    val fitW = if (wider) cw else ch * ratio
                    val fitH = if (wider) cw / ratio else ch
                    val fillW = if (wider) ch * ratio else cw
                    val fillH = if (wider) ch else cw / ratio
                    val w = (fitW + (fillW - fitW) * fillAmount).roundToInt().coerceAtLeast(1)
                    val h = (fitH + (fillH - fitH) * fillAmount).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            (constraints.maxWidth - w) / 2,
                            (constraints.maxHeight - h) / 2,
                        )
                    }
                }
                .graphicsLayer { this.alpha = alpha },
        )
    }
}

/**
 * The YouTube button in the app bar. Monochrome when off, the real logo colours when on,
 * a small spring when it toggles, and a gentle pulse while the video is being found.
 */
@Composable
fun YouTubeToggleIcon(
    active: Boolean,
    loading: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "youtube-scale",
    )
    val pulse by rememberInfiniteTransition(label = "youtube-pulse").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "youtube-pulse-alpha",
    )
    Crossfade(targetState = active, animationSpec = tween(250), label = "youtube-icon") { on ->
        Icon(
            painter = painterResource(if (on) R.drawable.ic_youtube else R.drawable.ic_youtube_mono),
            contentDescription = description,
            tint = if (on) Color.Unspecified else Color.White,
            modifier = modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = if (loading) pulse else 1f
                },
        )
    }
}
