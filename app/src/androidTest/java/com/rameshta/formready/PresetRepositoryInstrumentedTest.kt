package com.rameshta.formready

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.formready.core.data.local.PresetDao
import com.rameshta.formready.core.data.local.PresetEntity
import com.rameshta.formready.core.data.repository.PresetImportException
import com.rameshta.formready.core.data.repository.PresetImportIssue
import com.rameshta.formready.core.data.repository.PresetRecord
import com.rameshta.formready.core.data.repository.PresetTargetType
import com.rameshta.formready.core.data.repository.RoomPresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetRepositoryInstrumentedTest {
    private val repository = RoomPresetRepository(NoOpPresetDao)

    @Test
    fun importCreatesNewCustomIdentityAndKeepsOnlyHttpsSource() {
        val imported = repository.parseImport(
            """
            {
              "schemaVersion": 1,
              "revision": 4,
              "id": "untrusted-id",
              "name": " My preset ",
              "targetType": "PHOTO",
              "specification": {"maximumBytes": 200000, "widthPx": 600, "heightPx": 800},
              "sourceUrl": "http://insecure.example/rules"
            }
            """.trimIndent(),
        )

        assertEquals("My preset", imported.name)
        assertEquals(4, imported.revision)
        assertEquals(PresetTargetType.PHOTO, imported.targetType)
        assertEquals(true, imported.isCustom)
        assertNull(imported.sourceUrl)
        check(imported.id != "untrusted-id")
    }

    @Test(expected = IllegalArgumentException::class)
    fun importRejectsUnsafeDimensions() {
        repository.parseImport(
            """
            {
              "schemaVersion": 1,
              "name": "Unsafe",
              "targetType": "PHOTO",
              "specification": {"maximumBytes": 200000, "widthPx": 50000, "heightPx": 800}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun exportCanBeImportedAsFriendlyPresetFile() {
        val original = PresetRecord(
            id = "local-preset",
            name = "Application photo",
            targetType = PresetTargetType.PHOTO,
            specificationJson =
                """{"maximumBytes":200000,"widthPx":600,"heightPx":800}""",
        )

        val exported = repository.export(original)
        val imported = repository.parseImport(exported)

        check(exported.contains("\"fileType\": \"FormReadyPreset\""))
        assertEquals(original.name, imported.name)
        assertEquals(original.targetType, imported.targetType)
        assertEquals(600, repository.specification(imported).widthPx)
        check(imported.id != original.id)
    }

    @Test
    fun importReportsUnsupportedVersionPrecisely() {
        val error = runCatching {
            repository.parseImport(
                """
                {
                  "schemaVersion": 99,
                  "name": "Future preset",
                  "targetType": "PDF",
                  "specification": {"maximumBytes": 1000000, "maximumPages": 10}
                }
                """.trimIndent(),
            )
        }.exceptionOrNull() as PresetImportException

        assertEquals(PresetImportIssue.UNSUPPORTED_VERSION, error.issue)
    }

    @Test
    fun importReportsMissingNamePrecisely() {
        val error = runCatching {
            repository.parseImport(
                """
                {
                  "schemaVersion": 1,
                  "targetType": "PDF",
                  "specification": {"maximumBytes": 1000000, "maximumPages": 10}
                }
                """.trimIndent(),
            )
        }.exceptionOrNull() as PresetImportException

        assertEquals(PresetImportIssue.MISSING_NAME, error.issue)
    }

    private object NoOpPresetDao : PresetDao {
        override fun observeAll(): Flow<List<PresetEntity>> = flowOf(emptyList())
        override suspend fun insert(preset: PresetEntity) = Unit
        override suspend fun get(id: String): PresetEntity? = null
        override suspend fun update(preset: PresetEntity): Int = 0
        override suspend fun setFavourite(id: String, favourite: Boolean): Int = 0
        override suspend fun deleteCustom(id: String): Int = 0
    }
}
