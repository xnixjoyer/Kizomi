package com.anisync.android.data.tracking

import com.anisync.android.data.util.safeApiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrackingMutationBoundaryTest {
    @Test
    fun `all production list mutations remain inside the single adapter boundary`() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
        val saveMutation = "Save" + "MediaListEntryMutation"
        val deleteMutation = "Delete" + "MediaListEntryMutation"
        val productionRoot = File(root, "app/src/main/java")
        val adapterPath =
            "app/src/main/java/com/anisync/android/data/tracking/AniListTrackingProviderAdapter.kt"

        productionRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .forEach { file ->
                val relativePath = file.relativeTo(root).invariantSeparatorsPath
                val source = file.readText()
                if (relativePath == adapterPath) {
                    assertTrue(relativePath, source.contains(saveMutation))
                    assertTrue(relativePath, source.contains(deleteMutation))
                } else {
                    assertFalse(relativePath, source.contains(saveMutation))
                    assertFalse(relativePath, source.contains(deleteMutation))
                }
            }

        val repositories = listOf(
            "app/src/main/java/com/anisync/android/data/LibraryRepositoryImpl.kt",
            "app/src/main/java/com/anisync/android/data/DetailsRepositoryImpl.kt",
        )
        repositories.forEach { relativePath ->
            val source = File(root, relativePath).readText()
            assertTrue(relativePath, source.contains("trackingCommands.enqueueAniList"))
        }
        assertFalse(
            File(root, "app/src/main/java/com/anisync/android/domain/LibraryRepository.kt")
                .readText()
                .contains("updateProgressLocal"),
        )
        assertFalse(
            File(root, "app/src/main/java/com/anisync/android/worker/EpisodeUpdateWorker.kt")
                .readText()
                .contains("updateProgressLocal"),
        )
    }

    @Test
    fun `structured cancellation never becomes a raw Result error`() = runTest {
        var propagated = false
        try {
            safeApiCall<Unit> { throw CancellationException("obsolete tracking request") }
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }
}
