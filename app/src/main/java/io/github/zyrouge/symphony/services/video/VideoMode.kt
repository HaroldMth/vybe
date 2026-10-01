package io.github.zyrouge.symphony.services.video

import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.api.VybeVideoItem
import io.github.zyrouge.symphony.services.api.VybeVideoStreamData
import io.github.zyrouge.symphony.services.radio.Radio
import io.github.zyrouge.symphony.services.radio.RadioPlayer
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns everything about "video mode": the ExoPlayer, which video belongs to which song,
 * and the rules for what happens when the screen turns off or the song changes.
 *
 * It lives at service level (not inside a composable) so the video survives lyrics
 * toggles and rotation, and so its audio keeps going while the app is in the background.
 *
 * While the video is [Phase.Ready] it *owns the transport*: [Radio] routes isPlaying,
 * position, pause/resume/seek to it, so the seek bar, bottom controls, lock-screen
 * notification and sleep timer all control the video without knowing it exists.
 * The radio's own audio player is kept paused underneath and is never seeked, because
 * the audio and video versions of a song can differ.
 */
class VideoMode(private val symphony: Symphony) : Symphony.Hooks {
    enum class Phase { Off, Loading, Ready, Error }

    data class State(
        val phase: Phase = Phase.Off,
        /** Song id, or "video:<id>" for a video opened straight from search. */
        val key: String? = null,
        val standalone: VybeVideoItem? = null,
        val stream: VybeVideoStreamData? = null,
        val message: String? = null,
        /** Technical reason for an error, e.g. the ExoPlayer error code. */
        val detail: String? = null,
        /** True while re-fetching a link that just failed to play. */
        val retrying: Boolean = false,
    ) {
        val engaged get() = phase != Phase.Off
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    /** True once a frame has actually been drawn; the UI crossfades the poster away on it. */
    private val _hasFrame = MutableStateFlow(false)
    val hasFrame: StateFlow<Boolean> = _hasFrame.asStateFlow()

    /** Width / height of the decoded video, 0 while unknown. */
    private val _aspect = MutableStateFlow(0f)
    val aspect: StateFlow<Float> = _aspect.asStateFlow()

    /** true = crop to fill the square, false = fit with black bars. */
    private val _fillMode = MutableStateFlow(true)
    val fillMode: StateFlow<Boolean> = _fillMode.asStateFlow()

    private var player: ExoPlayer? = null
    private var loadJob: Job? = null
    private var tickerJob: Job? = null
    private var fadeJob: Job? = null
    private var prefetchJob: Job? = null

    private var autostartAfterLoad = true
    private var audioSuppressedForLoad = false
    private var audioWasPlaying = false
    private var retriedKey: String? = null
    private var triedAltUrl = false
    private var fallbackDuration = 0L

    // ExoPlayer may only be touched on the main thread, but Radio/RadioSession read
    // position and play state from other threads, so they get these cached copies.
    @Volatile
    private var cachedPosition = 0L

    @Volatile
    private var cachedDuration = 0L

    @Volatile
    private var cachedWantPlaying = false

    private val streamCache = object : LinkedHashMap<String, VybeVideoStreamData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VybeVideoStreamData>?) =
            size > STREAM_CACHE_SIZE
    }

    // ── Facts the Radio routes through ───────────────────────────────────────────────

    val ownsTransport get() = _state.value.phase == Phase.Ready

    /** While a video is loading or ready, the radio must not start its own audio. */
    val suppressesAudio
        get() = _state.value.phase.let { it == Phase.Loading || it == Phase.Ready }

    val isPlaying get() = ownsTransport && cachedWantPlaying

    val playbackPosition: RadioPlayer.PlaybackPosition?
        get() = when {
            !ownsTransport -> null
            else -> RadioPlayer.PlaybackPosition(
                played = cachedPosition,
                total = cachedDuration.takeIf { it > 0L } ?: fallbackDuration,
            )
        }

    // ── Hooks / lifecycle ────────────────────────────────────────────────────────────

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = handleForeground()
        override fun onStop(owner: LifecycleOwner) = handleBackground()
    }

    override fun onSymphonyReady() {
        onMain {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        }
    }

    override fun onSymphonyDestroy() {
        onMain { release() }
    }

    /** Screen off and "left the app" are the same thing here: the process is no longer STARTED. */
    private fun isAppVisible() =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun handleBackground() {
        val s = _state.value
        when (s.phase) {
            // Nothing to see yet and nobody looking: stay audio-only.
            Phase.Loading -> disengage(resumeAudio = true)
            Phase.Ready -> when {
                // No queue/notification to keep a standalone video alive, so just pause it.
                s.standalone != null -> pause()
                // The song's audio keeps playing; only stop decoding frames nobody can see.
                else -> setVideoTrackEnabled(false)
            }

            else -> Unit
        }
    }

    private fun handleForeground() {
        if (_state.value.phase == Phase.Ready) {
            setVideoTrackEnabled(true)
        }
    }

    // ── Entry points ─────────────────────────────────────────────────────────────────

    /** The YouTube button in the now-playing bar. */
    fun toggleForSong(songId: String) = onMain {
        when {
            _state.value.engaged -> disengage(resumeAudio = true)
            else -> loadFor(songId, standalone = null, autostart = true, fromSongChange = false)
        }
    }

    /** A video tapped in search results. It is not tied to any song in the queue. */
    fun playStandalone(item: VybeVideoItem) = onMain {
        val key = "video:${item.videoId}"
        val s = _state.value
        if (s.key == key && (s.phase == Phase.Loading || s.phase == Phase.Ready)) {
            return@onMain
        }
        audioWasPlaying = when {
            s.standalone != null -> audioWasPlaying
            s.phase == Phase.Ready -> cachedWantPlaying
            else -> symphony.radio.isAudioPlaying
        }
        loadFor(key, standalone = item, autostart = true, fromSongChange = false)
    }

    fun retry() = onMain {
        val s = _state.value
        val key = s.key
        if (s.phase == Phase.Error && key != null) {
            retriedKey = null
            loadFor(key, s.standalone, autostart = true, fromSongChange = false)
        }
    }

    fun exit(resumeAudio: Boolean = true) = onMain { disengage(resumeAudio) }

    /**
     * Called by [Radio.play] before it stages a song. This is what keeps the video in
     * step with the queue: visible -> load the new song's video, hidden -> audio only.
     */
    fun onSongChanging(songId: String, autostart: Boolean) = onMain {
        val s = _state.value
        if (!s.engaged) {
            return@onMain
        }
        when {
            !isAppVisible() || s.standalone != null -> disengage(resumeAudio = false)
            else -> loadFor(songId, standalone = null, autostart = autostart, fromSongChange = true)
        }
    }

    // ── Transport (called by Radio) ──────────────────────────────────────────────────

    fun play() = onMain {
        val p = player ?: return@onMain
        if (!ownsTransport) {
            return@onMain
        }
        val granted = symphony.radio.requestAudioFocus()
        if (symphony.settings.requireAudioFocus.value && !granted) {
            return@onMain
        }
        fadeJob?.cancel()
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0L)
        }
        when {
            symphony.settings.fadePlayback.value -> {
                p.volume = 0f
                fadeVolume(1f, FADE_MS)
            }

            else -> p.volume = 1f
        }
        p.playWhenReady = true
    }

    fun pause(fade: Boolean = false, onDone: () -> Unit = {}) = onMain {
        val p = player
        if (p == null || !ownsTransport || !p.playWhenReady) {
            onDone()
            return@onMain
        }
        fadeJob?.cancel()
        val finish = {
            p.playWhenReady = false
            p.volume = 1f
            symphony.radio.abandonAudioFocus()
            onDone()
        }
        when {
            fade || symphony.settings.fadePlayback.value -> fadeJob = scope.launch {
                fadeSteps(p, 0f, FADE_MS)
                finish()
            }

            else -> finish()
        }
    }

    fun seek(positionMs: Long) = onMain {
        val p = player ?: return@onMain
        if (!ownsTransport) {
            return@onMain
        }
        val total = cachedDuration.takeIf { it > 0L } ?: fallbackDuration
        p.seekTo(
            when {
                total > 0L -> positionMs.coerceIn(0L, total)
                else -> positionMs.coerceAtLeast(0L)
            }
        )
        refreshCache()
        dispatch(Radio.Events.Player.Seeked)
    }

    fun seekBy(deltaMs: Long) = seek(cachedPosition + deltaMs)

    fun duck() = onMain {
        if (ownsTransport) {
            fadeJob?.cancel()
            player?.volume = RadioPlayer.DUCK_VOLUME
        }
    }

    fun restoreVolume() = onMain {
        if (ownsTransport) {
            player?.volume = RadioPlayer.MAX_VOLUME
        }
    }

    /** Re-applies the radio's speed/pitch (set from the existing dialogs) to the video. */
    fun syncPlaybackParams() = onMain {
        player?.let { applyPlaybackParams(it) }
    }

    fun toggleFill() {
        _fillMode.value = !_fillMode.value
    }

    // ── Surface ──────────────────────────────────────────────────────────────────────

    fun attachTexture(view: TextureView) = onMain {
        ensurePlayer().setVideoTextureView(view)
    }

    fun detachTexture(view: TextureView) = onMain {
        player?.clearVideoTextureView(view)
        // Re-attaching renders a fresh first frame; the poster covers the gap until then.
        _hasFrame.value = false
    }

    // ── Loading ──────────────────────────────────────────────────────────────────────

    private fun loadFor(
        key: String,
        standalone: VybeVideoItem?,
        autostart: Boolean,
        fromSongChange: Boolean,
        isRetry: Boolean = false,
    ) {
        loadJob?.cancel()
        prefetchJob?.cancel()
        fadeJob?.cancel()
        stopTicker()
        player?.run {
            playWhenReady = false
            stop()
            clearMediaItems()
            volume = 1f
        }
        if (!isRetry) {
            retriedKey = null
        }
        autostartAfterLoad = autostart
        audioSuppressedForLoad = fromSongChange
        cachedWantPlaying = false
        cachedPosition = 0L
        cachedDuration = 0L
        _hasFrame.value = false
        _isBuffering.value = false
        _aspect.value = 0f
        _state.value = State(phase = Phase.Loading, key = key, standalone = standalone, retrying = isRetry)
        dispatch(Radio.Events.Player.Paused)

        loadJob = scope.launch {
            val stream = try {
                resolveStream(key, standalone)
            } catch (err: CancellationException) {
                throw err
            } catch (err: Exception) {
                Logger.warn("VideoMode", "stream lookup failed for $key", err)
                null
            }
            when {
                stream == null || stream.url.isBlank() -> fail(MESSAGE_NOT_FOUND)
                else -> beginPlayback(key, standalone, stream)
            }
        }
    }

    private suspend fun resolveStream(key: String, standalone: VybeVideoItem?): VybeVideoStreamData? {
        streamCache[key]?.let { return it }
        val stream = when {
            standalone != null -> symphony.vybeApi.getVideoStream(
                input = standalone.watchUrl ?: standalone.videoId,
            )

            else -> {
                val song = symphony.groove.song.get(key) ?: return null
                symphony.vybeApi.getVideoStream(
                    input = song.title,
                    title = song.title,
                    artist = song.artists.firstOrNull(),
                    durationSec = song.duration / 1000,
                )
            }
        }
        if (stream != null && stream.url.isNotBlank()) {
            streamCache[key] = stream
        }
        return stream
    }

    private fun beginPlayback(key: String, standalone: VybeVideoItem?, stream: VybeVideoStreamData) {
        val p = ensurePlayer()
        // The video's own audio takes over; the radio's player stays paused underneath.
        symphony.radio.silenceAudioForVideo()
        fallbackDuration = when {
            standalone != null -> (standalone.durationSec ?: 0L) * 1000L
            else -> symphony.groove.song.get(key)?.duration ?: 0L
        }
        triedAltUrl = false
        p.setMediaItem(buildMediaItem(stream))
        p.prepare()
        setVideoTrackEnabled(isAppVisible())
        p.volume = 1f
        applyPlaybackParams(p)
        audioSuppressedForLoad = false
        _state.value = _state.value.copy(phase = Phase.Ready, stream = stream, message = null)

        val granted = symphony.radio.requestAudioFocus()
        val allowed = !symphony.settings.requireAudioFocus.value || granted
        p.playWhenReady = autostartAfterLoad && allowed
        refreshCache()
        startTicker()
        dispatch(if (p.playWhenReady) Radio.Events.Player.Resumed else Radio.Events.Player.Paused)
        prefetchNext()
    }

    /** Warms the cache for the next song so switching to it starts instantly. */
    private fun prefetchNext() {
        val s = _state.value
        if (s.standalone != null) {
            return
        }
        val nextId = symphony.radio.nextSongIdForPrefetch() ?: return
        if (nextId == s.key || streamCache.containsKey(nextId)) {
            return
        }
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            try {
                resolveStream(nextId, null)
            } catch (err: CancellationException) {
                throw err
            } catch (_: Exception) {
            }
        }
    }

    private fun fail(message: String, detail: String? = null) {
        val s = _state.value
        val wasPlaying = s.phase == Phase.Ready && cachedWantPlaying
        loadJob?.cancel()
        fadeJob?.cancel()
        stopTicker()
        player?.run {
            playWhenReady = false
            stop()
            clearMediaItems()
        }
        cachedWantPlaying = false
        _hasFrame.value = false
        _isBuffering.value = false
        _state.value = s.copy(phase = Phase.Error, message = message, detail = detail, stream = null, retrying = false)
        // Never leave the listener in silence because a video failed: hand back to the audio.
        val audioOwed = s.standalone == null && (wasPlaying ||
                (audioSuppressedForLoad && autostartAfterLoad && !symphony.radio.isAudioPlaying))
        audioSuppressedForLoad = false
        when {
            audioOwed -> symphony.radio.resumeAudio()
            else -> dispatch(Radio.Events.Player.Paused)
        }
    }

    private fun disengage(resumeAudio: Boolean) {
        val s = _state.value
        if (!s.engaged) {
            return
        }
        val wasReady = s.phase == Phase.Ready
        val wasPlaying = wasReady && cachedWantPlaying
        val audioOwed = resumeAudio && when {
            s.standalone != null -> wasReady && audioWasPlaying
            wasReady -> wasPlaying
            else -> s.phase == Phase.Loading && audioSuppressedForLoad && autostartAfterLoad
        }
        loadJob?.cancel()
        prefetchJob?.cancel()
        fadeJob?.cancel()
        stopTicker()
        player?.run {
            playWhenReady = false
            stop()
            clearMediaItems()
            volume = 1f
        }
        cachedWantPlaying = false
        cachedPosition = 0L
        cachedDuration = 0L
        audioSuppressedForLoad = false
        _hasFrame.value = false
        _isBuffering.value = false
        _aspect.value = 0f
        _state.value = State()
        // The audio is resumed exactly where it was paused. Its position is never synced
        // to the video's, since the two recordings can differ.
        when {
            audioOwed -> symphony.radio.resumeAudio()
            else -> dispatch(Radio.Events.Player.Paused)
        }
    }

    // ── Player plumbing ──────────────────────────────────────────────────────────────

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        // Stream links are third-party and often redirect (http -> https, CDN hops), so
        // allow cross-protocol redirects and don't hang forever on a dead host.
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setUserAgent(USER_AGENT)
        val created = ExoPlayer.Builder(symphony.applicationContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(symphony.applicationContext).setDataSourceFactory(http)
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        created.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            // Focus is handled by RadioFocus so the two players never fight over it.
            false,
        )
        created.setWakeMode(C.WAKE_MODE_NETWORK)
        created.addListener(playerListener)
        player = created
        return created
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            refreshCache()
            when (playbackState) {
                Player.STATE_READY -> dispatch(Radio.Events.Player.Seeked)
                Player.STATE_ENDED -> onEnded()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            refreshCache()
            dispatch(
                when {
                    playWhenReady -> Radio.Events.Player.Resumed
                    else -> Radio.Events.Player.Paused
                }
            )
        }

        override fun onRenderedFirstFrame() {
            _hasFrame.value = true
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                _aspect.value = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val s = _state.value
            Logger.warn(
                "VideoMode",
                "playback error ${error.errorCodeName} (${error.errorCode}) " +
                        "format=${s.stream?.format} type=${s.stream?.type} " +
                        "url=${s.stream?.url?.substringBefore('?')}",
                error,
            )
            // The backend can also hand out a second link (download_url). Try it before
            // spending a whole extra round trip re-resolving the video.
            val failed = s.stream
            val alt = failed?.let { st ->
                st.download_url?.takeIf { it.isNotBlank() && it != st.url }
            }
            val p = player
            if (p != null && failed != null && alt != null && !triedAltUrl) {
                triedAltUrl = true
                p.setMediaItem(buildMediaItem(failed, alt))
                p.prepare()
                p.playWhenReady = autostartAfterLoad
                return
            }
            val key = s.key
            if (key != null && retriedKey != key) {
                // Upstream stream links can expire; refetch once before giving up.
                retriedKey = key
                streamCache.remove(key)
                loadFor(
                    key,
                    s.standalone,
                    autostart = cachedWantPlaying || autostartAfterLoad,
                    fromSongChange = audioSuppressedForLoad,
                    isRetry = true,
                )
                return
            }
            fail(MESSAGE_PLAYBACK, "${error.errorCodeName} (${error.errorCode})")
        }
    }

    private fun onEnded() {
        cachedWantPlaying = false
        val s = _state.value
        if (s.standalone != null) {
            player?.run {
                playWhenReady = false
                seekTo(0L)
            }
            refreshCache()
            dispatch(Radio.Events.Player.Paused)
            return
        }
        dispatch(Radio.Events.Player.Paused)
        // Same path as an audio track ending: loop modes, "pause after this song",
        // and the foreground/background rule in onSongChanging all apply.
        symphony.radio.onVideoEnded()
    }

    /** The backend may hand back HLS/DASH links with no file extension; use its hint then. */
    private fun buildMediaItem(stream: VybeVideoStreamData, url: String = stream.url): MediaItem {
        val hint = listOfNotNull(stream.format, stream.type).joinToString(" ").lowercase()
        val mime = when {
            "m3u8" in hint || "hls" in hint -> MimeTypes.APPLICATION_M3U8
            "mpd" in hint || "dash" in hint -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        return MediaItem.Builder()
            .setUri(url)
            .apply { mime?.let { setMimeType(it) } }
            .build()
    }

    private fun refreshCache() {
        val p = player ?: return
        cachedPosition = p.currentPosition.coerceAtLeast(0L)
        val duration = p.duration
        cachedDuration = if (duration == C.TIME_UNSET || duration <= 0L) 0L else duration
        cachedWantPlaying = p.playWhenReady && p.playbackState != Player.STATE_ENDED
    }

    private fun applyPlaybackParams(p: ExoPlayer) {
        p.playbackParameters = PlaybackParameters(
            symphony.radio.currentSpeed,
            symphony.radio.currentPitch,
        )
    }

    private fun setVideoTrackEnabled(enabled: Boolean) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                refreshCache()
                val visible = isAppVisible()
                if (visible) {
                    playbackPosition?.let { symphony.radio.onPlaybackPositionUpdate.dispatch(it) }
                }
                delay(if (visible) TICK_VISIBLE_MS else TICK_HIDDEN_MS)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun fadeVolume(to: Float, durationMs: Long) {
        val p = player ?: return
        fadeJob?.cancel()
        fadeJob = scope.launch { fadeSteps(p, to, durationMs) }
    }

    private suspend fun fadeSteps(p: ExoPlayer, to: Float, durationMs: Long) {
        val from = p.volume
        for (i in 1..FADE_STEPS) {
            p.volume = from + (to - from) * i / FADE_STEPS
            delay(durationMs / FADE_STEPS)
        }
    }

    private fun release() {
        loadJob?.cancel()
        prefetchJob?.cancel()
        stopTicker()
        fadeJob?.cancel()
        player?.release()
        player = null
        _state.value = State()
    }

    private fun dispatch(event: Radio.Events.Player) = symphony.radio.onUpdate.dispatch(event)

    private inline fun onMain(crossinline block: () -> Unit) {
        when (Looper.myLooper()) {
            Looper.getMainLooper() -> block()
            else -> mainHandler.post { block() }
        }
    }

    companion object {
        private const val STREAM_CACHE_SIZE = 12
        private const val TICK_VISIBLE_MS = 250L
        private const val TICK_HIDDEN_MS = 1000L
        private const val FADE_MS = 350L
        private const val FADE_STEPS = 12
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0 Mobile Safari/537.36"
        const val MESSAGE_NOT_FOUND = "Couldn't find a video for this song"
        const val MESSAGE_PLAYBACK = "This video can't be played right now"
    }
}
