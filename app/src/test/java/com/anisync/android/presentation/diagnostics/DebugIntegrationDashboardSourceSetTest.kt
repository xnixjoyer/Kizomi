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
                assertFalse(
                    "Dashboard implementation or visible resource leaked into $sourceSet",
                    leakedReference,
                )
            }
        }
    }

    @Test
    fun `every supported locale contains real account and debug translations`() {
        val root = repositoryRoot()
        val debugResources = File(root, "app/src/debug/res")
        val mainResources = File(root, "app/src/main/res")
        val defaultDebug = File(
            debugResources,
            "values/strings_integration_diagnostics.xml",
        ).readText()
        val defaultAccount = File(
            mainResources,
            "values/strings_mal_account_diagnostics.xml",
        ).readText()

        assertFalse(defaultDebug.contains("translatable=\"false\""))
        assertFalse(defaultAccount.contains("translatable=\"false\""))
        listOf("ar", "de", "es", "fa", "fr", "peo", "pt", "ru", "ta").forEach { locale ->
            val translatedDebug = File(
                debugResources,
                "values-$locale/strings_integration_diagnostics.xml",
            )
            val translatedAccount = File(
                mainResources,
                "values-$locale/strings_mal_account_diagnostics.xml",
            )
            assertTrue("Missing debug translation for $locale", translatedDebug.isFile)
            assertTrue("Missing account translation for $locale", translatedAccount.isFile)

            val debugText = translatedDebug.readText()
            val accountText = translatedAccount.readText()
            assertTrue(debugText.contains("name=\"diagnostics_screen_title\""))
            assertTrue(accountText.contains("name=\"provider_account_screen_title\""))
            assertFalse(debugText.contains("translatable=\"false\""))
            assertFalse(accountText.contains("translatable=\"false\""))
            assertNotEquals("Debug locale $locale is still the English file", defaultDebug, debugText)
            assertNotEquals("Account locale $locale is still the English file", defaultAccount, accountText)
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
            "Retrofit",
            "AuthenticatedMalClient",
            "MalApiService",
            ".execute(",
            ".query(",
            ".mutation(",
            ".enqueue(",
        ).forEach { forbidden ->
            assertFalse("Network symbol found in local snapshot source: $forbidden", source.contains(forbidden))
        }
        assertTrue(source.contains("sourceRevision = null"))
        assertTrue(
            source.contains("pendingOAuthTransaction = DiagnosticAvailability.UNKNOWN"),
        )
        assertFalse(source.contains("pendingOAuthTransaction = DiagnosticAvailability.AVAILABLE"))
    }

    @Test
    fun `diagnostics implementation contains no direct raw logging path`() {
        val root = repositoryRoot()
        val diagnosticsFiles = listOf(
            File(root, "app/src/main/java/com/anisync/android/data/diagnostics"),
            File(root, "app/src/main/java/com/anisync/android/presentation/diagnostics"),
            File(root, "app/src/debug/java/com/anisync/android/data/diagnostics"),
            File(root, "app/src/debug/java/com/anisync/android/presentation/diagnostics"),
        ).flatMap { directory ->
            if (directory.isDirectory) {
                directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            } else {
                emptyList()
            }
        }

        listOf("Log.", "Timber.", "println(", "print(").forEach { forbidden ->
            val offender = diagnosticsFiles.firstOrNull { it.readText().contains(forbidden) }
            assertFalse("Raw logging symbol $forbidden found in ${offender?.path}", offender != null)
        }
        val logFormatter = File(
            root,
            "app/src/main/java/com/anisync/android/presentation/diagnostics/DiagnosticRedactor.kt",
        ).readText()
        assertTrue(logFormatter.contains("object SanitizedDiagnosticLogFormatter"))
        assertTrue(logFormatter.contains("SanitizedDiagnosticExporter.format(snapshot)"))
    }

    private fun repositoryRoot(): File = generateSequence(
        File(System.getProperty("user.dir")).absoluteFile,
    ) { current -> current.parentFile }
        .firstOrNull { candidate -> File(candidate, "app/src").isDirectory }
        ?: error("Unable to locate repository root from ${System.getProperty("user.dir")}")
}
