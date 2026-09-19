package io.github.zyrouge.symphony.services.home

import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.api.VybeFypData
import io.github.zyrouge.symphony.services.api.VybeAlbum
import io.github.zyrouge.symphony.services.api.VybeArtist
import io.github.zyrouge.symphony.services.api.VybeHomeData
import io.github.zyrouge.symphony.services.api.VybeTrack
import io.github.zyrouge.symphony.services.groove.repositories.PlaylistRepository
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.Locale
import kotlin.random.Random

/**
 * Builds the long, endless, ever-changing home feed.
 *
 * The server is stateless, so the feed is assembled here from many small "recipes"
 * (one API call each, see the FYP docs). Every session/refresh gets a fresh random
 * seed, so the mix, the order and the picked seeds differ each time:
 *
 * - static sections cut from `/api/home` (charts, new releases, editor's picks, ...)
 * - sections seeded by what you played (related songs, artist radio, new albums,
 *   similar artists)
 * - mood rows from tags, and genre deep-dives
 *
 * Sections are shown progressively: a first batch on load, then a couple more each
 * time the user nears the bottom. When the recipe queue runs dry it is refilled with
 * new seeds (taken from what has been shown so far), so the feed keeps going until
 * [MAX_SECTIONS]. Sections of the same kind are kept apart, and songs already shown
 * higher up are dropped from later song sections.
 */
class HomeFeed(private val symphony: Symphony) {
    private enum class Kind { Songs, Albums, Artists, Playlists, Genres }

    private class Recipe(
        val key: String,
        val kind: Kind,
        val pinned: Boolean = false,
        val run: suspend () -> List<FeedSection>,
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Mutex()

    private val _sections = MutableStateFlow<List<FeedSection>>(emptyList())
    val sections: StateFlow<List<FeedSection>> = _sections.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** True once the first build has finished (successfully or not). */
    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private val _isExhausted = MutableStateFlow(false)
    val isExhausted: StateFlow<Boolean> = _isExhausted.asStateFlow()

    private val _heroSongId = MutableStateFlow<String?>(null)
    val heroSongId: StateFlow<String?> = _heroSongId.asStateFlow()

    private val _trendingSongIds = MutableStateFlow<List<String>>(emptyList())
    val trendingSongIds: StateFlow<List<String>> = _trendingSongIds.asStateFlow()

    @Volatile
    private var started = false
    private var home: VybeHomeData? = null
    private var random = Random(System.nanoTime())
    private val shownSongIds = HashSet<String>()
    private val sectionKeys = HashSet<String>()
    private val usedRecipeKeys = HashSet<String>()
    private val pending = mutableListOf<Recipe>()
    private var lastKind: Kind? = null
    private var refills = 0

    /** False when the server has no /api/fyp (older deployment): fall back to client recipes. */
    private var fypAvailable = true
    private var fypRound = 0

    private class FypSeeds(
        val tracks: List<String>,
        val artists: List<String>,
        val played: List<String>,
    )

    /** Loads the first page once; later calls are no-ops (use [refresh] to reshuffle). */
    fun start() {
        if (started) {
            return
        }
        started = true
        refresh()
    }

    /** Throws the current feed away and builds a brand-new one with a new random seed. */
    fun refresh() {
        if (!_isRefreshing.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            lock.lock()
            try {
                rebuild()
            } catch (err: CancellationException) {
                throw err
            } catch (err: Exception) {
                Logger.error("HomeFeed", "refresh failed", err)
            } finally {
                _hasLoaded.value = true
                _isRefreshing.value = false
                lock.unlock()
            }
        }
    }

    /** Appends the next couple of sections. Safe to call as often as you like. */
    fun loadMore() {
        if (_isExhausted.value || _isRefreshing.value) {
            return
        }
        if (!_isLoadingMore.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            if (!lock.tryLock()) {
                _isLoadingMore.value = false
                return@launch
            }
            try {
                if (_sections.value.size >= MAX_SECTIONS) {
                    _isExhausted.value = true
                    return@launch
                }
                val more = takeBatch(PAGE_BATCH)
                if (more.isEmpty()) {
                    _isExhausted.value = true
                } else {
                    _sections.value = _sections.value + more
                }
            } catch (err: CancellationException) {
                throw err
            } catch (err: Exception) {
                Logger.error("HomeFeed", "loadMore failed", err)
            } finally {
                _isLoadingMore.value = false
                lock.unlock()
            }
        }
    }

    // ---------------------------------------------------------------- building

    private suspend fun rebuild() {
        random = Random(System.nanoTime())
        shownSongIds.clear()
        sectionKeys.clear()
        usedRecipeKeys.clear()
        pending.clear()
        lastKind = null
        refills = 0
        fypRound = 0
        fypAvailable = true

        val data = symphony.vybeApi.getHome(countryCode())
        if (data != null) {
            symphony.groove.catalog.ingestHome(data)
            home = data
        }
        val h = home
        _trendingSongIds.value = h?.trending?.map { songIdOf(it) }.orEmpty()
        _heroSongId.value = h?.trending?.take(HERO_POOL)?.randomOrNull(random)?.let { songIdOf(it) }

        // Personalised shelves come from the server in ONE call, seeded with local history.
        var fyp: VybeFypData? = null
        val seeds = fypSeeds(shuffle = false)
        if (seeds.tracks.isNotEmpty() || seeds.artists.isNotEmpty()) {
            fyp = symphony.vybeApi.getFyp(seeds.tracks, seeds.artists, seeds.played, countryCode(), FYP_LIMIT)
            fypAvailable = fyp != null
        }

        pending.addAll(buildRecipes(h, fyp))
        _isExhausted.value = false
        _sections.value = takeBatch(INITIAL_BATCH)
    }

    private fun buildRecipes(h: VybeHomeData?, fyp: VybeFypData?): List<Recipe> {
        val pinned = mutableListOf<Recipe>()
        val rest = mutableListOf<Recipe>()
        val recent = symphony.history.recentlyPlayed.value.filter { it.startsWith(VYBE_PREFIX) }

        if (recent.size >= MIN_ITEMS) {
            pinned.addUnique(
                Recipe("jump-back-in", Kind.Songs, pinned = true) { jumpBackIn(recent) }
            )
        }
        // The very first personalised shelf goes right under "Jump back in".
        fyp?.let { data -> fypRecipes(data, pinFirst = 1) }?.forEach { recipe ->
            if (recipe.pinned) pinned.addUnique(recipe) else rest.addUnique(recipe)
        }
        if (h != null && h.trending.size >= MIN_ITEMS) {
            val trendingRecipe = Recipe("trending", Kind.Songs, pinned = pinned.isEmpty()) { trending(h) }
            if (trendingRecipe.pinned) pinned.addUnique(trendingRecipe) else rest.addUnique(trendingRecipe)
        }

        // Older server without /api/fyp: rebuild "because you played" / artist shelves ourselves.
        if (!fypAvailable && recent.isNotEmpty()) {
            val seeds = listOf(recent.first()) + recent.drop(1).take(10).shuffled(random).take(2)
            seeds.forEachIndexed { i, seed ->
                val style = if (i == 0) FeedSection.Songs.Style.Rows else FeedSection.Songs.Style.Cards
                rest.addUnique(Recipe("because:$seed", Kind.Songs) { because(seed, style) })
            }
            seeds.take(2).forEach { seed ->
                rest.addUnique(Recipe("artist-of:$seed", Kind.Songs) { artistBundle(seed) })
            }
        }

        val followed = artistIdsOf(recent).take(RADAR_ARTISTS)
        if (followed.size >= MIN_ITEMS) {
            rest.addUnique(Recipe("radar", Kind.Albums) { radar(followed) })
        }
        TEMPO_LANES.shuffled(random).take(1).forEach { (lane, title) ->
            rest.addUnique(Recipe("bpm:$lane", Kind.Songs) { tempo(lane, title) })
        }

        if (h != null) {
            staticRecipes(h).forEach { rest.addUnique(it) }
        }
        TAGS.shuffled(random).take(2).forEach { tag ->
            rest.addUnique(Recipe("tag:$tag", Kind.Songs) { mood(tag) })
        }
        return pinned + rest.shuffled(random)
    }

    private fun staticRecipes(h: VybeHomeData): List<Recipe> {
        val out = mutableListOf<Recipe>()

        h.topInCountry?.songs?.takeIf { it.size >= MIN_ITEMS }?.let { songs ->
            val name = countryName(h.topInCountry?.country)
            out += Recipe("top-country", Kind.Songs) {
                songsSection(
                    "top-country",
                    "Top songs in $name",
                    existing(songs.take(10)),
                    FeedSection.Songs.Style.Ranked,
                )
            }
        }
        if (h.newReleases.size >= MIN_ITEMS) {
            out += Recipe("new-releases", Kind.Albums) {
                albumsSection("new-releases", "New releases", h.newReleases.take(12).map { it.id })
            }
        }
        if (h.trendingAlbums.size >= MIN_ITEMS) {
            out += Recipe("trending-albums", Kind.Albums) {
                albumsSection(
                    "trending-albums",
                    "Trending albums",
                    h.trendingAlbums.take(12).map { it.id },
                )
            }
        }
        h.topAlbumsInCountry?.albums?.takeIf { it.size >= MIN_ITEMS }?.let { albums ->
            val name = countryName(h.topAlbumsInCountry?.country)
            out += Recipe("top-albums-country", Kind.Albums) {
                albumsSection("top-albums-country", "Top albums in $name", albums.take(12).map { it.id })
            }
        }
        val pickedAlbums = h.editorsPicks.filter { !it.isPlaylist }
        if (pickedAlbums.size >= MIN_ITEMS) {
            out += Recipe("editors-albums", Kind.Albums) {
                albumsSection("editors-albums", "Editor's picks", pickedAlbums.take(12).map { it.id })
            }
        }
        val pickedPlaylists = h.editorsPicks.filter { it.isPlaylist }
        if (pickedPlaylists.size >= MIN_ITEMS) {
            out += Recipe("editors-playlists", Kind.Playlists) {
                playlistsSection(
                    "editors-playlists",
                    "Curated for you",
                    pickedPlaylists.take(10).map { PlaylistRepository.remoteId(it.id) },
                )
            }
        }
        if (h.playlists.size >= MIN_ITEMS) {
            out += Recipe("popular-playlists", Kind.Playlists) {
                playlistsSection(
                    "popular-playlists",
                    "Popular playlists",
                    h.playlists.shuffled(random).take(10).map { PlaylistRepository.remoteId(it.id) },
                )
            }
        }
        if (h.artists.size >= MIN_ITEMS) {
            out += Recipe("popular-artists", Kind.Artists) {
                artistsSection(
                    "popular-artists",
                    "Popular artists",
                    h.artists.map { it.name }.filter { it.isNotBlank() }.shuffled(random).take(12),
                )
            }
        }
        if (h.genres.size >= MIN_ITEMS) {
            out += Recipe("browse-genres", Kind.Genres) {
                val names = h.genres.map { it.name }.filter { it.isNotBlank() }.shuffled(random).take(14)
                if (names.size >= MIN_ITEMS) {
                    listOf(FeedSection.Genres("browse-genres", "Browse genres", names))
                } else {
                    emptyList()
                }
            }
        }
        h.spotlight.forEach { spot ->
            val name = spot.genre?.name?.takeIf { it.isNotBlank() } ?: return@forEach
            out += Recipe("spotlight:$name", Kind.Songs) { spotlight(spot.songs, spot.artists.map { it.name }, name) }
        }
        return out
    }

    /** Runs when the queue is empty: new seeds from what was played/shown so far. */
    private fun refill() {
        refills++
        val h = home
        val pool = (symphony.history.recentlyPlayed.value + shownSongIds.toList())
            .filter { it.startsWith(VYBE_PREFIX) }
            .distinct()
        val out = mutableListOf<Recipe>()

        if (fypAvailable) {
            out.addUnique(fypRoundRecipe())
        } else {
            pool.filter { "because:$it" !in usedRecipeKeys }.shuffled(random).take(2).forEach { seed ->
                out.addUnique(Recipe("because:$seed", Kind.Songs) { because(seed, FeedSection.Songs.Style.Cards) })
            }
            pool.filter { "artist-of:$it" !in usedRecipeKeys }.shuffled(random).take(1).forEach { seed ->
                out.addUnique(Recipe("artist-of:$seed", Kind.Songs) { artistBundle(seed) })
            }
        }
        TEMPO_LANES.filter { (lane, _) -> "bpm:$lane" !in usedRecipeKeys }.shuffled(random).take(1)
            .forEach { (lane, title) ->
                out.addUnique(Recipe("bpm:$lane", Kind.Songs) { tempo(lane, title) })
            }
        TAGS.filter { "tag:$it" !in usedRecipeKeys }.shuffled(random).take(2).forEach { tag ->
            out.addUnique(Recipe("tag:$tag", Kind.Songs) { mood(tag) })
        }
        h?.genres?.map { it.name }?.filter { it.isNotBlank() && "genre:$it" !in usedRecipeKeys }
            ?.shuffled(random)?.take(1)?.forEach { genre ->
                out.addUnique(Recipe("genre:$genre", Kind.Songs) { genreDeepDive(genre) })
            }
        pending.addAll(out.shuffled(random))
    }

    private suspend fun takeBatch(want: Int): List<FeedSection> {
        val out = mutableListOf<FeedSection>()
        var rounds = 0
        while (out.size < want && rounds < MAX_ROUNDS) {
            rounds++
            if (pending.isEmpty()) {
                if (refills >= MAX_REFILLS) {
                    break
                }
                refill()
                if (pending.isEmpty()) {
                    break
                }
            }
            val picks = mutableListOf<Recipe>()
            repeat(want - out.size) {
                nextRecipe()?.let { picks.add(it) }
            }
            if (picks.isEmpty()) {
                break
            }
            val results = coroutineScope {
                picks.map { recipe -> async { runSafely(recipe) } }.awaitAll()
            }
            for (sections in results) {
                for (section in sections) {
                    accept(section)?.let { out.add(it) }
                }
            }
        }
        return out
    }

    /** Pinned recipes go first; otherwise avoid two neighbours of the same kind. */
    private fun nextRecipe(): Recipe? {
        if (pending.isEmpty()) {
            return null
        }
        val index = if (pending.first().pinned) {
            0
        } else {
            pending.indexOfFirst { it.kind != lastKind }.takeIf { it >= 0 } ?: 0
        }
        val recipe = pending.removeAt(index)
        lastKind = recipe.kind
        return recipe
    }

    private suspend fun runSafely(recipe: Recipe): List<FeedSection> = try {
        withTimeoutOrNull(RECIPE_TIMEOUT_MS) { recipe.run() } ?: emptyList()
    } catch (err: CancellationException) {
        throw err
    } catch (err: Exception) {
        Logger.warn("HomeFeed", "recipe ${recipe.key} failed", err)
        emptyList()
    }

    private fun accept(section: FeedSection): FeedSection? {
        if (!sectionKeys.add(section.key)) {
            return null
        }
        if (section is FeedSection.Songs) {
            shownSongIds.addAll(section.songIds)
        }
        return section
    }

    // ----------------------------------------------------------------- recipes

    private fun jumpBackIn(recent: List<String>): List<FeedSection> {
        val ids = recent.filter { symphony.groove.song.get(it) != null }.take(10)
        return songsSection("jump-back-in", "Jump back in", ids, FeedSection.Songs.Style.Cards)
    }

    private fun trending(h: VybeHomeData): List<FeedSection> =
        songsSection("trending", "Trending now", existing(h.trending.take(10)), FeedSection.Songs.Style.Ranked)

    private suspend fun because(seedId: String, style: FeedSection.Songs.Style): List<FeedSection> {
        val deezerId = seedId.removePrefix(VYBE_PREFIX)
        val related = symphony.vybeApi.getRelatedTracks(deezerId, RELATED_LIMIT) ?: return emptyList()
        val ingested = symphony.groove.catalog.ingestTracks(related.songs)
        val seedTitle = symphony.groove.song.get(seedId)?.title
            ?: symphony.vybeApi.getSong(deezerId)?.let { symphony.groove.catalog.ingestTrack(it).title }
            ?: return emptyList()
        val ids = unseen(ingested.map { it.id }).take(12)
        return songsSection(
            "because:$seedId",
            "Because you played $seedTitle",
            ids,
            style,
            leadSongId = seedId,
        )
    }

    private suspend fun artistBundle(seedId: String): List<FeedSection> {
        val (artistId, artistName) = artistOf(seedId) ?: return emptyList()
        val detail = symphony.vybeApi.getArtist(artistId) ?: return emptyList()
        symphony.groove.catalog.ingestArtistDetail(detail)
        val out = mutableListOf<FeedSection>()
        out += songsSection(
            "artist-radio:$artistId",
            "$artistName radio",
            unseen(existing(detail.radio)).take(12),
            FeedSection.Songs.Style.Cards,
        )
        if (detail.albums.size >= MIN_ALBUMS) {
            out += FeedSection.Albums(
                "artist-albums:$artistId",
                "New from $artistName",
                detail.albums.take(10).map { it.id },
            )
        }
        out += artistsSection(
            "artist-related:$artistId",
            "Fans of $artistName also like",
            detail.related.map { it.name }.filter { it.isNotBlank() && it != artistName }.take(12),
        )
        return out
    }

    private suspend fun mood(tag: String): List<FeedSection> {
        val data = symphony.vybeApi.getTagSongs(tag, TAG_LIMIT) ?: return emptyList()
        val ingested = symphony.groove.catalog.ingestTracks(data.songs)
        val ids = unseen(ingested.map { it.id }).take(12)
        val title = "Vibe: " + tag.replaceFirstChar { it.uppercase() }
        return songsSection("tag:$tag", title, ids, FeedSection.Songs.Style.Cards)
    }

    private suspend fun genreDeepDive(name: String): List<FeedSection> {
        val genreId = symphony.groove.catalog.genreIdForName(name) ?: return emptyList()
        val data = symphony.vybeApi.getGenre(genreId) ?: return emptyList()
        symphony.groove.catalog.ingestGenreDetail(data)
        val out = mutableListOf<FeedSection>()
        out += songsSection(
            "genre:$name",
            "Deep dive: $name",
            unseen(existing(data.songs)).take(12),
            FeedSection.Songs.Style.Cards,
        )
        out += artistsSection(
            "genre-artists:$name",
            "$name artists",
            data.artists.map { it.name }.filter { it.isNotBlank() }.take(12),
        )
        return out
    }

    private fun spotlight(
        tracks: List<VybeTrack>,
        artistNames: List<String>,
        genre: String,
    ): List<FeedSection> {
        val out = mutableListOf<FeedSection>()
        out += songsSection(
            "spotlight:$genre",
            "Spotlight: $genre",
            unseen(existing(tracks)).take(12),
            FeedSection.Songs.Style.Cards,
        )
        out += artistsSection(
            "spotlight-artists:$genre",
            "$genre artists",
            artistNames.filter { it.isNotBlank() }.take(12),
        )
        return out
    }

    /** (artistId, artistName) of the first artist of a song, fetching the song if unknown. */
    private suspend fun artistOf(songId: String): Pair<String, String>? {
        val name = symphony.groove.song.get(songId)?.artists?.firstOrNull()
        val knownId = name?.let { symphony.groove.catalog.artistIdForName(it) }
        if (name != null && knownId != null) {
            return knownId to name
        }
        val track = symphony.vybeApi.getSong(songId.removePrefix(VYBE_PREFIX)) ?: return null
        symphony.groove.catalog.ingestTrack(track)
        val artist = track.artists?.primary?.firstOrNull() ?: return null
        val id = artist.id?.takeIf { it.isNotBlank() } ?: return null
        val artistName = artist.name?.takeIf { it.isNotBlank() } ?: return null
        return id to artistName
    }

    // ------------------------------------------------------ server-blended (FYP)

    /** Seeds for /api/fyp: recent plays (or a random mix of plays and shown songs), most recent first. */
    private fun fypSeeds(shuffle: Boolean): FypSeeds {
        val recent = symphony.history.recentlyPlayed.value.filter { it.startsWith(VYBE_PREFIX) }
        val pool = if (shuffle) {
            (recent + shownSongIds.toList()).distinct().shuffled(random)
        } else {
            recent
        }
        return FypSeeds(
            tracks = pool.mapNotNull { deezerIdOf(it) }.take(FYP_SEEDS),
            artists = artistIdsOf(pool).take(FYP_SEEDS),
            played = (recent + shownSongIds).mapNotNull { deezerIdOf(it) }.distinct().take(FYP_PLAYED),
        )
    }

    /** Deezer artist ids of the songs' first artists, in order, without duplicates. */
    private fun artistIdsOf(songIds: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (songId in songIds) {
            val name = symphony.groove.song.get(songId)?.artists?.firstOrNull() ?: continue
            val id = symphony.groove.catalog.artistIdForName(name) ?: continue
            if (id.isNotEmpty() && id.all { it.isDigit() }) {
                out.add(id)
            }
        }
        return out.toList()
    }

    private fun deezerIdOf(songId: String): String? =
        songId.takeIf { it.startsWith(VYBE_PREFIX) }
            ?.removePrefix(VYBE_PREFIX)
            ?.takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }

    /** Each server shelf becomes its own recipe so it interleaves with the other sections. */
    private fun fypRecipes(data: VybeFypData, pinFirst: Int): List<Recipe> =
        fypSections(data, round = 0).mapIndexed { index, section ->
            Recipe("fyp:${section.key}", kindOf(section), pinned = index < pinFirst) { listOf(section) }
        }

    /** A fresh /api/fyp call with new random seeds, used every time the feed needs more. */
    private fun fypRoundRecipe(): Recipe {
        val round = ++fypRound
        return Recipe("fyp-round:$round", Kind.Songs) {
            val seeds = fypSeeds(shuffle = true)
            val data = if (seeds.tracks.isEmpty() && seeds.artists.isEmpty()) {
                null
            } else {
                symphony.vybeApi.getFyp(seeds.tracks, seeds.artists, seeds.played, countryCode(), FYP_LIMIT)
            }
            if (data == null) emptyList() else fypSections(data, round)
        }
    }

    /** Ingests everything in the response and turns it into feed sections. */
    private fun fypSections(data: VybeFypData, round: Int): List<FeedSection> {
        val json = symphony.vybeApi.json
        val catalog = symphony.groove.catalog
        val out = mutableListOf<FeedSection>()

        if (data.feed.size >= MIN_ITEMS) {
            val ids = catalog.ingestTracks(data.feed.take(FEED_MIX_SIZE)).map { it.id }
            out += songsSection(
                "made-for-you:$round",
                if (round == 0) "Made for you" else "More for you",
                ids,
                FeedSection.Songs.Style.Rows,
            )
        }
        data.rows.forEach { row ->
            // With /api/home available the country-less "trending" shelf would just repeat it.
            if (row.id == "trending" && home != null) {
                return@forEach
            }
            val key = "fyp:${row.id}"
            when (row.type) {
                "tracks" -> {
                    val tracks = row.items.mapNotNull {
                        runCatching { json.decodeFromJsonElement<VybeTrack>(it) }.getOrNull()
                    }
                    val ids = catalog.ingestTracks(tracks).map { it.id }
                    val lead = row.id.takeIf { it.startsWith("because-") }
                        ?.removePrefix("because-")
                        ?.let { "$VYBE_PREFIX$it" }
                    out += songsSection(key, row.title, ids.take(12), FeedSection.Songs.Style.Cards, lead)
                }

                "albums" -> {
                    val albums = row.items.mapNotNull {
                        runCatching { json.decodeFromJsonElement<VybeAlbum>(it) }.getOrNull()
                    }
                    albums.forEach { catalog.ingestAlbumStub(it) }
                    out += albumsSection(key, row.title, albums.take(12).map { it.id })
                }

                "artists" -> {
                    val artists = row.items.mapNotNull {
                        runCatching { json.decodeFromJsonElement<VybeArtist>(it) }.getOrNull()
                    }
                    artists.forEach { catalog.ingestArtistStub(it) }
                    out += artistsSection(
                        key,
                        row.title,
                        artists.map { it.name }.filter { it.isNotBlank() }.take(12),
                    )
                }
            }
        }
        return out
    }

    private fun kindOf(section: FeedSection): Kind = when (section) {
        is FeedSection.Songs -> Kind.Songs
        is FeedSection.Albums -> Kind.Albums
        is FeedSection.Artists -> Kind.Artists
        is FeedSection.Playlists -> Kind.Playlists
        is FeedSection.Genres -> Kind.Genres
    }

    private suspend fun radar(artistIds: List<String>): List<FeedSection> {
        val data = symphony.vybeApi.getRadar(artistIds, RADAR_DAYS, 20) ?: return emptyList()
        data.releases.forEach { symphony.groove.catalog.ingestAlbumStub(it) }
        return albumsSection("radar", "Release radar", data.releases.map { it.id }.take(12))
    }

    private suspend fun tempo(lane: String, title: String): List<FeedSection> {
        val data = symphony.vybeApi.getBpmLane(lane, BPM_LIMIT) ?: return emptyList()
        val ingested = symphony.groove.catalog.ingestTracks(data.songs)
        return songsSection(
            "bpm:$lane",
            title,
            unseen(ingested.map { it.id }).take(12),
            FeedSection.Songs.Style.Cards,
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun songIdOf(track: VybeTrack) = "$VYBE_PREFIX${track.id}"

    /** Song ids of [tracks] that made it into the local catalog. */
    private fun existing(tracks: List<VybeTrack>) =
        tracks.map { songIdOf(it) }.filter { symphony.groove.song.get(it) != null }

    private fun unseen(ids: List<String>) = ids.filter { it !in shownSongIds }

    private fun songsSection(
        key: String,
        title: String,
        ids: List<String>,
        style: FeedSection.Songs.Style,
        leadSongId: String? = null,
    ): List<FeedSection> =
        if (ids.size >= MIN_ITEMS) {
            listOf(FeedSection.Songs(key, title, ids, style, leadSongId))
        } else {
            emptyList()
        }

    private fun albumsSection(key: String, title: String, ids: List<String>): List<FeedSection> {
        val known = symphony.groove.album.get(ids).map { it.id }
        return if (known.size >= MIN_ITEMS) listOf(FeedSection.Albums(key, title, known)) else emptyList()
    }

    private fun artistsSection(key: String, title: String, names: List<String>): List<FeedSection> =
        if (names.size >= MIN_ITEMS) listOf(FeedSection.Artists(key, title, names)) else emptyList()

    private fun playlistsSection(key: String, title: String, ids: List<String>): List<FeedSection> {
        val known = ids.filter { symphony.groove.playlist.get(it) != null }
        return if (known.size >= MIN_ITEMS) listOf(FeedSection.Playlists(key, title, known)) else emptyList()
    }

    private fun MutableList<Recipe>.addUnique(recipe: Recipe) {
        if (usedRecipeKeys.add(recipe.key)) {
            add(recipe)
        }
    }

    /** Phone region for the country charts: SIM country first, then the locale's region. */
    private fun countryCode(): String? {
        val telephony = symphony.applicationContext
            .getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        val sim = telephony?.simCountryIso?.lowercase()?.takeIf { it.length == 2 }
        return sim ?: Locale.getDefault().country.lowercase().takeIf { it.length == 2 }
    }

    private fun countryName(code: String?): String =
        code?.takeIf { it.length == 2 }
            ?.let { Locale("", it).displayCountry }
            ?.takeIf { it.isNotBlank() }
            ?: "your country"

    companion object {
        private const val VYBE_PREFIX = "vybe_"
        private const val INITIAL_BATCH = 5
        private const val PAGE_BATCH = 2
        private const val MAX_SECTIONS = 60
        private const val MAX_ROUNDS = 6
        private const val MAX_REFILLS = 8
        private const val MIN_ITEMS = 3
        private const val MIN_ALBUMS = 2
        private const val HERO_POOL = 8
        private const val RELATED_LIMIT = 25
        private const val TAG_LIMIT = 25
        private const val RECIPE_TIMEOUT_MS = 10_000L
        private const val FYP_LIMIT = 30
        private const val FYP_SEEDS = 6
        private const val FYP_PLAYED = 300
        private const val FEED_MIX_SIZE = 12
        private const val RADAR_ARTISTS = 30
        private const val RADAR_DAYS = 90
        private const val BPM_LIMIT = 20

        private val TEMPO_LANES = listOf(
            "workout" to "Workout tempo",
            "running" to "Running pace",
            "focus" to "Focus flow",
            "chill" to "Chill tempo",
        )

        private val TAGS = listOf(
            "chill", "workout", "afrobeats", "amapiano", "focus", "party", "late night",
            "road trip", "throwback", "feel good", "sleep", "hip hop", "r&b", "acoustic",
            "gaming",
        )
    }
}
