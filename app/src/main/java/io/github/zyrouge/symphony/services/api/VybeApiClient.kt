package io.github.zyrouge.symphony.services.api

import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.AppMeta
import io.github.zyrouge.symphony.utils.HttpClient
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import java.io.IOException
import okhttp3.Response
import okhttp3.Callback
import okhttp3.Call
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.RequestBody.Companion.toRequestBody

class VybeApiClient(private val symphony: Symphony) {
    private companion object {
        const val SEARCH_CACHE_TTL_MS = 5 * 60 * 1000L
    }

    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    // Shared with the rest of the app (downloads, Coil's image loader) so
    // connections to the same hosts get reused instead of every subsystem
    // opening its own. See utils/Http.kt for why the timeouts are generous.
    @PublishedApi
    internal val httpClient = HttpClient

    fun getBaseUrl(): String {
        val configured = symphony.settings.apiBaseUrl.value?.trim()?.removeSuffix("/")
        if (!configured.isNullOrEmpty()) {
            return configured
        }
        return AppMeta.defaultApiBaseUrl
    }

    /**
     * Enqueue instead of the blocking execute(): when the calling coroutine is
     * cancelled the in-flight HTTP call is aborted too, so a stale search stops
     * eating bandwidth on a slow connection the moment the user types on.
     */
    @PublishedApi
    internal suspend fun executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else response.close()
                }
            })
        }

    suspend inline fun <reified T> fetch(endpoint: String): T? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val url =
                if (endpoint.startsWith("http")) endpoint else "$baseUrl/$endpoint".replace(
                    Regex("(?<!:)/{2,}"),
                    "/"
                )
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            executeCancellable(request).use { response ->
                if (!response.isSuccessful) {
                    Logger.error("VybeApiClient", "HTTP Error ${response.code} for $url")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val env = json.decodeFromString<VybeResponse<T>>(bodyString)
                if (env.success) env.data else null
            }
        } catch (err: Exception) {
            // A cancelled search (user kept typing) must stay cancelled, not
            // turn into a "successful" null result that overwrites the list.
            currentCoroutineContext().ensureActive()
            Logger.error("VybeApiClient", "Fetch failed for $endpoint", err)
            null
        }
    }

    suspend inline fun <reified T> post(endpoint: String, body: JsonObject): T? = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl()}/$endpoint".replace(Regex("(?<!:)/{2,}"), "/")
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            executeCancellable(request).use { response ->
                if (!response.isSuccessful) {
                    Logger.error("VybeApiClient", "HTTP Error ${response.code} for $url")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val env = json.decodeFromString<VybeResponse<T>>(bodyString)
                if (env.success) env.data else null
            }
        } catch (err: Exception) {
            currentCoroutineContext().ensureActive()
            Logger.error("VybeApiClient", "Post failed for $endpoint", err)
            null
        }
    }

    /**
     * One-call blended For You page (POST /api/fyp). The server is stateless, so the app
     * sends its own history: [tracks]/[artists] are Deezer ids, most recent first, and
     * [played] are ids to hide.
     */
    suspend fun getFyp(
        tracks: List<String>,
        artists: List<String>,
        played: List<String>,
        country: String?,
        limit: Int = 30,
    ): VybeFypData? {
        val body = buildJsonObject {
            put("tracks", JsonArray(tracks.map { JsonPrimitive(it) }))
            put("artists", JsonArray(artists.map { JsonPrimitive(it) }))
            put("played", JsonArray(played.map { JsonPrimitive(it) }))
            if (!country.isNullOrBlank()) {
                put("country", country)
            }
            put("limit", limit)
        }
        return post("fyp", body)
    }

    /** New releases from the given artists, newest first (GET /api/radar). */
    suspend fun getRadar(artistIds: List<String>, days: Int = 60, limit: Int = 30): VybeRadarData? =
        fetch("radar?artists=${artistIds.take(30).joinToString(",")}&days=$days&limit=$limit")

    /** Songs in a tempo lane: chill, focus, workout or running (GET /api/discovery/bpm). */
    suspend fun getBpmLane(lane: String, limit: Int = 20): VybeBpmData? =
        fetch("discovery/bpm?lane=$lane&limit=$limit")

    suspend fun getHome(country: String? = null): VybeHomeData? =
        fetch(if (country.isNullOrBlank()) "home" else "home?country=$country")

    suspend fun getCharts(): VybeChartsData? = fetch("charts")

    /** "More like this" for a Deezer track id (GET /api/song/:id/related). */
    suspend fun getRelatedTracks(deezerId: String, limit: Int = 20): VybeRelatedData? =
        fetch("song/$deezerId/related?limit=$limit")

    /** Related artists for a Deezer artist id (GET /api/recommendations/artist/:id). */
    suspend fun getRelatedArtists(deezerArtistId: String, limit: Int = 12): VybeRelatedArtistsData? =
        fetch("recommendations/artist/$deezerArtistId?limit=$limit")

    /** Mood / tag songs, e.g. "chill" (GET /api/recommendations/tag/:tag). */
    suspend fun getTagSongs(tag: String, limit: Int = 20): VybeTagData? {
        val encoded = java.net.URLEncoder.encode(tag, "UTF-8").replace("+", "%20")
        return fetch("recommendations/tag/$encoded?limit=$limit")
    }

    // Remember recent searches so backspacing / retyping a query is instant
    // instead of another round trip.
    private val searchCache = object : LinkedHashMap<String, Pair<Long, VybeSearchData>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, VybeSearchData>>?) =
            size > 40
    }

    suspend fun search(query: String): VybeSearchData? {
        val key = query.trim().lowercase()
        synchronized(searchCache) { searchCache[key] }?.let { (at, data) ->
            if (System.currentTimeMillis() - at < SEARCH_CACHE_TTL_MS) return data
        }
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val data = fetch<VybeSearchData>("search?q=$encoded")
        if (data != null) {
            synchronized(searchCache) { searchCache[key] = System.currentTimeMillis() to data }
        }
        return data
    }

    suspend fun getSong(id: String): VybeTrack? = fetch("song/$id")

    suspend fun getArtist(id: String): VybeArtistDetailData? = fetch("artist/$id")

    suspend fun getAlbum(id: String): VybeAlbum? = fetch("album/$id")

    suspend fun getPlaylist(id: String): VybePlaylist? = fetch("playlist/$id")

    suspend fun getGenre(id: String): VybeGenreDetailData? = fetch("genre/$id")

    suspend fun getLyrics(
        query: String? = null,
        title: String? = null,
        artist: String? = null,
        duration: Long? = null,
    ): VybeLyricsData? {
        val builder = getBaseUrl().toHttpUrlOrNull()?.newBuilder()?.addPathSegment("lyrics")
            ?: return null
        if (!query.isNullOrBlank()) builder.addQueryParameter("q", query)
        if (!title.isNullOrBlank()) builder.addQueryParameter("title", title)
        if (!artist.isNullOrBlank()) builder.addQueryParameter("artist", artist)
        if (duration != null && duration > 0) builder.addQueryParameter("duration", duration.toString())
        return fetch(builder.build().toString())
    }

    fun buildAudioStreamUrl(
        deezerId: String?,
        title: String?,
        artist: String?,
        durationSec: Long? = null,
    ): String {
        val baseUrl = getBaseUrl()
        val builder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("stream")
            ?.addPathSegment("audio")
            ?: return "$baseUrl/stream/audio"
        val query = listOfNotNull(
            title?.takeIf { it.isNotBlank() },
            artist?.takeIf { it.isNotBlank() },
        ).joinToString(" ").ifBlank { deezerId?.takeIf { it.isNotBlank() } ?: "track" }
        builder.addQueryParameter("q", query)
        if (!deezerId.isNullOrBlank()) builder.addQueryParameter("deezerId", deezerId)
        if (!title.isNullOrBlank()) builder.addQueryParameter("title", title)
        if (!artist.isNullOrBlank()) builder.addQueryParameter("artist", artist)
        if (durationSec != null && durationSec > 0) {
            builder.addQueryParameter("duration", durationSec.toString())
        }
        return builder.build().toString()
    }

    suspend fun searchVideos(query: String, limit: Int = 10): List<VybeVideoItem>? {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return fetch("videos/search?q=$encoded&limit=$limit")
    }

    suspend fun getVideoStream(
        input: String,
        title: String? = null,
        artist: String? = null,
        durationSec: Long? = null,
    ): VybeVideoStreamData? {
        val builder = getBaseUrl().toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("videos")
            ?.addPathSegment("stream") ?: return null
        builder.addQueryParameter("url", input)
        if (!title.isNullOrBlank()) builder.addQueryParameter("title", title)
        if (!artist.isNullOrBlank()) builder.addQueryParameter("artist", artist)
        if (durationSec != null && durationSec > 0) builder.addQueryParameter("duration", durationSec.toString())
        return fetch(builder.build().toString())
    }
}
