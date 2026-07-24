package com.anisync.android.presentation.provider.details

import com.anisync.android.presentation.model.ProviderMediaIdentity

enum class ProviderDetailsFailure {
    INVALID_IDENTITY,
    AUTHENTICATION_REQUIRED,
    RATE_LIMITED,
    OFFLINE,
    TIMEOUT,
    TEMPORARY,
    INVALID_RESPONSE,
    UNKNOWN,
}

enum class ProviderDetailsSection {
    ALTERNATIVE_TITLES,
    LIST_STATE,
    SYNOPSIS,
    FACTS,
    STATISTICS,
    GENRES,
    CREDITS,
    BACKGROUND,
    RELATIONS,
    RECOMMENDATIONS,
}

data class ProviderDetailsListState(
    val status: String?,
    val progress: Int,
    val secondaryProgress: Int? = null,
    val score100: Double? = null,
)

data class ProviderRelatedMediaPresentation(
    val identity: ProviderMediaIdentity,
    val title: String,
    val coverUrl: String?,
    val relationship: String? = null,
)

data class ProviderMediaDetailsPresentation(
    val identity: ProviderMediaIdentity,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val coverUrl: String? = null,
    val heroImageUrl: String? = null,
    val synopsis: String? = null,
    val background: String? = null,
    val format: String? = null,
    val status: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val episodeCount: Int? = null,
    val chapterCount: Int? = null,
    val volumeCount: Int? = null,
    val score: Double? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    val creators: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val listState: ProviderDetailsListState? = null,
    val relations: List<ProviderRelatedMediaPresentation> = emptyList(),
    val recommendations: List<ProviderRelatedMediaPresentation> = emptyList(),
) {
    fun visibleSections(editAvailable: Boolean): Set<ProviderDetailsSection> = buildSet {
        if (alternativeTitles.isNotEmpty()) add(ProviderDetailsSection.ALTERNATIVE_TITLES)
        if (listState != null || editAvailable) add(ProviderDetailsSection.LIST_STATE)
        if (!synopsis.isNullOrBlank()) add(ProviderDetailsSection.SYNOPSIS)
        if (
            listOf(format, status, startDate, endDate).any { !it.isNullOrBlank() } ||
            listOf(episodeCount, chapterCount, volumeCount).any { it != null }
        ) {
            add(ProviderDetailsSection.FACTS)
        }
        if (score != null || rank != null || popularity != null) {
            add(ProviderDetailsSection.STATISTICS)
        }
        if (genres.isNotEmpty()) add(ProviderDetailsSection.GENRES)
        if (creators.isNotEmpty() || studios.isNotEmpty()) add(ProviderDetailsSection.CREDITS)
        if (!background.isNullOrBlank()) add(ProviderDetailsSection.BACKGROUND)
        if (relations.isNotEmpty()) add(ProviderDetailsSection.RELATIONS)
        if (recommendations.isNotEmpty()) add(ProviderDetailsSection.RECOMMENDATIONS)
    }
}

data class ProviderDetailsUiState(
    val details: ProviderMediaDetailsPresentation? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val failure: ProviderDetailsFailure? = null,
) {
    val isStale: Boolean
        get() = details != null && failure != null
}
