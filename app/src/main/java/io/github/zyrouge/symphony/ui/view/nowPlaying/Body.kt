package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.ui.helpers.ScreenOrientation
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.NowPlayingData
import io.github.zyrouge.symphony.ui.view.NowPlayingDefaults
import io.github.zyrouge.symphony.ui.view.NowPlayingLyricsLayout
import io.github.zyrouge.symphony.ui.view.NowPlayingStates
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

internal val defaultHorizontalPadding = 20.dp

@Composable
fun NowPlayingBody(context: ViewContext, data: NowPlayingData) {
    val states = remember {
        NowPlayingStates(
            showLyrics = MutableStateFlow(
                data.lyricsLayout == NowPlayingLyricsLayout.ReplaceArtwork && NowPlayingDefaults.showLyrics
            ),
            showExtraOptions = MutableStateFlow(false),
        )
    }

    data.run {
        Box(modifier = Modifier.fillMaxSize()) {
            // ── Blurred album art background ──────────────────────────────────
            AsyncImage(
                song.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
                    .graphicsLayer { alpha = 0.55f },
            )
            // Dark scrim over the blur
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )

            // ── Actual content ────────────────────────────────────────────────
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val orientation = ScreenOrientation.fromConstraints(this@BoxWithConstraints)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    topBar = {
                        if (orientation.isPortrait) {
                            NowPlayingAppBar(context, data, states)
                        }
                    },
                    content = { contentPadding ->
                        Box(modifier = Modifier.padding(contentPadding)) {
                            when (orientation) {
                                ScreenOrientation.PORTRAIT -> Column(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(bottom = 20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        NowPlayingBodyCover(context, data, states, orientation)
                                    }
                                    Column {
                                        NowPlayingBodyContent(context, data, states)
                                    }
                                }

                                ScreenOrientation.LANDSCAPE -> Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(top = 12.dp, bottom = 20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        NowPlayingBodyCover(context, data, states, orientation)
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        Column {
                                            NowPlayingLandscapeAppBar(context, data, states)
                                            Box(modifier = Modifier.weight(1f))
                                            NowPlayingBodyContent(context, data, states)
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
