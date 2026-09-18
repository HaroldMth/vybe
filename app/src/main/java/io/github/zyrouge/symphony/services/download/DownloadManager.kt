package io.github.zyrouge.symphony.services.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.utils.Logger
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

    // songId -> content/file URI string, for completed downloads only.
    private val localUris = ConcurrentHashMap<String, String>()

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    init {
        prefs.all.forEach { (songId, uri) ->
            if (uri is String && uri.isNotBlank()) {
                localUris[songId] = uri
            }
        }
        _states.value = localUris.keys.associateWith {
            DownloadState(DownloadStatus.COMPLETED, 1f)
        }
    }

    fun isDownloaded(songId: String) = localUris.containsKey(songId)

    fun stateFor(songId: String): DownloadState = states.value[songId] ?: DownloadState()

    /** Playback should call this instead of `song.uri` directly. */
    fun resolvePlaybackUri(song: Song): Uri =
        localUris[song.id]?.toUri() ?: song.uri

    fun download(song: Song) {
        if (isDownloaded(song.id)) return
        val current = _states.value[song.id]?.status
        if (current == DownloadStatus.QUEUED || current == DownloadStatus.DOWNLOADING) return

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
        prefs.edit().remove(songId).apply()
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

    private fun setState(songId: String, state: DownloadState) {
        _states.update { it + (songId to state) }
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
    }
}
