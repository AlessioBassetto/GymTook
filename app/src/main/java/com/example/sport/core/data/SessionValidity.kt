package com.example.sport.core.data

import com.example.sport.core.database.entity.ExerciseSetEntity
import java.time.Duration

/*
 * Regole che tengono fuori dallo storico le sessioni avviate per errore. Funzioni pure: le stesse
 * condizioni sono applicate dal database in `WorkoutSessionDao.finishSession`.
 */

/** Sotto questa durata un allenamento è quasi certamente un tocco sbagliato. */
val MIN_SESSION_DURATION: Duration = Duration.ofSeconds(60)

/** Giudizio su una sessione che l'utente sta per concludere. */
enum class SessionVerdict {
    /** Dati validi e durata plausibile: si salva con la conferma abituale. */
    VALID,

    /** Ci sono serie valide ma è durata meno di un minuto: l'app chiede se salvarla o scartarla. */
    TOO_SHORT,

    /** Nessuna serie completata con dati validi: sessione nulla, non viene salvata. */
    EMPTY,
}

/** Una serie descrive un lavoro svolto solo se riporta ripetizioni, una durata o una distanza. */
fun ExerciseSetEntity.hasValidData(): Boolean =
    (reps ?: 0) > 0 || (durationSeconds ?: 0) > 0 || (distanceMeters ?: 0.0) > 0.0

fun sessionVerdict(validCompletedSets: Int, elapsed: Duration): SessionVerdict = when {
    validCompletedSets <= 0 -> SessionVerdict.EMPTY
    elapsed < MIN_SESSION_DURATION -> SessionVerdict.TOO_SHORT
    else -> SessionVerdict.VALID
}
