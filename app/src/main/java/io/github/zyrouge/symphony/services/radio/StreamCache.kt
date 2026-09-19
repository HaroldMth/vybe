package io.github.zyrouge.symphony.services.radio

import android.net.Uri
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Small on-disk cache for streamed songs.
 *
 * The queue keeps one song of look-ahead: once the *current* song is fully buffered
 * (see [RadioPlayer.setOnFullyBufferedListener]) the *next* song is downloaded here in
 * full. When it becomes current, [resolve] hands the player the local file, so it starts
 * instantly, and the chain repeats for the song after it.
 *
 * Files are written to `<id>.part` and only renamed once complete, so [resolve] never
 * returns a half-downloaded file.
 */
class StreamCache(private val symphony: Symphony) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = ConcurrentHashMap<String, Job>()
    private val dir by lazy {
        File(symphony.applicationContext.cacheDir, DIR_NAME).apply { mkdirs() }
    }

    /** Called (on a background thread) with the song id once its file is complete. */
    @Volatile
    var onSongCached: ((String) -> Unit)? = null

    fun resolve(song: Song): Uri? {
        val file = fileFor(song.id)
        if (!file.isFile || file.length() <= 0L) {
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return Uri.fromFile(file)
    }

    fun isCached(songId: String) = fileFor(songId).let { it.isFile && it.length() > 0L }

    /**
     * Downloads [song] in the background. Only one look-ahead download runs at a time:
     * starting a new one cancels any other still in flight (e.g. the user skipped ahead).
     */
    fun prefetch(song: Song) {
        if (!isRemote(song.uri) || isCached(song.id) || jobs.containsKey(song.id)) {
            return
        }
        jobs.forEach { (id, job) ->
            if (id != song.id) {
                job.cancel()
            }
        }
        // Lazy start so the job is registered before it can possibly finish and remove itself.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                download(song)
                evictIfNeeded(keep = song.id)
                onSongCached?.invoke(song.id)
            } catch (err: CancellationException) {
                throw err
            } catch (err: Exception) {
                Logger.warn("StreamCache", "prefetch failed for ${song.id}", err)
            } finally {
                partFor(song.id).delete()
                jobs.remove(song.id)
            }
        }
        jobs[song.id] = job
        job.start()
    }

    private suspend fun download(song: Song) {
        val request = Request.Builder().url(song.uri.toString()).build()
        symphony.vybeApi.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while caching ${song.id}")
            }
            val body = response.body ?: throw IOException("empty body while caching ${song.id}")
            val part = partFor(song.id)
            body.byteStream().use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (part.length() <= 0L) {
                throw IOException("empty download for ${song.id}")
            }
            val target = fileFor(song.id)
            if (!part.renameTo(target)) {
                throw IOException("unable to finalise cache file for ${song.id}")
            }
        }
    }

    private fun evictIfNeeded(keep: String) {
        val files = dir.listFiles { f -> f.isFile && f.extension == EXTENSION }?.toMutableList()
            ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) {
            return
        }
        files.sortBy { it.lastModified() }
        for (file in files) {
            if (total <= MAX_BYTES) {
                break
            }
            if (file.nameWithoutExtension == safeName(keep)) {
                continue
            }
            total -= file.length()
            file.delete()
        }
    }

    private fun fileFor(songId: String) = File(dir, "${safeName(songId)}.$EXTENSION")
    private fun partFor(songId: String) = File(dir, "${safeName(songId)}.part")
    private fun safeName(songId: String) = songId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun isRemote(uri: Uri) = uri.scheme == "http" || uri.scheme == "https"

    companion object {
        private const val DIR_NAME = "vybe_stream"
        private const val EXTENSION = "audio"
        private const val BUFFER_SIZE = 16 * 1024
        private const val MAX_BYTES = 300L * 1024 * 1024
    }
}
