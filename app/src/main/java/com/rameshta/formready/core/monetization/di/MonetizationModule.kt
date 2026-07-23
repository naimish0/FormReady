package com.rameshta.formready.core.monetization.di

import com.rameshta.formready.core.monetization.AdManager
import com.rameshta.formready.core.monetization.NoOpAdManager
import com.rameshta.formready.core.monetization.PlayBillingProManager
import com.rameshta.formready.core.monetization.ProManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MonetizationBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAdManager(implementation: NoOpAdManager): AdManager

    @Binds
    @Singleton
    abstract fun bindProManager(implementation: PlayBillingProManager): ProManager
}

@Module
@InstallIn(SingletonComponent::class)
object MonetizationModule {
    @Provides
    @Singleton
    fun provideNoOpAdManager(): NoOpAdManager = NoOpAdManager()
}
