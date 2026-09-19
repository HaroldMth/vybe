package io.github.zyrouge.symphony.services

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.mutableStateMapOf
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class DownloadManager(private val symphony: Symphony) {
    private val downloadDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
        "Vybe"
    )

    private val _downloadProgress = mutableStateMapOf<String, Float>()
    val downloadProgress: Map<String, Float> get() = _downloadProgress

    private val _downloadedSongs = MutableStateFlow<Set<String>>(emptySet())
    val downloadedSongs: StateFlow<Set<String>> = _downloadedSongs

    init {
        // Create download directory if it doesn't exist
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        // Load existing downloaded songs
        loadDownloadedSongs()
    }

    private fun loadDownloadedSongs() {
        val downloaded = mutableSetOf<String>()
        downloadDir.listFiles()?.forEach { file ->
            // Extract song ID from filename (assuming format: songId.mp3)
            val songId = file.nameWithoutExtension
            downloaded.add(songId)
        }
        _downloadedSongs.value = downloaded
    }

    fun isDownloaded(songId: String): Boolean {
        return _downloadedSongs.value.contains(songId)
    }

    fun getDownloadProgress(songId: String): Float {
        return _downloadProgress[songId] ?: 0f
    }

    suspend fun downloadSong(context: Context, song: Song, onProgress: (Float) -> Unit = {}) {
        val songId = song.id
        if (isDownloaded(songId)) {
            onProgress(1f)
            return
        }

        _downloadProgress[songId] = 0f

        try {
            // This is a placeholder for actual download logic
            // In a real implementation, you would:
            // 1. Get the stream URL from the song object
            // 2. Download the file using OkHttp or similar
            // 3. Save to the download directory
            // 4. Update progress during download

            // Simulate download progress for now
            for (i in 0..100) {
                _downloadProgress[songId] = i / 100f
                onProgress(i / 100f)
                kotlinx.coroutines.delay(50)
            }

            // Mark as downloaded
            val newDownloaded = _downloadedSongs.value.toMutableSet()
            newDownloaded.add(songId)
            _downloadedSongs.value = newDownloaded

            _downloadProgress.remove(songId)
        } catch (e: Exception) {
            _downloadProgress.remove(songId)
            throw e
        }
    }

    fun deleteDownload(songId: String) {
        val file = File(downloadDir, "$songId.mp3")
        if (file.exists()) {
            file.delete()
        }
        val newDownloaded = _downloadedSongs.value.toMutableSet()
        newDownloaded.remove(songId)
        _downloadedSongs.value = newDownloaded
    }

    fun getDownloadedFile(songId: String): File? {
        val file = File(downloadDir, "$songId.mp3")
        return if (file.exists()) file else null
    }
}