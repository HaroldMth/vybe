package io.github.zyrouge.symphony.services.history

import android.content.Context
import io.github.zyrouge.symphony.Symphony
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

    fun addPlayed(songId: String) {
        val updated = (listOf(songId) + _recentlyPlayed.value.filterNot { it == songId })
            .take(MAX_PLAYED)
        _recentlyPlayed.value = updated
        saveList(KEY_PLAYED, updated)
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
        private const val MAX_SEARCHES = 10
        private const val MAX_PLAYED = 50
    }
}
