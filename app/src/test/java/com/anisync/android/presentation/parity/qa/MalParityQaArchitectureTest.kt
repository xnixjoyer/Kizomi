package com.anisync.android.presentation.parity.qa

import com.anisync.android.data.mal.api.MalCatalogRequestFactory
import com.anisync.android.data.mal.api.MalListRequestFactory
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.tracking.ProviderNetworkPolicy
import com.anisync.android.domain.tracking.TrackingAccountSelection
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingIdentitySelection
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingRouteResolver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalParityQaArchitectureTest {
    @Test
    fun `provider runtime exposes exactly one configured provider at a time`() {
        assertEquals(
            listOf(
                ActiveProvider.UNCONFIGURED,
                ActiveProvider.ANILIST_ONLY,
                ActiveProvider.MAL_ONLY,
            ),
            ActiveProvider.entries,
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
        assertNull(available.target?.blocker)

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
        val body = readSource(
            "app/src/main/java/com/anisync/android/presentation/mal/MalProviderMainScreen.kt",
        ).functionBody("fun MalProviderMainScreen()")

        assertTrue(body.containsCall("MainScreen"))
        assertFalse(body.containsCall("Scaffold"))
        assertFalse(body.containsCall("NavHost"))
        assertFalse(body.containsCall("rememberNavController"))
    }

    @Test
    fun `MAL request factories stay on the official HTTPS API host and reject unsafe paging URLs`() {
        val catalog = MalCatalogRequestFactory()
        val library = MalListRequestFactory()

        val search = catalog.search(
            mediaType = TrackingMediaType.ANIME,
            query = "test",
            limit = 1,
            offset = 0,
        )
        assertEquals("https", search.url.scheme)
        assertEquals("api.myanimelist.net", search.url.host)
        assertEquals("/v2/anime", search.url.encodedPath)

        val firstLibraryPage = library.firstPage(
            mediaType = TrackingMediaType.MANGA,
            limit = 1,
        )
        assertEquals("https", firstLibraryPage.url.scheme)
        assertEquals("api.myanimelist.net", firstLibraryPage.url.host)
        assertEquals("/v2/users/@me/mangalist", firstLibraryPage.url.encodedPath)

        assertNotNull(
            catalog.nextPage(
                TrackingMediaType.ANIME,
                "https://api.myanimelist.net/v2/anime?offset=1",
            ),
        )
        assertNull(
            catalog.nextPage(
                TrackingMediaType.ANIME,
                "https://example.invalid/v2/anime?offset=1",
            ),
        )
        assertNull(
            catalog.nextPage(
                TrackingMediaType.ANIME,
                "https://user@api.myanimelist.net/v2/anime?offset=1",
            ),
        )
        assertNull(
            catalog.nextPage(
                TrackingMediaType.ANIME,
                "https://api.myanimelist.net/v2/anime?offset=1#fragment",
            ),
        )
        assertNull(
            catalog.nextPage(
                TrackingMediaType.ANIME,
                "https://api.myanimelist.net/v2/manga?offset=1",
            ),
        )

        assertNotNull(
            library.nextPage(
                "https://api.myanimelist.net/v2/users/@me/animelist?offset=1",
            ),
        )
        assertNull(
            library.nextPage(
                "https://example.invalid/v2/users/@me/animelist?offset=1",
            ),
        )
        assertNull(
            library.nextPage(
                "https://user@api.myanimelist.net/v2/users/@me/animelist?offset=1",
            ),
        )
        assertNull(
            library.nextPage(
                "https://api.myanimelist.net/v2/users/@me/animelist?offset=1#fragment",
            ),
        )
    }

    @Test
    fun `destructive provider change retains the frozen purge fan-out and ordering`() {
        val source = readSource(
            "app/src/main/java/com/anisync/android/data/provider/ProviderSessionCoordinator.kt",
        )

        // These collaborator calls are the durable destructive-purge architecture contract. The
        // guard deliberately ignores formatting, line layout and unrelated helper implementation.
        val purgeBody = source.functionBody("private suspend fun purgeEveryProviderLocally()")
        listOf(
            """\bstopProviderWork\s*\(""",
            """\bmalOAuthSessions\s*\.\s*clearPending\s*\(""",
            """\bmalAccounts\s*\.\s*removeLocal\s*\(""",
            """\baniListAccounts\s*\.\s*clearAll\s*\(""",
            """\bapolloClient\s*\.\s*apolloStore\s*\.\s*clearAll\s*\(""",
            """\blibraryDao\s*\.\s*deleteAll\s*\(""",
            """\bmediaDetailsDao\s*\.\s*clear\s*\(""",
            """\buserProfileDao\s*\.\s*clear\s*\(""",
            """\bairingScheduleDao\s*\.\s*clearAll\s*\(""",
            """\btrackingDao\s*\.\s*purgeAllProviderBoundState\s*\(""",
            """\bmediaIdentityDao\s*\.\s*deleteAllProviderIdentities\s*\(""",
            """\bappSettings\s*\.\s*clearAccountScoped\s*\(""",
            """\bclearControllableCaches\s*\(""",
        ).forEach { pattern ->
            assertTrue(
                "Missing destructive-purge contract: $pattern",
                Regex(pattern).containsMatchIn(purgeBody),
            )
        }

        val disconnectBody = source.functionBody(
            "suspend fun disconnectAndDeleteAllLocalProviderData()",
        )
        val begin = disconnectBody.indexOfCall("beginPurge")
        val purge = disconnectBody.indexOfCall("purgeEveryProviderLocally")
        val finish = disconnectBody.indexOfCall("finishPurge")
        assertTrue(begin >= 0)
        assertTrue(purge > begin)
        assertTrue(finish > purge)
    }

    private fun readSource(path: String): String = File(repositoryRoot(), path).readText()

    private fun repositoryRoot(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }

    private fun String.functionBody(signature: String): String {
        val signatureStart = indexOf(signature)
        require(signatureStart >= 0) { "Function signature not found: $signature" }
        val openingBrace = indexOf('{', signatureStart + signature.length)
        require(openingBrace >= 0) { "Function body not found: $signature" }

        var depth = 0
        for (index in openingBrace until length) {
            when (this[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return substring(openingBrace + 1, index)
                }
            }
        }
        error("Unterminated function body: $signature")
    }

    private fun String.containsCall(name: String): Boolean = indexOfCall(name) >= 0

    private fun String.indexOfCall(name: String): Int =
        Regex("""\b${Regex.escape(name)}\s*\(""").find(this)?.range?.first ?: -1
}
