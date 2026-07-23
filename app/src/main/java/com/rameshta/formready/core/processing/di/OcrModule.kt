package com.rameshta.formready.core.processing.di

import com.rameshta.formready.core.processing.MlKitOcrEngine
import com.rameshta.formready.core.processing.OcrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds
    @Singleton
    abstract fun bindOcrEngine(implementation: MlKitOcrEngine): OcrEngine
}
