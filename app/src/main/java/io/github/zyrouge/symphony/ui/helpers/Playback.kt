package io.github.zyrouge.symphony.ui.helpers

import io.github.zyrouge.symphony.ui.view.NowPlayingViewRoute

/**
 * The one path every "user tapped a song" gesture goes through.
 *
 * It plays exactly that song and then opens Now Playing. Previously screens
 * queued the whole list the row was rendered from and seeked to it by index
 * (`playQueue(list, index = ...)`), which is brittle: the rendered list and
 * the live queue can disagree (sorting, filtering, duplicate ids), some
 * screens passed no index at all (so every tap played the first track), and
 * remote/catalog ids don't always resolve — any of which made a tap land on
 * the wrong song or do nothing. A single-song play lets RadioShorty build its
 * usual endless "related" queue from that exact song, so playback still
 * continues naturally.
 */
fun ViewContext.playSong(songId: String) {
    if (songId.isBlank()) return
    symphony.radio.shorty.playQueue(songId)
    navController.navigate(NowPlayingViewRoute) {
        launchSingleTop = true
    }
}
