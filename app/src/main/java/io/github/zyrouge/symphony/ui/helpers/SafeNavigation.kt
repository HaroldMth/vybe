package io.github.zyrouge.symphony.ui.helpers

import androidx.navigation.NavController
import io.github.zyrouge.symphony.ui.view.AlbumArtistViewRoute
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.ui.view.GenreViewRoute
import io.github.zyrouge.symphony.ui.view.PlaylistViewRoute

/**
 * Type-safe Navigation (2.8.x) crashes with
 * `IllegalStateException: Unexpected null value for non-nullable argument`
 * when a route's String argument is empty/blank. Names coming from song tags
 * or the API can be blank, so every entity route goes through this instead of
 * calling `navigate` directly. Blank targets are ignored.
 */
fun NavController.navigateSafe(route: ArtistViewRoute) {
    if (route.artistName.isNotBlank()) navigate(route)
}

fun NavController.navigateSafe(route: AlbumArtistViewRoute) {
    if (route.albumArtistName.isNotBlank()) navigate(route)
}

fun NavController.navigateSafe(route: GenreViewRoute) {
    if (route.genreName.isNotBlank()) navigate(route)
}

fun NavController.navigateSafe(route: AlbumViewRoute) {
    if (route.albumId.isNotBlank()) navigate(route)
}

fun NavController.navigateSafe(route: PlaylistViewRoute) {
    if (route.playlistId.isNotBlank()) navigate(route)
}
