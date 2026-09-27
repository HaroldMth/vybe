package io.github.zyrouge.symphony

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import io.github.zyrouge.symphony.utils.HttpClient

/**
 * Registered as the app's `android:name` in the manifest.
 *
 * Without this, every `AsyncImage` (song art, album/artist/playlist covers,
 * the now-playing background, etc.) falls back to Coil's zero-config default
 * ImageLoader: its own private OkHttpClient (no connection-pool sharing with
 * the rest of the app), a small default disk cache, and no crossfade. On a
 * slow/flaky connection that means every screen re-opens a fresh connection
 * per image and re-downloads art that was already fetched minutes ago.
 *
 * This shares the app's single OkHttpClient (same connection pool as the
 * Vybe API/streaming requests) and gives images a real, sized disk + memory
 * cache so artwork loads instantly from cache on revisits and slow networks
 * don't compound: only the *first* view of a given cover pays the network
 * cost.
 */
class VybeApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(HttpClient)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024) // 256MB
                .build()
        }
        .crossfade(true)
        .apply {
            if (BuildConfig.DEBUG) {
                logger(DebugLogger())
            }
        }
        .build()
}
