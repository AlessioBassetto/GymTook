package com.example.sport.core.data.progress

import com.example.sport.core.database.relation.InclineSetRow
import java.time.Instant
import kotlin.math.abs

/**
 * Una sessione di tapis roulant in salita, riassunta: tempo totale e pendenza media pesata sul
 * tempo (10 minuti al 12% contano più di 2 minuti al 15%), più la pendenza massima raggiunta.
 */
data class InclineSession(
    val sessionId: Long,
    val performedAt: Instant,
    val totalSeconds: Int,
    val averageIncline: Double,
    val maxIncline: Double,
    val setCount: Int,
)

/**
 * Come è andata una sessione rispetto alla precedente. Le tolleranze evitano di segnalare come
 * progresso o calo differenze che sono solo arrotondamenti ([INCLINE_TOLERANCE], [TIME_TOLERANCE_SECONDS]).
 */
enum class InclineTrend {
    /** Prima sessione del periodo: niente con cui confrontarla. */
    FIRST,

    /** Stessa pendenza mantenuta più a lungo. */
    LONGER_SAME_INCLINE,

    /** Pendenza maggiore a parità di durata. */
    STEEPER_SAME_TIME,

    /** Più pendenza e più tempo. */
    STEEPER_AND_LONGER,

    /** Pendenza maggiore, ma per meno tempo. */
    STEEPER,

    /** Più tempo, ma a pendenza minore. */
    LONGER,

    /** Uguale alla volta precedente. */
    SAME,

    /** Meno pendenza e meno tempo (o uno dei due a parità dell'altro). */
    LOWER,
    ;

    val isImprovement: Boolean get() = this == LONGER_SAME_INCLINE || this == STEEPER_SAME_TIME || this == STEEPER_AND_LONGER
}

/** Mezzo punto è lo scatto minimo dei tapis roulant: sotto il quarto di punto è "uguale". */
const val INCLINE_TOLERANCE = 0.25

/** Mezzo minuto di differenza è ancora "stessa durata". */
const val TIME_TOLERANCE_SECONDS = 30

/** Righe per serie (in ordine cronologico) → una [InclineSession] per allenamento, dalla più vecchia. */
fun inclineSessions(rows: List<InclineSetRow>): List<InclineSession> =
    rows.groupBy { it.sessionId }
        .map { (sessionId, sets) ->
            val seconds = sets.sumOf { it.durationSeconds }
            // Pendenza non scritta = in piano.
            val weighted = sets.sumOf { (it.inclinePercent ?: 0.0) * it.durationSeconds }
            InclineSession(
                sessionId = sessionId,
                performedAt = sets.first().performedAt,
                totalSeconds = seconds,
                averageIncline = if (seconds > 0) weighted / seconds else 0.0,
                maxIncline = sets.maxOf { it.inclinePercent ?: 0.0 },
                setCount = sets.size,
            )
        }
        .sortedBy { it.performedAt }

/** Confronto di [current] con la sessione [previous] (null = prima sessione). */
fun inclineTrend(previous: InclineSession?, current: InclineSession): InclineTrend {
    if (previous == null) return InclineTrend.FIRST
    val inclineDelta = current.averageIncline - previous.averageIncline
    val timeDelta = current.totalSeconds - previous.totalSeconds
    val steeper = inclineDelta > INCLINE_TOLERANCE
    val flatter = inclineDelta < -INCLINE_TOLERANCE
    val longer = timeDelta > TIME_TOLERANCE_SECONDS
    val shorter = timeDelta < -TIME_TOLERANCE_SECONDS
    val sameIncline = abs(inclineDelta) <= INCLINE_TOLERANCE
    val sameTime = abs(timeDelta) <= TIME_TOLERANCE_SECONDS
    return when {
        steeper && longer -> InclineTrend.STEEPER_AND_LONGER
        sameIncline && longer -> InclineTrend.LONGER_SAME_INCLINE
        steeper && sameTime -> InclineTrend.STEEPER_SAME_TIME
        steeper && shorter -> InclineTrend.STEEPER
        flatter && longer -> InclineTrend.LONGER
        sameIncline && sameTime -> InclineTrend.SAME
        else -> InclineTrend.LOWER
    }
}
