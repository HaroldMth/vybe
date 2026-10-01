package io.github.zyrouge.symphony.services.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.api.VybeVideoStreamData
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.utils.Logger
import io.github.zyrouge.symphony.utils.SongJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

enum class DownloadStatus { NONE, QUEUED, DOWNLOADING, COMPLETED, FAILED }

data class DownloadState(
    val status: DownloadStatus = DownloadStatus.NONE,
    val progress: Float = 0f,
)

/**
 * Downloads a (remote/streamed) song to local storage by re-requesting the same
 * stream URL the player already uses, this time writing the response body to a
 * file instead of ExoPlayer, with byte-level progress reported along the way.
 *
 * Saved to the public Music/Vybe folder via MediaStore on API 29+, and directly
 * to the Music/Vybe directory on older devices (which need WRITE_EXTERNAL_STORAGE).
 * Completed downloads are persisted in SharedPreferences (songId -> content/file
 * URI) so they survive app restarts, and playback (Radio.kt) prefers this local
 * URI over the network whenever one exists.
 */
class DownloadManager(private val symphony: Symphony) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val prefs by lazy {
        symphony.applicationContext.getSharedPreferences("vybe_downloads", Context.MODE_PRIVATE)
    }

    // songId -> content/file URI string, for completed AUDIO downloads only.
    private val localUris = ConcurrentHashMap<String, String>()

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    // Video downloads are tracked completely separately from the audio ones: the same
    // song can have either, both or neither saved, and each has its own status/icon.
    private val videoPrefs by lazy {
        symphony.applicationContext
            .getSharedPreferences("vybe_video_downloads", Context.MODE_PRIVATE)
    }
    private val videoLocalUris = ConcurrentHashMap<String, String>()
    private val _videoStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val videoStates: StateFlow<Map<String, DownloadState>> = _videoStates.asStateFlow()

    init {
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(META_PREFIX) || value !is String || value.isBlank()) {
                return@forEach
            }
            localUris[key] = value
        }
        videoPrefs.all.forEach { (key, value) ->
            if (value is String && value.isNotBlank()) {
                videoLocalUris[key] = value
            }
        }
        _states.value = localUris.keys.associateWith {
            DownloadState(DownloadStatus.COMPLETED, 1f)
        }
        _videoStates.value = videoLocalUris.keys.associateWith {
            DownloadState(DownloadStatus.COMPLETED, 1f)
        }
        // Downloaded songs live only as a songId->URI pair on disk; the actual
        // Song (title/artist/artwork/etc.) normally comes from VybeCatalog's
        // in-memory cache, which is only populated by whatever the home feed /
        // charts / search happen to have fetched this session. On a cold start
        // that cache is empty, so without this the Downloads screen silently
        // drops every entry it can't resolve a Song for. Restore the snapshot
        // taken at download time instead of depending on that cache.
        rehydrate()
    }

    /**
     * Re-registers every downloaded song's snapshot into the groove caches.
     * Runs at startup and again after each library (re)scan/reset, since a
     * reset clears those in-memory caches and nothing else would put them back.
     */
    fun rehydrate() {
        localUris.keys.forEach { songId ->
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

    fun isDownloaded(songId: String) = localUris.containsKey(songId)

    fun stateFor(songId: String): DownloadState = states.value[songId] ?: DownloadState()

    /** Playback should call this instead of `song.uri` directly. */
    fun resolvePlaybackUri(song: Song): Uri =
        localUris[song.id]?.toUri()
            ?: symphony.streamCache.resolve(song)
            ?: song.uri

    fun download(song: Song) {
        if (isDownloaded(song.id)) return
        val current = _states.value[song.id]?.status
        if (current == DownloadStatus.QUEUED || current == DownloadStatus.DOWNLOADING) return

        // Snapshot the metadata up front (not just on success) so a resumed or
        // retried download still has something to show; it's harmless to keep
        // around and gets cleaned up by removeDownload regardless of outcome.
        writeSongSnapshot(song)
        setState(song.id, DownloadState(DownloadStatus.QUEUED, 0f))
        coroutineScope.launch {
            try {
                setState(song.id, DownloadState(DownloadStatus.DOWNLOADING, 0f))
                val uri = performDownload(song)
                localUris[song.id] = uri.toString()
                prefs.edit().putString(song.id, uri.toString()).apply()
                setState(song.id, DownloadState(DownloadStatus.COMPLETED, 1f))
            } catch (err: Exception) {
                Logger.error("DownloadManager", "download failed for ${song.id}", err)
                setState(song.id, DownloadState(DownloadStatus.FAILED, 0f))
            }
        }
    }

    fun removeDownload(songId: String) {
        val uriStr = localUris.remove(songId) ?: return
        prefs.edit().remove(songId).remove(META_PREFIX + songId).apply()
        setState(songId, DownloadState(DownloadStatus.NONE, 0f))
        try {
            val uri = uriStr.toUri()
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                symphony.applicationContext.contentResolver.delete(uri, null, null)
            }
        } catch (err: Exception) {
            Logger.error("DownloadManager", "failed deleting download for $songId", err)
        }
    }

    // ── Video downloads ──────────────────────────────────────────────────────────

    fun isVideoDownloaded(songId: String) = videoLocalUris.containsKey(songId)

    fun videoStateFor(songId: String): DownloadState = videoStates.value[songId] ?: DownloadState()

    /** Playback should call this before resolving a network video stream. */
    fun videoUriFor(songId: String): Uri? = videoLocalUris[songId]?.toUri()

    /**
     * Downloads the video copy of a song, saving it to Movies/Vybe. The stream is reused
     * from [stream] when the player has already resolved one (so opening the chooser while
     * a video is playing doesn't trigger a second lookup), otherwise it is fetched here.
     */
    fun downloadVideo(song: Song, stream: VybeVideoStreamData? = null) {
        if (isVideoDownloaded(song.id)) return
        val current = _videoStates.value[song.id]?.status
        if (current == DownloadStatus.QUEUED || current == DownloadStatus.DOWNLOADING) return

        setVideoState(song.id, DownloadState(DownloadStatus.QUEUED, 0f))
        coroutineScope.launch {
            try {
                setVideoState(song.id, DownloadState(DownloadStatus.DOWNLOADING, 0f))
                val resolved = stream ?: symphony.vybeApi.getVideoStream(
                    input = song.title,
                    title = song.title,
                    artist = song.artists.firstOrNull(),
                    durationSec = song.duration / 1000,
                )
                // download_url is the direct media file; url can be an adaptive manifest
                // (m3u8/mpd) that is not itself a saveable video, so prefer download_url.
                val url = resolved?.download_url?.takeIf { it.isNotBlank() }
                    ?: resolved?.url?.takeIf { it.isNotBlank() }
                    ?: throw IOException("no video stream found for ${song.id}")
                val uri = performVideoDownload(song, url)
                videoLocalUris[song.id] = uri.toString()
                videoPrefs.edit().putString(song.id, uri.toString()).apply()
                setVideoState(song.id, DownloadState(DownloadStatus.COMPLETED, 1f))
            } catch (err: Exception) {
                Logger.error("DownloadManager", "video download failed for ${song.id}", err)
                setVideoState(song.id, DownloadState(DownloadStatus.FAILED, 0f))
            }
        }
    }

    fun removeVideoDownload(songId: String) {
        val uriStr = videoLocalUris.remove(songId) ?: return
        videoPrefs.edit().remove(songId).apply()
        setVideoState(songId, DownloadState(DownloadStatus.NONE, 0f))
        try {
            val uri = uriStr.toUri()
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                symphony.applicationContext.contentResolver.delete(uri, null, null)
            }
        } catch (err: Exception) {
            Logger.error("DownloadManager", "failed deleting video download for $songId", err)
        }
    }

    private fun performVideoDownload(song: Song, url: String): Uri {
        val context = symphony.applicationContext
        val request = Request.Builder().url(url).build()
        val response = symphony.vybeApi.httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} while downloading video ${song.id}")
        }
        val body = response.body ?: throw IOException("empty video response body for ${song.id}")
        val contentLength = body.contentLength()
        val fileName = sanitizeVideoFileName(song, url)

        var targetUri: Uri? = null
        try {
            val output: OutputStream
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, videoMimeType(url))
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Vybe")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val collection =
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val inserted = context.contentResolver.insert(collection, values)
                    ?: throw IOException("failed to create MediaStore entry for video ${song.id}")
                targetUri = inserted
                output = context.contentResolver.openOutputStream(inserted)
                    ?: throw IOException("failed to open output stream for video ${song.id}")
            } else {
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "Vybe",
                )
                if (!moviesDir.exists()) moviesDir.mkdirs()
                val file = File(moviesDir, fileName)
                targetUri = file.toUri()
                output = FileOutputStream(file)
            }

            body.byteStream().use { input ->
                output.use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesCopied = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        out.write(buffer, 0, read)
                        bytesCopied += read
                        if (contentLength > 0) {
                            val progress = (bytesCopied.toFloat() / contentLength).coerceIn(0f, 1f)
                            setVideoState(
                                song.id,
                                DownloadState(DownloadStatus.DOWNLOADING, progress),
                            )
                        }
                        read = input.read(buffer)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                context.contentResolver.update(targetUri, values, null, null)
            }

            return targetUri
        } catch (err: Exception) {
            targetUri?.let {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && it.scheme != "file") {
                        context.contentResolver.delete(it, null, null)
                    } else {
                        it.path?.let { path -> File(path).delete() }
                    }
                } catch (_: Exception) {
                }
            }
            throw err
        }
    }

    private fun sanitizeVideoFileName(song: Song, url: String): String {
        val artist = song.artists.firstOrNull() ?: "Unknown"
        val raw = "$artist - ${song.title}"
        val cleaned = raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { song.id }
        val extension = when {
            "m3u8" in url -> "m3u8"
            "mpd" in url -> "mpd"
            else -> "mp4"
        }
        return "$cleaned.$extension"
    }

    private fun videoMimeType(url: String): String = when {
        "m3u8" in url -> "application/x-mpegURL"
        "mpd" in url -> "application/dash+xml"
        else -> "video/mp4"
    }

    private fun writeSongSnapshot(song: Song) {
        try {
            prefs.edit().putString(META_PREFIX + song.id, SongJson.encode(song)).apply()
        } catch (err: Exception) {
            Logger.error("DownloadManager", "failed snapshotting metadata for ${song.id}", err)
        }
    }

    private fun readSongSnapshot(songId: String): Song? {
        val raw = prefs.getString(META_PREFIX + songId, null) ?: return null
        return try {
            SongJson.decode(raw)
        } catch (err: Exception) {
            Logger.error("DownloadManager", "failed restoring metadata for $songId", err)
            null
        }
    }

    private fun setState(songId: String, state: DownloadState) {
        _states.update { it + (songId to state) }
    }

    private fun setVideoState(songId: String, state: DownloadState) {
        _videoStates.update { it + (songId to state) }
    }

    private fun performDownload(song: Song): Uri {
        val context = symphony.applicationContext
        val streamUrl = song.path
        val request = Request.Builder().url(streamUrl).build()
        val response = symphony.vybeApi.httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} while downloading ${song.id}")
        }
        val body = response.body ?: throw IOException("empty response body for ${song.id}")
        val contentLength = body.contentLength()
        val fileName = sanitizeFileName(song)

        var targetUri: Uri? = null
        try {
            val output: OutputStream
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Vybe")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val inserted = context.contentResolver.insert(collection, values)
                    ?: throw IOException("failed to create MediaStore entry for ${song.id}")
                targetUri = inserted
                output = context.contentResolver.openOutputStream(inserted)
                    ?: throw IOException("failed to open output stream for ${song.id}")
            } else {
                val musicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Vybe",
                )
                if (!musicDir.exists()) musicDir.mkdirs()
                val file = File(musicDir, fileName)
                targetUri = file.toUri()
                output = FileOutputStream(file)
            }

            body.byteStream().use { input ->
                output.use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesCopied = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        out.write(buffer, 0, read)
                        bytesCopied += read
                        if (contentLength > 0) {
                            val progress = (bytesCopied.toFloat() / contentLength).coerceIn(0f, 1f)
                            setState(song.id, DownloadState(DownloadStatus.DOWNLOADING, progress))
                        }
                        read = input.read(buffer)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                context.contentResolver.update(targetUri, values, null, null)
            }

            return targetUri
        } catch (err: Exception) {
            targetUri?.let {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && it.scheme != "file") {
                        context.contentResolver.delete(it, null, null)
                    } else {
                        it.path?.let { path -> File(path).delete() }
                    }
                } catch (_: Exception) {
                }
            }
            throw err
        }
    }

    private fun sanitizeFileName(song: Song): String {
        val artist = song.artists.firstOrNull() ?: "Unknown"
        val raw = "$artist - ${song.title}"
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { song.id }
        return "$cleaned.m4a"
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
        private const val META_PREFIX = "meta_"
    }
}
