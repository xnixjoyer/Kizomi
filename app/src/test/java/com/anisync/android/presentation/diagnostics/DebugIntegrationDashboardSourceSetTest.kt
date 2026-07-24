package com.anisync.android.presentation.diagnostics

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugIntegrationDashboardSourceSetTest {
    @Test
    fun `dashboard implementation exists only in debug source set`() {
        val root = repositoryRoot()
        val relativeScreen =
            "com/anisync/android/presentation/diagnostics/DebugIntegrationDashboardScreen.kt"
        val relativeViewModel =
            "com/anisync/android/presentation/diagnostics/DebugIntegrationDashboardViewModel.kt"

        assertTrue(File(root, "app/src/debug/java/$relativeScreen").isFile)
        assertTrue(File(root, "app/src/debug/java/$relativeViewModel").isFile)
        assertFalse(File(root, "app/src/main/java/$relativeScreen").exists())
        assertFalse(File(root, "app/src/main/java/$relativeViewModel").exists())
        assertFalse(File(root, "app/src/release/java/$relativeScreen").exists())
        assertFalse(File(root, "app/src/release/java/$relativeViewModel").exists())
    }

    private fun repositoryRoot(): File = generateSequence(
        File(System.getProperty("user.dir")).absoluteFile,
    ) { current -> current.parentFile }
        .firstOrNull { candidate -> File(candidate, "app/src").isDirectory }
        ?: error("Unable to locate repository root from ${System.getProperty("user.dir")}")
}
