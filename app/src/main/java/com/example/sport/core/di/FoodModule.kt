package com.example.sport.core.di

import com.example.sport.BuildConfig
import com.example.sport.core.data.food.OpenFoodFactsConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FoodModule {

    /**
     * Open Food Facts chiede un User-Agent riconoscibile: nome e versione dell'app, senza contatti
     * né altri dati dell'utente.
     */
    @Provides
    @Singleton
    fun provideOpenFoodFactsConfig(): OpenFoodFactsConfig = OpenFoodFactsConfig(
        baseUrl = OpenFoodFactsConfig.PRODUCTION_URL,
        userAgent = "Sport/${BuildConfig.VERSION_NAME} (Android)",
    )
}
