package io.github.zyrouge.symphony.utils

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.util.Collections

/**
 * Warms Coil's disk cache for artwork we're about to show, so covers are
 * already on disk (instant) by the time a list/rail/player scrolls them into
 * view, instead of each one starting its download only when it appears. That
 * matters most on slow connections and right after the backend cold-starts.
 *
 * Only the disk cache is warmed (memory policy disabled) so prefetching a
 * couple dozen covers doesn't evict what's currently on screen. Each URL is
 * requested at most once per process; the set is bounded so it can't grow
 * forever in a long session.
 */
object ImagePrefetcher {
    private const val MAX_REMEMBERED = 2000

    private val requested: MutableSet<String> = Collections.synchronizedSet(
        object : LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                val added = super.add(element)
                if (added && size > MAX_REMEMBERED) {
                    val it = iterator()
                    it.next()
                    it.remove()
                }
                return added
            }
        }
    )

    fun prefetch(context: Context, urls: Collection<String?>, sizePx: Int = 300) {
        val loader = context.imageLoader
        urls.forEach { url ->
            if (url.isNullOrBlank()) return@forEach
            if (!url.startsWith("http://") && !url.startsWith("https://")) return@forEach
            if (!requested.add(url)) return@forEach
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(sizePx)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        }
    }
}
