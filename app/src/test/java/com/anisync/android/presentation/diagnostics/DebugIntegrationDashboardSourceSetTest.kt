package com.anisync.android.presentation.diagnostics

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugIntegrationDashboardSourceSetTest {
    @Test
    fun `dashboard implementation route and visible resources stay out of release`() {
        val root = repositoryRoot()
        val relativeScreen =
            "com/anisync/android/presentation/diagnostics/DebugIntegrationDashboardScreen.kt"
        val relativeViewModel =
            "com/anisync/android/presentation/diagnostics/DebugIntegrationDashboardViewModel.kt"
        val diagnosticsResource = "strings_integration_diagnostics.xml"

        assertTrue(File(root, "app/src/debug/java/$relativeScreen").isFile)
        assertTrue(File(root, "app/src/debug/java/$relativeViewModel").isFile)
        assertTrue(File(root, "app/src/debug/res/values/$diagnosticsResource").isFile)

        listOf("main", "release").forEach { sourceSet ->
            assertFalse(File(root, "app/src/$sourceSet/java/$relativeScreen").exists())
            assertFalse(File(root, "app/src/$sourceSet/java/$relativeViewModel").exists())
            assertFalse(File(root, "app/src/$sourceSet/res/values/$diagnosticsResource").exists())

            val sourceRoot = File(root, "app/src/$sourceSet")
            if (sourceRoot.isDirectory) {
                val leakedReference = sourceRoot.walkTopDown()
                    .filter(File::isFile)
                    .filter { file -> file.extension in setOf("kt", "java", "xml") }
                    .any { file ->
                        val text = file.readText()
                        text.contains("DebugIntegrationDashboardScreen") ||
                            text.contains("DebugIntegrationDashboardViewModel") ||
                            text.contains("diagnostics_screen_title")
                    }
                assertFalse("Dashboard implementation or visible resource leaked into $sourceSet", leakedReference)
            }
        }
    }

    @Test
    fun `every supported debug locale contains real translatable dashboard text`() {
        val root = repositoryRoot()
        val debugResources = File(root, "app/src/debug/res")
        val defaultText = File(
            debugResources,
            "values/strings_integration_diagnostics.xml",
        ).readText()

        assertFalse(defaultText.contains("translatable=\"false\""))
        listOf("ar", "de", "es", "fa", "fr", "peo", "pt", "ru", "ta").forEach { locale ->
            val translated = File(
                debugResources,
                "values-$locale/strings_integration_diagnostics.xml",
            )
            assertTrue("Missing debug translation for $locale", translated.isFile)
            val translatedText = translated.readText()
            assertTrue(translatedText.contains("name=\"diagnostics_screen_title\""))
            assertFalse(translatedText.contains("translatable=\"false\""))
            assertNotEquals("Locale $locale still contains the English fallback file", defaultText, translatedText)
        }
    }

    @Test
    fun `debug snapshot source contains no provider network client and keeps unavailable facts unknown`() {
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
        assertTrue(source.contains("sourceRevision = null"))
        assertTrue(
            source.contains("pendingOAuthTransaction = DiagnosticAvailability.UNKNOWN"),
        )
        assertFalse(source.contains("pendingOAuthTransaction = DiagnosticAvailability.AVAILABLE"))
    }

    private fun repositoryRoot(): File = generateSequence(
        File(System.getProperty("user.dir")).absoluteFile,
    ) { current -> current.parentFile }
        .firstOrNull { candidate -> File(candidate, "app/src").isDirectory }
        ?: error("Unable to locate repository root from ${System.getProperty("user.dir")}")
}
