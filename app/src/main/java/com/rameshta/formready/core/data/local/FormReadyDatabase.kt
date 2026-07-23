package com.rameshta.formready.core.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ProcessingJobEntity::class,
        PresetEntity::class,
        OutputArtifactEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class FormReadyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    abstract fun processingJobDao(): ProcessingJobDao

    abstract fun presetDao(): PresetDao

    abstract fun outputArtifactDao(): OutputArtifactDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE output_artifacts ADD COLUMN widthPx INTEGER")
                db.execSQL("ALTER TABLE output_artifacts ADD COLUMN heightPx INTEGER")
                db.execSQL("ALTER TABLE output_artifacts ADD COLUMN dpi INTEGER")
                db.execSQL(
                    "ALTER TABLE output_artifacts ADD COLUMN readiness TEXT NOT NULL " +
                        "DEFAULT 'NOT_READY'",
                )
                db.execSQL(
                    "ALTER TABLE output_artifacts ADD COLUMN validationJson TEXT NOT NULL " +
                        "DEFAULT '[]'",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE processing_jobs ADD COLUMN isFavourite INTEGER NOT NULL " +
                        "DEFAULT 0",
                )
            }
        }
    }
}
