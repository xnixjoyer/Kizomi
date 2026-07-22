package com.anisync.android.data.identity

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MediaIdentityRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: MediaIdentityRepository
    private lateinit var databaseFile: File
    private var nextId = 0
    private var now = 1_000L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        databaseFile = context.getDatabasePath("media-identity-repository-test.db")
        context.deleteDatabase(databaseFile.name)
        database = openDatabase()
        repository = newRepository(database)
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
        context.deleteDatabase(databaseFile.name)
    }

    @Test
    fun `MAL only media does not require AniList identity`() = runTest {
        val local = create(LocalMediaType.ANIME)
        val attached = repository.attachProviderIdentity(
            local.id,
            MediaIdentityProvider.MYANIMELIST,
            44L,
            LocalMediaType.ANIME,
            MediaIdentityMappingSource.MAL_IMPORT,
            MediaIdentityVerificationStatus.IMPORTED,
        )
        assertTrue(attached is MediaIdentityResult.Success)
        assertEquals(local.id, success(repository.resolveByMalId(LocalMediaType.ANIME, 44L))?.id)
        assertNull(success(repository.resolveByAniListId(LocalMediaType.ANIME, 44L)))
    }

    @Test
    fun `AniList adapter resolves in both directions without changing production id`() = runTest {
        val adapter = AniListMediaIdentityAdapter(repository)
        val local = (adapter.ensureLocalIdentity(
            LocalMediaType.MANGA,
            123,
        ) as MediaIdentityResult.Success).value
        assertNotEquals("123", local.id)
        assertEquals(local.id, success(adapter.resolveLocalIdentity(LocalMediaType.MANGA, 123))?.id)
        assertEquals(123L, success(adapter.resolveAniListId(local.id)))
        assertEquals(local.id, success(adapter.ensureLocalIdentity(LocalMediaType.MANGA, 123)).id)
    }

    @Test
    fun `conflicting attach never overwrites existing local or global mapping`() = runTest {
        val first = create(LocalMediaType.ANIME)
        val second = create(LocalMediaType.ANIME)
        attach(first, 500L)
        val localConflict = repository.attachProviderIdentity(
            first.id,
            MediaIdentityProvider.MYANIMELIST,
            501L,
            LocalMediaType.ANIME,
            MediaIdentityMappingSource.MANUAL_CONFIRMATION,
            MediaIdentityVerificationStatus.CONFIRMED,
        )
        val globalConflict = repository.attachProviderIdentity(
            second.id,
            MediaIdentityProvider.MYANIMELIST,
            500L,
            LocalMediaType.ANIME,
            MediaIdentityMappingSource.MAL_IMPORT,
            MediaIdentityVerificationStatus.IMPORTED,
        )
        assertTrue(localConflict is MediaIdentityResult.Conflict)
        assertTrue(globalConflict is MediaIdentityResult.Conflict)
        assertEquals(first.id, success(repository.resolveByMalId(LocalMediaType.ANIME, 500L))?.id)
        assertNull(success(repository.resolveByMalId(LocalMediaType.ANIME, 501L)))
        assertTrue(success(repository.listConflicting(LocalMediaType.ANIME)).size >= 2)
    }

    @Test
    fun `rejection blocks automatic attach and explicit confirmation can restore candidate`() = runTest {
        val local = create(LocalMediaType.ANIME)
        attach(local, 700L)
        assertTrue(repository.rejectProviderIdentity(
            local.id,
            MediaIdentityProvider.MYANIMELIST,
            700L,
            LocalMediaType.ANIME,
            "manual rejection",
        ) is MediaIdentityResult.Success)
        assertNull(success(repository.resolveByMalId(LocalMediaType.ANIME, 700L)))
        assertTrue(repository.attachProviderIdentity(
            local.id,
            MediaIdentityProvider.MYANIMELIST,
            700L,
            LocalMediaType.ANIME,
            MediaIdentityMappingSource.ANILIST_ID_MAL,
            MediaIdentityVerificationStatus.EXACT,
        ) is MediaIdentityResult.Rejected)
        val confirmed = repository.confirmProviderIdentity(
            local.id,
            MediaIdentityProvider.MYANIMELIST,
            700L,
            LocalMediaType.ANIME,
        )
        assertTrue(confirmed is MediaIdentityResult.Success)
        assertEquals(
            MediaIdentityVerificationStatus.CONFIRMED,
            (confirmed as MediaIdentityResult.Success).value.verificationStatus,
        )
    }

    @Test
    fun `same numeric provider id remains distinct for Anime and Manga`() = runTest {
        val anime = create(LocalMediaType.ANIME)
        val manga = create(LocalMediaType.MANGA)
        attach(anime, 808L, LocalMediaType.ANIME)
        attach(manga, 808L, LocalMediaType.MANGA)
        assertEquals(anime.id, success(repository.resolveByMalId(LocalMediaType.ANIME, 808L))?.id)
        assertEquals(manga.id, success(repository.resolveByMalId(LocalMediaType.MANGA, 808L))?.id)
    }

    @Test
    fun `repository recreation preserves provider identities and review issues`() = runTest {
        val local = create(LocalMediaType.ANIME)
        attach(local, 900L)
        repository.markConflict(
            local.id,
            MediaIdentityProvider.MYANIMELIST,
            901L,
            LocalMediaType.ANIME,
            MediaIdentityMappingSource.MANUAL_CONFIRMATION,
            "fixture conflict",
        )
        database.close()
        database = openDatabase()
        repository = newRepository(database)
        assertEquals(local.id, success(repository.resolveByMalId(LocalMediaType.ANIME, 900L))?.id)
        assertEquals(1, success(repository.listConflicting(LocalMediaType.ANIME)).size)
    }

    @Test
    fun `parallel provider attaches produce one winner and no silent overwrite`() = runTest {
        val locals = listOf(create(LocalMediaType.ANIME), create(LocalMediaType.ANIME))
        val results = locals.map { local ->
            async(Dispatchers.IO) {
                repository.attachProviderIdentity(
                    local.id,
                    MediaIdentityProvider.MYANIMELIST,
                    1_234L,
                    LocalMediaType.ANIME,
                    MediaIdentityMappingSource.MAL_IMPORT,
                    MediaIdentityVerificationStatus.IMPORTED,
                )
            }
        }.awaitAll()
        assertEquals(1, results.count { it is MediaIdentityResult.Success })
        assertEquals(1, database.mediaIdentityDao().countProviderIdentities())
        assertTrue(results.any { it is MediaIdentityResult.Conflict })
    }

    private suspend fun create(type: LocalMediaType): LocalMediaIdentity =
        (repository.createLocalIdentity(type) as MediaIdentityResult.Success).value

    private suspend fun attach(
        local: LocalMediaIdentity,
        providerId: Long,
        type: LocalMediaType = local.mediaType,
    ): ProviderMediaIdentity =
        (repository.attachProviderIdentity(
            local.id,
            MediaIdentityProvider.MYANIMELIST,
            providerId,
            type,
            MediaIdentityMappingSource.MAL_IMPORT,
            MediaIdentityVerificationStatus.IMPORTED,
        ) as MediaIdentityResult.Success).value

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, databaseFile.name)
            .addMigrations(*com.anisync.android.data.local.Migrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    private fun newRepository(database: AppDatabase): MediaIdentityRepository =
        MediaIdentityRepository(
            database,
            database.mediaIdentityDao(),
            object : MediaIdentityClock {
                override fun nowEpochMillis(): Long = now++
            },
            object : MediaIdentityIdGenerator {
                override fun newLocalMediaId(): String = "local-${++nextId}"
            },
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T> success(result: MediaIdentityResult<T>): T =
        (result as MediaIdentityResult.Success<T>).value
}
