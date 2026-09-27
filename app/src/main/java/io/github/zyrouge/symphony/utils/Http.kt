package io.github.zyrouge.symphony.utils

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The single shared OkHttpClient for the app. Used to be duplicated:
 * VybeApiClient built its own separate instance (own connection pool, own
 * thread pool) instead of reusing this one, so every API/search/home/charts
 * request and every image load opened fresh connections to the same hosts
 * instead of reusing one. Everything that talks to the network — API calls,
 * downloads, streaming, and Coil's image loader (see VybeApplication) —
 * should go through this one client so TLS/connection reuse actually works.
 *
 * connectTimeout is generous (25s) because the backend
 * (vybe-api27.onrender.com, see AppMeta) is a free-tier Render deployment
 * that spins down when idle; the first request after a period of inactivity
 * can take 20-40s to get a connection at all as the instance cold-starts.
 * A shorter timeout there reads as "the app is broken" on the very first
 * request of a session.
 */
val HttpClient = OkHttpClient.Builder()
    .cache(null)
    .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
    .connectTimeout(25, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
