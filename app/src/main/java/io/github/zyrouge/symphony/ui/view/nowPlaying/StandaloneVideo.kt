package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.services.api.VybeVideoItem
import io.github.zyrouge.symphony.ui.helpers.Haptic
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.helpers.haptic

/**
 * A video opened straight from search results. It has no song and no queue, so it gets
 * its own small screen: the video card, its title, the shared seek bar and play/pause.
 * Leaving the screen stops the video and hands the music back.
 */
@Composable
fun StandaloneVideoView(context: ViewContext, item: VybeVideoItem) {
    val videoMode = context.symphony.videoMode
    val isPlaying by context.symphony.radio.observatory.isPlaying.collectAsState()

    DisposableEffect(item.videoId) {
        onDispose {
            if (videoMode.state.value.standalone?.videoId == item.videoId) {
                videoMode.exit(resumeAudio = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(40.dp)
                .graphicsLayer { alpha = 0.55f },
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { context.navController.popBackStack() }) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = defaultHorizontalPadding)
                    .padding(bottom = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints {
                    val dimension = min(maxWidth, maxHeight)
                    VideoCard(
                        context = context,
                        poster = item.thumbnail,
                        modifier = Modifier.size(dimension),
                        canSwipe = false,
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = defaultHorizontalPadding)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.channel.isNotBlank()) {
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        item.channel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.65f),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.size(12.dp))
            NowPlayingSeekBar(context)
            Spacer(modifier = Modifier.size(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    context.haptic(Haptic.Toggle)
                    context.symphony.radio.shorty.seekFromCurrent(
                        -context.symphony.settings.seekBackDuration.value
                    )
                }) {
                    Icon(Icons.Filled.Replay10, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.width(20.dp))
                Surface(
                    onClick = {
                        context.haptic(Haptic.Toggle)
                        context.symphony.radio.shorty.playPause()
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(68.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                IconButton(onClick = {
                    context.haptic(Haptic.Toggle)
                    context.symphony.radio.shorty.seekFromCurrent(
                        context.symphony.settings.seekForwardDuration.value
                    )
                }) {
                    Icon(Icons.Filled.Forward10, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}
