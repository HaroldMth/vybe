package io.github.zyrouge.symphony.services.groove

import android.net.Uri
import androidx.core.net.toUri
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.api.VybeAlbum
import io.github.zyrouge.symphony.services.api.VybeArtist
import io.github.zyrouge.symphony.services.api.VybeArtistDetailData
import io.github.zyrouge.symphony.services.api.VybeChartsData
import io.github.zyrouge.symphony.services.api.VybeGenre
import io.github.zyrouge.symphony.services.api.VybeGenreDetailData
import io.github.zyrouge.symphony.services.api.VybeHomeData
import io.github.zyrouge.symphony.services.api.VybePlaylist
import io.github.zyrouge.symphony.services.api.VybeSearchData
import io.github.zyrouge.symphony.services.api.VybeTrack
import io.github.zyrouge.symphony.services.groove.repositories.PlaylistRepository
import io.github.zyrouge.symphony.utils.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class VybeCatalog(private val symphony: Symphony) {
    // The catalog is populated both during application startup and when the For You
    // screen is opened.  Serialise track ingestion so two identical responses cannot
    // add the same path to SongRepository's file index at the same time.
    private val ingestLock = Any()
    private val albumIdBySongId = ConcurrentHashMap<String, String>()
    private val artistIdByName = ConcurrentHashMap<String, String>()
    private val genreIdByName = ConcurrentHashMap<String, String>()
    private val playlistCoverById = ConcurrentHashMap<String, String>()
    private val albumCoverById = ConcurrentHashMap<String, String>()
    private val artistCoverByName = ConcurrentHashMap<String, String>()

    fun albumIdForSong(songId: String) = albumIdBySongId[songId]
    fun artistIdForName(name: String) = artistIdByName[name]
    fun genreIdForName(name: String) = genreIdByName[name]
    fun playlistCoverUri(playlistId: String): Uri? = playlistCoverById[playlistId]?.toUri()
    fun albumCoverUri(albumId: String): Uri? = albumCoverById[albumId]?.toUri()
    fun artistCoverUri(artistName: String): Uri? = artistCoverByName[artistName]?.toUri()

    fun reset() {
        albumIdBySongId.clear()
        artistIdByName.clear()
        genreIdByName.clear()
        playlistCoverById.clear()
        albumCoverById.clear()
        artistCoverByName.clear()
    }

    suspend fun bootstrap() {
        try {
            val home = symphony.vybeApi.getHome()
            if (home != null) ingestHome(home)
        } catch (err: Exception) {
            Logger.error("VybeCatalog", "home bootstrap failed", err)
        }
        try {
            val charts = symphony.vybeApi.getCharts()
            if (charts != null) ingestCharts(charts)
        } catch (err: Exception) {
            Logger.error("VybeCatalog", "charts bootstrap failed", err)
        }
    }

    fun ingestHome(data: VybeHomeData) {
        ingestTracks(data.trending)
        data.newReleases.forEach { ingestAlbumStub(it) }
        data.artists.forEach { ingestArtistStub(it) }
        data.playlists.forEach { ingestPlaylistStub(it) }
        data.genres.forEach { ingestGenreStub(it) }
    }

    fun ingestCharts(data: VybeChartsData) {
        ingestTracks(data.songs)
        data.albums.forEach { ingestAlbumStub(it) }
        data.artists.forEach { ingestArtistStub(it) }
        data.playlists.forEach { ingestPlaylistStub(it) }
    }

    fun ingestSearch(data: VybeSearchData): SearchIds {
        val songIds = ingestTracks(data.songs).map { it.id }
        val artistNames = data.artists.map { ingestArtistStub(it); it.name }
        val albumIds = data.albums.map { ingestAlbumStub(it); it.id }
        val playlistIds = data.playlists.map { ingestPlaylistStub(it) }
        return SearchIds(
            songIds = songIds,
            artistNames = artistNames,
            albumIds = albumIds,
            playlistIds = playlistIds,
        )
    }

    fun ingestTracks(tracks: List<VybeTrack>): List<Song> = tracks.map { ingestTrack(it) }

    fun ingestTrack(track: VybeTrack): Song = synchronized(ingestLock) {
        val existingId = "vybe_${track.id}"
        val existing = symphony.groove.song.get(existingId)
        if (existing != null) {
            track.album?.id?.takeIf { it.isNotBlank() }?.let { albumId ->
                albumIdBySongId[existing.id] = albumId
                track.coverUrl?.let { url -> albumCoverById.putIfAbsent(albumId, url) }
            }
            rememberArtistIds(track)
            return existing
        }

        val durationSec = if (track.duration > 100000) track.duration / 1000 else track.duration
        val streamUrl = symphony.vybeApi.buildAudioStreamUrl(
            deezerId = track.id,
            title = track.title,
            artist = track.artistName,
            durationSec = durationSec.takeIf { it > 0 },
        )
        val song = Song.fromVybeTrack(track, streamUrl)
        track.album?.id?.takeIf { it.isNotBlank() }?.let {
            albumIdBySongId[song.id] = it
            track.coverUrl?.let { url -> albumCoverById.putIfAbsent(it, url) }
        }
        rememberArtistIds(track)
        symphony.groove.albumArtist.onSong(song)
        symphony.groove.album.onSong(song)
        symphony.groove.artist.onSong(song)
        symphony.groove.genre.onSong(song)
        symphony.groove.song.onSong(song)
        return song
    }

    fun ingestAlbumStub(album: VybeAlbum) {
        val artists = album.artists?.primary?.mapNotNull { it.name }?.toMutableSet()
            ?: mutableSetOf(album.artistName)
        album.coverUrl?.let { albumCoverById[album.id] = it }
        album.artists?.primary?.forEach { short ->
            val name = short.name ?: return@forEach
            val id = short.id?.takeIf { it.isNotBlank() }
            if (id != null) artistIdByName[name] = id
        }
        val year = album.releaseDate?.take(4)?.toIntOrNull()
        symphony.groove.album.putStub(
            Album(
                id = album.id,
                name = album.name,
                artists = artists,
                startYear = year,
                endYear = year,
                numberOfTracks = album.nbTracks ?: album.songs.size,
                duration = 0.milliseconds,
            )
        )
        if (album.songs.isNotEmpty()) ingestTracks(album.songs)
    }

    fun ingestArtistStub(artist: VybeArtist) {
        // An artist with a blank name can arrive from the API (missing "name" field
        // defaults to ""). A blank string used as a type-safe Navigation argument
        // crashes RouteDecoder with "Unexpected null value for non-nullable argument"
        // when the artist is later tapped, so never let one enter the local store.
        if (artist.name.isBlank()) {
            return
        }
        if (artist.id.isNotBlank()) {
            artistIdByName[artist.name] = artist.id
        }
        artist.coverUrl?.let {
            artistCoverByName[artist.name] = it
            if (artist.id.isNotBlank()) artistCoverByName[artist.id] = it
        }
        symphony.groove.artist.putStub(
            Artist(
                name = artist.name,
                numberOfAlbums = artist.nbAlbum ?: 0,
                numberOfTracks = 0,
            ),
            apiId = artist.id,
        )
    }

    fun ingestGenreStub(genre: VybeGenre) {
        if (genre.name.isNotBlank()) {
            genreIdByName[genre.name] = genre.id
        }
        symphony.groove.genre.putStub(genre.name)
    }

    fun ingestPlaylistStub(playlist: VybePlaylist): String {
        val id = PlaylistRepository.remoteId(playlist.id)
        playlist.coverUrl?.let { playlistCoverById[id] = it }
        val songIds = if (playlist.songs.isNotEmpty()) ingestTracks(playlist.songs).map { it.id } else emptyList()
        symphony.groove.playlist.putRemote(
            Playlist(
                id = id,
                title = playlist.name.ifBlank { "Playlist" },
                songPaths = songIds,
                uri = null,
                path = null,
            )
        )
        return id
    }

    fun ingestAlbumDetail(album: VybeAlbum) {
        ingestAlbumStub(album)
        ingestTracks(album.songs)
    }

    fun ingestArtistDetail(data: VybeArtistDetailData) {
        data.info?.let { ingestArtistStub(it) }
        ingestTracks(data.songs)
        ingestTracks(data.radio)
        data.albums.forEach { ingestAlbumStub(it) }
        data.related.forEach { ingestArtistStub(it) }
    }

    fun ingestGenreDetail(data: VybeGenreDetailData) {
        data.genre?.let { ingestGenreStub(it) }
        ingestTracks(data.songs)
        data.artists.forEach { ingestArtistStub(it) }
    }

    fun ingestPlaylistDetail(playlist: VybePlaylist) {
        ingestPlaylistStub(playlist)
    }

    suspend fun ensureAlbum(albumId: String) {
        if (symphony.groove.album.getSongIds(albumId).isNotEmpty()) return
        val data = symphony.vybeApi.getAlbum(albumId) ?: return
        ingestAlbumDetail(data)
    }

    suspend fun ensureArtist(nameOrId: String) {
        val apiId = artistIdByName[nameOrId] ?: nameOrId
        val existingSongs = symphony.groove.artist.getSongIds(nameOrId)
            .ifEmpty { symphony.groove.artist.getSongIds(apiId) }
        if (existingSongs.size >= 5) return

        var detail = symphony.vybeApi.getArtist(apiId)
        if (detail == null && apiId != nameOrId) {
            detail = symphony.vybeApi.getArtist(nameOrId)
        }
        if (detail == null) {
            val search = symphony.vybeApi.search(nameOrId)
            val match = search?.artists?.firstOrNull {
                it.name.equals(nameOrId, ignoreCase = true) || it.id == nameOrId
            } ?: search?.artists?.firstOrNull()
            if (match != null) {
                detail = symphony.vybeApi.getArtist(match.id)
            }
        }
        if (detail != null) ingestArtistDetail(detail)
    }

    suspend fun ensureGenre(nameOrId: String) {
        val apiId = genreIdByName[nameOrId] ?: nameOrId
        if (symphony.groove.genre.getSongIds(nameOrId).isNotEmpty()) return
        val data = symphony.vybeApi.getGenre(apiId) ?: return
        ingestGenreDetail(data)
    }

    suspend fun ensurePlaylist(playlistId: String) {
        if (!PlaylistRepository.isRemoteId(playlistId)) return
        val existing = symphony.groove.playlist.get(playlistId)
        if (existing != null && existing.songPaths.isNotEmpty()) return
        val apiId = PlaylistRepository.remoteApiId(playlistId)
        val data = symphony.vybeApi.getPlaylist(apiId) ?: return
        ingestPlaylistDetail(data)
    }

    private fun rememberArtistIds(track: VybeTrack) {
        track.artists?.primary?.forEach { short ->
            val name = short.name ?: return@forEach
            val id = short.id?.takeIf { it.isNotBlank() } ?: return@forEach
            artistIdByName[name] = id
        }
    }

    data class SearchIds(
        val songIds: List<String>,
        val artistNames: List<String>,
        val albumIds: List<String>,
        val playlistIds: List<String>,
    )
}
