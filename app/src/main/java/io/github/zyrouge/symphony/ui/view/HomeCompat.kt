package io.github.zyrouge.symphony.ui.view

/**
 * Legacy enum kept for backwards-compatibility with stored SharedPreferences.
 * The new 3-tab nav uses [VybePage]. These values are only referenced by
 * [io.github.zyrouge.symphony.services.Settings] for persisting/restoring a
 * now-unused preference key ("home_last_page" / "home_tabs").
 */
enum class HomePage {
    ForYou,
    Songs,
    Artists,
    Albums,
    AlbumArtists,
    Genres,
    Playlists,
    Browser,
    Folders,
    Tree,
}

fun HomePage.label(context: io.github.zyrouge.symphony.ui.helpers.ViewContext) = when (this) {
    HomePage.ForYou -> context.symphony.t.ForYou
    HomePage.Songs -> context.symphony.t.Songs
    HomePage.Artists -> context.symphony.t.Artists
    HomePage.Albums -> context.symphony.t.Albums
    HomePage.AlbumArtists -> context.symphony.t.AlbumArtists
    HomePage.Genres -> context.symphony.t.Genres
    HomePage.Playlists -> context.symphony.t.Playlists
    HomePage.Browser -> "Browser"
    HomePage.Folders -> "Folders"
    HomePage.Tree -> "Tree"
}

/** Legacy visibility setting — kept so Settings.kt compiles. */
enum class HomePageBottomBarLabelVisibility {
    ALWAYS_VISIBLE,
    VISIBLE_WHEN_ACTIVE,
    INVISIBLE,
}
