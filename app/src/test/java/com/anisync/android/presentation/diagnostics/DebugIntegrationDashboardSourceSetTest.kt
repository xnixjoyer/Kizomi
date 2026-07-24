package com.anisync.android.presentation.diagnostics

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugIntegrationDashboardSourceSetTest {
    @Test
    fun `dashboard implementation and route stay out of release sources`() {
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

        listOf("main", "release").forEach { sourceSet ->
            val sourceRoot = File(root, "app/src/$sourceSet/java")
            if (sourceRoot.isDirectory) {
                val routeReferences = sourceRoot.walkTopDown()
                    .filter(File::isFile)
                    .filter { file -> file.extension == "kt" || file.extension == "java" }
                    .any { file ->
                        file.readText().contains("DebugIntegrationDashboardScreen") ||
                            file.readText().contains("DebugIntegrationDashboardViewModel")
                    }
                assertFalse("Dashboard route leaked into $sourceSet", routeReferences)
            }
        }
    }

    @Test
    fun `debug snapshot source contains no provider network client`() {
        val root = repositoryRoot()
        val source = File(
            root,
            "app/src/debug/java/com/anisync/android/data/diagnostics/" +
                "DebugIntegrationDiagnosticsSnapshotSource.kt",
        ).readText()

        listOf(
            "ApolloClient",
            "OkHttpClient",
            "AuthenticatedMalClient",
            ".execute(",
            ".query(",
            ".mutation(",
        ).forEach { forbidden ->
            assertFalse("Network symbol found in local snapshot source: $forbidden", source.contains(forbidden))
        }
    }

    private fun repositoryRoot(): File = generateSequence(
        File(System.getProperty("user.dir")).absoluteFile,
    ) { current -> current.parentFile }
        .firstOrNull { candidate -> File(candidate, "app/src").isDirectory }
        ?: error("Unable to locate repository root from ${System.getProperty("user.dir")}")
}
