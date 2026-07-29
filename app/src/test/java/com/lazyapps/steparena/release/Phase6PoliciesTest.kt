package com.lazyapps.steparena.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Phase6PoliciesTest {
    @Test fun onboardingVersionOnlyChangesForMaterialFlowChanges() {
        assertEquals(2, ONBOARDING_VERSION)
        assertTrue(ONBOARDING_VERSION > 0)
    }

    @Test fun csvEscapesRfc4180SpecialCharacters() {
        assertEquals("plain", csvCell("plain"))
        assertEquals("\"a,b\"", csvCell("a,b"))
        assertEquals("\"a\"\"b\"", csvCell("a\"b"))
        assertEquals("\"a\nb\"", csvCell("a\nb"))
    }

    @Test fun jsonEscapingProducesSafeMetadataStrings() {
        assertEquals("a\\\"b\\\\c\\n", escapeJson("a\"b\\c\n"))
    }

    @Test fun diagnosticsExcludeSensitiveHealthAndDeviceValues() {
        val output = safeDiagnosticLines(
            mapOf(
                "version" to "1.0.0",
                "trackingRequested" to "false",
                "height" to "170",
                "weight" to "60",
                "steps" to "12345",
                "serial" to "secret",
                "recordId" to "external",
            ),
        )
        assertTrue(output.contains("version: 1.0.0"))
        assertFalse(output.contains("170"))
        assertFalse(output.contains("12345"))
        assertFalse(output.contains("secret"))
        assertFalse(output.contains("external"))
    }

    @Test fun releaseComposableSourcesDoNotContainJapaneseStringLiterals() {
        val direct = File("src/main/java/com/lazyapps/steparena")
        val sourceRoot = if (direct.isDirectory) direct else File("app", direct.path)
        assertTrue("Release source root must exist", sourceRoot.isDirectory)
        val targets = listOf(File(sourceRoot, "feature"), File(sourceRoot, "app"))
            .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
        val japaneseLiteral = Regex(
            "\"[^\"\\r\\n]*[\\u3040-\\u30ff\\u3400-\\u9fff][^\"\\r\\n]*\"",
        )
        val violations = targets.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (japaneseLiteral.containsMatchIn(line)) {
                    "${file.relativeTo(sourceRoot)}:${index + 1}"
                } else {
                    null
                }
            }
        }
        assertEquals("Public UI strings must use Android resources", emptyList<String>(), violations)
    }
}
