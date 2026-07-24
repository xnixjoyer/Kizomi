package com.anisync.android.presentation.model

/** Provider-neutral media kind used only by presentation contracts. */
enum class PresentationMediaType {
    ANIME,
    MANGA,
}

/**
 * Typed UI identity. Provider-native IDs stay structurally separate and can never silently alias.
 */
sealed interface ProviderMediaIdentity {
    val mediaType: PresentationMediaType
    val stableKey: String

    data class AniList(
        val mediaId: Int,
        override val mediaType: PresentationMediaType,
    ) : ProviderMediaIdentity {
        init {
            require(mediaId > 0)
        }

        override val stableKey: String = "ANILIST:${mediaType.name}:$mediaId"
    }

    data class MyAnimeList(
        val malId: Long,
        override val mediaType: PresentationMediaType,
    ) : ProviderMediaIdentity {
        init {
            require(malId > 0L)
        }

        override val stableKey: String = "MYANIMELIST:${mediaType.name}:$malId"
    }
}

/**
 * Smallest shared model needed by the first provider-neutral list/search card.
 *
 * It deliberately excludes provider DTOs, editing commands, airing/community metadata and other
 * richer AniList-only card concerns until a shared production surface actually needs them.
 */
data class MediaListItemPresentation(
    val identity: ProviderMediaIdentity,
    val title: String,
    val coverUrl: String?,
    val progress: Int? = null,
    val total: Int? = null,
) {
    val normalizedProgress: Int?
        get() = progress?.coerceAtLeast(0)

    val normalizedTotal: Int?
        get() = total?.takeIf { it > 0 }
}
