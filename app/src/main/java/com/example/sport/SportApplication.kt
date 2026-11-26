package com.example.sport

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.example.sport.core.data.CalorieRepository
import com.example.sport.core.database.seed.ExerciseCatalogSeeder
import com.example.sport.core.database.seed.FoodCatalogSeeder
import com.example.sport.core.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class SportApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var exerciseCatalogSeeder: ExerciseCatalogSeeder

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var foodCatalogSeeder: FoodCatalogSeeder

    @Inject
    lateinit var calorieRepository: CalorieRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Installa/aggiorna il catalogo esercizi in background al primo avvio e dopo ogni update.
        exerciseCatalogSeeder.seedInBackground()
        // Catalogo dei piatti italiani della dieta: stessa logica, versione separata.
        foodCatalogSeeder.seedInBackground()
        // Allenamenti salvati prima delle calorie (schema v3) o senza pesate: stima appena possibile.
        calorieRepository.recalculateMissingInBackground(applicationScope)
    }

    /** Coil usa l'ImageLoader configurato in MediaModule (OkHttp condiviso + cache su disco). */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    /** WorkManager crea i worker tramite Hilt (inizializzazione on-demand, vedi manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
