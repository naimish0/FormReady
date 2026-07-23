package com.rameshta.formready.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity)
}

@Dao
interface ProcessingJobDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(job: ProcessingJobEntity)

    @Query("SELECT * FROM processing_jobs WHERE id = :id")
    suspend fun get(id: String): ProcessingJobEntity?

    @Query("SELECT * FROM processing_jobs WHERE id = :id")
    fun observe(id: String): Flow<ProcessingJobEntity?>

    @Query(
        """
        UPDATE processing_jobs
        SET status = :newStatus,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            errorCode = :errorCode
        WHERE id = :id AND status = :expectedStatus
        """,
    )
    suspend fun transition(
        id: String,
        expectedStatus: String,
        newStatus: String,
        updatedAtEpochMillis: Long,
        errorCode: String?,
    ): Int

    @Query("SELECT * FROM processing_jobs ORDER BY updatedAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ProcessingJobEntity>>

    @Query("UPDATE processing_jobs SET isFavourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: String, favourite: Boolean): Int

    @Query("DELETE FROM processing_jobs WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query(
        "DELETE FROM processing_jobs " +
            "WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')",
    )
    suspend fun clearHistory()
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY isFavourite DESC, name ASC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(preset: PresetEntity)

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun get(id: String): PresetEntity?

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(preset: PresetEntity): Int

    @Query("UPDATE presets SET isFavourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: String, favourite: Boolean): Int

    @Query("DELETE FROM presets WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: String): Int
}

@Dao
interface OutputArtifactDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(artifact: OutputArtifactEntity)

    @Query("SELECT * FROM output_artifacts WHERE jobId = :jobId")
    suspend fun forJob(jobId: String): List<OutputArtifactEntity>

    @Query(
        "SELECT * FROM output_artifacts WHERE jobId = :jobId " +
            "ORDER BY createdAtEpochMillis DESC LIMIT 1",
    )
    fun observeLatestForJob(jobId: String): Flow<OutputArtifactEntity?>

    @Query("SELECT * FROM output_artifacts ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<OutputArtifactEntity>>
}
