package io.github.zyrouge.symphony.ui.components
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
    // Fires the moment the layout choice changes, so switching sides is an
    // immediate, animated transition instead of a setting that only applies
    // next time the lyrics toggle is tapped separately.
    onLayoutChange: (NowPlayingLyricsLayout) -> Unit = {},
) {
    val keepScreenAwake by context.symphony.settings.lyricsKeepScreenAwake.flow.collectAsState()
    val lyricsLayout by context.symphony.settings.nowPlayingLyricsLayout.flow.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    // No explicit size here on purpose: IconButton's Material3 default is
    // 48dp, the minimum comfortable touch target. It used to be hard-capped
    // to 28dp, which — sitting right next to the source pill and the
    // "LYRICS" label — was genuinely fiddly to hit reliably.
    IconButton(onClick = { showMenu = true }) {
        Icon(
            Icons.Filled.Settings,
            null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            // A fixed comfortable minimum regardless of screen size/density,
            // rather than shrink-wrapping to content (which is how you get
            // menus that feel cramped on some devices and fine on others).
            modifier = Modifier.widthIn(min = 240.dp),
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
                    onLayoutChange(next)
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
                        // Disabled on purpose: this used to have its own
                        // onCheckedChange AND sit inside a row with its own
                        // onClick doing the same toggle. Tapping the switch
                        // fired both — the switch flipped it one way, then
                        // the row's click handler flipped it right back
                        // using a stale value from before recomposition. Net
                        // effect: tapping the switch directly did nothing (or
                        // flickered), which is exactly what "unresponsive"
                        // looks like. The row's onClick below is now the only
                        // thing that toggles it, so a tap anywhere on the
                        // item — including right on the switch — works the
                        // same single time.
                        onCheckedChange = null,
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
