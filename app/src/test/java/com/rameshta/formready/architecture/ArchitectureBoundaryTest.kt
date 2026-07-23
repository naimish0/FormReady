package com.rameshta.formready.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureBoundaryTest {
    private val sourceRoot = File("src/main/java")

    @Test
    fun modelLayerRemainsPlatformIndependent() {
        val violations = kotlinSources("com/rameshta/formready/core/model")
            .filter { source -> source.readText().contains(ANDROID_IMPORT) }
            .map(File::getName)
            .toList()

        assertTrue(
            "core.model must remain free of Android APIs: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun coreLayersDoNotDependOnFeaturePackages() {
        val violations = kotlinSources("com/rameshta/formready/core")
            .filter { source -> source.readText().contains(FEATURE_IMPORT) }
            .map { source -> source.relativeTo(sourceRoot).path }
            .toList()

        assertTrue(
            "core packages must not depend on feature packages: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinSources(relativePath: String): Sequence<File> {
        val directory = sourceRoot.resolve(relativePath)
        check(directory.isDirectory) { "Missing source boundary: ${directory.path}" }
        return directory.walkTopDown().filter { file -> file.isFile && file.extension == "kt" }
    }

    private companion object {
        const val ANDROID_IMPORT = "import android."
        const val FEATURE_IMPORT = "import com.rameshta.formready.feature."
    }
}
