package io.github.zyrouge.symphony.services.groove.repositories

import android.net.Uri
import androidx.core.net.toUri
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.Assets
import io.github.zyrouge.symphony.ui.helpers.createHandyImageRequest
import io.github.zyrouge.symphony.utils.FuzzySearchOption
import io.github.zyrouge.symphony.utils.FuzzySearcher
import io.github.zyrouge.symphony.utils.KeyGenerator
import io.github.zyrouge.symphony.utils.Logger
import io.github.zyrouge.symphony.utils.SimpleFileSystem
import io.github.zyrouge.symphony.utils.SimplePath
import io.github.zyrouge.symphony.utils.joinToStringIfNotEmpty
import io.github.zyrouge.symphony.utils.withCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.doubleOrNull
import java.util.concurrent.ConcurrentHashMap

class SongRepository(private val symphony: Symphony) {
    enum class SortBy {
        CUSTOM,
        TITLE,
        ARTIST,
        ALBUM,
        DURATION,
        DATE_MODIFIED,
        COMPOSER,
        ALBUM_ARTIST,
        YEAR,
        FILENAME,
        TRACK_NUMBER,
    }

    private val cache = ConcurrentHashMap<String, Song>()
    internal val pathCache = ConcurrentHashMap<String, String>()
    internal val idGenerator = KeyGenerator.TimeIncremental()
    private val searcher = FuzzySearcher<String>(
        options = listOf(
            FuzzySearchOption({ v -> get(v)?.title?.let { compareString(it) } }, 3),
            FuzzySearchOption({ v -> get(v)?.filename?.let { compareString(it) } }, 2),
            FuzzySearchOption({ v -> get(v)?.artists?.let { compareCollection(it) } }),
            FuzzySearchOption({ v -> get(v)?.album?.let { compareString(it) } })
        )
    )

    val isUpdating get() = symphony.groove.exposer.isUpdating
    private val _all = MutableStateFlow<List<String>>(emptyList())
    val all = _all.asStateFlow()
    private val _count = MutableStateFlow(0)
    val count = _count.asStateFlow()
    private val _id = MutableStateFlow(System.currentTimeMillis())
    val id = _id.asStateFlow()
    var explorer = SimpleFileSystem.Folder()

    private fun emitCount() = _count.update { cache.size }

    private fun emitIds() = _id.update {
        System.currentTimeMillis()
    }

    internal fun onSong(song: Song) {
        cache[song.id] = song
        pathCache[song.path] = song.id
        pathCache[song.id] = song.id
        explorer.addChildFile(SimplePath(song.path)).data = song.id
        emitIds()
        _all.update {
            if (it.contains(song.id)) it else it + song.id
        }
        emitCount()
    }

    fun reset() {
        cache.clear()
        pathCache.clear()
        explorer = SimpleFileSystem.Folder()
        emitIds()
        _all.update {
            emptyList()
        }
        emitCount()
    }

    fun search(songIds: List<String>, terms: String, limit: Int = 7) = searcher
        .search(terms, songIds, maxLength = limit)

    fun sort(songIds: List<String>, by: SortBy, reverse: Boolean): List<String> {
        val sensitive = symphony.settings.caseSensitiveSorting.value
        val sorted = when (by) {
            SortBy.CUSTOM -> songIds
            SortBy.TITLE -> songIds.sortedBy { get(it)?.title?.withCase(sensitive) }
            SortBy.ARTIST -> songIds.sortedBy { get(it)?.artists?.joinToStringIfNotEmpty(sensitive) }
            SortBy.ALBUM -> songIds.sortedBy { get(it)?.album?.withCase(sensitive) }
            SortBy.DURATION -> songIds.sortedBy { get(it)?.duration }
            SortBy.DATE_MODIFIED -> songIds.sortedBy { get(it)?.dateModified }
            SortBy.COMPOSER -> songIds.sortedBy {
                get(it)?.composers?.joinToStringIfNotEmpty(sensitive)
            }

            SortBy.ALBUM_ARTIST -> songIds.sortedBy {
                get(it)?.albumArtists?.joinToStringIfNotEmpty(sensitive)
            }

            SortBy.YEAR -> songIds.sortedBy { get(it)?.year }
            SortBy.FILENAME -> songIds.sortedBy { get(it)?.filename?.withCase(sensitive) }
            SortBy.TRACK_NUMBER -> songIds.sortedWith(
                compareBy({ get(it)?.discNumber }, { get(it)?.trackNumber }),
            )
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun count() = cache.size
    fun ids() = cache.keys.toList()
    fun values() = cache.values.toList()

    fun get(id: String) = cache[id]
    fun get(ids: List<String>) = ids.mapNotNull { get(it) }

    fun getArtworkUri(songId: String): Uri = get(songId)?.coverFile?.let {
        if (it.startsWith("http://") || it.startsWith("https://")) {
            it.toUri()
        } else {
            symphony.database.artworkCache.get(it).toUri()
        }
    } ?: getDefaultArtworkUri()

    fun getDefaultArtworkUri() = Assets.getPlaceholderUri(symphony)

    fun createArtworkImageRequest(songId: String) = createHandyImageRequest(
        symphony.applicationContext,
        image = getArtworkUri(songId),
        fallback = Assets.getPlaceholderId(symphony),
    )

    fun addVybeTrack(track: io.github.zyrouge.symphony.services.api.VybeTrack): Song {
        return symphony.groove.catalog.ingestTrack(track)
    }

    fun addVybeTracks(tracks: List<io.github.zyrouge.symphony.services.api.VybeTrack>): List<Song> {
        return symphony.groove.catalog.ingestTracks(tracks)
    }

    suspend fun getLyrics(song: Song): String? {
        try {
            val cached = symphony.database.lyricsCache.get(song.id)
            if (cached != null) return cached

            val artist = song.artists.firstOrNull()
            val remoteLyrics = symphony.vybeApi.getLyrics(title = song.title, artist = artist)
            if (remoteLyrics?.lyrics != null) {
                val formatted = parseVybeLyricsToLrc(remoteLyrics)
                if (!formatted.isNullOrBlank()) {
                    symphony.database.lyricsCache.put(song.id, formatted)
                    return formatted
                }
            }

            val lrcPath = SimplePath(song.path).let {
                it.parent?.join(it.nameWithoutExtension + ".lrc")?.pathString
            }
            symphony.groove.exposer.uris[lrcPath]?.let { uri ->
                symphony.applicationContext.contentResolver.openInputStream(uri)?.use {
                    return String(it.readBytes())
                }
            }
        } catch (err: Exception) {
            Logger.error("LyricsRepository", "fetch lyrics failed", err)
        }
        return null
    }

    private fun parseVybeLyricsToLrc(data: io.github.zyrouge.symphony.services.api.VybeLyricsData): String? {
        val lyricsElement = data.lyrics ?: return null
        return when {
            data.type == "synced" && lyricsElement is kotlinx.serialization.json.JsonArray -> {
                buildString {
                    for (line in lyricsElement) {
                        if (line is kotlinx.serialization.json.JsonObject) {
                            // API returns startTime in milliseconds (see lyrics API docs)
                            val startTimeMs = line["startTime"]?.let {
                                (it as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull
                            } ?: 0.0
                            val text = line["text"]?.let {
                                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                            } ?: ""
                            val totalMs = startTimeMs.toLong()
                            val minutes = totalMs / 60000
                            val seconds = (totalMs % 60000) / 1000
                            val millis = totalMs % 1000
                            val timestamp = String.format(
                                java.util.Locale.US,
                                "[%02d:%02d.%03d]",
                                minutes,
                                seconds,
                                millis,
                            )
                            appendLine("$timestamp $text")
                        }
                    }
                }
            }
            lyricsElement is kotlinx.serialization.json.JsonPrimitive && lyricsElement.isString -> {
                lyricsElement.content
            }
            else -> null
        }
    }
}
