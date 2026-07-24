package com.anisync.android.presentation.mal

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalDiscoverDetailsArchitectureTest {
    @Test
    fun `shared discover and details composables import no provider transport DTO`() {
        val root = repositoryRoot()
        val sharedDirectories = listOf(
            "app/src/main/java/com/anisync/android/presentation/provider/discover",
            "app/src/main/java/com/anisync/android/presentation/provider/details",
        )
        val source = sharedDirectories
            .flatMap { path ->
                File(root, path).walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
            }
            .joinToString("\n") { it.readText() }

        assertFalse(source.contains("data.mal.api"))
        assertFalse(source.contains("data.anilist"))
        assertFalse(source.contains("com.apollographql"))
        assertFalse(source.contains("api.myanimelist.net"))
        assertFalse(source.contains("graphql", ignoreCase = true))
    }

    @Test
    fun `MAL shared routes use only MAL repository and neutral presentation`() {
        val root = repositoryRoot()
        val files = listOf(
            "app/src/main/java/com/anisync/android/presentation/mal/MalCatalogSharedDiscoverViewModel.kt",
            "app/src/main/java/com/anisync/android/presentation/mal/MalCatalogSharedDiscoverScreen.kt",
            "app/src/main/java/com/anisync/android/presentation/mal/MalDetailsSharedScreen.kt",
            "app/src/main/java/com/anisync/android/presentation/mal/MalCatalogPresentationAdapters.kt",
        )
        val source = files.joinToString("\n") { File(root, it).readText() }

        assertTrue(source.contains("MalCatalogRepository"))
        assertTrue(source.contains("ProviderMediaIdentity"))
        assertFalse(source.contains("Apollo"))
        assertFalse(source.contains("AniListClient"))
        assertFalse(source.contains("DetailsRepositoryImpl"))
        assertFalse(source.contains("DiscoverRepositoryImpl"))
    }

    @Test
    fun `existing AniList discover and details production paths remain present`() {
        val root = repositoryRoot()

        assertTrue(
            File(
                root,
                "app/src/main/java/com/anisync/android/presentation/discover/DiscoverScreen.kt",
            ).isFile,
        )
        assertTrue(
            File(
                root,
                "app/src/main/java/com/anisync/android/presentation/details/MediaDetailsScreen.kt",
            ).isFile,
        )
    }

    private fun repositoryRoot(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
}
