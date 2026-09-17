package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.components.IconTextBody
import io.github.zyrouge.symphony.ui.helpers.ViewContext

@Composable
fun NothingPlaying(context: ViewContext) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
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
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                NothingPlayingBody(context)
            }
        }
    )
}

@Composable
fun NothingPlayingBody(context: ViewContext) {
    IconTextBody(
        icon = { modifier ->
            Icon(
                Icons.Filled.Headphones,
                null,
                modifier = modifier
            )
        },
        content = {
            Text(context.symphony.t.NothingIsBeingPlayedRightNow)
        }
    )
}
