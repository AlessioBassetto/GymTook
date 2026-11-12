package com.example.sport.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sport.core.database.entity.ExerciseSetEntity
import com.example.sport.core.database.entity.RoutineExerciseEntity
import com.example.sport.core.database.entity.WorkoutRoutineEntity
import com.example.sport.core.model.SplitType
import com.example.sport.core.database.seed.AssetExerciseCatalogSource
import com.example.sport.core.database.seed.CatalogSeedResult
import com.example.sport.core.database.seed.ExerciseCatalogSeeder
import com.example.sport.core.database.seed.ExerciseCatalogSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Istante di riferimento dei test: lunedì 2 novembre 2026, 07:00 UTC. */
val T0: Instant = Instant.parse("2026-11-02T07:00:00Z")

fun hoursAfterT0(hours: Long): Instant = T0.plus(Duration.ofHours(hours))

/** Database Room in memoria con lo schema reale; un'istanza nuova per ogni test. */
abstract class DatabaseTest {

    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected lateinit var db: SportDatabase

    @Before
    fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(context, SportDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    protected fun seeder(
        source: ExerciseCatalogSource = AssetExerciseCatalogSource(context, Dispatchers.IO),
        now: Instant = T0,
    ): ExerciseCatalogSeeder = ExerciseCatalogSeeder(
        database = db,
        source = source,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    /** Installa il catalogo reale impacchettato negli asset. */
    protected suspend fun seedBundledCatalog(): CatalogSeedResult = seeder().seedIfNeeded()

    /**
     * Crea una scheda attiva con un singolo giorno.
     * @param exercises coppie (id esercizio, serie target).
     * @return (id scheda, id giorno).
     */
    protected suspend fun createRoutineWithDay(
        dayName: String,
        exercises: List<Pair<String, Int>>,
        routineName: String = "Scheda test",
    ): Pair<Long, Long> {
        val routineDao = db.routineDao()
        val routineId = routineDao.insertRoutine(
            WorkoutRoutineEntity(name = routineName, splitType = SplitType.CUSTOM, createdAt = T0, updatedAt = T0),
        )
        val dayId = routineDao.appendDay(routineId, dayName)
        exercises.forEach { (exerciseId, sets) ->
            routineDao.appendExerciseToDay(
                RoutineExerciseEntity(
                    routineDayId = dayId,
                    exerciseId = exerciseId,
                    position = 0,
                    targetSets = sets,
                    targetRepsMin = 8,
                    targetRepsMax = 12,
                ),
            )
        }
        return routineId to dayId
    }

    /** Compila e spunta una serie. */
    protected suspend fun completeSet(set: ExerciseSetEntity, weightKg: Double?, reps: Int?, at: Instant) {
        db.workoutSessionDao().updateSet(
            set.copy(weightKg = weightKg, reps = reps, isCompleted = true, completedAt = at),
        )
    }
}

/** assertThrows compatibile con codice sospendibile dentro `runTest`. */
inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw AssertionError("Attesa ${T::class.simpleName}, ricevuta ${error::class.simpleName}", error)
    }
    fail("Attesa ${T::class.simpleName}, nessuna eccezione lanciata")
    throw IllegalStateException("non raggiungibile")
}
