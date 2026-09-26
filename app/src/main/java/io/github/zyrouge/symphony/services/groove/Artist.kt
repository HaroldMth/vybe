package io.github.zyrouge.symphony.services.groove

import androidx.compose.runtime.Immutable
import io.github.zyrouge.symphony.Symphony

@Immutable
data class Artist(
    val name: String,
    var numberOfAlbums: Int,
    var numberOfTracks: Int,
    var bio: String? = null,
    var nbFan: Int? = null,
    var listenersCount: Long? = null,
    var country: String? = null,
    var formedYear: String? = null,
    var genres: List<String> = emptyList(),
    var apiId: String? = null,
) {
    val followersFormatted: String?
        get() {
            val count = nbFan?.toLong() ?: listenersCount ?: return null
            return when {
                count >= 1_000_000 -> "%.1fM followers".format(count / 1_000_000.0)
                count >= 1_000 -> "${count / 1_000}K followers"
                else -> "$count followers"
            }
        }

    val listenersFormatted: String?
        get() {
            val count = listenersCount ?: return null
            return when {
                count >= 1_000_000 -> "%.1fM monthly listeners".format(count / 1_000_000.0)
                count >= 1_000 -> "${count / 1_000}K monthly listeners"
                else -> "$count monthly listeners"
            }
        }

    fun createArtworkImageRequest(symphony: Symphony) =
        symphony.groove.artist.createArtworkImageRequest(name)

    fun getSongIds(symphony: Symphony) = symphony.groove.artist.getSongIds(name)
    fun getSortedSongIds(symphony: Symphony) = symphony.groove.song.sort(
        getSongIds(symphony),
        symphony.settings.lastUsedSongsSortBy.value,
        symphony.settings.lastUsedSongsSortReverse.value,
    )

    fun getAlbumIds(symphony: Symphony) = symphony.groove.artist.getAlbumIds(name)
}
