package com.rameshta.formready.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ProcessingJobEntity::class,
        PresetEntity::class,
        OutputArtifactEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FormReadyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    abstract fun processingJobDao(): ProcessingJobDao

    abstract fun presetDao(): PresetDao

    abstract fun outputArtifactDao(): OutputArtifactDao
}
