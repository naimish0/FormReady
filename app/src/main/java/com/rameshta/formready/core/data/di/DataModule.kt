package com.rameshta.formready.core.data.di

import android.content.Context
import androidx.room.Room
import com.rameshta.formready.core.data.local.FormReadyDatabase
import com.rameshta.formready.core.data.local.OutputArtifactDao
import com.rameshta.formready.core.data.local.PresetDao
import com.rameshta.formready.core.data.local.ProcessingJobDao
import com.rameshta.formready.core.data.local.ProjectDao
import com.rameshta.formready.core.data.repository.JobRepository
import com.rameshta.formready.core.data.repository.OutputArtifactRepository
import com.rameshta.formready.core.data.repository.PresetRepository
import com.rameshta.formready.core.data.repository.RoomPresetRepository
import com.rameshta.formready.core.data.repository.RoomJobRepository
import com.rameshta.formready.core.data.repository.RoomOutputArtifactRepository
import com.rameshta.formready.core.data.repository.TimeProvider
import com.rameshta.formready.core.data.settings.DataStoreSettingsRepository
import com.rameshta.formready.core.data.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindJobRepository(implementation: RoomJobRepository): JobRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        implementation: DataStoreSettingsRepository,
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindOutputArtifactRepository(
        implementation: RoomOutputArtifactRepository,
    ): OutputArtifactRepository

    @Binds
    @Singleton
    abstract fun bindPresetRepository(implementation: RoomPresetRepository): PresetRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FormReadyDatabase =
        Room.databaseBuilder(
            context,
            FormReadyDatabase::class.java,
            "formready.db",
        ).addMigrations(
            FormReadyDatabase.MIGRATION_1_2,
            FormReadyDatabase.MIGRATION_2_3,
        ).build()

    @Provides
    fun provideProjectDao(database: FormReadyDatabase): ProjectDao = database.projectDao()

    @Provides
    fun provideJobDao(database: FormReadyDatabase): ProcessingJobDao =
        database.processingJobDao()

    @Provides
    fun providePresetDao(database: FormReadyDatabase): PresetDao = database.presetDao()

    @Provides
    fun provideOutputArtifactDao(database: FormReadyDatabase): OutputArtifactDao =
        database.outputArtifactDao()

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = TimeProvider(System::currentTimeMillis)
}
