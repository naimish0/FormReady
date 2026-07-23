package com.rameshta.formready.core.processing.di

import com.rameshta.formready.core.processing.FaceGuidanceEngine
import com.rameshta.formready.core.processing.MlKitFaceGuidanceEngine
import com.rameshta.formready.core.processing.MlKitPersonSegmentationEngine
import com.rameshta.formready.core.processing.PersonSegmentationEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IdPhotoModule {
    @Binds
    @Singleton
    abstract fun bindFaceGuidance(implementation: MlKitFaceGuidanceEngine): FaceGuidanceEngine

    @Binds
    @Singleton
    abstract fun bindSegmentation(
        implementation: MlKitPersonSegmentationEngine,
    ): PersonSegmentationEngine
}
