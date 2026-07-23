package com.rameshta.formready

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.formready.core.data.local.FormReadyDatabase
import com.rameshta.formready.core.data.local.ProcessingJobEntity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormReadyDatabaseMigrationTest {
    @Test
    fun migrateFrom1To3PreservesSchemaAndAddsPhase2AndPhase4Fields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        val path = context.getDatabasePath(TEST_DATABASE)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            execSQL(
                """
                CREATE TABLE projects (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE processing_jobs (
                    id TEXT NOT NULL PRIMARY KEY,
                    projectId TEXT,
                    type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    processingPlanJson TEXT NOT NULL,
                    stagedInputRelativePath TEXT,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    errorCode TEXT,
                    FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE presets (
                    id TEXT NOT NULL PRIMARY KEY,
                    schemaVersion INTEGER NOT NULL,
                    revision INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    targetType TEXT NOT NULL,
                    specificationJson TEXT NOT NULL,
                    sourceUrl TEXT,
                    sourceCheckedAtEpochMillis INTEGER,
                    isCustom INTEGER NOT NULL,
                    isFavourite INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE output_artifacts (
                    id TEXT NOT NULL PRIMARY KEY,
                    jobId TEXT NOT NULL,
                    uri TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    byteCount INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    accessState TEXT NOT NULL,
                    FOREIGN KEY(jobId) REFERENCES processing_jobs(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            execSQL("CREATE INDEX index_processing_jobs_projectId ON processing_jobs(projectId)")
            execSQL("CREATE INDEX index_processing_jobs_status ON processing_jobs(status)")
            execSQL(
                "CREATE INDEX index_processing_jobs_updatedAtEpochMillis " +
                    "ON processing_jobs(updatedAtEpochMillis)",
            )
            execSQL("CREATE UNIQUE INDEX index_presets_name_revision ON presets(name, revision)")
            execSQL("CREATE INDEX index_output_artifacts_jobId ON output_artifacts(jobId)")
            execSQL(
                """
                INSERT INTO processing_jobs (
                    id, projectId, type, status, processingPlanJson,
                    stagedInputRelativePath, createdAtEpochMillis,
                    updatedAtEpochMillis, errorCode
                ) VALUES (
                    'job', NULL, 'PHOTO', 'SUCCEEDED', '{}',
                    NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO output_artifacts (
                    id, jobId, uri, displayName, mimeType,
                    byteCount, createdAtEpochMillis, accessState
                ) VALUES (
                    'artifact', 'job', 'outputs/photo.jpg', 'photo.jpg',
                    'image/jpeg', 1000, 1, 'AVAILABLE'
                )
                """.trimIndent(),
            )
            version = 1
            close()
        }

        val database = Room.databaseBuilder(context, FormReadyDatabase::class.java, TEST_DATABASE)
            .addMigrations(FormReadyDatabase.MIGRATION_1_2, FormReadyDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            database.openHelper.writableDatabase.query(
                """
                SELECT output_artifacts.readiness, output_artifacts.validationJson,
                    processing_jobs.isFavourite
                FROM output_artifacts
                JOIN processing_jobs ON processing_jobs.id = output_artifacts.jobId
                WHERE output_artifacts.id = 'artifact'
                """.trimIndent(),
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getString(0) == "NOT_READY")
                check(cursor.getString(1) == "[]")
                check(cursor.getInt(2) == 0)
            }
            runBlocking {
                database.processingJobDao().insert(
                    ProcessingJobEntity(
                        id = "active-job",
                        projectId = null,
                        type = "PHOTO",
                        status = "RUNNING",
                        processingPlanJson = "{}",
                        stagedInputRelativePath = null,
                        createdAtEpochMillis = 2,
                        updatedAtEpochMillis = 2,
                        errorCode = null,
                    ),
                )
                database.processingJobDao().clearHistory()
            }
            database.openHelper.writableDatabase.query(
                "SELECT id FROM processing_jobs ORDER BY id",
            ).use { cursor ->
                check(cursor.count == 1)
                check(cursor.moveToFirst())
                check(cursor.getString(0) == "active-job")
            }
        } finally {
            database.close()
        }
        context.deleteDatabase(TEST_DATABASE)
    }

    private companion object {
        const val TEST_DATABASE = "phase1-migration-test"
    }
}
