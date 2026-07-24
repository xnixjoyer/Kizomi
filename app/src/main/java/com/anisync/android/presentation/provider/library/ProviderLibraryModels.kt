package com.anisync.android.presentation.provider.library

import com.anisync.android.presentation.model.MediaListItemPresentation
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity

private const val DEFAULT_STALE_AFTER_MILLIS = 24 * 60 * 60_000L

enum class ProviderLibraryStatus {
    CURRENT,
    PLANNING,
    COMPLETED,
    DROPPED,
    PAUSED,
    REPEATING,
}

enum class ProviderLibrarySort {
    TITLE,
    PROGRESS,
    SCORE,
    LAST_UPDATED,
    START_DATE,
}

enum class ProviderLibraryLayout {
    GRID,
    LIST,
    ADAPTIVE,
}

data class ProviderLibraryItem(
    val card: MediaListItemPresentation,
    val alternativeTitles: List<String> = emptyList(),
    val status: ProviderLibraryStatus,
    val progress: Int,
    val total: Int? = null,
    val secondaryProgress: Int? = null,
    val secondaryTotal: Int? = null,
    val score100: Double? = null,
    val repeatCount: Int = 0,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val mediaStartDate: String? = null,
    val providerUpdatedAtEpochMillis: Long? = null,
    val fetchedAtEpochMillis: Long,
) {
    init {
        require(progress >= 0)
        require(total == null || total >= 0)
        require(secondaryProgress == null || secondaryProgress >= 0)
        require(secondaryTotal == null || secondaryTotal >= 0)
        require(score100 == null || score100 in 0.0..100.0)
        require(repeatCount >= 0)
    }

    val identity: ProviderMediaIdentity
        get() = card.identity

    val normalizedTotal: Int?
        get() = total?.takeIf { it > 0 }

    val normalizedSecondaryTotal: Int?
        get() = secondaryTotal?.takeIf { it > 0 }

    internal fun matches(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isEmpty()) return true
        return card.title.contains(normalized, ignoreCase = true) ||
            alternativeTitles.any { it.contains(normalized, ignoreCase = true) }
    }
}

data class ProviderLibraryQuery(
    val mediaType: PresentationMediaType = PresentationMediaType.ANIME,
    val statuses: Set<ProviderLibraryStatus> = ProviderLibraryStatus.entries.toSet(),
    val searchQuery: String = "",
    val sort: ProviderLibrarySort = ProviderLibrarySort.TITLE,
    val ascending: Boolean = true,
    val layout: ProviderLibraryLayout = ProviderLibraryLayout.ADAPTIVE,
)

data class ProviderLibrarySnapshot(
    val query: ProviderLibraryQuery,
    val allItems: List<ProviderLibraryItem>,
    val visibleItems: List<ProviderLibraryItem>,
    val groupedItems: Map<ProviderLibraryStatus, List<ProviderLibraryItem>>,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val hasStaleContent: Boolean = false,
) {
    val isInitialLoading: Boolean
        get() = isRefreshing && allItems.isEmpty()

    val isEmpty: Boolean
        get() = !isInitialLoading && errorMessage == null && visibleItems.isEmpty()
}

fun buildProviderLibrarySnapshot(
    items: List<ProviderLibraryItem>,
    query: ProviderLibraryQuery,
    isRefreshing: Boolean = false,
    errorMessage: String? = null,
    nowEpochMillis: Long = System.currentTimeMillis(),
    staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
): ProviderLibrarySnapshot {
    require(staleAfterMillis >= 0L)

    val typeItems = items.filter { it.identity.mediaType == query.mediaType }
    val filtered = typeItems.filter { item ->
        item.status in query.statuses && item.matches(query.searchQuery)
    }
    val visible = filtered.sortedWith(providerLibraryComparator(query.sort, query.ascending))
    val grouped = buildMap {
        ProviderLibraryStatus.entries.forEach { status ->
            val group = visible.filter { it.status == status }
            if (group.isNotEmpty()) put(status, group)
        }
    }
    val hasStaleContent = typeItems.any { item ->
        item.fetchedAtEpochMillis <= 0L ||
            nowEpochMillis - item.fetchedAtEpochMillis > staleAfterMillis
    }

    return ProviderLibrarySnapshot(
        query = query,
        allItems = typeItems,
        visibleItems = visible,
        groupedItems = grouped,
        isRefreshing = isRefreshing,
        errorMessage = errorMessage,
        hasStaleContent = hasStaleContent,
    )
}

private fun providerLibraryComparator(
    sort: ProviderLibrarySort,
    ascending: Boolean,
): Comparator<ProviderLibraryItem> = Comparator { left, right ->
    val primary = when (sort) {
        ProviderLibrarySort.TITLE -> compareText(left.card.title, right.card.title, ascending)
        ProviderLibrarySort.PROGRESS -> compareNullable(left.progress, right.progress, ascending)
        ProviderLibrarySort.SCORE -> compareNullable(left.score100, right.score100, ascending)
        ProviderLibrarySort.LAST_UPDATED -> compareNullable(
            left.providerUpdatedAtEpochMillis,
            right.providerUpdatedAtEpochMillis,
            ascending,
        )
        ProviderLibrarySort.START_DATE -> compareNullable(
            left.mediaStartDate,
            right.mediaStartDate,
            ascending,
        )
    }
    if (primary != 0) {
        primary
    } else {
        val title = compareText(left.card.title, right.card.title, true)
        if (title != 0) title else left.identity.stableKey.compareTo(right.identity.stableKey)
    }
}

private fun compareText(left: String, right: String, ascending: Boolean): Int {
    val result = left.compareTo(right, ignoreCase = true)
    return if (ascending) result else -result
}

private fun <T : Comparable<T>> compareNullable(left: T?, right: T?, ascending: Boolean): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    ascending -> left.compareTo(right)
    else -> right.compareTo(left)
}
