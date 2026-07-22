package com.anisync.android.data.identity

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.entity.LocalMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityIssueEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaIdentityDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `DAO stores local provider and issue rows and resolves both directions`() = runTest {
        val dao = database.mediaIdentityDao()
        dao.insertLocalIdentity(local("local-anime", "ANIME"))
        val providerId = dao.insertProviderIdentity(provider("local-anime", 100L, "ANIME"))
        dao.insertIssue(issue("local-anime", 200L, "ANIME", "UNRESOLVED"))

        assertNotNull(dao.getLocalIdentity("local-anime"))
        assertEquals(providerId, dao.findByProviderId("ANILIST", 100L, "ANIME")?.id)
        assertEquals(100L, dao.findProviderForLocal("local-anime", "ANILIST", "ANIME")?.providerMediaId)
        assertEquals(1, dao.getProviderIdentities("local-anime").size)
        assertEquals(1, dao.listIssues("UNRESOLVED", "ANIME").size)
        assertEquals(1, dao.countLocalIdentities())
        assertEquals(1, dao.countProviderIdentities())
        assertEquals(1, dao.countIssues())
    }

    @Test
    fun `unique provider and local slots reject silent overwrite`() = runTest {
        val dao = database.mediaIdentityDao()
        dao.insertLocalIdentity(local("first", "ANIME"))
        dao.insertLocalIdentity(local("second", "ANIME"))
        dao.insertProviderIdentity(provider("first", 300L, "ANIME"))

        val globalCollision = runCatching {
            dao.insertProviderIdentity(provider("second", 300L, "ANIME"))
        }
        val localSlotCollision = runCatching {
            dao.insertProviderIdentity(provider("first", 301L, "ANIME"))
        }
        assertTrue(globalCollision.isFailure)
        assertTrue(localSlotCollision.isFailure)
        assertEquals(1, dao.countProviderIdentities())
    }

    @Test
    fun `same numeric provider id is valid across Anime and Manga`() = runTest {
        val dao = database.mediaIdentityDao()
        dao.insertLocalIdentity(local("anime", "ANIME"))
        dao.insertLocalIdentity(local("manga", "MANGA"))
        dao.insertProviderIdentity(provider("anime", 400L, "ANIME"))
        dao.insertProviderIdentity(provider("manga", 400L, "MANGA"))
        assertEquals(2, dao.countProviderIdentities())
    }

    @Test
    fun `foreign key rejects provider row without local identity`() = runTest {
        val result = runCatching {
            database.mediaIdentityDao().insertProviderIdentity(
                provider("missing", 500L, "ANIME")
            )
        }
        assertTrue(result.isFailure)
        assertEquals(0, database.mediaIdentityDao().countProviderIdentities())
    }

    @Test
    fun `review states remain queryable and provider row can be removed explicitly`() = runTest {
        val dao = database.mediaIdentityDao()
        dao.insertLocalIdentity(local("local", "ANIME"))
        val active = provider("local", 600L, "ANIME")
        val rowId = dao.insertProviderIdentity(active)
        dao.insertIssue(issue("local", null, "ANIME", "UNRESOLVED"))
        dao.insertIssue(issue("local", 601L, "ANIME", "CONFLICTING"))
        dao.insertIssue(issue("local", 602L, "ANIME", "REJECTED"))

        assertEquals(1, dao.listIssues("UNRESOLVED", null).size)
        assertEquals(1, dao.listIssues("CONFLICTING", "ANIME").size)
        assertNotNull(dao.findIssue("local", "ANILIST", 602L, "ANIME", "REJECTED"))
        assertEquals(1, dao.removeProviderIdentity(active.copy(id = rowId)))
        assertEquals(0, dao.countProviderIdentities())
    }

    private fun local(id: String, type: String) = LocalMediaIdentityEntity(id, type, 1L, 1L)

    private fun provider(localId: String, providerId: Long, type: String) =
        ProviderMediaIdentityEntity(
            localMediaId = localId,
            provider = "ANILIST",
            providerMediaId = providerId,
            mediaType = type,
            mappingSource = "EXISTING_ANILIST_MIGRATION",
            verificationStatus = "EXACT",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

    private fun issue(localId: String, providerId: Long?, type: String, status: String) =
        ProviderMediaIdentityIssueEntity(
            localMediaId = localId,
            provider = "ANILIST",
            providerMediaId = providerId,
            mediaType = type,
            mappingSource = "EXISTING_ANILIST_MIGRATION",
            verificationStatus = status,
            reason = "fixture",
            sourceTable = null,
            sourceRowKey = null,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
}
