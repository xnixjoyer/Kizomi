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
        val source = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/mal/MalProviderMainScreen.kt",
        ).readText()

        assertTrue(source.contains("rememberNavController()"))
        assertTrue(source.contains("composable<MalNativeDetails>"))
        assertTrue(source.contains("MalNativeDetails(key.mediaType.name, key.malId)"))
        assertFalse(source.contains("detailsKey"))
        assertFalse(source.contains("MalDetailsScreen(\n                onBackClick = { detailsKey"))
    }

    private fun repositoryRoot(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
}
