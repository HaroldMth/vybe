package io.github.zyrouge.symphony.services.video

/**
 * Video mode's few UI strings.
 *
 * These deliberately aren't in the i18n schema: phrasey generates every key as a
 * parameter of one constructor, and that class is already within a handful of keys of
 * the DEX limit on method arguments (255). Past it the generated serializer fails
 * bytecode verification and the app can't start.
 */
object VideoText {
    const val VIDEO_MODE = "Video mode"
    const val VIDEOS = "Videos"
    const val LOADING = "Finding the video..."
    const val RETRYING = "That link failed, trying another..."
    const val NOT_FOUND = "Couldn't find a video for this song"
    const val PLAYBACK_FAILED = "This video can't be played right now"
    const val TRY_AGAIN = "Try again"
    const val FILL = "Fill"
    const val FIT = "Fit"
    const val LYRICS = "Lyrics"
    const val UP_NEXT = "Up next"
}
