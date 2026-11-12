package com.example.sport.core.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.example.sport.BuildConfig
import com.example.sport.core.media.MediaConfig
import com.example.sport.core.media.OfflineMediaStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    /** Cache LRU delle immagini viste: si riempie usando l'app, si svuota da sola oltre il limite. */
    private const val IMAGE_DISK_CACHE_BYTES = 256L * 1024 * 1024

    @Provides
    @Singleton
    fun provideMediaConfig(): MediaConfig = MediaConfig(BuildConfig.MEDIA_BASE_URL)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideOfflineMediaStore(
        @ApplicationContext context: Context,
        config: MediaConfig,
        okHttpClient: OkHttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): OfflineMediaStore = OfflineMediaStore(
        rootDir = context.filesDir.resolve("media"),
        config = config,
        httpClient = okHttpClient,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context, okHttpClient: OkHttpClient): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
}
