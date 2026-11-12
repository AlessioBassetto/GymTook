package com.example.sport.core.di

import com.example.sport.BuildConfig
import com.example.sport.core.update.AndroidApkInspector
import com.example.sport.core.update.AndroidConnectivityChecker
import com.example.sport.core.update.ApkInspector
import com.example.sport.core.update.ConnectivityChecker
import com.example.sport.core.update.UpdateConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds
    abstract fun bindConnectivityChecker(impl: AndroidConnectivityChecker): ConnectivityChecker

    @Binds
    abstract fun bindApkInspector(impl: AndroidApkInspector): ApkInspector

    companion object {
        /** Repository e versione arrivano dalla build (proprietà Gradle sport.update.repository). */
        @Provides
        @Singleton
        fun provideUpdateConfig(): UpdateConfig = UpdateConfig(
            repository = BuildConfig.UPDATE_REPOSITORY,
            apiBaseUrl = BuildConfig.UPDATE_API_BASE_URL,
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
        )
    }
}
