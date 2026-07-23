package com.rameshta.formready.core.data.repository

import com.rameshta.formready.core.data.local.PresetDao
import com.rameshta.formready.core.data.local.PresetEntity
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

enum class PresetTargetType {
    PHOTO,
    SIGNATURE,
    PDF,
}

data class PresetRecord(
    val id: String,
    val schemaVersion: Int = 1,
    val revision: Int = 1,
    val name: String,
    val targetType: PresetTargetType,
    val specificationJson: String,
    val sourceUrl: String? = null,
    val sourceCheckedAtEpochMillis: Long? = null,
    val isCustom: Boolean = true,
    val isFavourite: Boolean = false,
)

data class PresetSpecification(
    val maximumBytes: Long,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val maximumPages: Int? = null,
)

enum class PresetImportIssue {
    FILE_TOO_LARGE,
    DAMAGED_FILE,
    UNSUPPORTED_VERSION,
    MISSING_NAME,
    NAME_TOO_LONG,
    UNSUPPORTED_TYPE,
    INVALID_MAXIMUM_SIZE,
    INVALID_DIMENSIONS,
    INVALID_PAGE_LIMIT,
}

class PresetImportException(
    val issue: PresetImportIssue,
) : IllegalArgumentException(issue.name)

interface PresetRepository {
    fun observeAll(): Flow<List<PresetRecord>>

    suspend fun save(record: PresetRecord)

    suspend fun setFavourite(id: String, favourite: Boolean)

    suspend fun deleteCustom(id: String)

    fun export(record: PresetRecord): String

    fun parseImport(contents: String): PresetRecord

    fun specification(record: PresetRecord): PresetSpecification
}

class RoomPresetRepository @Inject constructor(
    private val dao: PresetDao,
) : PresetRepository {
    override fun observeAll(): Flow<List<PresetRecord>> =
        dao.observeAll().map { records -> records.map { it.toModel() } }

    override suspend fun save(record: PresetRecord) {
        require(record.name.isNotBlank() && record.name.length <= 80)
        parseSpecification(JSONObject(record.specificationJson), record.targetType)
        val entity = record.toEntity()
        if (dao.get(record.id) == null) {
            dao.insert(entity)
        } else {
            check(dao.update(entity) == 1)
        }
    }

    override suspend fun setFavourite(id: String, favourite: Boolean) {
        check(dao.setFavourite(id, favourite) == 1)
    }

    override suspend fun deleteCustom(id: String) {
        dao.deleteCustom(id)
    }

    override fun export(record: PresetRecord): String = JSONObject()
        .put("fileType", "FormReadyPreset")
        .put("schemaVersion", record.schemaVersion)
        .put("revision", record.revision)
        .put("id", record.id)
        .put("name", record.name)
        .put("targetType", record.targetType.name)
        .put("specification", JSONObject(record.specificationJson))
        .put("sourceUrl", record.sourceUrl ?: JSONObject.NULL)
        .put("sourceCheckedAtEpochMillis", record.sourceCheckedAtEpochMillis ?: JSONObject.NULL)
        .toString(2)

    override fun parseImport(contents: String): PresetRecord {
        if (contents.length > MAX_IMPORT_CHARS) fail(PresetImportIssue.FILE_TOO_LARGE)
        val root = runCatching { JSONObject(contents) }
            .getOrElse { fail(PresetImportIssue.DAMAGED_FILE) }
        if (root.optInt("schemaVersion", -1) != 1) {
            fail(PresetImportIssue.UNSUPPORTED_VERSION)
        }
        val targetType = root.optString("targetType")
            .let { stored -> PresetTargetType.entries.firstOrNull { it.name == stored } }
            ?: fail(PresetImportIssue.UNSUPPORTED_TYPE)
        val specificationObject = root.optJSONObject("specification")
            ?: fail(PresetImportIssue.DAMAGED_FILE)
        parseSpecification(specificationObject, targetType)
        val specification = specificationObject.toString()
        val name = root.optString("name").trim()
        if (name.isBlank()) fail(PresetImportIssue.MISSING_NAME)
        if (name.length > 80) fail(PresetImportIssue.NAME_TOO_LONG)
        return PresetRecord(
            id = UUID.randomUUID().toString(),
            revision = root.optInt("revision", 1).coerceAtLeast(1),
            name = name,
            targetType = targetType,
            specificationJson = specification,
            sourceUrl = root.optString("sourceUrl").takeIf { it.startsWith("https://") },
            sourceCheckedAtEpochMillis = root.optLong("sourceCheckedAtEpochMillis")
                .takeIf { it > 0L },
            isCustom = true,
        )
    }

    override fun specification(record: PresetRecord): PresetSpecification =
        parseSpecification(JSONObject(record.specificationJson), record.targetType)

    private fun parseSpecification(
        value: JSONObject,
        targetType: PresetTargetType,
    ): PresetSpecification {
        if (value.toString().length > MAX_SPECIFICATION_CHARS) {
            fail(PresetImportIssue.FILE_TOO_LARGE)
        }
        val maximumBytes = value.numberLong("maximumBytes")
            ?: fail(PresetImportIssue.INVALID_MAXIMUM_SIZE)
        if (maximumBytes !in 1..MAXIMUM_PRESET_BYTES) {
            fail(PresetImportIssue.INVALID_MAXIMUM_SIZE)
        }
        return when (targetType) {
            PresetTargetType.PHOTO, PresetTargetType.SIGNATURE -> {
                val width = value.numberInt("widthPx")
                    ?: fail(PresetImportIssue.INVALID_DIMENSIONS)
                val height = value.numberInt("heightPx")
                    ?: fail(PresetImportIssue.INVALID_DIMENSIONS)
                if (width !in 1..20_000 || height !in 1..20_000) {
                    fail(PresetImportIssue.INVALID_DIMENSIONS)
                }
                PresetSpecification(
                    maximumBytes = maximumBytes,
                    widthPx = width,
                    heightPx = height,
                )
            }
            PresetTargetType.PDF -> {
                val pages = value.numberInt("maximumPages")
                    ?: fail(PresetImportIssue.INVALID_PAGE_LIMIT)
                if (pages !in 1..250) fail(PresetImportIssue.INVALID_PAGE_LIMIT)
                PresetSpecification(maximumBytes = maximumBytes, maximumPages = pages)
            }
        }
    }

    private fun JSONObject.numberLong(key: String): Long? =
        (opt(key) as? Number)?.toLong()

    private fun JSONObject.numberInt(key: String): Int? =
        (opt(key) as? Number)?.toLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    private fun fail(issue: PresetImportIssue): Nothing = throw PresetImportException(issue)

    private fun PresetEntity.toModel() = PresetRecord(
        id = id,
        schemaVersion = schemaVersion,
        revision = revision,
        name = name,
        targetType = PresetTargetType.valueOf(targetType),
        specificationJson = specificationJson,
        sourceUrl = sourceUrl,
        sourceCheckedAtEpochMillis = sourceCheckedAtEpochMillis,
        isCustom = isCustom,
        isFavourite = isFavourite,
    )

    private fun PresetRecord.toEntity() = PresetEntity(
        id = id,
        schemaVersion = schemaVersion,
        revision = revision,
        name = name,
        targetType = targetType.name,
        specificationJson = specificationJson,
        sourceUrl = sourceUrl,
        sourceCheckedAtEpochMillis = sourceCheckedAtEpochMillis,
        isCustom = isCustom,
        isFavourite = isFavourite,
    )

    private companion object {
        const val MAX_IMPORT_CHARS = 64 * 1024
        const val MAX_SPECIFICATION_CHARS = 16 * 1024
        const val MAXIMUM_PRESET_BYTES = 200L * 1024L * 1024L
    }
}
