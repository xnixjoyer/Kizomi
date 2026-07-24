package com.anisync.android.presentation.settings.provider

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAccountCoordinatorBoundaryTest {
    @Test
    fun `default actions call existing destructive coordinator APIs`() {
        val root = repositoryRoot()
        val source = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/settings/provider/" +
                "ProviderAccountActionCoordinator.kt",
        ).readText()

        assertTrue(source.contains("coordinator.disconnectAndDeleteAllLocalProviderData()"))
        assertTrue(source.contains("coordinator.prepareDestructiveProviderChange()"))
    }

    private fun repositoryRoot(): File = generateSequence(
        File(System.getProperty("user.dir")).absoluteFile,
    ) { current -> current.parentFile }
        .firstOrNull { candidate -> File(candidate, "app/src").isDirectory }
        ?: error("Unable to locate repository root from ${System.getProperty("user.dir")}")
}
