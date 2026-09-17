package io.github.zyrouge.symphony.services.api

import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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
        return "http://192.168.0.142:4000/api"
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

    suspend fun getHome(): VybeHomeData? = fetch("home")

    suspend fun getCharts(): VybeChartsData? = fetch("charts")

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
