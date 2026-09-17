package io.github.zyrouge.symphony.ui.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.components.IntroductoryDialog
import io.github.zyrouge.symphony.ui.components.NowPlayingBottomBar
import io.github.zyrouge.symphony.ui.helpers.ScaleTransition
import io.github.zyrouge.symphony.ui.helpers.SlideTransition
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.home.ForYouView
import io.github.zyrouge.symphony.ui.view.home.LibraryView
import kotlinx.serialization.Serializable
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.runtime.collectAsState

enum class VybePage(
    val label: String,
    val selectedIcon: @Composable () -> ImageVector,
    val unselectedIcon: @Composable () -> ImageVector,
) {
    ForYou(
        label = "For You",
        selectedIcon = { Icons.Filled.Home },
        unselectedIcon = { Icons.Outlined.Home },
    ),
    Search(
        label = "Search",
        selectedIcon = { Icons.Filled.Search },
        unselectedIcon = { Icons.Filled.Search },
    ),
    Library(
        label = "Library",
        selectedIcon = { Icons.Filled.LibraryMusic },
        unselectedIcon = { Icons.Outlined.LibraryMusic },
    ),
}

@Serializable
object HomeViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(context: ViewContext) {
    val readIntroductoryMessage by context.symphony.settings.readIntroductoryMessage.flow.collectAsState()
    var currentPage by rememberSaveable { mutableStateOf(VybePage.ForYou) }
    var showOptionsDropdown by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            when (currentPage) {
                VybePage.ForYou -> CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        // vybe wordmark
                        Text(
                            "vybe",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    actions = {
                        IconButton(onClick = { showOptionsDropdown = !showOptionsDropdown }) {
                            Icon(Icons.Filled.MoreVert, null)
                            DropdownMenu(
                                expanded = showOptionsDropdown,
                                onDismissRequest = { showOptionsDropdown = false },
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                    text = { Text(context.symphony.t.Rescan) },
                                    onClick = {
                                        showOptionsDropdown = false
                                        context.symphony.radio.stop()
                                        context.symphony.groove.fetch(
                                            Groove.FetchOptions(resetInMemoryCache = true)
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                    text = { Text(context.symphony.t.Settings) },
                                    onClick = {
                                        showOptionsDropdown = false
                                        context.navController.navigate(SettingsViewRoute())
                                    }
                                )
                            }
                        }
                    }
                )

                VybePage.Library -> CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        Text(
                            "Library",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    actions = {
                        IconButton(onClick = { showOptionsDropdown = !showOptionsDropdown }) {
                            Icon(Icons.Filled.MoreVert, null)
                            DropdownMenu(
                                expanded = showOptionsDropdown,
                                onDismissRequest = { showOptionsDropdown = false },
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                    text = { Text(context.symphony.t.Settings) },
                                    onClick = {
                                        showOptionsDropdown = false
                                        context.navController.navigate(SettingsViewRoute())
                                    }
                                )
                            }
                        }
                    }
                )

                else -> {} // Search has its own inline header
            }
        },
        content = { contentPadding ->
            AnimatedContent(
                label = "vybe-page-content",
                targetState = currentPage,
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
                transitionSpec = {
                    SlideTransition.slideUp.enterTransition()
                        .togetherWith(ScaleTransition.scaleDown.exitTransition())
                },
            ) { page ->
                when (page) {
                    VybePage.ForYou -> ForYouView(context)
                    VybePage.Search -> EmbeddedSearchView(context)
                    VybePage.Library -> LibraryView(context)
                }
            }
        },
        bottomBar = {
            Column {
                NowPlayingBottomBar(context, false)
                NavigationBar {
                    VybePage.entries.forEach { page ->
                        val isSelected = currentPage == page
                        NavigationBarItem(
                            selected = isSelected,
                            icon = {
                                Icon(
                                    if (isSelected) page.selectedIcon() else page.unselectedIcon(),
                                    page.label,
                                )
                            },
                            label = {
                                Text(
                                    page.label,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            onClick = { currentPage = page },
                        )
                    }
                }
            }
        }
    )

    if (!readIntroductoryMessage) {
        IntroductoryDialog(
            context,
            onDismissRequest = {
                context.symphony.settings.readIntroductoryMessage.setValue(true)
            },
        )
    }
}
