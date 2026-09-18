package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.components.KeepScreenAwake
import io.github.zyrouge.symphony.ui.components.LyricsText
import io.github.zyrouge.symphony.ui.components.TimedContentTextStyle
import io.github.zyrouge.symphony.ui.components.swipeable
import io.github.zyrouge.symphony.ui.helpers.FadeTransition
import io.github.zyrouge.symphony.ui.helpers.ScreenOrientation
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import io.github.zyrouge.symphony.ui.view.NowPlayingData
import io.github.zyrouge.symphony.ui.view.NowPlayingStates

@Composable
fun NowPlayingBodyCover(
    context: ViewContext,
    data: NowPlayingData,
    states: NowPlayingStates,
    orientation: ScreenOrientation,
) {
    val showLyrics by states.showLyrics.collectAsState()

    Box(modifier = Modifier.padding(defaultHorizontalPadding, 0.dp)) {
        AnimatedContent(
            label = "now-playing-body-cover",
            targetState = showLyrics,
            contentAlignment = Alignment.Center,
            transitionSpec = {
                val from = FadeTransition.enterTransition()
                val to = FadeTransition.exitTransition()
                from togetherWith to
            },
        ) { targetStateShowLyrics ->
            if (targetStateShowLyrics) {
                NowPlayingBodyCoverLyrics(context, orientation)
            } else {
                NowPlayingBodyCoverArtwork(context, data.song)
            }
        }
    }
}

@Composable
private fun NowPlayingBodyCoverLyrics(context: ViewContext, orientation: ScreenOrientation) {
    val keepScreenAwake by context.symphony.settings.lyricsKeepScreenAwake.flow.collectAsState()
    val lyricsData by context.symphony.radio.observatory.lyrics.collectAsState()
    val density = LocalDensity.current
    // Measured from the real header instead of a hardcoded offset, so the
    // lyrics never start underneath the "LYRICS" row regardless of font
    // scale or how long the source pill's text ends up being.
    var headerHeightDp by remember { mutableStateOf(48.dp) }

    if (keepScreenAwake) {
        KeepScreenAwake()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp, if (orientation == ScreenOrientation.LANDSCAPE) 0.dp else 8.dp)
            .background(Color(0xFF1A1A1A).copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Lyrics card header
        Box(modifier = Modifier.fillMaxSize()) {
            // Header row
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp, 12.dp, 12.dp, 0.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        headerHeightDp = with(density) { it.size.height.toDp() }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "LYRICS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                )
                Spacer(modifier = Modifier.weight(1f))
                // Source pill
                val source = lyricsData?.source
                if (source != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ) {
                        Text(
                            "● ${lyricsData?.type?.replaceFirstChar { it.uppercase() } ?: "Synced"} · $source",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { /* TODO: lyrics settings */ },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            // Lyrics content
            LyricsText(
                context,
                padding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    // Header's real measured height + breathing room, not a magic number.
                    top = headerHeightDp + 20.dp,
                    bottom = 24.dp,
                ),
                style = TimedContentTextStyle(
                    highlighted = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                    ),
                    active = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                    inactive = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White.copy(alpha = 0.4f),
                    ),
                    spacing = 10.dp,
                ),
            )
        }
    }
}

@Composable
private fun NowPlayingBodyCoverArtwork(context: ViewContext, song: Song) {
    BoxWithConstraints {
        val dimension = min(this@BoxWithConstraints.maxHeight, this@BoxWithConstraints.maxWidth)
        val downloadStates by context.symphony.downloader.states.collectAsState()
        val isDownloaded = downloadStates[song.id]?.status ==
            io.github.zyrouge.symphony.services.download.DownloadStatus.COMPLETED

        Box(modifier = Modifier.size(dimension)) {
            AnimatedContent(
                label = "now-playing-body-cover-artwork",
                modifier = Modifier.fillMaxSize(),
                targetState = song,
                transitionSpec = {
                    FadeTransition.enterTransition()
                        .togetherWith(FadeTransition.exitTransition())
                },
            ) { targetStateSong ->
                AsyncImage(
                    targetStateSong
                        .createArtworkImageRequest(context.symphony)
                        .build(),
                    null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)) // Larger radius than before (was 12.dp)
                        .swipeable(
                            minimumDragAmount = 100f,
                            onSwipeLeft = {
                                if (context.symphony.radio.canJumpToNext()) {
                                    context.symphony.radio.jumpToNext()
                                }
                            },
                            onSwipeRight = {
                                if (context.symphony.radio.canJumpToPrevious()) {
                                    context.symphony.radio.jumpToPrevious()
                                }
                            },
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { _ ->
                                context.symphony.groove.album
                                    .getIdFromSong(song)
                                    ?.let {
                                        context.navController.navigate(AlbumViewRoute(it))
                                    }
                            }
                        }
                )
            }

            if (isDownloaded) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f),
                ) {
                    Icon(
                        Icons.Filled.CloudDone,
                        null,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
