package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.services.video.VideoText
import io.github.zyrouge.symphony.ui.helpers.Haptic
import io.github.zyrouge.symphony.ui.helpers.ScreenOrientation
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.helpers.haptic
import io.github.zyrouge.symphony.ui.view.NowPlayingData

/**
 * What fills the space the square cover used to take: lyrics and the queue, one tab each.
 * Lyrics come first because that is what you look at while a music video plays.
 */
@Composable
internal fun NowPlayingVideoPanel(context: ViewContext, data: NowPlayingData) {
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = defaultHorizontalPadding - 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PanelTab(VideoText.LYRICS, selected = tab == 0) { tab = 0 }
            PanelTab(VideoText.UP_NEXT, selected = tab == 1) { tab = 1 }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(
                targetState = tab,
                animationSpec = tween(200),
                label = "video-panel-tab",
            ) { current ->
                when (current) {
                    0 -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                    ) {
                        // Video and audio versions of a song can differ in timing, so the
                        // lyrics are shown unsynced here, as they were on the square card.
                        NowPlayingBodyCoverLyrics(
                            context,
                            ScreenOrientation.PORTRAIT,
                            forceUnsynced = true,
                            showTitle = false,
                        )
                    }

                    else -> UpNextList(context)
                }
            }
        }
    }
}

@Composable
private fun PanelTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(1.dp),
                ),
        )
    }
}

@Composable
private fun UpNextList(context: ViewContext) {
    val queue by context.symphony.radio.observatory.queue.collectAsState()
    val queueIndex by context.symphony.radio.observatory.queueIndex.collectAsState()
    val listState = rememberLazyListState()

    // Keep the playing song at the top so what's coming next is what you see first.
    LaunchedEffect(queueIndex, queue.size) {
        if (queue.isNotEmpty()) {
            listState.animateScrollToItem(queueIndex.coerceIn(0, queue.lastIndex))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        itemsIndexed(queue, key = { i, id -> "$i-$id" }) { i, songId ->
            val song = context.symphony.groove.song.get(songId) ?: return@itemsIndexed
            UpNextRow(
                context = context,
                song = song,
                index = i,
                current = i == queueIndex,
                played = i < queueIndex,
            )
        }
    }
}

@Composable
private fun UpNextRow(
    context: ViewContext,
    song: Song,
    index: Int,
    current: Boolean,
    played: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.haptic(Haptic.Toggle)
                context.symphony.radio.jumpTo(index)
            }
            .padding(horizontal = defaultHorizontalPadding, vertical = 8.dp)
            .graphicsLayer { alpha = if (played) 0.45f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            song.createArtworkImageRequest(context.symphony).build(),
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = if (current) MaterialTheme.colorScheme.primary else Color.White,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            if (song.artists.isNotEmpty()) {
                Text(
                    song.artists.joinToString(", "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.6f),
                    ),
                )
            }
        }
        if (current) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.GraphicEq,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
