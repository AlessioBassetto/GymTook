package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.example.sport.core.database.entity.RoutineDayEntity
import com.example.sport.core.database.entity.RoutineExerciseEntity
import com.example.sport.core.database.entity.WeeklyScheduleEntity
import com.example.sport.core.database.entity.WorkoutRoutineEntity
import com.example.sport.core.database.relation.RoutineDayWithExercises
import com.example.sport.core.database.relation.RoutineWithDays
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant

/** Schede, giorni, esercizi pianificati e calendario settimanale. */
@Dao
interface RoutineDao {

    // ---------------------------------------------------------------- Letture reattive

    @Query("SELECT * FROM routines WHERE is_archived = 0 ORDER BY is_active DESC, updated_at DESC")
    fun observeRoutines(): Flow<List<WorkoutRoutineEntity>>

    @Transaction
    @Query("SELECT * FROM routines WHERE is_archived = 0 ORDER BY is_active DESC, updated_at DESC")
    fun observeRoutinesWithDays(): Flow<List<RoutineWithDays>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :routineId")
    fun observeRoutineWithDays(routineId: Long): Flow<RoutineWithDays?>

    @Transaction
    @Query("SELECT * FROM routines WHERE is_active = 1 LIMIT 1")
    fun observeActiveRoutine(): Flow<RoutineWithDays?>

    /** Allenamento previsto per [dayOfWeek] nella scheda attiva (null = riposo / nessuna scheda). */
    @Transaction
    @Query(
        """
        SELECT rd.* FROM routine_days rd
        INNER JOIN weekly_schedule ws ON ws.routine_day_id = rd.id
        INNER JOIN routines r ON r.id = ws.routine_id
        WHERE r.is_active = 1 AND ws.day_of_week = :dayOfWeek
        LIMIT 1
        """,
    )
    fun observeActiveRoutineDayFor(dayOfWeek: DayOfWeek): Flow<RoutineDayWithExercises?>

    @Transaction
    @Query("SELECT * FROM routine_days WHERE id = :dayId")
    fun observeRoutineDay(dayId: Long): Flow<RoutineDayWithExercises?>

    // ---------------------------------------------------------------- Letture puntuali

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :routineId")
    suspend fun getRoutineWithDays(routineId: Long): RoutineWithDays?

    @Query("SELECT * FROM routines WHERE id = :routineId")
    suspend fun getRoutine(routineId: Long): WorkoutRoutineEntity?

    @Query("SELECT * FROM routine_days WHERE routine_id = :routineId ORDER BY position")
    suspend fun getDays(routineId: Long): List<RoutineDayEntity>

    @Query("SELECT * FROM routine_days WHERE id = :dayId")
    suspend fun getDay(dayId: Long): RoutineDayEntity?

    @Query("SELECT * FROM routine_exercises WHERE routine_day_id = :dayId ORDER BY position")
    suspend fun getRoutineExercises(dayId: Long): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_exercises WHERE id = :routineExerciseId")
    suspend fun getRoutineExercise(routineExerciseId: Long): RoutineExerciseEntity?

    @Query("SELECT * FROM weekly_schedule WHERE routine_id = :routineId ORDER BY day_of_week")
    suspend fun getSchedule(routineId: Long): List<WeeklyScheduleEntity>

    @Query("SELECT id FROM routines WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveRoutineId(): Long?

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM routine_days WHERE routine_id = :routineId")
    suspend fun nextDayPosition(routineId: Long): Int

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM routine_exercises WHERE routine_day_id = :dayId")
    suspend fun nextExercisePosition(dayId: Long): Int

    // ---------------------------------------------------------------- Scritture semplici

    @Insert
    suspend fun insertRoutine(routine: WorkoutRoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: WorkoutRoutineEntity)

    @Delete
    suspend fun deleteRoutine(routine: WorkoutRoutineEntity)

    @Insert
    suspend fun insertDay(day: RoutineDayEntity): Long

    @Update
    suspend fun updateDay(day: RoutineDayEntity)

    @Update
    suspend fun updateDays(days: List<RoutineDayEntity>)

    @Delete
    suspend fun deleteDay(day: RoutineDayEntity)

    @Insert
    suspend fun insertRoutineExercise(exercise: RoutineExerciseEntity): Long

    @Insert
    suspend fun insertRoutineExercises(exercises: List<RoutineExerciseEntity>): List<Long>

    @Update
    suspend fun updateRoutineExercise(exercise: RoutineExerciseEntity)

    @Update
    suspend fun updateRoutineExercises(exercises: List<RoutineExerciseEntity>)

    @Delete
    suspend fun deleteRoutineExercise(exercise: RoutineExerciseEntity)

    @Upsert
    suspend fun upsertScheduleEntry(entry: WeeklyScheduleEntity)

    @Insert
    suspend fun insertSchedule(entries: List<WeeklyScheduleEntity>)

    @Query("DELETE FROM weekly_schedule WHERE routine_id = :routineId AND day_of_week = :dayOfWeek")
    suspend fun clearScheduleDay(routineId: Long, dayOfWeek: DayOfWeek)

    @Query("UPDATE routines SET is_active = 0 WHERE is_active = 1")
    suspend fun clearActiveRoutine()

    @Query("UPDATE routines SET is_active = 1, is_archived = 0, updated_at = :now WHERE id = :routineId")
    suspend fun markActive(routineId: Long, now: Instant)

    @Query("UPDATE routines SET updated_at = :now WHERE id = :routineId")
    suspend fun touch(routineId: Long, now: Instant)

    // ---------------------------------------------------------------- Transazioni del builder

    /** Rende [routineId] l'unica scheda attiva. */
    @Transaction
    suspend fun setActiveRoutine(routineId: Long, now: Instant) {
        clearActiveRoutine()
        markActive(routineId, now)
    }

    /** Assegna un giorno di scheda a un giorno della settimana (sovrascrive l'eventuale precedente). */
    @Transaction
    suspend fun assignWeekday(routineId: Long, dayOfWeek: DayOfWeek, routineDayId: Long) {
        val day = checkNotNull(getDay(routineDayId)) { "Giorno di scheda $routineDayId inesistente" }
        require(day.routineId == routineId) { "Il giorno $routineDayId non appartiene alla scheda $routineId" }
        upsertScheduleEntry(WeeklyScheduleEntity(routineId, dayOfWeek, routineDayId))
    }

    @Transaction
    suspend fun appendDay(routineId: Long, name: String): Long =
        insertDay(RoutineDayEntity(routineId = routineId, name = name, position = nextDayPosition(routineId)))

    /** Aggiunge un esercizio in coda al giorno (la `position` passata viene ignorata). */
    @Transaction
    suspend fun appendExerciseToDay(exercise: RoutineExerciseEntity): Long =
        insertRoutineExercise(exercise.copy(id = 0, position = nextExercisePosition(exercise.routineDayId)))

    /** Drag-and-drop: sposta l'esercizio da [fromIndex] a [toIndex] e ricompatta le posizioni. */
    @Transaction
    suspend fun moveRoutineExercise(dayId: Long, fromIndex: Int, toIndex: Int) {
        val items = getRoutineExercises(dayId).toMutableList()
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return
        items.add(toIndex, items.removeAt(fromIndex))
        updateRoutineExercises(items.mapIndexed { index, item -> item.copy(position = index) })
    }

    /** Drag-and-drop dei giorni della scheda. */
    @Transaction
    suspend fun moveDay(routineId: Long, fromIndex: Int, toIndex: Int) {
        val days = getDays(routineId).toMutableList()
        if (fromIndex !in days.indices || toIndex !in days.indices || fromIndex == toIndex) return
        days.add(toIndex, days.removeAt(fromIndex))
        updateDays(days.mapIndexed { index, day -> day.copy(position = index) })
    }

    @Transaction
    suspend fun removeRoutineExercise(exercise: RoutineExerciseEntity) {
        deleteRoutineExercise(exercise)
        updateRoutineExercises(
            getRoutineExercises(exercise.routineDayId).mapIndexed { index, item -> item.copy(position = index) },
        )
    }

    /**
     * Duplica una scheda in profondità (giorni, esercizi, calendario). La copia non è attiva e
     * tiene traccia dell'origine in `parent_routine_id`: è la base per "Salva variante".
     *
     * @return id della nuova scheda.
     */
    @Transaction
    suspend fun duplicateRoutine(sourceRoutineId: Long, newName: String, now: Instant): Long {
        val source = checkNotNull(getRoutine(sourceRoutineId)) { "Scheda $sourceRoutineId inesistente" }
        val newRoutineId = insertRoutine(
            source.copy(
                id = 0,
                name = newName,
                isActive = false,
                isArchived = false,
                parentRoutineId = source.id,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val dayIdMapping = mutableMapOf<Long, Long>()
        getDays(sourceRoutineId).forEach { day ->
            val newDayId = insertDay(day.copy(id = 0, routineId = newRoutineId))
            dayIdMapping[day.id] = newDayId
            insertRoutineExercises(
                getRoutineExercises(day.id).map { it.copy(id = 0, routineDayId = newDayId) },
            )
        }
        insertSchedule(
            getSchedule(sourceRoutineId).map {
                it.copy(routineId = newRoutineId, routineDayId = dayIdMapping.getValue(it.routineDayId))
            },
        )
        return newRoutineId
    }

    /** Duplica un giorno nella stessa scheda (es. "Push A" → "Push B"), in coda. */
    @Transaction
    suspend fun duplicateDay(dayId: Long, newName: String): Long {
        val source = checkNotNull(getDay(dayId)) { "Giorno di scheda $dayId inesistente" }
        val newDayId = insertDay(
            source.copy(id = 0, name = newName, position = nextDayPosition(source.routineId)),
        )
        insertRoutineExercises(getRoutineExercises(dayId).map { it.copy(id = 0, routineDayId = newDayId) })
        return newDayId
    }
}
