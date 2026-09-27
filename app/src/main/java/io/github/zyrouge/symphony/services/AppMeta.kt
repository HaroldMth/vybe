package io.github.zyrouge.symphony.services

import io.github.zyrouge.symphony.BuildConfig
import io.github.zyrouge.symphony.utils.HttpClient
import io.github.zyrouge.symphony.utils.Logger
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

@Suppress("ConstPropertyName")
object AppMeta {
    const val appName = "Vybe"
    const val author = "HANS TECH"

    // Vybe's own repo/releases. Update this once the HaroldMth/vybe repo has
    // actual GitHub releases published; until then leave checkForUpdates off
    // by default (see Settings) so it doesn't silently 404 on startup.
    const val githubRepositoryOwner = "HaroldMth"
    const val githubRepositoryName = "vybe"
    const val githubRepositoryUrl =
        "https://github.com/$githubRepositoryOwner/$githubRepositoryName"

    // Vybe is a fork of Symphony — kept separate from githubRepositoryUrl
    // above so the update-checker (which used to point here, comparing
    // Vybe's own version string against Symphony's releases) never gets
    // pointed at the wrong repo again. Only referenced from the Credits page.
    const val symphonyRepositoryUrl = "https://github.com/zyrouge/symphony"

    const val version = "v${BuildConfig.VERSION_NAME}"
    var latestVersion: String? = null
    const val githubLatestReleaseUrl = "$githubRepositoryUrl/releases/latest"
    const val githubIssuesUrl = "$githubRepositoryUrl/issues"

    const val packageName = "xyz.hanstech.vybe"

    fun isNightlyBuild() = version.contains("-nightly")

    fun fetchLatestVersion() = when {
        isNightlyBuild() -> fetchLatestNightlyVersion()
        else -> fetchLatestStableVersion()
    }

    fun fetchLatestStableVersion(): String? {
        try {
            val latestReleaseUrl =
                "https://api.github.com/repos/$githubRepositoryOwner/$githubRepositoryName/releases/latest"
            val req = Request.Builder()
                .url(latestReleaseUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            val res = HttpClient.newCall(req).execute()
            val content = res.body?.string() ?: ""
            val json = JSONObject(content)
            val tagName = json.getString("tag_name")
            val draft = json.getBoolean("draft")
            if (!draft) {
                latestVersion = tagName
            }
        } catch (err: Exception) {
            Logger.warn("AppMeta", "version check failed: $err")
        }
        return latestVersion
    }

    fun fetchLatestNightlyVersion(): String? {
        try {
            val latestReleaseUrl =
                "https://api.github.com/repos/$githubRepositoryOwner/$githubRepositoryName/releases"
            val req = Request.Builder()
                .url(latestReleaseUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            val res = HttpClient.newCall(req).execute()
            val content = res.body?.string() ?: ""
            val json = JSONArray(content)
            for (i in 0 until json.length()) {
                val x = json.getJSONObject(i)
                val tagName = x.getString("tag_name")
                val prerelease = x.getBoolean("prerelease")
                if (prerelease) {
                    latestVersion = tagName
                    break
                }
            }
        } catch (err: Exception) {
            Logger.warn("AppMeta", "nightly version check failed: $err")
        }
        return latestVersion
    }
}
