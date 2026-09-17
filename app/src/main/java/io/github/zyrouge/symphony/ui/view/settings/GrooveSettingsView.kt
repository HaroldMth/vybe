package io.github.zyrouge.symphony.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.components.AdaptiveSnackbar
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.settings.ConsiderContributingTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsOptionTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSideHeading
import io.github.zyrouge.symphony.ui.components.settings.SettingsSimpleTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSwitchTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsTextInputTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.ImagePreserver
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class GrooveSettingsViewRoute(val initialElement: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun GrooveSettingsView(context: ViewContext, route: GrooveSettingsViewRoute) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val artworkQuality by context.symphony.settings.artworkQuality.flow.collectAsState()
    val caseSensitiveSorting by context.symphony.settings.caseSensitiveSorting.flow.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState) {
                AdaptiveSnackbar(it)
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${context.symphony.t.Settings} - ${context.symphony.t.Groove}")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            context.navController.popBackStack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButtonPlaceholder()
                },
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    ConsiderContributingTile(context)
                    SettingsSideHeading("Vybe Backend Config")
                    val apiBaseUrl by context.symphony.settings.apiBaseUrl.flow.collectAsState()
                    SettingsTextInputTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.Storage, null)
                        },
                        title = {
                            Text("Vybe API Base URL")
                        },
                        value = apiBaseUrl ?: "http://192.168.0.142:4000/api",
                        onReset = {
                            context.symphony.settings.apiBaseUrl.setValue(null)
                        },
                        onChange = { value ->
                            context.symphony.settings.apiBaseUrl.setValue(value.trim())
                        }
                    )
                    HorizontalDivider()
                    SettingsSideHeading(context.symphony.t.Groove)
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Image, null)
                        },
                        title = {
                            Text(context.symphony.t.ArtworkQuality)
                        },
                        value = artworkQuality,
                        values = ImagePreserver.Quality.entries
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            context.symphony.settings.artworkQuality.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.TextFields, null)
                        },
                        title = {
                            Text(context.symphony.t.CaseSensitiveSorting)
                        },
                        value = caseSensitiveSorting,
                        onChange = { value ->
                            context.symphony.settings.caseSensitiveSorting.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSimpleTile(
                        icon = {
                            Icon(Icons.Filled.Storage, null)
                        },
                        title = {
                            Text(context.symphony.t.ClearSongCache)
                        },
                        onClick = {
                            refreshMediaLibrary(context.symphony, true)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.symphony.t.SongCacheCleared,
                                    withDismissAction = true,
                                )
                            }
                        }
                    )
                }
            }
        }
    )
}

fun ImagePreserver.Quality.label(context: ViewContext) = when (this) {
    ImagePreserver.Quality.Low -> context.symphony.t.Low
    ImagePreserver.Quality.Medium -> context.symphony.t.Medium
    ImagePreserver.Quality.High -> context.symphony.t.High
    ImagePreserver.Quality.Loseless -> context.symphony.t.Loseless
}

private fun refreshMediaLibrary(symphony: Symphony, clearCache: Boolean = false) {
    symphony.radio.stop()
    symphony.groove.coroutineScope.launch {
        val options = Groove.FetchOptions(
            resetInMemoryCache = true,
            resetPersistentCache = clearCache,
        )
        symphony.groove.fetch(options)
    }
}
