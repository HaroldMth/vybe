package io.github.zyrouge.symphony.services.history

import android.content.Context
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.utils.Logger
import io.github.zyrouge.symphony.utils.SongJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Small persisted history of recent search terms and recently played songs,
 * shown on the Search tab's empty state (before you type/search anything).
 */
class HistoryManager(private val symphony: Symphony) {
    private val prefs by lazy {
        symphony.applicationContext.getSharedPreferences("vybe_history", Context.MODE_PRIVATE)
    }

    private val _recentSearches = MutableStateFlow(loadList(KEY_SEARCHES))
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow(loadList(KEY_PLAYED))
    val recentlyPlayed: StateFlow<List<String>> = _recentlyPlayed.asStateFlow()

    init {
        // Recently-played only ever stored songIds; resolving them to a Song
        // for display goes through groove.song.get(id), which is just an
        // in-memory cache populated by whatever the catalog happened to fetch
        // this session. On a cold start that's usually empty for anything
        // that isn't currently trending, so the "Recently played" row would
        // silently render nothing under its own header. Snapshot metadata
        // the same way downloads do, and restore it up front.
        _recentlyPlayed.value.forEach { songId ->
            readSongSnapshot(songId)?.let { song -> registerSong(song) }
        }
    }

    private fun registerSong(song: Song) {
        symphony.groove.albumArtist.onSong(song)
        symphony.groove.album.onSong(song)
        symphony.groove.artist.onSong(song)
        symphony.groove.genre.onSong(song)
        symphony.groove.song.onSong(song)
    }

    fun addSearch(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + _recentSearches.value.filterNot {
            it.equals(trimmed, ignoreCase = true)
        }).take(MAX_SEARCHES)
        _recentSearches.value = updated
        saveList(KEY_SEARCHES, updated)
    }

    fun removeSearch(term: String) {
        val updated = _recentSearches.value.filterNot { it == term }
        _recentSearches.value = updated
        saveList(KEY_SEARCHES, updated)
    }

    fun clearSearches() {
        _recentSearches.value = emptyList()
        saveList(KEY_SEARCHES, emptyList())
    }

    fun addPlayed(song: Song) {
        writeSongSnapshot(song)
        val merged = listOf(song.id) + _recentlyPlayed.value.filterNot { it == song.id }
        val updated = merged.take(MAX_PLAYED)
        val evicted = merged.drop(MAX_PLAYED)
        _recentlyPlayed.value = updated
        saveList(KEY_PLAYED, updated)
        evicted.forEach { prefs.edit().remove(META_PREFIX + it).apply() }
    }

    private fun writeSongSnapshot(song: Song) {
        try {
            prefs.edit().putString(META_PREFIX + song.id, SongJson.encode(song)).apply()
        } catch (err: Exception) {
            Logger.error("HistoryManager", "failed snapshotting metadata for ${song.id}", err)
        }
    }

    private fun readSongSnapshot(songId: String): Song? {
        val raw = prefs.getString(META_PREFIX + songId, null) ?: return null
        return try {
            SongJson.decode(raw)
        } catch (err: Exception) {
            Logger.error("HistoryManager", "failed restoring metadata for $songId", err)
            null
        }
    }

    private fun loadList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveList(key: String, list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    companion object {
        private const val KEY_SEARCHES = "recent_searches"
        private const val KEY_PLAYED = "recently_played"
        private const val META_PREFIX = "meta_"
        private const val MAX_SEARCHES = 10
        private const val MAX_PLAYED = 50
    }
}
