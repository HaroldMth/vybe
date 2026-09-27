package io.github.zyrouge.symphony.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.NowPlayingLyricsLayout

/**
 * The lyrics gear menu: layout toggle (over-artwork vs separate page) + keep
 * screen awake. Shared by the "over artwork" lyrics overlay and the
 * standalone Lyrics tab so both stay in sync and neither ships a dead
 * placeholder icon instead of a real control.
 */
@Composable
fun LyricsSettingsButton(
    context: ViewContext,
    tint: Color = LocalContentColor.current,
) {
    val keepScreenAwake by context.symphony.settings.lyricsKeepScreenAwake.flow.collectAsState()
    val lyricsLayout by context.symphony.settings.nowPlayingLyricsLayout.flow.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showMenu = true },
        modifier = Modifier.size(28.dp),
    ) {
        Icon(
            Icons.Filled.Settings,
            null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Outlined.Article, null)
                },
                text = {
                    Text(
                        when (lyricsLayout) {
                            NowPlayingLyricsLayout.ReplaceArtwork -> "Layout: Over artwork"
                            NowPlayingLyricsLayout.SeparatePage -> "Layout: Separate page"
                        }
                    )
                },
                onClick = {
                    val next = when (lyricsLayout) {
                        NowPlayingLyricsLayout.ReplaceArtwork ->
                            NowPlayingLyricsLayout.SeparatePage
                        NowPlayingLyricsLayout.SeparatePage ->
                            NowPlayingLyricsLayout.ReplaceArtwork
                    }
                    context.symphony.settings.nowPlayingLyricsLayout.setValue(next)
                }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.Nightlight, null)
                },
                text = {
                    Text("Keep screen awake")
                },
                trailingIcon = {
                    Switch(
                        checked = keepScreenAwake,
                        onCheckedChange = {
                            context.symphony.settings.lyricsKeepScreenAwake.setValue(it)
                        },
                        colors = SwitchDefaults.colors(),
                    )
                },
                onClick = {
                    context.symphony.settings.lyricsKeepScreenAwake.setValue(!keepScreenAwake)
                }
            )
        }
    }
}
