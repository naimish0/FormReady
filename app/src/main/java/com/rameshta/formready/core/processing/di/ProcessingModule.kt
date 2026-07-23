package com.rameshta.formready.core.processing.di

import android.content.Context
import androidx.work.WorkManager
import com.rameshta.formready.core.processing.InputStager
import com.rameshta.formready.core.processing.AndroidImageMetadataReader
import com.rameshta.formready.core.processing.AndroidImageTransformEngine
import com.rameshta.formready.core.processing.AndroidSignatureProcessor
import com.rameshta.formready.core.processing.ImageMetadataReader
import com.rameshta.formready.core.processing.ImageTransformEngine
import com.rameshta.formready.core.processing.JobProcessor
import com.rameshta.formready.core.processing.PhotoJobProcessor
import com.rameshta.formready.core.processing.PhotoOutputAccess
import com.rameshta.formready.core.processing.PhotoPreparationService
import com.rameshta.formready.core.processing.PrivateInputStager
import com.rameshta.formready.core.processing.PrivatePhotoOutputAccess
import com.rameshta.formready.core.processing.PrivatePhotoPreparationService
import com.rameshta.formready.core.processing.ProcessingScheduler
import com.rameshta.formready.core.processing.SignatureJobProcessor
import com.rameshta.formready.core.processing.SignatureProcessor
import com.rameshta.formready.core.processing.WorkManagerProcessingScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessingBindingsModule {
    @Binds
    @Singleton
    abstract fun bindInputStager(implementation: PrivateInputStager): InputStager

    @Binds
    @Singleton
    abstract fun bindImageMetadataReader(
        implementation: AndroidImageMetadataReader,
    ): ImageMetadataReader

    @Binds
    @Singleton
    abstract fun bindImageTransformEngine(
        implementation: AndroidImageTransformEngine,
    ): ImageTransformEngine

    @Binds
    @Singleton
    abstract fun bindSignatureProcessor(
        implementation: AndroidSignatureProcessor,
    ): SignatureProcessor

    @Binds
    @Singleton
    abstract fun bindPhotoPreparationService(
        implementation: PrivatePhotoPreparationService,
    ): PhotoPreparationService

    @Binds
    @Singleton
    abstract fun bindPhotoOutputAccess(
        implementation: PrivatePhotoOutputAccess,
    ): PhotoOutputAccess

    @Binds
    @Singleton
    abstract fun bindProcessingScheduler(
        implementation: WorkManagerProcessingScheduler,
    ): ProcessingScheduler

    @Multibinds
    abstract fun bindJobProcessors(): Set<JobProcessor>

    @Binds
    @IntoSet
    abstract fun bindPhotoJobProcessor(implementation: PhotoJobProcessor): JobProcessor

    @Binds
    @IntoSet
    abstract fun bindSignatureJobProcessor(implementation: SignatureJobProcessor): JobProcessor
}

@Module
@InstallIn(SingletonComponent::class)
object ProcessingModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
