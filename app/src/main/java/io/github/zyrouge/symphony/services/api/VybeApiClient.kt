package io.github.zyrouge.symphony.services.api

import io.github.zyrouge.symphony.Symphony
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class VybeApiClient(private val symphony: Symphony) {
    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @PublishedApi
    internal val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getBaseUrl(): String {
        val configured = symphony.settings.apiBaseUrl.value?.trim()?.removeSuffix("/")
        if (!configured.isNullOrEmpty()) {
            return configured
        }
        return "http://192.168.8.10:4000/api"
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

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.error("VybeApiClient", "HTTP Error ${response.code} for $url")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val env = json.decodeFromString<VybeResponse<T>>(bodyString)
                if (env.success) env.data else null
            }
        } catch (err: Exception) {
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

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.error("VybeApiClient", "HTTP Error ${response.code} for $url")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val env = json.decodeFromString<VybeResponse<T>>(bodyString)
                if (env.success) env.data else null
            }
        } catch (err: Exception) {
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

    suspend fun search(query: String): VybeSearchData? {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return fetch("search?q=$encoded")
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
}
