package com.example.sport.feature.exercises

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sport.core.data.ExerciseRepository
import com.example.sport.core.media.MediaRepository
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import com.example.sport.core.ui.exercise.ExerciseRowUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class ExerciseListUiState(
    val isLoading: Boolean = true,
    val exercises: List<ExerciseRowUi> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    /** Testo e filtri sopravvivono a rotazione e morte del processo. */
    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val mutableFilters = MutableStateFlow(LibraryFilters.decode(savedStateHandle[KEY_FILTERS]))
    val filters: StateFlow<LibraryFilters> = mutableFilters.asStateFlow()

    val uiState: StateFlow<ExerciseListUiState> = combine(query.debounce(SEARCH_DEBOUNCE_MS), filters) { text, current ->
        current.toQuery(text)
    }
        .flatMapLatest { filter ->
            combine(exerciseRepository.observeExercises(filter), exerciseRepository.observeThumbnailPaths()) { exercises, thumbs ->
                ExerciseListUiState(
                    isLoading = false,
                    exercises = exercises.map { exercise ->
                        ExerciseRowUi.from(exercise, thumbs[exercise.id]?.let(mediaRepository::resolve))
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseListUiState())

    fun onQueryChange(text: String) {
        savedStateHandle[KEY_QUERY] = text
    }

    fun onGroupSelected(group: MuscleGroup?) = update { it.selectGroup(group) }

    fun onToggleFavorites() = update { it.copy(onlyFavorites = !it.onlyFavorites) }

    fun onToggleEquipment(equipment: Equipment) = update { it.toggleEquipment(equipment) }

    fun onToggleCategory(category: ExerciseCategory) = update { it.toggleCategory(category) }

    fun onToggleMuscle(muscle: Muscle) = update { it.toggleMuscle(muscle) }

    fun onIncludeSecondaryChange(include: Boolean) = update { it.copy(includeSecondary = include) }

    fun onOnlyCustomChange(onlyCustom: Boolean) = update { it.copy(onlyCustom = onlyCustom) }

    fun onClearAdvanced() = update { it.clearAdvanced() }

    private fun update(transform: (LibraryFilters) -> LibraryFilters) {
        val updated = transform(mutableFilters.value)
        mutableFilters.value = updated
        savedStateHandle[KEY_FILTERS] = updated.encode()
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_FILTERS = "filters"
        const val SEARCH_DEBOUNCE_MS = 150L
    }
}
