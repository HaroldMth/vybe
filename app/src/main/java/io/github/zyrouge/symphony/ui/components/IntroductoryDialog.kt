package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.helpers.ViewContext

/**
 * First-launch dialog. Used to be Symphony's "opt into update checks" prompt
 * (with two toggles); Vybe doesn't need that decision made on first open, so
 * this is now just a plain welcome message.
 */
@Composable
fun IntroductoryDialog(
    context: ViewContext,
    onDismissRequest: () -> Unit,
) {
    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("\uD83D\uDC4B Welcome to Vybe")
        },
        content = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "This is a beta build \u2014 things may still be rough " +
                        "around the edges. Play your own library, stream and " +
                        "download from the cloud catalog, and let us know " +
                        "what breaks.",
                    modifier = Modifier.padding(16.dp, 12.dp),
                )
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text("Got it")
            }
        }
    )
}
