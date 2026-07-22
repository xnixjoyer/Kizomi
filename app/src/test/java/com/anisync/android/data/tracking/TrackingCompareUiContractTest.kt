package com.anisync.android.data.tracking

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingCompareUiContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `compare UI exposes explicit directions preview safety result and pause resume actions`() {
        val screen = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/settings/TrackingCenterScreen.kt",
        ).readText()
        val viewModel = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/settings/TrackingCenterViewModel.kt",
        ).readText()
        val strings = File(
            root,
            "app/src/main/res/values/tracking_compare_strings.xml",
        ).readText()

        listOf(
            "TrackingMediaType.ANIME",
            "TrackingMediaType.MANGA",
            "TrackingProvider.ANILIST",
            "TrackingProvider.MYANIMELIST",
            "previewMissingOnly",
            "executeMissingOnly",
            "pauseMissingOnly",
            "refreshMissingOnly",
            "ReconciliationItemRow",
            "CircularProgressIndicator",
        ).forEach { required -> assertTrue("Missing UI contract: $required", required in screen || required in viewModel) }
        listOf(
            "Only entries absent from the target",
            "existing entries are never updated or deleted",
            "Create missing entries only",
            "Unmapped",
            "Blocked",
            "Failed",
        ).forEach { required -> assertTrue("Missing user-facing safety text: $required", required in strings) }
    }
}
