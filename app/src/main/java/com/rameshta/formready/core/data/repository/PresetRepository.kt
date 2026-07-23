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

interface PresetRepository {
    fun observeAll(): Flow<List<PresetRecord>>

    suspend fun save(record: PresetRecord)

    suspend fun setFavourite(id: String, favourite: Boolean)

    suspend fun deleteCustom(id: String)

    fun export(record: PresetRecord): String

    fun parseImport(json: String): PresetRecord
}

class RoomPresetRepository @Inject constructor(
    private val dao: PresetDao,
) : PresetRepository {
    override fun observeAll(): Flow<List<PresetRecord>> =
        dao.observeAll().map { records -> records.map { it.toModel() } }

    override suspend fun save(record: PresetRecord) {
        require(record.name.isNotBlank() && record.name.length <= 80)
        validateSpecification(record.specificationJson, record.targetType)
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
        .put("schemaVersion", record.schemaVersion)
        .put("revision", record.revision)
        .put("id", record.id)
        .put("name", record.name)
        .put("targetType", record.targetType.name)
        .put("specification", JSONObject(record.specificationJson))
        .put("sourceUrl", record.sourceUrl ?: JSONObject.NULL)
        .put("sourceCheckedAtEpochMillis", record.sourceCheckedAtEpochMillis ?: JSONObject.NULL)
        .toString(2)

    override fun parseImport(json: String): PresetRecord {
        require(json.length <= MAX_IMPORT_CHARS)
        val root = JSONObject(json)
        require(root.getInt("schemaVersion") == 1)
        val targetType = PresetTargetType.valueOf(root.getString("targetType"))
        val specification = root.getJSONObject("specification").toString()
        validateSpecification(specification, targetType)
        val name = root.getString("name").trim()
        require(name.isNotBlank() && name.length <= 80)
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

    private fun validateSpecification(json: String, targetType: PresetTargetType) {
        require(json.length <= MAX_SPECIFICATION_CHARS)
        val value = JSONObject(json)
        val maximumBytes = value.optLong("maximumBytes", 0L)
        require(maximumBytes in 1..MAXIMUM_PRESET_BYTES)
        require(value.has("widthPx") == value.has("heightPx"))
        if (value.has("widthPx")) require(value.getInt("widthPx") in 1..20_000)
        if (value.has("heightPx")) require(value.getInt("heightPx") in 1..20_000)
        if (value.has("maximumPages")) {
            require(targetType == PresetTargetType.PDF)
            require(value.getInt("maximumPages") in 1..250)
        }
    }

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
