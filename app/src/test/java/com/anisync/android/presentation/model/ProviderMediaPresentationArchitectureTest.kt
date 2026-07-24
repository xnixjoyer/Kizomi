package com.anisync.android.presentation.model

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMediaPresentationArchitectureTest {
    @Test
    fun `neutral model and shared composable do not import provider transport types`() {
        val root = repositoryRoot()
        val model = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/model/MediaListItemPresentation.kt",
        ).readText()
        val component = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/components/ProviderMediaListItem.kt",
        ).readText()
        val neutralSource = model + component

        assertFalse(neutralSource.contains("data.mal.api"))
        assertFalse(neutralSource.contains("com.anisync.android.type"))
        assertFalse(neutralSource.contains("com.apollographql"))
        assertFalse(neutralSource.contains("graphql"))
        assertTrue(component.contains("onClick: (ProviderMediaIdentity) -> Unit"))
    }

    @Test
    fun `provider transformations remain in the adapter boundary`() {
        val root = repositoryRoot()
        val adapters = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/adapters/MediaListItemPresentationAdapters.kt",
        ).readText()

        assertTrue(adapters.contains("LibraryEntry.toMediaListItemPresentation"))
        assertTrue(adapters.contains("MalLibraryItem.toMediaListItemPresentation"))
        assertTrue(adapters.contains("ProviderMediaIdentity.AniList"))
        assertTrue(adapters.contains("ProviderMediaIdentity.MyAnimeList"))
    }

    @Test
    fun `AniList and MAL production paths use the same neutral card`() {
        val root = repositoryRoot()
        val aniList = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/library/components/LibraryEmptyStates.kt",
        ).readText()
        val mal = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/mal/MalSharedRootScreens.kt",
        ).readText()

        assertTrue(aniList.contains("ProviderMediaListItem("))
        assertTrue(aniList.contains("ProviderMediaIdentity.AniList"))
        assertTrue(mal.contains("ProviderMediaListItem("))
        assertTrue(mal.contains("ProviderMediaIdentity.MyAnimeList"))
        assertFalse(mal.contains("com.anisync.android.domain.LibraryEntry"))
        assertFalse(aniList.contains("data.mal.api"))
    }

    private fun repositoryRoot(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
}
