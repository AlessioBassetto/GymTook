package com.example.sport.core.data

import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.model.DifficultyLevel
import com.example.sport.core.model.Muscle

/**
 * Ordina le alternative per la sostituzione al volo. Un buon cambio allena gli stessi muscoli con un
 * movimento simile: per lo stacco rumeno lo stacco a gambe tese o il good morning, non box jump o
 * girate, che hanno lo stesso muscolo primario ma sono esercizi di potenza.
 *
 * Segnali, tutti già nel catalogo:
 * - somiglianza dell'insieme di muscoli coinvolti (primari + secondari, indice di Jaccard);
 * - stesso muscolo specifico, stessa meccanica (multiarticolare/monoarticolare) e tipo di forza;
 * - stesse metriche da registrare (peso e ripetizioni vs tempo vs distanza);
 * - parole in comune nel nome, in italiano o in inglese (varianti dello stesso esercizio);
 * - esercizi base curati e preferiti dell'utente.
 * Un tipo di movimento diverso (forza vs olimpico, pliometrico o cardio) pesa molto in negativo; i
 * movimenti che coinvolgono molti più muscoli dell'originale e quelli avanzati, se l'originale non
 * lo è, vengono penalizzati.
 */
internal object AlternativeRanker {

    private const val MUSCLE_OVERLAP = 40.0
    private const val SAME_PRIMARY_MUSCLE = 20.0
    private const val SAME_MECHANIC = 10.0
    private const val SAME_FORCE = 5.0
    private const val SAME_TRACKING = 10.0
    private const val DIFFERENT_METRICS = -40.0
    private const val NAME_OVERLAP = 30.0
    private const val CURATED = 15.0
    private const val FAVORITE = 8.0
    private const val SAME_EQUIPMENT = 3.0
    private const val EXTRA_MUSCLE = -4.0
    private const val ADVANCED_JUMP = -8.0
    private const val DIFFERENT_CATEGORY = -35.0

    /** [muscles]: muscoli coinvolti (primari e secondari) per id, compreso l'originale. */
    fun rank(
        original: ExerciseEntity,
        candidates: List<ExerciseEntity>,
        muscles: Map<String, Set<Muscle>>,
    ): List<ExerciseEntity> {
        val reference = Profile.of(original, muscles)
        return candidates
            .asSequence()
            .filter { it.id != original.id }
            .map { candidate -> candidate to score(reference, Profile.of(candidate, muscles)) }
            .sortedWith(
                compareByDescending<Pair<ExerciseEntity, Double>> { it.second }
                    .thenBy { it.first.name.lowercase() },
            )
            .map { it.first }
            .toList()
    }

    private fun score(original: Profile, candidate: Profile): Double {
        val a = original.exercise
        val b = candidate.exercise
        var score = MUSCLE_OVERLAP * jaccard(original.muscles, candidate.muscles)
        if (a.primaryMuscle == b.primaryMuscle) score += SAME_PRIMARY_MUSCLE
        if (a.mechanic != null && a.mechanic == b.mechanic) score += SAME_MECHANIC
        if (a.force != null && a.force == b.force) score += SAME_FORCE
        score += when {
            a.trackingType == b.trackingType -> SAME_TRACKING
            original.metrics != candidate.metrics -> DIFFERENT_METRICS
            else -> 0.0
        }
        score += NAME_OVERLAP * maxOf(jaccard(original.nameTokens, candidate.nameTokens), jaccard(original.englishTokens, candidate.englishTokens))
        if (b.keyPoints.isNotEmpty()) score += CURATED
        if (b.isFavorite) score += FAVORITE
        if (a.equipment == b.equipment) score += SAME_EQUIPMENT
        score += EXTRA_MUSCLE * (candidate.muscles.size - original.muscles.size - 1).coerceAtLeast(0)
        if (b.level == DifficultyLevel.ADVANCED && a.level != DifficultyLevel.ADVANCED) score += ADVANCED_JUMP
        if (a.category != b.category) score += DIFFERENT_CATEGORY
        return score
    }

    private class Profile(
        val exercise: ExerciseEntity,
        val muscles: Set<Muscle>,
        val metrics: List<SetMetric>,
        val nameTokens: Set<String>,
        val englishTokens: Set<String>,
    ) {
        companion object {
            fun of(exercise: ExerciseEntity, muscles: Map<String, Set<Muscle>>) = Profile(
                exercise = exercise,
                muscles = muscles[exercise.id].orEmpty() + exercise.primaryMuscle,
                metrics = SetMetric.forTracking(exercise.trackingType, withRpe = false),
                nameTokens = tokens(exercise.name),
                englishTokens = tokens(exercise.nameEn.orEmpty()),
            )
        }
    }

    private val STOP_WORDS = setOf(
        "a", "ad", "al", "alla", "alle", "allo", "ai", "agli", "con", "da", "dal", "dalla", "dai", "di", "del",
        "della", "dei", "delle", "e", "in", "su", "sul", "sulla", "per", "il", "la", "lo", "le", "gli", "i",
        "un", "una", "the", "with", "on", "of", "to", "and", "from", "an",
    )

    internal fun tokens(name: String): Set<String> =
        name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 && it !in STOP_WORDS }.toSet()

    private fun <T> jaccard(a: Set<T>, b: Set<T>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return (a intersect b).size.toDouble() / (a union b).size
    }
}
