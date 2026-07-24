package com.anisync.android.domain.calendar.provider

import com.anisync.android.domain.provider.ActiveProvider

enum class ProviderCalendarMediaType {
    ANIME,
    MANGA,
}

enum class ProviderCalendarPrecision {
    EXACT_EPISODE_AIRING,
    RECURRING_BROADCAST_SLOT,
    SEASONAL_CATALOG_ONLY,
}

enum class ProviderCalendarCapability {
    SEASONAL_CATALOG,
    RECURRING_BROADCAST_SLOTS,
    EXACT_EPISODE_SCHEDULE,
    BACKGROUND_REFRESH,
    WIDGET_SNAPSHOT,
    AIRING_NOTIFICATIONS,
}

enum class ProviderCalendarNotice {
    EXACT_EPISODE_SCHEDULE_UNAVAILABLE,
    AIRING_NOTIFICATIONS_UNAVAILABLE,
    RECURRING_SLOT_MAY_CHANGE,
    PARTIAL_PROVIDER_RESPONSE,
}

enum class ProviderCalendarUnavailableReason {
    UNCONFIGURED,
    PROVIDER_TRANSITION,
    PROVIDER_UNSUPPORTED,
    AUTHENTICATION_REQUIRED,
    EXACT_EPISODE_SCHEDULE_UNSUPPORTED,
}

data class ProviderCalendarSession(
    val runtimeProvider: ActiveProvider,
    val providerTrafficAllowed: Boolean,
    val accountKey: String? = null,
) {
    override fun toString(): String =
        "ProviderCalendarSession(runtimeProvider=${runtimeProvider.name}, " +
            "providerTrafficAllowed=$providerTrafficAllowed, accountKey=<redacted>)"
}

data class ProviderCalendarQuery(
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val zoneId: String,
) {
    init {
        require(endEpochSeconds > startEpochSeconds) {
            "Calendar query end must be greater than start"
        }
        require(zoneId.isNotBlank()) { "Calendar query zone must not be blank" }
    }
}

data class ProviderCalendarEntry(
    val provider: ActiveProvider,
    val providerMediaId: Long,
    val mediaType: ProviderCalendarMediaType,
    val title: String,
    val coverUrl: String?,
    val scheduledAtEpochSeconds: Long,
    val episodeNumber: Int?,
    val isOnList: Boolean,
    val precision: ProviderCalendarPrecision,
) {
    init {
        require(provider != ActiveProvider.UNCONFIGURED)
        require(providerMediaId > 0L)
        require(title.isNotBlank())
        require(scheduledAtEpochSeconds > 0L)
        require(episodeNumber == null || episodeNumber > 0)
    }

    val stableKey: String =
        "${provider.name}:${mediaType.name}:$providerMediaId:$scheduledAtEpochSeconds"

    override fun toString(): String =
        "ProviderCalendarEntry(provider=${provider.name}, providerMediaId=$providerMediaId, " +
            "mediaType=${mediaType.name}, title=<redacted>, coverUrl=<redacted>, " +
            "scheduledAtEpochSeconds=$scheduledAtEpochSeconds, episodeNumber=${episodeNumber ?: "none"}, " +
            "isOnList=$isOnList, precision=${precision.name})"
}

sealed interface ProviderCalendarLoadResult {
    data class Content(
        val entries: List<ProviderCalendarEntry>,
        val capabilities: Set<ProviderCalendarCapability>,
        val notices: Set<ProviderCalendarNotice> = emptySet(),
        val fetchedAtEpochMillis: Long,
    ) : ProviderCalendarLoadResult

    data class Unavailable(
        val reason: ProviderCalendarUnavailableReason,
    ) : ProviderCalendarLoadResult

    data class Failure(
        val reason: String,
        val retryable: Boolean,
        val retryAfterMillis: Long? = null,
    ) : ProviderCalendarLoadResult {
        override fun toString(): String =
            "ProviderCalendarLoadResult.Failure(reason=<redacted>, retryable=$retryable, " +
                "retryAfterMillis=${retryAfterMillis ?: "none"})"
    }
}

interface ProviderCalendarSource {
    val provider: ActiveProvider

    suspend fun load(
        session: ProviderCalendarSession,
        query: ProviderCalendarQuery,
        forceRefresh: Boolean = false,
    ): ProviderCalendarLoadResult
}

/**
 * Fail-closed provider router. It invokes exactly one source selected by the authoritative runtime
 * provider and never attempts another provider when a source is absent, unavailable, or fails.
 */
class ProviderCalendarRouter(
    sources: Set<ProviderCalendarSource>,
) {
    private val sourcesByProvider = sources.associateBy { it.provider }

    init {
        require(sources.none { it.provider == ActiveProvider.UNCONFIGURED }) {
            "UNCONFIGURED cannot own a calendar source"
        }
        require(sourcesByProvider.size == sources.size) {
            "Only one calendar source may be registered per provider"
        }
    }

    suspend fun load(
        session: ProviderCalendarSession,
        query: ProviderCalendarQuery,
        forceRefresh: Boolean = false,
    ): ProviderCalendarLoadResult {
        if (!session.providerTrafficAllowed) {
            return ProviderCalendarLoadResult.Unavailable(
                if (session.runtimeProvider == ActiveProvider.UNCONFIGURED) {
                    ProviderCalendarUnavailableReason.UNCONFIGURED
                } else {
                    ProviderCalendarUnavailableReason.PROVIDER_TRANSITION
                }
            )
        }
        if (session.runtimeProvider == ActiveProvider.UNCONFIGURED) {
            return ProviderCalendarLoadResult.Unavailable(
                ProviderCalendarUnavailableReason.UNCONFIGURED
            )
        }
        val source = sourcesByProvider[session.runtimeProvider]
            ?: return ProviderCalendarLoadResult.Unavailable(
                ProviderCalendarUnavailableReason.PROVIDER_UNSUPPORTED
            )
        return source.load(session, query, forceRefresh)
    }
}
