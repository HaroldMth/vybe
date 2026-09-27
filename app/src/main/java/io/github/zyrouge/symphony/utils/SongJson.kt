package io.github.zyrouge.symphony.utils

import androidx.core.net.toUri
import io.github.zyrouge.symphony.services.groove.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * Compact JSON snapshot of a [Song]'s display/playback fields, for stashing
 * outside the in-memory groove cache (SharedPrefs-backed download and
 * recently-played records, currently). Local library fields that only make
 * sense for on-device scanned files (trackNumber, size, bitrate, etc.) are
 * dropped since remote catalog songs don't have them populated anyway.
 */
object SongJson {
    fun encode(song: Song): String = JSONObject().apply {
        put("id", song.id)
        put("title", song.title)
        put("album", song.album)
        put("artists", JSONArray(song.artists.toList()))
        put("albumArtists", JSONArray(song.albumArtists.toList()))
        put("genres", JSONArray(song.genres.toList()))
        put("duration", song.duration)
        put("encoder", song.encoder)
        put("coverFile", song.coverFile)
        put("uri", song.uri.toString())
        put("path", song.path)
    }.toString()

    fun decode(raw: String): Song {
        val json = JSONObject(raw)
        fun stringSet(key: String) = buildSet {
            val arr = json.optJSONArray(key) ?: JSONArray()
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
        return Song(
            id = json.getString("id"),
            title = json.getString("title"),
            album = json.optString("album").takeIf { it.isNotEmpty() },
            artists = stringSet("artists"),
            composers = emptySet(),
            albumArtists = stringSet("albumArtists"),
            genres = stringSet("genres"),
            trackNumber = null,
            trackTotal = null,
            discNumber = null,
            discTotal = null,
            date = null,
            year = null,
            duration = json.getLong("duration"),
            bitrate = null,
            samplingRate = null,
            channels = null,
            encoder = json.optString("encoder").takeIf { it.isNotEmpty() },
            dateModified = System.currentTimeMillis(),
            size = 0L,
            coverFile = json.optString("coverFile").takeIf { it.isNotEmpty() },
            uri = json.getString("uri").toUri(),
            path = json.getString("path"),
        )
    }
}
