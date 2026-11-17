package com.example.sport.core.di

import android.content.Context
import com.example.sport.core.database.SportDatabase
import com.example.sport.core.database.dao.BodyProfileDao
import com.example.sport.core.database.dao.BodyWeightDao
import com.example.sport.core.database.dao.CalorieDao
import com.example.sport.core.database.dao.CatalogDao
import com.example.sport.core.database.dao.DietDao
import com.example.sport.core.database.dao.ExerciseDao
import com.example.sport.core.database.dao.MachineSetupDao
import com.example.sport.core.database.dao.MediaDao
import com.example.sport.core.database.dao.ProgressDao
import com.example.sport.core.database.dao.RoutineDao
import com.example.sport.core.database.dao.WorkoutSessionDao
import com.example.sport.core.database.seed.AssetExerciseCatalogSource
import com.example.sport.core.database.seed.ExerciseCatalogSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SportDatabase = SportDatabase.build(context)

    @Provides
    fun provideExerciseDao(database: SportDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideCatalogDao(database: SportDatabase): CatalogDao = database.catalogDao()

    @Provides
    fun provideMachineSetupDao(database: SportDatabase): MachineSetupDao = database.machineSetupDao()

    @Provides
    fun provideRoutineDao(database: SportDatabase): RoutineDao = database.routineDao()

    @Provides
    fun provideWorkoutSessionDao(database: SportDatabase): WorkoutSessionDao = database.workoutSessionDao()

    @Provides
    fun provideProgressDao(database: SportDatabase): ProgressDao = database.progressDao()

    @Provides
    fun provideBodyWeightDao(database: SportDatabase): BodyWeightDao = database.bodyWeightDao()

    @Provides
    fun provideBodyProfileDao(database: SportDatabase): BodyProfileDao = database.bodyProfileDao()

    @Provides
    fun provideCalorieDao(database: SportDatabase): CalorieDao = database.calorieDao()

    @Provides
    fun provideDietDao(database: SportDatabase): DietDao = database.dietDao()

    @Provides
    fun provideMediaDao(database: SportDatabase): MediaDao = database.mediaDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogSourceModule {

    @Binds
    abstract fun bindExerciseCatalogSource(impl: AssetExerciseCatalogSource): ExerciseCatalogSource
}
