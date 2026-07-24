package com.anisync.android.presentation.parity.qa

import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.tracking.ProviderNetworkPolicy
import com.anisync.android.domain.tracking.TrackingAccountSelection
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingIdentitySelection
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingRouteResolver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalParityQaArchitectureTest {
    @Test
    fun `provider runtime exposes exactly one configured provider at a time`() {
        val source = readSource(
            "app/src/main/java/com/anisync/android/domain/provider/ProviderState.kt",
        )
        val enumBody = Regex(
            pattern = """enum class ActiveProvider\s*\{(.*?);""",
            option = RegexOption.DOT_MATCHES_ALL,
        ).find(source)?.groupValues?.get(1)
        assertNotNull("ActiveProvider enum declaration was not found", enumBody)

        val constants = requireNotNull(enumBody)
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)

        assertEquals(
            listOf("UNCONFIGURED", "ANILIST_ONLY", "MAL_ONLY"),
            constants,
        )
    }

    @Test
    fun `tracking stays on the active MAL target and never falls back to AniList`() {
        val resolver = TrackingRouteResolver()
        val accounts = TrackingAccountSelection(
            aniListAccountId = "anilist-account",
            myAnimeListAccountId = "mal-account",
        )
        val identities = TrackingIdentitySelection(
            aniListId = 101L,
            myAnimeListId = 202L,
        )

        val available = resolver.resolve(
            activeProvider = ActiveProvider.MAL_ONLY,
            accounts = accounts,
            identities = identities,
            network = ProviderNetworkPolicy(
                allowAniList = true,
                allowMyAnimeList = true,
            ),
        )
        assertEquals(TrackingProvider.MYANIMELIST, available.target?.provider)
        assertEquals("mal-account", available.target?.providerAccountId)
        assertEquals(202L, available.target?.providerMediaId)
        assertEquals(null, available.target?.blocker)

        val blocked = resolver.resolve(
            activeProvider = ActiveProvider.MAL_ONLY,
            accounts = accounts,
            identities = identities,
            network = ProviderNetworkPolicy(
                allowAniList = true,
                allowMyAnimeList = false,
            ),
        )
        assertEquals(TrackingProvider.MYANIMELIST, blocked.target?.provider)
        assertEquals(TrackingFailureKind.NETWORK_BLOCKED, blocked.target?.blocker)
        assertFalse(blocked.fullyExecutable)
    }

    @Test
    fun `MAL compatibility entry delegates to the shared app shell`() {
        val source = readSource(
            "app/src/main/java/com/anisync/android/presentation/mal/MalProviderMainScreen.kt",
        )
        val body = Regex(
            pattern = """fun MalProviderMainScreen\(\)\s*\{([^}]*)}""",
            option = RegexOption.DOT_MATCHES_ALL,
        ).find(source)?.groupValues?.get(1)?.trim()

        assertEquals("MainScreen()", body)
        assertFalse(source.substringBefore("data class MalLibraryUiState").contains("Scaffold("))
        assertFalse(source.substringBefore("data class MalLibraryUiState").contains("rememberNavController("))
    }

    @Test
    fun `MAL request factories stay on the official HTTPS API host and validate paging URLs`() {
        val catalog = readSource(
            "app/src/main/java/com/anisync/android/data/mal/api/MalCatalogApi.kt",
        )
        val library = readSource(
            "app/src/main/java/com/anisync/android/data/mal/api/MalListApi.kt",
        )
        val combined = catalog + library

        assertTrue(catalog.contains("https://api.myanimelist.net/v2/"))
        assertTrue(library.contains("https://api.myanimelist.net/v2/"))
        assertFalse(combined.contains("http://"))
        assertFalse(combined.contains("Jsoup"))
        assertFalse(combined.contains("WebView"))
        assertFalse(combined.contains("CookieManager"))

        listOf(catalog, library).forEach { source ->
            assertTrue(source.contains("next.scheme != baseUrl.scheme"))
            assertTrue(source.contains("next.host != baseUrl.host"))
            assertTrue(source.contains("next.username.isNotEmpty()"))
            assertTrue(source.contains("next.password.isNotEmpty()"))
            assertTrue(source.contains("next.fragment != null"))
        }
    }

    @Test
    fun `destructive provider change purges credentials provider data shared state work and caches`() {
        val source = readSource(
            "app/src/main/java/com/anisync/android/data/provider/ProviderSessionCoordinator.kt",
        )
        val purgeStart = source.indexOf("private suspend fun purgeEveryProviderLocally()")
        val purgeEnd = source.indexOf("private suspend fun purgeAniListProviderLocally()", purgeStart)
        assertTrue("purgeEveryProviderLocally was not found", purgeStart >= 0)
        assertTrue("purgeEveryProviderLocally boundary was not found", purgeEnd > purgeStart)
        val purgeBody = source.substring(purgeStart, purgeEnd)

        listOf(
            "stopProviderWork()",
            "malOAuthSessions.clearPending(null)",
            "malAccounts.removeLocal",
            "aniListAccounts.clearAll()",
            "apolloClient.apolloStore.clearAll()",
            "libraryDao.deleteAll()",
            "mediaDetailsDao.clear()",
            "userProfileDao.clear()",
            "airingScheduleDao.clearAll()",
            "trackingDao.purgeAllProviderBoundState()",
            "mediaIdentityDao.deleteAllProviderIdentities()",
            "appSettings.clearAccountScoped()",
            "clearControllableCaches()",
        ).forEach { marker ->
            assertTrue("Missing destructive-purge marker: $marker", purgeBody.contains(marker))
        }

        val disconnectStart = source.indexOf("suspend fun disconnectAndDeleteAllLocalProviderData()")
        val disconnectEnd = source.indexOf("suspend fun prepareDestructiveProviderChange()", disconnectStart)
        val disconnectBody = source.substring(disconnectStart, disconnectEnd)
        assertTrue(disconnectBody.indexOf("stateStore.beginPurge()") < disconnectBody.indexOf("purgeEveryProviderLocally()"))
        assertTrue(disconnectBody.indexOf("purgeEveryProviderLocally()") < disconnectBody.indexOf("stateStore.finishPurge()"))
    }

    private fun readSource(path: String): String = File(repositoryRoot(), path).readText()

    private fun repositoryRoot(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
}
