package com.example.sport.core.data

import com.example.sport.core.database.relation.RoutineDayWithExercises
import kotlin.math.roundToInt

/** Secondi di lavoro stimati per serie (esecuzione + preparazione). */
private const val WORK_SECONDS_PER_SET = 45

/**
 * Durata stimata di un giorno di scheda: ~45 s di lavoro per serie più il recupero previsto.
 * In una superserie si recupera solo dopo l'ultimo esercizio del giro: gli altri non contano
 * recupero. Usata da "Oggi" e dal builder, così le due stime coincidono.
 */
fun RoutineDayWithExercises.estimatedMinutes(): Int {
    val ordered = orderedExercises
    val seconds = ordered.withIndex().sumOf { (index, item) ->
        val group = item.routineExercise.supersetGroup
        val restsAfter = group == null || ordered.getOrNull(index + 1)?.routineExercise?.supersetGroup != group
        val rest = if (restsAfter) item.routineExercise.restSeconds ?: item.exercise.defaultRestSeconds else 0
        item.routineExercise.targetSets * (WORK_SECONDS_PER_SET + rest)
    }
    return (seconds / 60.0).roundToInt()
}

/** Serie totali previste nel giorno. */
val RoutineDayWithExercises.totalSets: Int
    get() = exercises.sumOf { it.routineExercise.targetSets }
