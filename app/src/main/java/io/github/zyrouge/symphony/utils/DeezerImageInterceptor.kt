package io.github.zyrouge.symphony.utils

import android.net.Uri
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.size.Dimension

/**
 * The API hands out Deezer CDN covers in four sizes (56 / 250 / 500 / 1000 px)
 * and the app used to always take the biggest (1000x1000, easily 100-200 KB) —
 * even for a 52dp list thumbnail. The URLs differ only in the "NxN" part, so
 * this rewrites every Deezer cover request to the smallest variant that still
 * looks sharp at the size the image is actually being drawn (Coil hands us the
 * resolved target size, so it works for every AsyncImage in the app without
 * touching call sites).
 *
 *   <= 72 px  -> 56   (tiny avatars)
 *   <= 400 px -> 250  (list rows, cards, mini player — the common case)
 *   otherwise -> 500  (album/player headers; never the 1000 px original)
 *
 * List rows and cards deliberately share the 250 variant so a cover downloaded
 * on one screen is a cache hit on the next.
 */
class DeezerImageInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = when (val data = request.data) {
            is String -> data
            is Uri -> data.toString()
            else -> null
        }
        if (url == null || !url.contains("dzcdn.net/images/")) {
            return chain.proceed(request)
        }
        val px = listOf(chain.size.width, chain.size.height)
            .mapNotNull { (it as? Dimension.Pixels)?.px }
            .maxOrNull()
            ?: return chain.proceed(request)
        val bucket = when {
            px <= 72 -> 56
            px <= 400 -> 250
            else -> 500
        }
        val rewritten = SIZE_REGEX.replaceFirst(url, "/${bucket}x${bucket}-")
        if (rewritten == url) {
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().data(rewritten).build())
    }

    private companion object {
        val SIZE_REGEX = Regex("/\\d{2,4}x\\d{2,4}-")
    }
}
