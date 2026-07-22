package com.anisync.android.data.calendar

import com.anisync.android.AiringScheduleQuery
import com.anisync.android.data.AppSettings
import com.anisync.android.data.mapper.toDomainStatus
import com.anisync.android.data.util.safeApiCall
import com.anisync.android.domain.AiringEpisode
import com.anisync.android.domain.Result
import com.anisync.android.domain.calendar.CalendarDateRange
import com.anisync.android.domain.calendar.CalendarProvider
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.cache.normalized.FetchPolicy
import com.apollographql.apollo.cache.normalized.fetchPolicy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListCalendarProvider @Inject constructor(
    private val apolloClient: ApolloClient,
    private val appSettings: AppSettings
) : CalendarProvider {
    override val providerId: String = PROVIDER_ID

    override suspend fun getEntries(
        range: CalendarDateRange,
        forceRefresh: Boolean
    ): Result<List<AiringEpisode>> = safeApiCall {
        val showAdult = appSettings.showAdultContent.value
        val episodes = mutableListOf<AiringEpisode>()
        var page = 1
        var hasNextPage = true

        while (hasNextPage && page <= MAX_PAGES) {
            val response = apolloClient.query(
                AiringScheduleQuery(
                    page = Optional.present(page),
                    perPage = Optional.present(PER_PAGE),
                    airingAtGreater = Optional.present(range.startEpochSeconds.toInt()),
                    airingAtLesser = Optional.present(range.endEpochSeconds.toInt())
                )
            )
                .fetchPolicy(if (forceRefresh) FetchPolicy.NetworkOnly else FetchPolicy.NetworkFirst)
                .execute()

            val pageData = response.data?.Page
            pageData?.airingSchedules?.filterNotNull().orEmpty().forEach { schedule ->
                val media = schedule.media ?: return@forEach
                val scheduleId = schedule.id ?: return@forEach
                val mediaId = media.id ?: return@forEach
                val isAdult = media.isAdult == true
                if (isAdult && !showAdult) return@forEach

                episodes += AiringEpisode(
                    id = scheduleId,
                    episode = schedule.episode ?: 0,
                    airingAt = (schedule.airingAt ?: 0).toLong(),
                    mediaId = mediaId,
                    titleRomaji = media.title?.romaji,
                    titleEnglish = media.title?.english,
                    titleNative = media.title?.native,
                    titleUserPreferred = media.title?.userPreferred ?: "Unknown",
                    coverImageUrl = media.coverImage?.extraLarge
                        ?: media.coverImage?.large
                        ?: media.coverImage?.medium,
                    format = media.format?.rawValue,
                    averageScore = media.averageScore,
                    isOnList = media.mediaListEntry != null,
                    listStatus = media.mediaListEntry?.status?.toDomainStatus(),
                    isAdult = isAdult
                )
            }

            hasNextPage = pageData?.pageInfo?.hasNextPage == true
            page++
        }

        episodes.sortedBy { it.airingAt }
    }

    companion object {
        const val PROVIDER_ID = "anilist"
        private const val PER_PAGE = 50
        private const val MAX_PAGES = 10
    }
}
