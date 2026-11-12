package com.example.sport.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sorgenti dati per il player video.
 *
 * I video di catalogo arrivano dalla CDN e passano da una cache su disco a scadenza LRU: rivisti
 * offline in palestra, senza riscaricarli. I file locali (video personali, media "disponibili
 * offline") si leggono direttamente, senza duplicarli in cache.
 */
@Singleton
@OptIn(UnstableApi::class)
class VideoDataSources @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Una sola istanza per cartella (SimpleCache la blocca): creata al primo video di rete.
    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, CACHE_DIRECTORY),
            LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    private val network: DataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Sorgente adatta a [uri]: cache per http(s), lettura diretta per tutto il resto. */
    fun factoryFor(uri: String): DataSource.Factory =
        if (uri.startsWith("http://") || uri.startsWith("https://")) network else DefaultDataSource.Factory(context)

    private companion object {
        const val CACHE_DIRECTORY = "video-cache"
        const val CACHE_BYTES = 300L * 1024 * 1024
    }
}
