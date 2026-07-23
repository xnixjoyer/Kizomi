package com.anisync.android.data.provider

import androidx.room.withTransaction
import android.content.Context
import androidx.work.WorkManager
import coil.ImageLoader
import com.anisync.android.data.AppSettings
import com.anisync.android.data.account.AccountStore
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.dao.AiringScheduleDao
import com.anisync.android.data.local.dao.LibraryDao
import com.anisync.android.data.local.dao.MediaDetailsDao
import com.anisync.android.data.local.dao.MediaIdentityDao
import com.anisync.android.data.local.dao.SavedForumThreadDao
import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.dao.TrendingDao
import com.anisync.android.data.local.dao.UserProfileDao
import com.anisync.android.data.mal.account.MalAccountRepository
import com.anisync.android.data.mal.oauth.MalOAuthSessionStore
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderRuntimeState
import com.anisync.android.domain.provider.ProviderTransitionPhase
import com.anisync.android.domain.tracking.TrackingProvider
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.cache.normalized.apolloStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderSessionCoordinator @Inject constructor(
    private val stateStore: ActiveProviderStore,
    private val aniListAccounts: AccountStore,
    private val malAccounts: MalAccountRepository,
    private val malOAuthSessions: MalOAuthSessionStore,
    private val database: AppDatabase,
    private val trackingDao: TrackingDao,
    private val mediaIdentityDao: MediaIdentityDao,
    private val libraryDao: LibraryDao,
    private val mediaDetailsDao: MediaDetailsDao,
    private val userProfileDao: UserProfileDao,
    private val airingScheduleDao: AiringScheduleDao,
    private val savedForumThreadDao: SavedForumThreadDao,
    private val trendingDao: TrendingDao,
    private val appSettings: AppSettings,
    private val apolloClient: ApolloClient,
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
) {
    private val transitionMutex = Mutex()

    suspend fun initialize(): ProviderRuntimeState = transitionMutex.withLock {
        withContext(Dispatchers.IO) {
        if (stateStore.snapshot().transitionPhase == ProviderTransitionPhase.PURGING) {
            purgeEveryProviderLocally()
            stateStore.finishPurge()
        }
        val hasAniList = aniListAccounts.accounts.value.isNotEmpty()
        val hasMal = malAccounts.listAccounts().isNotEmpty()
        val current = stateStore.snapshot()
        if (current.transitionPhase == ProviderTransitionPhase.AUTHENTICATING) {
            val completed = when (current.pendingProvider) {
                ActiveProvider.ANILIST_ONLY -> hasAniList && !hasMal
                ActiveProvider.MAL_ONLY -> hasMal && !hasAniList
                else -> false
            }
            if (completed) return@withContext stateStore.completeLogin(requireNotNull(current.pendingProvider))
        }
        stateStore.reconcileLocalAccounts(hasAniList, hasMal)
        }
    }

    fun beginLogin(provider: ActiveProvider): ProviderRuntimeState = stateStore.beginLogin(provider)

    fun cancelLogin(): ProviderRuntimeState = stateStore.cancelLogin()

    suspend fun completeLogin(provider: ActiveProvider): ProviderRuntimeState =
        transitionMutex.withLock { withContext(Dispatchers.IO) {
            require(provider != ActiveProvider.UNCONFIGURED)
            when (provider) {
                ActiveProvider.ANILIST_ONLY -> purgeMalProviderLocally()
                ActiveProvider.MAL_ONLY -> purgeAniListProviderLocally()
                ActiveProvider.UNCONFIGURED -> error("unreachable")
            }
            purgeSharedTrackingState()
            stateStore.completeLogin(provider)
        } }

    suspend fun selectLegacyProvider(provider: ActiveProvider): ProviderRuntimeState =
        transitionMutex.withLock { withContext(Dispatchers.IO) {
            require(provider != ActiveProvider.UNCONFIGURED)
            require(
                stateStore.snapshot().transitionPhase ==
                    ProviderTransitionPhase.LEGACY_SELECTION_REQUIRED
            )
            stateStore.beginPurge()
            stopProviderWork()
            when (provider) {
                ActiveProvider.ANILIST_ONLY -> purgeMalProviderLocally()
                ActiveProvider.MAL_ONLY -> purgeAniListProviderLocally()
                ActiveProvider.UNCONFIGURED -> error("unreachable")
            }
            purgeSharedTrackingState()
            stateStore.activateExisting(provider)
        } }

    suspend fun disconnectAndDeleteAllLocalProviderData(): ProviderRuntimeState =
        transitionMutex.withLock { withContext(Dispatchers.IO) {
            stateStore.beginPurge()
            purgeEveryProviderLocally()
            stateStore.finishPurge()
        } }

    suspend fun prepareDestructiveProviderChange(): ProviderRuntimeState =
        disconnectAndDeleteAllLocalProviderData()

    private suspend fun purgeEveryProviderLocally() {
        stopProviderWork()
        malOAuthSessions.clearPending(null)
        malAccounts.listAccounts().forEach { malAccounts.removeLocal(it.localAccountId) }
        aniListAccounts.clearAll()
        runCatching { apolloClient.apolloStore.clearAll() }
        database.withTransaction {
            libraryDao.deleteAll()
            mediaDetailsDao.clear()
            userProfileDao.clear()
            airingScheduleDao.clearAll()
            savedForumThreadDao.deleteAll()
            trendingDao.clearAll()
            trackingDao.purgeAllProviderBoundState()
            mediaIdentityDao.deleteAllProviderIdentities()
            mediaIdentityDao.deleteAllIdentityIssues()
        }
        appSettings.clearAccountScoped()
        clearControllableCaches()
    }

    private suspend fun purgeAniListProviderLocally() {
        aniListAccounts.clearAll()
        runCatching { apolloClient.apolloStore.clearAll() }
        database.withTransaction {
            libraryDao.deleteAll()
            mediaDetailsDao.clear()
            userProfileDao.clear()
            airingScheduleDao.clearAll()
            savedForumThreadDao.deleteAll()
            trendingDao.clearAll()
            mediaIdentityDao.deleteProviderIdentities(TrackingProvider.ANILIST.name)
            mediaIdentityDao.deleteIdentityIssues(TrackingProvider.ANILIST.name)
        }
        appSettings.clearAccountScoped()
    }

    private suspend fun purgeMalProviderLocally() {
        malOAuthSessions.clearPending(null)
        malAccounts.listAccounts().forEach { malAccounts.removeLocal(it.localAccountId) }
        database.withTransaction {
            trackingDao.purgeMalLocalData()
            mediaIdentityDao.deleteProviderIdentities(TrackingProvider.MYANIMELIST.name)
            mediaIdentityDao.deleteIdentityIssues(TrackingProvider.MYANIMELIST.name)
        }
        clearControllableCaches()
    }

    private suspend fun purgeSharedTrackingState() {
        database.withTransaction { trackingDao.purgeAllProviderBoundState() }
    }

    private fun stopProviderWork() {
        WorkManager.getInstance(context).cancelAllWork()
    }

    private fun clearControllableCaches() {
        imageLoader.memoryCache?.clear()
        runCatching { imageLoader.diskCache?.clear() }
    }
}
