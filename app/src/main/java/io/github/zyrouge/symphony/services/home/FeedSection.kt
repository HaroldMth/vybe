package io.github.zyrouge.symphony.services.home

/**
 * One block of the home feed. Every id refers to something already ingested into
 * the groove repositories, so the UI only has to look it up.
 */
sealed interface FeedSection {
    val key: String
    val title: String

    data class Songs(
        override val key: String,
        override val title: String,
        val songIds: List<String>,
        val style: Style,
        /** Song whose artwork is shown next to the title ("Because you played ..."). */
        val leadSongId: String? = null,
    ) : FeedSection {
        enum class Style {
            /** Numbered cards, for real rankings (charts). */
            Ranked,

            /** Plain square cards. */
            Cards,

            /** Compact vertical list. */
            Rows,
        }
    }

    data class Albums(
        override val key: String,
        override val title: String,
        val albumIds: List<String>,
    ) : FeedSection

    data class Artists(
        override val key: String,
        override val title: String,
        val artistNames: List<String>,
    ) : FeedSection

    data class Playlists(
        override val key: String,
        override val title: String,
        val playlistIds: List<String>,
    ) : FeedSection

    data class Genres(
        override val key: String,
        override val title: String,
        val genreNames: List<String>,
    ) : FeedSection
}
