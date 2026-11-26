package com.example.sport.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.sport.core.database.converter.Converters
import com.example.sport.core.database.dao.BodyProfileDao
import com.example.sport.core.database.dao.BodyWeightDao
import com.example.sport.core.database.dao.CalorieDao
import com.example.sport.core.database.dao.CatalogDao
import com.example.sport.core.database.dao.DietDao
import com.example.sport.core.database.dao.ExerciseDao
import com.example.sport.core.database.dao.FoodDao
import com.example.sport.core.database.dao.MachineSetupDao
import com.example.sport.core.database.dao.MediaDao
import com.example.sport.core.database.dao.ProgressDao
import com.example.sport.core.database.dao.RoutineDao
import com.example.sport.core.database.dao.WorkoutSessionDao
import com.example.sport.core.database.entity.AppMetadataEntity
import com.example.sport.core.database.entity.BodyProfileEntity
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import com.example.sport.core.database.entity.DietPlanEntity
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.ExerciseMediaEntity
import com.example.sport.core.database.entity.ExerciseMuscleEntity
import com.example.sport.core.database.entity.ExerciseUserVideoEntity
import com.example.sport.core.database.entity.FoodItemEntity
import com.example.sport.core.database.entity.FoodRecipeIngredientEntity
import com.example.sport.core.database.entity.MealEntryEntity
import com.example.sport.core.database.migration.MANUAL_MIGRATIONS
import com.example.sport.core.database.migration.Migration1To2
import com.example.sport.core.database.entity.ExerciseSetEntity
import com.example.sport.core.database.entity.MachineSetupEntity
import com.example.sport.core.database.entity.MealEntity
import com.example.sport.core.database.entity.MealItemEntity
import com.example.sport.core.database.entity.MealLogEntity
import com.example.sport.core.database.entity.RoutineDayEntity
import com.example.sport.core.database.entity.RoutineExerciseEntity
import com.example.sport.core.database.entity.SessionExerciseEntity
import com.example.sport.core.database.entity.WeeklyScheduleEntity
import com.example.sport.core.database.entity.WorkoutRoutineEntity
import com.example.sport.core.database.entity.WorkoutSessionEntity

/**
 * Database locale unico dell'app (100% offline-first: è la sorgente di verità di ogni schermata).
 *
 * Politica di versione: ogni modifica allo schema incrementa [version] e aggiunge una Migration
 * esplicita (o un `@AutoMigration`), verificata dagli schemi esportati in `app/schemas`.
 * Nessun fallback distruttivo: lo storico di allenamento non deve mai essere cancellato.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        ExerciseMuscleEntity::class,
        ExerciseMediaEntity::class,
        MachineSetupEntity::class,
        WorkoutRoutineEntity::class,
        RoutineDayEntity::class,
        RoutineExerciseEntity::class,
        WeeklyScheduleEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        ExerciseSetEntity::class,
        BodyWeightEntryEntity::class,
        DietPlanEntity::class,
        MealEntity::class,
        MealItemEntity::class,
        MealLogEntity::class,
        AppMetadataEntity::class,
        ExerciseUserVideoEntity::class,
        BodyProfileEntity::class,
        FoodItemEntity::class,
        FoodRecipeIngredientEntity::class,
        MealEntryEntity::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = Migration1To2::class),
        // v2 → v3 (Fase 5): colonna `exercises.category` (default 'STRENGTH', il catalogo v3 la
        // compila all'avvio) e tabella `exercise_user_videos`. Solo aggiunte: nessuna spec.
        AutoMigration(from = 2, to = 3),
        // v3 → v4 (calorie): Migration scritta a mano, vedi MIGRATION_3_4 in Migrations.kt.
        // v4 → v5 (tapis roulant): colonna exercise_sets.incline_percent, vedi MIGRATION_4_5.
        // v5 → v6 (tapis + dieta): exercise_sets.speed_kmh e le tabelle food_items,
        // food_recipe_ingredients e meal_entries, vedi MIGRATION_5_6.
    ],
)
@TypeConverters(Converters::class)
abstract class SportDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    abstract fun catalogDao(): CatalogDao

    abstract fun machineSetupDao(): MachineSetupDao

    abstract fun routineDao(): RoutineDao

    abstract fun workoutSessionDao(): WorkoutSessionDao

    abstract fun progressDao(): ProgressDao

    abstract fun bodyWeightDao(): BodyWeightDao

    abstract fun bodyProfileDao(): BodyProfileDao

    abstract fun calorieDao(): CalorieDao

    abstract fun dietDao(): DietDao

    abstract fun foodDao(): FoodDao

    abstract fun mediaDao(): MediaDao

    companion object {
        const val DATABASE_NAME = "sport.db"

        fun build(context: Context): SportDatabase =
            Room.databaseBuilder(context.applicationContext, SportDatabase::class.java, DATABASE_NAME)
                .addMigrations(*MANUAL_MIGRATIONS)
                .build()
    }
}
