package io.github.zyrouge.symphony.services

/**
 * The app's own release line, kept deliberately separate from the Android
 * `versionCode` / `versionName` that the store and the release tooling use.
 *
 * 1.0 is THE main release of Vybe. Every release after it is a fix-only release, so the
 * value stays small and human (`1.0` -> `1.0.1` -> `1.0.2` ...) instead of tracking the
 * build number. That is what lets the app compare the release it is running against the
 * release it last ran on, or against one a future release hands it.
 *
 * See `CHANGELOG.md` for the human-readable history.
 */
data class AppRelease(
    val major: Int,
    val minor: Int,
    val patch: Int = 0,
) : Comparable<AppRelease> {
    override fun compareTo(other: AppRelease): Int = compareValuesBy(
        this,
        other,
        { it.major },
        { it.minor },
        { it.patch },
    )

    /** `"1.0"` for the baseline, `"1.0.1"`, `"1.0.2"` ... for the fixes that follow it. */
    override fun toString(): String =
        if (patch == 0) "$major.$minor" else "$major.$minor.$patch"

    companion object {
        /** The main release. The changelog starts here. */
        val V1_0 = AppRelease(major = 1, minor = 0)

        /**
         * Parses `"1"`, `"1.0"`, `"1.0.1"` or a `v`-prefixed form. Anything malformed
         * yields null rather than a bogus release, so a corrupted stored value can never
         * be mistaken for an upgrade.
         */
        fun parse(text: String?): AppRelease? {
            val cleaned = text?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
            if (cleaned.isEmpty()) return null
            val parts = cleaned.split(".")
            if (parts.isEmpty() || parts.size > 3) return null
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            return AppRelease(
                major = numbers.getOrElse(0) { 0 },
                minor = numbers.getOrElse(1) { 0 },
                patch = numbers.getOrElse(2) { 0 },
            )
        }
    }
}
