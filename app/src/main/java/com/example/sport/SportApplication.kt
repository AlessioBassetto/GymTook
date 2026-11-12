package com.example.sport

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.example.sport.core.database.seed.ExerciseCatalogSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SportApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var exerciseCatalogSeeder: ExerciseCatalogSeeder

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Installa/aggiorna il catalogo esercizi in background al primo avvio e dopo ogni update.
        exerciseCatalogSeeder.seedInBackground()
    }

    /** Coil usa l'ImageLoader configurato in MediaModule (OkHttp condiviso + cache su disco). */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    /** WorkManager crea i worker tramite Hilt (inizializzazione on-demand, vedi manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
