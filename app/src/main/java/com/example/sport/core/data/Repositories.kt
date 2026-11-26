package com.example.sport.core.data

import androidx.room.withTransaction
import com.example.sport.core.data.template.RoutineTemplate
import com.example.sport.core.database.SportDatabase
import com.example.sport.core.database.dao.DietDao
import com.example.sport.core.database.dao.ExerciseDao
import com.example.sport.core.database.dao.MediaDao
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.RoutineExerciseEntity
import com.example.sport.core.database.entity.WorkoutRoutineEntity
import com.example.sport.core.database.query.ExerciseFilter
import com.example.sport.core.database.relation.DietPlanWithMeals
import com.example.sport.core.database.relation.ExerciseDetails
import com.example.sport.core.database.relation.RoutineDayWithExercises
import com.example.sport.core.database.relation.RoutineWithDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Cosa prevede oggi la scheda attiva. */
sealed interface TodayPlan {
    val date: LocalDate

    data class NoActiveRoutine(override val date: LocalDate) : TodayPlan

    data class Workout(
        override val date: LocalDate,
        val routineName: String,
        val day: RoutineDayWithExercises,
    ) : TodayPlan

    data class RestDay(
        override val date: LocalDate,
        val routineName: String,
        val next: ScheduledWorkout?,
    ) : TodayPlan
}

data class ScheduledWorkout(val dayName: String, val date: LocalDate)

/** Logica pura (testabile senza database): allenamento di oggi o prossimo allenamento in calendario. */
fun resolveTodayPlan(routine: RoutineWithDays?, today: LocalDate): TodayPlan {
    if (routine == null) return TodayPlan.NoActiveRoutine(today)
    val byWeekday = routine.dayByWeekday
    byWeekday[today.dayOfWeek]?.let { return TodayPlan.Workout(today, routine.routine.name, it) }
    val next = (1L..7L).asSequence()
        .map { today.plusDays(it) }
        .firstNotNullOfOrNull { date -> byWeekday[date.dayOfWeek]?.let { ScheduledWorkout(it.day.name, date) } }
    return TodayPlan.RestDay(today, routine.routine.name, next)
}

@Singleton
class RoutineRepository @Inject constructor(
    private val database: SportDatabase,
    private val clock: Clock,
) {
    private val dao = database.routineDao()

    fun observeRoutines(): Flow<List<RoutineWithDays>> = dao.observeRoutinesWithDays()

    /** La data è letta alla sottoscrizione: la UI si ri-sottoscrive quando torna in primo piano. */
    fun observeTodayPlan(): Flow<TodayPlan> =
        dao.observeActiveRoutine().map { resolveTodayPlan(it, LocalDate.now(clock)) }

    suspend fun setActive(routineId: Long) = dao.setActiveRoutine(routineId, Instant.now(clock))

    /** Crea una scheda completa (giorni, esercizi, calendario) da un modello, in un'unica transazione. */
    suspend fun createFromTemplate(template: RoutineTemplate, activate: Boolean = true): Long =
        database.withTransaction {
            val now = Instant.now(clock)
            val routineId = dao.insertRoutine(
                WorkoutRoutineEntity(
                    name = template.name,
                    description = template.description,
                    splitType = template.splitType,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            template.days.forEach { templateDay ->
                val dayId = dao.appendDay(routineId, templateDay.name)
                templateDay.exercises.forEach { item ->
                    dao.appendExerciseToDay(
                        RoutineExerciseEntity(
                            routineDayId = dayId,
                            exerciseId = item.exerciseId,
                            position = 0,
                            targetSets = item.sets,
                            targetRepsMin = item.repsMin,
                            targetRepsMax = item.repsMax,
                            restSeconds = item.restSeconds,
                        ),
                    )
                }
                templateDay.weekdays.forEach { dao.assignWeekday(routineId, it, dayId) }
            }
            if (activate) dao.setActiveRoutine(routineId, now)
            routineId
        }
}

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val mediaDao: MediaDao,
    private val clock: Clock,
) {
    fun observeExercises(filter: ExerciseFilter): Flow<List<ExerciseEntity>> = exerciseDao.observeExercises(filter)

    /** id esercizio → path della miniatura. */
    fun observeThumbnailPaths(): Flow<Map<String, String>> =
        mediaDao.observeThumbnailPaths().map { rows -> rows.associate { it.exerciseId to it.path } }

    fun observeDetails(exerciseId: String): Flow<ExerciseDetails?> = exerciseDao.observeDetails(exerciseId)

    fun observeRecentlyUsed(limit: Int): Flow<List<ExerciseEntity>> = exerciseDao.observeRecentlyUsed(limit)

    /** Alternative per la sostituzione al volo, dalla più simile (vedi [AlternativeRanker]). */
    suspend fun alternatives(exerciseId: String, limit: Int): List<ExerciseEntity> {
        val original = exerciseDao.getById(exerciseId) ?: return emptyList()
        val candidates = exerciseDao.getAlternativeCandidates(exerciseId)
        val muscles = exerciseDao.getMusclesFor(candidates.map { it.id } + exerciseId)
            .groupBy({ it.exerciseId }, { it.muscle })
            .mapValues { (_, list) -> list.toSet() }
        return AlternativeRanker.rank(original, candidates, muscles).take(limit)
    }

    suspend fun setFavorite(exerciseId: String, favorite: Boolean) =
        exerciseDao.setFavorite(exerciseId, favorite, Instant.now(clock))
}

/** Piano alimentare attivo con i pasti già spuntati nel giorno. */
data class NutritionDay(
    val date: LocalDate,
    val plan: DietPlanWithMeals,
    val consumedMealIds: Set<Long>,
) {
    private val consumedMeals get() = plan.meals.filter { it.meal.id in consumedMealIds }
    val consumedCaloriesKcal: Double get() = consumedMeals.sumOf { it.totalCaloriesKcal }
    val consumedProteinG: Double get() = consumedMeals.sumOf { it.totalProteinG }
    val consumedCarbsG: Double get() = consumedMeals.sumOf { it.totalCarbsG }
    val consumedFatG: Double get() = consumedMeals.sumOf { it.totalFatG }
}

@Singleton
class NutritionRepository @Inject constructor(
    private val dietDao: DietDao,
    private val clock: Clock,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun observeToday(): Flow<NutritionDay?> = observeDay(today())

    /** Piano attivo e pasti del piano spuntati nel giorno [date] (null se non c'è un piano attivo). */
    fun observeDay(date: LocalDate): Flow<NutritionDay?> =
        combine(dietDao.observeActivePlanWithMeals(), dietDao.observeLogsForDate(date)) { plan, logs ->
            plan?.let { NutritionDay(date, it, logs.map { log -> log.mealId }.toSet()) }
        }

    suspend fun setMealConsumed(mealId: Long, consumed: Boolean, date: LocalDate = today()) =
        dietDao.setMealConsumed(mealId, date, consumed, Instant.now(clock))
}
