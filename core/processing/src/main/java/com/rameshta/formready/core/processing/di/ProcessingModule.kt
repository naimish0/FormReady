package com.rameshta.formready.core.processing.di

import android.content.Context
import androidx.work.WorkManager
import com.rameshta.formready.core.processing.InputStager
import com.rameshta.formready.core.processing.JobProcessor
import com.rameshta.formready.core.processing.PrivateInputStager
import com.rameshta.formready.core.processing.ProcessingScheduler
import com.rameshta.formready.core.processing.WorkManagerProcessingScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
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
    abstract fun bindProcessingScheduler(
        implementation: WorkManagerProcessingScheduler,
    ): ProcessingScheduler

    @Multibinds
    abstract fun bindJobProcessors(): Set<JobProcessor>
}

@Module
@InstallIn(SingletonComponent::class)
object ProcessingModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
