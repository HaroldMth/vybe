package io.github.zyrouge.symphony.services.radio

import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Endless "related" queue.
 *
 * Clicking a single song turns the queue into a temporary playlist that keeps growing:
 * every time playback moves forward and fewer than [REFILL_THRESHOLD] songs remain, the
 * current song is used as the seed for `GET /api/song/:id/related` and the fresh results
 * are appended. The playlist therefore never ends until the user stops it or plays
 * something else.
 *
 * Only songs coming from the Vybe API (`vybe_<deezerId>`) can seed a feed.
 */
class RadioAutoplay(private val symphony: Symphony) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val extending = AtomicBoolean(false)
    private val seen = LinkedHashSet<String>()

    @Volatile
    var endless = false
        private set

    /** How many times the feed has been extended since it was started. */
    @Volatile
    var round = 0
        private set

    fun start(seedSongId: String) {
        synchronized(seen) {
            seen.clear()
            seen.add(seedSongId)
        }
        round = 0
        endless = deezerIdOf(seedSongId) != null
    }

    fun stop() {
        endless = false
        round = 0
        synchronized(seen) { seen.clear() }
    }

    /** Called by [Radio] whenever a new song starts playing. */
    fun onSongStarted() {
        if (!endless) {
            return
        }
        val queue = symphony.radio.queue
        trimPlayed()
        val remaining = queue.currentQueue.size - queue.currentSongIndex - 1
        if (remaining < REFILL_THRESHOLD) {
            extend()
        }
    }

    private fun extend() {
        if (!extending.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            try {
                val queue = symphony.radio.queue
                // Seed with the current song; if it yields nothing, fall back to earlier ones.
                val seedIds = queue.currentQueue.toList()
                    .take((queue.currentSongIndex + 1).coerceAtLeast(0))
                    .asReversed()
                    .take(SEED_FALLBACKS)
                for (seedId in seedIds) {
                    if (!endless) {
                        return@launch
                    }
                    val deezerId = deezerIdOf(seedId) ?: continue
                    val fresh = fetchFresh(deezerId)
                    if (fresh.isEmpty()) {
                        continue
                    }
                    withContext(Dispatchers.Main) {
                        if (endless) {
                            round++
                            queue.add(fresh)
                        }
                    }
                    return@launch
                }
            } catch (err: Exception) {
                Logger.warn("RadioAutoplay", "unable to extend the related feed", err)
            } finally {
                extending.set(false)
            }
        }
    }

    private suspend fun fetchFresh(deezerId: String): List<String> {
        val related = symphony.vybeApi.getRelatedTracks(deezerId, FETCH_LIMIT) ?: return emptyList()
        val songs = symphony.groove.catalog.ingestTracks(related.songs)

        val queued = symphony.radio.queue.currentQueue.toList()
        val artistCounts = HashMap<String, Int>()
        queued.forEach { id ->
            symphony.groove.song.get(id)?.artists?.firstOrNull()?.let {
                artistCounts[it] = (artistCounts[it] ?: 0) + 1
            }
        }

        val picked = mutableListOf<String>()
        synchronized(seen) {
            for (song in songs) {
                if (picked.size >= BATCH_SIZE) {
                    break
                }
                if (song.id in seen) {
                    continue
                }
                val artist = song.artists.firstOrNull()
                if (artist != null && (artistCounts[artist] ?: 0) >= MAX_PER_ARTIST) {
                    continue
                }
                seen.add(song.id)
                picked.add(song.id)
                if (artist != null) {
                    artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
                }
            }
        }
        return picked
    }

    /** Keeps the temporary playlist from growing without bound. */
    private fun trimPlayed() {
        val queue = symphony.radio.queue
        // Indices differ between original/current queue while shuffled, so leave it alone.
        if (queue.currentShuffleMode || queue.currentSongIndex <= MAX_PLAYED_BEHIND) {
            return
        }
        queue.remove((0 until TRIM_COUNT).toList())
    }

    private fun deezerIdOf(songId: String): String? =
        songId.takeIf { it.startsWith(VYBE_PREFIX) }?.removePrefix(VYBE_PREFIX)?.takeIf { it.isNotBlank() }

    companion object {
        private const val VYBE_PREFIX = "vybe_"
        private const val REFILL_THRESHOLD = 5
        private const val FETCH_LIMIT = 30
        private const val BATCH_SIZE = 15
        private const val MAX_PER_ARTIST = 3
        private const val SEED_FALLBACKS = 3
        private const val MAX_PLAYED_BEHIND = 40
        private const val TRIM_COUNT = 20
    }
}
