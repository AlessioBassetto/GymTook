package com.example.sport.feature.nutrition

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.core.data.DietPlanRepository
import com.example.sport.core.data.DietTargets
import com.example.sport.core.data.MealItemInput
import com.example.sport.core.database.entity.MealItemEntity
import com.example.sport.core.database.relation.DietPlanWithMeals
import com.example.sport.core.database.relation.MealWithItems
import com.example.sport.core.model.MealType
import com.example.sport.navigation.DietPlanEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/** Calorie e macro sommati (pasto, piano). */
@Immutable
data class MacroTotals(val caloriesKcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double) {
    companion object {
        val ZERO = MacroTotals(0.0, 0.0, 0.0, 0.0)

        fun of(items: List<MealItemEntity>) = MacroTotals(
            caloriesKcal = items.sumOf { it.caloriesKcal ?: 0.0 },
            proteinG = items.sumOf { it.proteinG ?: 0.0 },
            carbsG = items.sumOf { it.carbsG ?: 0.0 },
            fatG = items.sumOf { it.fatG ?: 0.0 },
        )
    }

    operator fun plus(other: MacroTotals) = MacroTotals(
        caloriesKcal + other.caloriesKcal,
        proteinG + other.proteinG,
        carbsG + other.carbsG,
        fatG + other.fatG,
    )
}

@Immutable
data class EditorItemUi(val id: Long, val input: MealItemInput)

@Immutable
data class EditorMealUi(
    val id: Long,
    val type: MealType,
    val customName: String?,
    val time: LocalTime?,
    val items: List<EditorItemUi>,
    val totals: MacroTotals,
    val isFirst: Boolean,
    val isLast: Boolean,
)

sealed interface DietPlanEditorUiState {
    data object Loading : DietPlanEditorUiState
    data object NotFound : DietPlanEditorUiState

    data class Content(
        val planId: Long,
        val name: String,
        val isActive: Boolean,
        val targets: DietTargets,
        val notes: String?,
        val meals: List<EditorMealUi>,
        /** Somma di tutti i pasti: si confronta con gli obiettivi. */
        val totals: MacroTotals,
    ) : DietPlanEditorUiState
}

sealed interface DietPlanEditorEvent {
    data class OpenPlan(val planId: Long) : DietPlanEditorEvent
    data object Deleted : DietPlanEditorEvent
}

@HiltViewModel
class DietPlanEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DietPlanRepository,
) : ViewModel() {

    private val planId = savedStateHandle.toRoute<DietPlanEditorRoute>().planId

    private val eventChannel = Channel<DietPlanEditorEvent>(Channel.BUFFERED)
    val events: Flow<DietPlanEditorEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<DietPlanEditorUiState> = repository.observePlan(planId)
        .map { plan -> plan?.toUi() ?: DietPlanEditorUiState.NotFound }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietPlanEditorUiState.Loading)

    fun onRename(name: String) = launch { repository.renamePlan(planId, name) }

    fun onTargets(targets: DietTargets) = launch { repository.updateTargets(planId, targets) }

    fun onNotes(notes: String) = launch { repository.updateNotes(planId, notes) }

    fun onSetActive() = launch { repository.setActive(planId) }

    fun onDuplicate(name: String) = launch {
        val copyId = repository.duplicatePlan(planId, name)
        eventChannel.send(DietPlanEditorEvent.OpenPlan(copyId))
    }

    fun onDelete() = launch {
        repository.deletePlan(planId)
        eventChannel.send(DietPlanEditorEvent.Deleted)
    }

    fun onAddMeal(type: MealType, name: String, time: LocalTime?) = launch { repository.addMeal(planId, type, name, time) }

    fun onUpdateMeal(mealId: Long, type: MealType, name: String, time: LocalTime?) =
        launch { repository.updateMeal(mealId, type, name, time) }

    fun onMoveMeal(mealId: Long, delta: Int) = launch { repository.moveMeal(mealId, delta) }

    fun onDeleteMeal(mealId: Long) = launch { repository.deleteMeal(mealId) }

    fun onAddItem(mealId: Long, input: MealItemInput) = launch { repository.addItem(mealId, input) }

    fun onUpdateItem(itemId: Long, input: MealItemInput) = launch { repository.updateItem(itemId, input) }

    fun onDeleteItem(itemId: Long) = launch { repository.deleteItem(itemId) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun DietPlanWithMeals.toUi(): DietPlanEditorUiState.Content {
        val ordered = orderedMeals
        val meals = ordered.mapIndexed { index, meal -> meal.toUi(isFirst = index == 0, isLast = index == ordered.lastIndex) }
        return DietPlanEditorUiState.Content(
            planId = plan.id,
            name = plan.name,
            isActive = plan.isActive,
            targets = DietTargets(plan.targetCaloriesKcal, plan.targetProteinG, plan.targetCarbsG, plan.targetFatG),
            notes = plan.notes,
            meals = meals,
            totals = meals.fold(MacroTotals.ZERO) { sum, meal -> sum + meal.totals },
        )
    }

    private fun MealWithItems.toUi(isFirst: Boolean, isLast: Boolean) = EditorMealUi(
        id = meal.id,
        type = meal.mealType,
        customName = meal.name,
        time = meal.timeOfDay,
        items = orderedItems.map { item ->
            EditorItemUi(
                id = item.id,
                input = MealItemInput(
                    name = item.name,
                    quantity = item.quantity,
                    unit = item.unit,
                    caloriesKcal = item.caloriesKcal,
                    proteinG = item.proteinG,
                    carbsG = item.carbsG,
                    fatG = item.fatG,
                    notes = item.notes,
                ),
            )
        },
        totals = MacroTotals.of(items),
        isFirst = isFirst,
        isLast = isLast,
    )
}
