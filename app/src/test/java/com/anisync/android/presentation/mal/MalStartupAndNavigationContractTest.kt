package com.anisync.android.presentation.mal

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalStartupAndNavigationContractTest {
    @Test
    fun `MAL startup restoration completes before the UI readiness gate opens`() {
        val root = repositoryRoot()
        val source = File(root, "app/src/main/java/com/anisync/android/MainActivity.kt").readText()

        val initialize = source.indexOf("providerCoordinator.initialize()")
        val callback = source.indexOf("handleMalOAuthRedirectForStartup(intent)")
        val restore = source.indexOf("malAuthRepository.resumePendingLogin()")
        val ready = source.indexOf("_providerStartupReady.value = true")

        assertTrue("provider initialization missing", initialize >= 0)
        assertTrue("cold-start MAL callback gate missing", callback > initialize)
        assertTrue("persistent MAL restoration missing", restore > callback)
        assertTrue("startup gate opens before restoration", ready > restore)
        assertFalse(
            "MAL restoration must not be launched without awaiting completion",
            source.contains("launch(Dispatchers.IO) { malAuthRepository.resumePendingLogin() }"),
        )
    }

    @Test
    fun `production MAL details use typed process-restorable navigation`() {
        val root = repositoryRoot()
        val graph = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/mal/MalSharedNavHost.kt",
        ).readText()

        assertTrue(graph.contains("composable<MalNativeDetails>"))
        assertTrue(graph.contains("MalNativeDetails(key.mediaType.name, key.malId)"))
        assertTrue(graph.contains("onBackClick = { navController.popBackStack() }"))
        assertFalse(graph.contains("detailsKey"))
    }

    @Test
    fun `MAL uses the shared adaptive app shell without AniList-only roots`() {
        val root = repositoryRoot()
        val entry = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/mal/MalProviderMainScreen.kt",
        ).readText()
        val shell = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/MainScreen.kt",
        ).readText()
        val graph = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/mal/MalSharedNavHost.kt",
        ).readText()

        assertTrue(entry.contains("MainScreen()"))
        assertFalse(entry.contains("NavigationBar("))
        assertFalse(entry.contains("rememberNavController()"))
        assertTrue(shell.contains("resolveProviderMainNavigation("))
        assertTrue(shell.contains("ActiveProvider.MAL_ONLY -> MalSharedNavHost("))
        assertTrue(graph.contains("composable<Library>"))
        assertTrue(graph.contains("composable<Discover>"))
        assertTrue(graph.contains("composable<Profile>"))
        assertFalse(graph.contains("composable<Feed>"))
        assertFalse(graph.contains("composable<Forum>"))
        assertFalse(graph.contains("AniSyncNavHost"))
    }

    @Test
    fun `AniList-only shell side effects are gated while MAL is active`() {
        val root = repositoryRoot()
        val shell = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/MainScreen.kt",
        ).readText()
        val viewModel = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/MainScreenViewModel.kt",
        ).readText()

        assertTrue(
            shell.count { false } == 0 &&
                shell.contains("if (activeProvider != ActiveProvider.ANILIST_ONLY) return@LaunchedEffect"),
        )
        assertTrue(viewModel.contains("provider.activeProvider != ActiveProvider.ANILIST_ONLY"))
        assertTrue(viewModel.contains("!provider.providerTrafficAllowed"))
    }

    private fun repositoryRoot(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
}
