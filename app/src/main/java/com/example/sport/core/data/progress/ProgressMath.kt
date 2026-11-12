package com.example.sport.core.data.progress

import com.example.sport.core.database.relation.CompletedSetRow
import java.time.Instant
import java.time.LocalDate

/*
 * Calcoli dei progressi: funzioni pure, senza Android né database, così sono verificabili con
 * semplici test JVM e restano coerenti tra grafici, record e riepiloghi.
 */

/** Oltre le 12 ripetizioni la stima del massimale perde affidabilità: non la calcoliamo. */
const val MAX_REPS_FOR_ONE_REP_MAX = 12

/**
 * Massimale stimato con la formula di Epley: kg × (1 + ripetizioni / 30); con 1 ripetizione è il
 * carico stesso. È la stessa formula della query dei grafici (`ProgressDao`).
 */
fun estimateOneRepMax(weightKg: Double, reps: Int): Double? = when {
    weightKg <= 0.0 || reps <= 0 -> null
    reps == 1 -> weightKg
    reps <= MAX_REPS_FOR_ONE_REP_MAX -> weightKg * (1.0 + reps / 30.0)
    else -> null
}

// ---------------------------------------------------------------------------- Peso corporeo

data class DatedWeight(val date: LocalDate, val weightKg: Double)

/** Pesata con la media mobile dei giorni precedenti (finestra di [WEEK_DAYS] giorni di calendario). */
data class WeightTrendPoint(val date: LocalDate, val weightKg: Double, val averageKg: Double)

const val WEEK_DAYS = 7

/**
 * Media mobile su una finestra di giorni di calendario (non di pesate): per ogni pesata, media di
 * tutte le pesate degli ultimi [windowDays] giorni, lei compresa. Giorni senza pesata non pesano,
 * quindi chi si pesa a giorni alterni ottiene comunque una media sensata.
 *
 * @param entries pesate in qualsiasi ordine, una per giorno.
 */
fun movingAverage(entries: List<DatedWeight>, windowDays: Int = WEEK_DAYS): List<WeightTrendPoint> {
    require(windowDays >= 1) { "Finestra non valida: $windowDays" }
    val sorted = entries.sortedBy { it.date }
    var windowStart = 0
    var windowSum = 0.0
    return sorted.mapIndexed { index, entry ->
        windowSum += entry.weightKg
        val firstIncluded = entry.date.minusDays((windowDays - 1).toLong())
        while (sorted[windowStart].date < firstIncluded) {
            windowSum -= sorted[windowStart].weightKg
            windowStart++
        }
        WeightTrendPoint(entry.date, entry.weightKg, windowSum / (index - windowStart + 1))
    }
}

/**
 * Variazione della media mobile nell'ultima settimana: media dell'ultima pesata meno la media
 * dell'ultima pesata di almeno 7 giorni prima. Null se lo storico non copre una settimana.
 */
fun weeklyChange(points: List<WeightTrendPoint>): Double? {
    val latest = points.maxByOrNull { it.date } ?: return null
    val reference = points.filter { !it.date.isAfter(latest.date.minusDays(WEEK_DAYS.toLong())) }.maxByOrNull { it.date } ?: return null
    return latest.averageKg - reference.averageKg
}

// ---------------------------------------------------------------------------- Record personali

enum class RecordKind {
    /** Serie più pesante (a parità di carico, quella con più ripetizioni). */
    HEAVIEST_SET,

    /** Miglior massimale stimato. */
    ESTIMATED_ONE_REP_MAX,

    /** Più volume in una singola sessione. */
    SESSION_VOLUME,
}

data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val kind: RecordKind,
    val valueKg: Double,
    /** Serie che ha stabilito il record (assenti per il volume di sessione). */
    val weightKg: Double?,
    val reps: Int?,
    val sessionId: Long,
    val achievedAt: Instant,
)

/** Record di un esercizio: la prima volta che è stato raggiunto il valore migliore. */
data class ExerciseRecords(
    val exerciseId: String,
    val exerciseName: String,
    val heaviestSet: PersonalRecord,
    val bestOneRepMax: PersonalRecord?,
    val bestSessionVolume: PersonalRecord,
) {
    /** Data del record più recente: ordina la lista "ultimi record". */
    val latestAchievedAt: Instant
        get() = listOfNotNull(heaviestSet, bestOneRepMax, bestSessionVolume).maxOf { it.achievedAt }
}

/**
 * Record per esercizio dalle serie completate. In caso di parità vale la prima volta: un record
 * eguagliato non è un record nuovo.
 */
fun personalRecords(sets: List<CompletedSetRow>): List<ExerciseRecords> =
    sets.groupBy { it.exerciseId }.map { (exerciseId, exerciseSets) ->
        val chronological = exerciseSets.sortedBy { it.performedAt }
        val name = chronological.last().exerciseName

        var heaviest = chronological.first()
        var bestEstimate: Pair<CompletedSetRow, Double>? = null
        chronological.forEach { set ->
            if (set.weightKg > heaviest.weightKg || (set.weightKg == heaviest.weightKg && set.reps > heaviest.reps)) heaviest = set
            estimateOneRepMax(set.weightKg, set.reps)?.let { estimate ->
                if (bestEstimate == null || estimate > bestEstimate!!.second) bestEstimate = set to estimate
            }
        }
        val volumes = chronological.groupBy { it.sessionId }.map { (sessionId, sessionSets) ->
            Triple(sessionId, sessionSets.first().performedAt, sessionSets.sumOf { it.weightKg * it.reps })
        }
        val bestVolume = volumes.reduce { best, candidate -> if (candidate.third > best.third) candidate else best }

        ExerciseRecords(
            exerciseId = exerciseId,
            exerciseName = name,
            heaviestSet = heaviest.toRecord(RecordKind.HEAVIEST_SET, heaviest.weightKg),
            bestOneRepMax = bestEstimate?.let { (set, estimate) -> set.toRecord(RecordKind.ESTIMATED_ONE_REP_MAX, estimate) },
            bestSessionVolume = PersonalRecord(
                exerciseId = exerciseId,
                exerciseName = name,
                kind = RecordKind.SESSION_VOLUME,
                valueKg = bestVolume.third,
                weightKg = null,
                reps = null,
                sessionId = bestVolume.first,
                achievedAt = bestVolume.second,
            ),
        )
    }.sortedByDescending { it.latestAchievedAt }

private fun CompletedSetRow.toRecord(kind: RecordKind, value: Double) = PersonalRecord(
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    kind = kind,
    valueKg = value,
    weightKg = weightKg,
    reps = reps,
    sessionId = sessionId,
    achievedAt = performedAt,
)

// ---------------------------------------------------------------------------- Riepilogo per esercizio

/** Andamento di un esercizio: ultima sessione contro la prima, sul massimale stimato. */
data class ExerciseTrend(
    val exerciseId: String,
    val exerciseName: String,
    val sessionCount: Int,
    val lastPerformedAt: Instant,
    /** Miglior massimale stimato dell'ultima sessione (null se solo serie oltre le 12 ripetizioni). */
    val latestOneRepMax: Double?,
    /** Variazione del massimale stimato dalla prima sessione all'ultima. */
    val oneRepMaxChange: Double?,
)

/** Esercizi allenati, dal più recente. */
fun exerciseTrends(sets: List<CompletedSetRow>): List<ExerciseTrend> =
    sets.groupBy { it.exerciseId }.map { (exerciseId, exerciseSets) ->
        val sessions = exerciseSets.groupBy { it.sessionId }.values.sortedBy { it.first().performedAt }
        fun List<CompletedSetRow>.bestEstimate() = mapNotNull { estimateOneRepMax(it.weightKg, it.reps) }.maxOrNull()
        val first = sessions.first().bestEstimate()
        val latest = sessions.last().bestEstimate()
        ExerciseTrend(
            exerciseId = exerciseId,
            exerciseName = exerciseSets.last().exerciseName,
            sessionCount = sessions.size,
            lastPerformedAt = sessions.last().first().performedAt,
            latestOneRepMax = latest,
            oneRepMaxChange = if (first != null && latest != null && sessions.size > 1) latest - first else null,
        )
    }.sortedByDescending { it.lastPerformedAt }
