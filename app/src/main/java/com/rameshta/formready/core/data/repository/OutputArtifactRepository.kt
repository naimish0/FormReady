package com.rameshta.formready.core.data.repository

import com.rameshta.formready.core.data.local.OutputArtifactDao
import com.rameshta.formready.core.data.local.OutputArtifactEntity
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.core.model.Readiness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

interface OutputArtifactRepository {
    suspend fun add(artifact: OutputArtifact)

    fun observeLatestForJob(jobId: UUID): Flow<OutputArtifact?>

    fun observeAll(): Flow<List<OutputArtifact>>
}

class RoomOutputArtifactRepository @Inject constructor(
    private val dao: OutputArtifactDao,
) : OutputArtifactRepository {
    override suspend fun add(artifact: OutputArtifact) {
        dao.insert(
            OutputArtifactEntity(
                id = artifact.id.toString(),
                jobId = artifact.jobId.toString(),
                uri = artifact.uri,
                displayName = artifact.displayName,
                mimeType = artifact.mimeType,
                byteCount = artifact.byteCount,
                widthPx = artifact.widthPx,
                heightPx = artifact.heightPx,
                dpi = artifact.dpi,
                readiness = artifact.readiness.name,
                validationJson = artifact.validationJson,
                createdAtEpochMillis = artifact.createdAtEpochMillis,
                accessState = "AVAILABLE",
            ),
        )
    }

    override fun observeLatestForJob(jobId: UUID): Flow<OutputArtifact?> =
        dao.observeLatestForJob(jobId.toString()).map { entity -> entity?.toModel() }

    override fun observeAll(): Flow<List<OutputArtifact>> =
        dao.observeAll().map { entities -> entities.map { it.toModel() } }

    private fun OutputArtifactEntity.toModel() = OutputArtifact(
        id = UUID.fromString(id),
        jobId = UUID.fromString(jobId),
        uri = uri,
        displayName = displayName,
        mimeType = mimeType,
        byteCount = byteCount,
        widthPx = widthPx,
        heightPx = heightPx,
        dpi = dpi,
        readiness = Readiness.valueOf(readiness),
        validationJson = validationJson,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
