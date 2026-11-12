package com.example.sport.core.database.query

import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import com.example.sport.core.database.util.SearchText

/**
 * Filtri della libreria esercizi. Valori dentro la stessa categoria sono in OR
 * (Manubri OPPURE Cavi), categorie diverse sono in AND (Manubri E Petto).
 */
data class ExerciseFilter(
    /** Testo libero: ogni parola deve comparire (in qualunque ordine) in nome, nome EN o alias. */
    val query: String = "",
    val equipment: Set<Equipment> = emptySet(),
    /** Tipo di movimento (forza, olimpico, pliometrico, cardio). */
    val categories: Set<ExerciseCategory> = emptySet(),
    val muscleGroups: Set<MuscleGroup> = emptySet(),
    /** Regioni specifiche (es. solo petto alto). */
    val muscles: Set<Muscle> = emptySet(),
    /** Se false i filtri muscolari considerano solo i muscoli primari. */
    val includeSecondaryMuscles: Boolean = false,
    val onlyFavorites: Boolean = false,
    val onlyCustom: Boolean = false,
    /** Include esercizi nascosti dall'utente o ritirati dal catalogo. */
    val includeHidden: Boolean = false,
    val limit: Int? = null,
)

/**
 * Costruisce la query SQL parametrica per [ExerciseFilter].
 * Tutti i valori sono passati come bind-args: nessun input utente è concatenato nell'SQL.
 */
internal object ExerciseQueryBuilder {

    fun build(filter: ExerciseFilter): SimpleSQLiteQuery {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()

        if (!filter.includeHidden) {
            conditions += "e.is_hidden = 0 AND e.is_retired = 0"
        }

        val tokens = SearchText.tokenize(filter.query)
        tokens.forEach { token ->
            conditions += "e.search_text LIKE ? ESCAPE '\\'"
            args += "%${SearchText.escapeLike(token)}%"
        }

        if (filter.equipment.isNotEmpty()) {
            conditions += "e.equipment IN (${placeholders(filter.equipment.size)})"
            args.addAll(filter.equipment.map { it.name })
        }

        if (filter.categories.isNotEmpty()) {
            conditions += "e.category IN (${placeholders(filter.categories.size)})"
            args.addAll(filter.categories.map { it.name })
        }

        if (filter.muscleGroups.isNotEmpty() || filter.muscles.isNotEmpty()) {
            val targets = mutableListOf<String>()
            if (filter.muscleGroups.isNotEmpty()) {
                targets += "em.muscle_group IN (${placeholders(filter.muscleGroups.size)})"
                args.addAll(filter.muscleGroups.map { it.name })
            }
            if (filter.muscles.isNotEmpty()) {
                targets += "em.muscle IN (${placeholders(filter.muscles.size)})"
                args.addAll(filter.muscles.map { it.name })
            }
            val roleCondition = if (filter.includeSecondaryMuscles) "" else " AND em.role = 'PRIMARY'"
            conditions += "EXISTS (SELECT 1 FROM exercise_muscles em " +
                "WHERE em.exercise_id = e.id$roleCondition AND (${targets.joinToString(" OR ")}))"
        }

        if (filter.onlyFavorites) conditions += "e.is_favorite = 1"
        if (filter.onlyCustom) conditions += "e.is_custom = 1"

        val sql = buildString {
            append("SELECT e.* FROM exercises e")
            if (conditions.isNotEmpty()) {
                append(" WHERE ")
                append(conditions.joinToString(" AND ") { "($it)" })
            }
            append(" ORDER BY e.is_favorite DESC, ")
            tokens.firstOrNull()?.let { first ->
                // Gli esercizi il cui nome inizia con la prima parola cercata vengono prima.
                append("CASE WHEN e.search_text LIKE ? ESCAPE '\\' THEN 0 ELSE 1 END, ")
                args += "${SearchText.escapeLike(first)}%"
            }
            append("e.name COLLATE NOCASE ASC")
            filter.limit?.let {
                append(" LIMIT ?")
                args += it
            }
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")
}
