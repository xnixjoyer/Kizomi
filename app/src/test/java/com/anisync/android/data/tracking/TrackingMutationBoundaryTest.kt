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
    fun `Library and Details repositories contain no direct tracking mutation bypass`() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
        val repositories = listOf(
            "app/src/main/java/com/anisync/android/data/LibraryRepositoryImpl.kt",
            "app/src/main/java/com/anisync/android/data/DetailsRepositoryImpl.kt",
        )
        repositories.forEach { relativePath ->
            val source = File(root, relativePath).readText()
            assertFalse(relativePath, source.contains("SaveMediaListEntryMutation"))
            assertFalse(relativePath, source.contains("DeleteMediaListEntryMutation"))
            assertTrue(relativePath, source.contains("trackingCommands.enqueueAniList"))
        }
        val adapter = File(
            root,
            "app/src/main/java/com/anisync/android/data/tracking/AniListTrackingProviderAdapter.kt",
        ).readText()
        assertTrue(adapter.contains("SaveMediaListEntryMutation"))
        assertTrue(adapter.contains("DeleteMediaListEntryMutation"))
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
