package com.rameshta.formready.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "processing_jobs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("projectId"),
        Index("status"),
        Index("updatedAtEpochMillis"),
    ],
)
data class ProcessingJobEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val type: String,
    val status: String,
    val processingPlanJson: String,
    val stagedInputRelativePath: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val errorCode: String?,
)

@Entity(
    tableName = "presets",
    indices = [Index(value = ["name", "revision"], unique = true)],
)
data class PresetEntity(
    @PrimaryKey val id: String,
    val schemaVersion: Int,
    val revision: Int,
    val name: String,
    val targetType: String,
    val specificationJson: String,
    val sourceUrl: String?,
    val sourceCheckedAtEpochMillis: Long?,
    val isCustom: Boolean,
    val isFavourite: Boolean,
)

@Entity(
    tableName = "output_artifacts",
    foreignKeys = [
        ForeignKey(
            entity = ProcessingJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId")],
)
data class OutputArtifactEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val byteCount: Long,
    val createdAtEpochMillis: Long,
    val accessState: String,
)
