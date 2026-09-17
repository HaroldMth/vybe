package io.github.zyrouge.symphony.services.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive

object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

@Serializable
data class VybeResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
)

@Serializable
data class VybeImage(
    val quality: String? = null,
    val url: String? = null,
)

@Serializable
data class VybeArtistsPayload(
    val primary: List<VybeArtistShort>? = emptyList(),
)

@Serializable
data class VybeArtistShort(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    val name: String? = null,
)

@Serializable
data class VybeAlbumShort(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    val name: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class VybeTrack(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String,
    @JsonNames("title", "name")
    val title: String = "",
    val duration: Long = 0,
    val explicit: Boolean = false,
    val artists: VybeArtistsPayload? = null,
    val album: VybeAlbumShort? = null,
    val image: List<VybeImage>? = null,
) {
    val artistName: String
        get() = artists?.primary?.firstOrNull()?.name ?: "Unknown Artist"

    val artistNamesSet: Set<String>
        get() = artists?.primary?.mapNotNull { it.name }?.toSet() ?: setOf("Unknown Artist")

    val coverUrl: String?
        get() = image?.lastOrNull()?.url ?: image?.firstOrNull()?.url
}

@Serializable
data class VybeArtistStats(
    val listeners: Long? = null,
    val playcount: Long? = null,
)

@Serializable
data class VybeArtist(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String,
    val name: String = "",
    val nbAlbum: Int? = null,
    val nbFan: Int? = null,
    val image: List<VybeImage>? = null,
    val bio: String? = null,
    val country: String? = null,
    val formedYear: String? = null,
    val genres: List<String> = emptyList(),
    val links: Map<String, String?> = emptyMap(),
    val externalStats: VybeArtistStats? = null,
) {
    val coverUrl: String?
        get() = image?.lastOrNull()?.url ?: image?.firstOrNull()?.url

    /** Formatted listener count, e.g. "2.1M listeners" */
    val listenersFormatted: String?
        get() {
            val count = externalStats?.listeners ?: nbFan?.toLong() ?: return null
            return when {
                count >= 1_000_000 -> "%.1fM listeners".format(count / 1_000_000.0)
                count >= 1_000 -> "${count / 1_000}K listeners"
                else -> "$count listeners"
            }
        }
}

@Serializable
data class VybeAlbum(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String,
    val name: String = "",
    val recordType: String? = null,
    val nbTracks: Int? = null,
    val releaseDate: String? = null,
    val explicit: Boolean = false,
    val image: List<VybeImage>? = null,
    val artists: VybeArtistsPayload? = null,
    val songs: List<VybeTrack> = emptyList(),
    val totalDuration: Long? = null,
    val label: String? = null,
    val genre: String? = null,
    val description: String? = null,
) {
    val coverUrl: String?
        get() = image?.lastOrNull()?.url ?: image?.firstOrNull()?.url

    val artistName: String
        get() = artists?.primary?.firstOrNull()?.name ?: "Unknown Artist"
}

@Serializable
data class VybeGenre(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String,
    val name: String = "",
    val picture: String? = null,
    val pictureXl: String? = null,
)

@Serializable
data class VybePlaylist(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String,
    val name: String = "",
    val description: String? = null,
    val nbTracks: Int? = null,
    val image: List<VybeImage>? = null,
    val songs: List<VybeTrack> = emptyList(),
    val creator: String? = null,
) {
    val coverUrl: String?
        get() = image?.lastOrNull()?.url ?: image?.firstOrNull()?.url
}

@Serializable
data class VybeHomeData(
    val trending: List<VybeTrack> = emptyList(),
    val newReleases: List<VybeAlbum> = emptyList(),
    val playlists: List<VybePlaylist> = emptyList(),
    val artists: List<VybeArtist> = emptyList(),
    val genres: List<VybeGenre> = emptyList(),
)

@Serializable
data class VybeSearchData(
    val songs: List<VybeTrack> = emptyList(),
    val artists: List<VybeArtist> = emptyList(),
    val albums: List<VybeAlbum> = emptyList(),
    val playlists: List<VybePlaylist> = emptyList(),
)

@Serializable
data class VybeChartsData(
    val songs: List<VybeTrack> = emptyList(),
    val albums: List<VybeAlbum> = emptyList(),
    val artists: List<VybeArtist> = emptyList(),
    val playlists: List<VybePlaylist> = emptyList(),
)

@Serializable
data class VybeSyncedLyricLine(
    val startTime: Double = 0.0,
    val text: String = "",
)

@Serializable
data class VybeLyricsData(
    val type: String? = null,
    val lyrics: JsonElement? = null,
    val track: String? = null,
    val artist: String? = null,
    val source: String? = null,
)

@Serializable
data class VybeStreamData(
    val url: String,
    val sourceUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val duration: String? = null,
    val thumbnail: String? = null,
    val source: String? = null,
)

@Serializable
data class VybeArtistDetailData(
    val info: VybeArtist? = null,
    val songs: List<VybeTrack> = emptyList(),
    val albums: List<VybeAlbum> = emptyList(),
    val related: List<VybeArtist> = emptyList(),
    val radio: List<VybeTrack> = emptyList(),
)

@Serializable
data class VybeGenreDetailData(
    val genre: VybeGenre? = null,
    val artists: List<VybeArtist> = emptyList(),
    val songs: List<VybeTrack> = emptyList(),
)
