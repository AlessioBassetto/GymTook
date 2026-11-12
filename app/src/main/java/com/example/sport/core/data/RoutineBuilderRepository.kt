package com.example.sport.core.data

import androidx.room.withTransaction
import com.example.sport.core.database.SportDatabase
import com.example.sport.core.database.entity.RoutineDayEntity
import com.example.sport.core.database.entity.RoutineExerciseEntity
import com.example.sport.core.database.entity.WorkoutRoutineEntity
import com.example.sport.core.database.relation.RoutineWithDays
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.SplitType
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Obiettivi di un esercizio in scheda, modificati dal pannello del builder. */
data class ExerciseTargets(
    val sets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    /** Null = recupero predefinito dell'esercizio. */
    val restSeconds: Int?,
) {
    companion object {
        const val MIN_SETS = 1
        const val MAX_SETS = 12
        const val MAX_REPS = 100
        const val MAX_REST_SECONDS = 600
    }
}

/**
 * Costruzione e modifica delle schede: giorni, esercizi, superserie, calendario, varianti.
 *
 * Ogni operazione è una transazione e lascia la scheda coerente: posizioni compatte (0, 1, 2…),
 * superserie solo tra esercizi consecutivi, calendario che punta a giorni esistenti.
 */
@Singleton
class RoutineBuilderRepository @Inject constructor(
    private val database: SportDatabase,
    private val clock: Clock,
) {
    private val dao = database.routineDao()
    private val exerciseDao = database.exerciseDao()

    fun observeRoutine(routineId: Long): Flow<RoutineWithDays?> = dao.observeRoutineWithDays(routineId)

    fun observeRoutines(): Flow<List<RoutineWithDays>> = dao.observeRoutinesWithDays()

    // ------------------------------------------------------------------ Schede

    /**
     * Nuova scheda personalizzata con un primo giorno vuoto. Diventa attiva se non ce n'è un'altra:
     * chi crea la prima scheda la vuole vedere subito in "Oggi".
     */
    suspend fun createRoutine(name: String, firstDayName: String): Long = database.withTransaction {
        val now = now()
        val routineId = dao.insertRoutine(
            WorkoutRoutineEntity(name = name.trim(), splitType = SplitType.CUSTOM, createdAt = now, updatedAt = now),
        )
        dao.appendDay(routineId, firstDayName.trim())
        if (dao.getActiveRoutineId() == null) dao.setActiveRoutine(routineId, now)
        routineId
    }

    suspend fun renameRoutine(routineId: Long, name: String) = database.withTransaction {
        val routine = dao.getRoutine(routineId) ?: return@withTransaction
        dao.updateRoutine(routine.copy(name = name.trim(), updatedAt = now()))
    }

    suspend fun setActive(routineId: Long) = dao.setActiveRoutine(routineId, now())

    /** Lo storico resta: le sessioni passate perdono solo il collegamento alla scheda. */
    suspend fun deleteRoutine(routineId: Long) = database.withTransaction {
        dao.getRoutine(routineId)?.let { dao.deleteRoutine(it) }
    }

    /**
     * Copia completa (giorni, esercizi, superserie, calendario) collegata all'originale.
     * Con [deload] le serie sono ridotte del 40% circa (4 → 2, 3 → 2, 5 → 3): settimana di scarico
     * con gli stessi esercizi e le stesse regolazioni.
     */
    suspend fun duplicateRoutine(routineId: Long, name: String, deload: Boolean): Long = database.withTransaction {
        val copyId = dao.duplicateRoutine(routineId, name.trim(), now())
        if (deload) {
            dao.getDays(copyId).forEach { day ->
                dao.updateRoutineExercises(
                    dao.getRoutineExercises(day.id).map { it.copy(targetSets = deloadSets(it.targetSets)) },
                )
            }
        }
        copyId
    }

    // ------------------------------------------------------------------ Giorni

    suspend fun addDay(routineId: Long, name: String): Long = database.withTransaction {
        dao.appendDay(routineId, name.trim()).also { touch(routineId) }
    }

    suspend fun renameDay(dayId: Long, name: String) = database.withTransaction {
        val day = dao.getDay(dayId) ?: return@withTransaction
        dao.updateDay(day.copy(name = name.trim()))
        touch(day.routineId)
    }

    suspend fun duplicateDay(dayId: Long, name: String): Long = database.withTransaction {
        val day = checkNotNull(dao.getDay(dayId)) { "Giorno $dayId inesistente" }
        dao.duplicateDay(dayId, name.trim()).also { touch(day.routineId) }
    }

    /** Elimina il giorno con i suoi esercizi; i giorni della settimana che lo usavano diventano riposo. */
    suspend fun deleteDay(dayId: Long) = database.withTransaction {
        val day = dao.getDay(dayId) ?: return@withTransaction
        dao.deleteDay(day)
        dao.updateDays(dao.getDays(day.routineId).mapIndexed { index, item -> item.copy(position = index) })
        touch(day.routineId)
    }

    suspend fun moveDay(routineId: Long, fromIndex: Int, toIndex: Int) = database.withTransaction {
        dao.moveDay(routineId, fromIndex, toIndex)
        touch(routineId)
    }

    // ------------------------------------------------------------------ Esercizi

    /**
     * Aggiunge in coda al giorno con obiettivi ragionevoli: 3 serie da 8–12 ripetizioni (nessuna
     * ripetizione per gli esercizi a tempo o distanza) e il recupero predefinito dell'esercizio.
     */
    suspend fun addExercise(dayId: Long, exerciseId: String): Long = database.withTransaction {
        val day = checkNotNull(dao.getDay(dayId)) { "Giorno $dayId inesistente" }
        val exercise = checkNotNull(exerciseDao.getById(exerciseId)) { "Esercizio $exerciseId inesistente" }
        val countsReps = exercise.trackingType != ExerciseTrackingType.DURATION &&
            exercise.trackingType != ExerciseTrackingType.DISTANCE_DURATION
        val id = dao.appendExerciseToDay(
            RoutineExerciseEntity(
                routineDayId = dayId,
                exerciseId = exerciseId,
                position = 0,
                targetSets = DEFAULT_SETS,
                targetRepsMin = if (countsReps) DEFAULT_REPS_MIN else null,
                targetRepsMax = if (countsReps) DEFAULT_REPS_MAX else null,
            ),
        )
        touch(day.routineId)
        id
    }

    suspend fun updateTargets(routineExerciseId: Long, targets: ExerciseTargets) = database.withTransaction {
        val item = dao.getRoutineExercise(routineExerciseId) ?: return@withTransaction
        val repsMin = targets.repsMin?.coerceIn(1, ExerciseTargets.MAX_REPS)
        val repsMax = targets.repsMax?.coerceIn(1, ExerciseTargets.MAX_REPS)
        dao.updateRoutineExercise(
            item.copy(
                targetSets = targets.sets.coerceIn(ExerciseTargets.MIN_SETS, ExerciseTargets.MAX_SETS),
                // Un intervallo scritto al contrario (12–8) viene raddrizzato.
                targetRepsMin = if (repsMin != null && repsMax != null) minOf(repsMin, repsMax) else repsMin,
                targetRepsMax = if (repsMin != null && repsMax != null) maxOf(repsMin, repsMax) else repsMax,
                restSeconds = targets.restSeconds?.coerceIn(0, ExerciseTargets.MAX_REST_SECONDS),
            ),
        )
        touchDay(item.routineDayId)
    }

    /** Stessi obiettivi e posto in scaletta, esercizio diverso (es. macchina non disponibile in questa palestra). */
    suspend fun replaceExercise(routineExerciseId: Long, exerciseId: String) = database.withTransaction {
        val item = dao.getRoutineExercise(routineExerciseId) ?: return@withTransaction
        checkNotNull(exerciseDao.getById(exerciseId)) { "Esercizio $exerciseId inesistente" }
        dao.updateRoutineExercise(item.copy(exerciseId = exerciseId))
        touchDay(item.routineDayId)
    }

    suspend fun exerciseIdOf(routineExerciseId: Long): String? = dao.getRoutineExercise(routineExerciseId)?.exerciseId

    suspend fun removeExercise(routineExerciseId: Long) = database.withTransaction {
        val item = dao.getRoutineExercise(routineExerciseId) ?: return@withTransaction
        dao.deleteRoutineExercise(item)
        rewriteDay(item.routineDayId, dao.getRoutineExercises(item.routineDayId))
    }

    /** Fine del trascinamento: da [fromIndex] a [toIndex] nello stesso giorno. */
    suspend fun moveExercise(dayId: Long, fromIndex: Int, toIndex: Int) = database.withTransaction {
        val items = dao.getRoutineExercises(dayId).toMutableList()
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return@withTransaction
        items.add(toIndex, items.removeAt(fromIndex))
        rewriteDay(dayId, items)
    }

    /** Sposta in coda a un altro giorno della stessa scheda (fuori da ogni superserie). */
    suspend fun moveExerciseToDay(routineExerciseId: Long, targetDayId: Long) = database.withTransaction {
        val item = dao.getRoutineExercise(routineExerciseId) ?: return@withTransaction
        if (item.routineDayId == targetDayId) return@withTransaction
        val source = checkNotNull(dao.getDay(item.routineDayId))
        val target = checkNotNull(dao.getDay(targetDayId)) { "Giorno $targetDayId inesistente" }
        require(source.routineId == target.routineId) { "I giorni appartengono a schede diverse" }
        dao.updateRoutineExercise(
            item.copy(routineDayId = targetDayId, position = dao.nextExercisePosition(targetDayId), supersetGroup = null),
        )
        rewriteDay(item.routineDayId, dao.getRoutineExercises(item.routineDayId))
        rewriteDay(targetDayId, dao.getRoutineExercises(targetDayId))
    }

    /** Collega l'esercizio al successivo in superserie (unendo i gruppi se entrambi ne hanno uno). */
    suspend fun linkWithNext(routineExerciseId: Long) = database.withTransaction {
        val item = dao.getRoutineExercise(routineExerciseId) ?: return@withTransaction
        rewriteDay(item.routineDayId, linkWithNext(dao.getRoutineExercises(item.routineDayId), routineExerciseId))
    }

    suspend fun unlinkSuperset(routineExerciseId: Long) = database.withTransaction {
        val item = dao.getRoutineExercise(routineExerciseId) ?: return@withTransaction
        rewriteDay(
            item.routineDayId,
            dao.getRoutineExercises(item.routineDayId).map { if (it.id == routineExerciseId) it.copy(supersetGroup = null) else it },
        )
    }

    // ------------------------------------------------------------------ Calendario

    /** [dayId] null = riposo. */
    suspend fun assignWeekday(routineId: Long, dayOfWeek: DayOfWeek, dayId: Long?) = database.withTransaction {
        if (dayId == null) dao.clearScheduleDay(routineId, dayOfWeek) else dao.assignWeekday(routineId, dayOfWeek, dayId)
        touch(routineId)
    }

    // ------------------------------------------------------------------ Supporto

    /** Riscrive il giorno nell'ordine dato: posizioni compatte e superserie normalizzate. */
    private suspend fun rewriteDay(dayId: Long, ordered: List<RoutineExerciseEntity>) {
        val normalized = normalizeSupersets(ordered.mapIndexed { index, item -> item.copy(position = index) })
        val current = dao.getRoutineExercises(dayId).associateBy { it.id }
        val changed = normalized.filter { current[it.id] != it }
        if (changed.isNotEmpty()) dao.updateRoutineExercises(changed)
        touchDay(dayId)
    }

    private suspend fun touchDay(dayId: Long) {
        dao.getDay(dayId)?.let { touch(it.routineId) }
    }

    private suspend fun touch(routineId: Long) = dao.touch(routineId, now())

    private fun now(): Instant = Instant.now(clock)

    companion object {
        const val DEFAULT_SETS = 3
        const val DEFAULT_REPS_MIN = 8
        const val DEFAULT_REPS_MAX = 12

        internal fun deloadSets(sets: Int): Int = (sets * DELOAD_FACTOR).roundToInt().coerceAtLeast(1)

        private const val DELOAD_FACTOR = 0.6
    }
}

/**
 * Superserie valide solo tra esercizi consecutivi: ogni sequenza continua con lo stesso gruppo
 * resta una superserie (rinumerata 1, 2, 3… nell'ordine del giorno); un esercizio rimasto da solo
 * nel suo gruppo, o separato dagli altri da un trascinamento, torna normale.
 */
internal fun normalizeSupersets(ordered: List<RoutineExerciseEntity>): List<RoutineExerciseEntity> {
    val result = ordered.toMutableList()
    var nextGroup = 1
    var start = 0
    while (start < result.size) {
        val group = result[start].supersetGroup
        var end = start
        if (group != null) {
            while (end + 1 < result.size && result[end + 1].supersetGroup == group) end++
            val newGroup = if (end > start) nextGroup++ else null
            for (index in start..end) result[index] = result[index].copy(supersetGroup = newGroup)
        }
        start = end + 1
    }
    return result
}

/** Collega [routineExerciseId] al successivo; se il successivo è in un altro gruppo, i gruppi si fondono. */
internal fun linkWithNext(ordered: List<RoutineExerciseEntity>, routineExerciseId: Long): List<RoutineExerciseEntity> {
    val index = ordered.indexOfFirst { it.id == routineExerciseId }
    if (index < 0 || index == ordered.lastIndex) return ordered
    val current = ordered[index]
    val next = ordered[index + 1]
    val group = current.supersetGroup ?: next.supersetGroup ?: ((ordered.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1)
    val merged = next.supersetGroup
    return ordered.mapIndexed { position, item ->
        when {
            position == index || position == index + 1 -> item.copy(supersetGroup = group)
            merged != null && item.supersetGroup == merged -> item.copy(supersetGroup = group)
            else -> item
        }
    }
}
