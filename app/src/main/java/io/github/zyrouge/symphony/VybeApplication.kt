package io.github.zyrouge.symphony

import io.github.zyrouge.symphony.utils.DeezerImageInterceptor
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
    /**
     * Same connection pool/dispatcher as the shared client (newBuilder()
     * reuses them), but rewrites cache headers on *image* responses only.
     * Coil's disk cache honors HTTP caching headers, so if the artwork
     * host answers with no-store / no-cache / a tiny max-age, every cover
     * gets re-downloaded on every launch and every screen. Cover art at a
     * given URL doesn't change, so pin it for 30 days. Audio/API traffic
     * goes through the untouched shared client.
     */
    private val imageHttpClient by lazy {
        HttpClient.newBuilder()
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (!response.isSuccessful) return@addNetworkInterceptor response
                response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Expires")
                    .header("Cache-Control", "public, max-age=2592000")
                    .build()
            }
            .build()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(imageHttpClient)
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
        .components {
            add(DeezerImageInterceptor())
        }
        .crossfade(true)
        .apply {
            if (BuildConfig.DEBUG) {
                logger(DebugLogger())
            }
        }
        .build()
}
