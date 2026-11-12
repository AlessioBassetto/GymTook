package com.example.sport.feature.exercises

import androidx.compose.runtime.Immutable
import com.example.sport.core.database.query.ExerciseFilter
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Filtri della libreria scelti dall'utente. Serializzabili: sopravvivono a rotazione e morte del
 * processo tramite `SavedStateHandle`.
 *
 * Il gruppo muscolare (riga di chip) e i muscoli specifici (pannello filtri) si escludono a vicenda
 * dove si contraddirebbero: i muscoli specifici sono più precisi e, se presenti, sostituiscono il
 * gruppo; scegliere un gruppo toglie i muscoli specifici degli altri gruppi.
 */
@Immutable
@Serializable
data class LibraryFilters(
    val group: MuscleGroup? = null,
    val equipment: Set<Equipment> = emptySet(),
    val categories: Set<ExerciseCategory> = emptySet(),
    val muscles: Set<Muscle> = emptySet(),
    val includeSecondary: Boolean = false,
    val onlyFavorites: Boolean = false,
    val onlyCustom: Boolean = false,
) {
    /** Filtri del pannello attivi (il numero sul chip "Filtri"). */
    val advancedCount: Int
        get() = equipment.size + categories.size + muscles.size +
            (if (includeSecondary) 1 else 0) + (if (onlyCustom) 1 else 0)

    val isEmpty: Boolean get() = this == LibraryFilters()

    fun selectGroup(newGroup: MuscleGroup?): LibraryFilters =
        copy(group = newGroup, muscles = if (newGroup == null) muscles else muscles.filterTo(mutableSetOf()) { it.group == newGroup })

    fun toggleMuscle(muscle: Muscle): LibraryFilters {
        val updated = muscles.toggle(muscle)
        return copy(muscles = updated, group = group?.takeIf { current -> updated.all { it.group == current } })
    }

    fun toggleEquipment(value: Equipment) = copy(equipment = equipment.toggle(value))

    fun toggleCategory(value: ExerciseCategory) = copy(categories = categories.toggle(value))

    /** "Azzera" nel pannello: toglie i filtri avanzati, lascia gruppo e preferiti della riga di chip. */
    fun clearAdvanced() = copy(equipment = emptySet(), categories = emptySet(), muscles = emptySet(), includeSecondary = false, onlyCustom = false)

    fun toQuery(text: String) = ExerciseFilter(
        query = text,
        equipment = equipment,
        categories = categories,
        muscleGroups = if (muscles.isEmpty()) setOfNotNull(group) else emptySet(),
        muscles = muscles,
        includeSecondaryMuscles = includeSecondary,
        onlyFavorites = onlyFavorites,
        onlyCustom = onlyCustom,
    )

    fun encode(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun decode(value: String?): LibraryFilters =
            value?.let { runCatching { Json.decodeFromString(serializer(), it) }.getOrNull() } ?: LibraryFilters()

        private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
    }
}
