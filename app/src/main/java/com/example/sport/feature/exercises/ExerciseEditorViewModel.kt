package com.example.sport.feature.exercises

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.core.data.CustomExerciseDraft
import com.example.sport.core.data.CustomExerciseRepository
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.navigation.ExerciseEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Immutable
data class ExerciseEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val draft: CustomExerciseDraft = CustomExerciseDraft(),
    /** Gli errori compaiono solo dopo il primo tentativo di salvataggio. */
    val showErrors: Boolean = false,
    /** Nome di un esercizio già presente con lo stesso nome (avviso, non blocca). */
    val sameNameAs: String? = null,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
) {
    val nameError: Boolean get() = showErrors && draft.name.isBlank()
    val primaryError: Boolean get() = showErrors && draft.primaryMuscles.isEmpty()
}

sealed interface ExerciseEditorEvent {
    data class Saved(val exerciseId: String, val isNew: Boolean) : ExerciseEditorEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ExerciseEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: CustomExerciseRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ExerciseEditorRoute>()
    private val state = MutableStateFlow(ExerciseEditorUiState(isNew = route.exerciseId == null))
    val uiState: StateFlow<ExerciseEditorUiState> = state.asStateFlow()

    private val eventChannel = Channel<ExerciseEditorEvent>(Channel.BUFFERED)
    val events: Flow<ExerciseEditorEvent> = eventChannel.receiveAsFlow()

    /** Bozza di partenza, per sapere se ci sono modifiche da non perdere. */
    private var original = CustomExerciseDraft()

    /** Finché l'utente non sceglie a mano cosa registrare, lo adattiamo all'attrezzo. */
    private var trackingChosenByUser = false

    init {
        viewModelScope.launch {
            original = route.exerciseId?.let { repository.getDraft(it) } ?: CustomExerciseDraft(name = route.initialName.orEmpty().trim())
            val restored = savedStateHandle.get<String>(KEY_DRAFT)?.let { json ->
                runCatching { Json.decodeFromString(CustomExerciseDraft.serializer(), json) }.getOrNull()
            }
            trackingChosenByUser = route.exerciseId != null || savedStateHandle.get<Boolean>(KEY_TRACKING_CHOSEN) == true
            val draft = restored ?: original
            state.update { it.copy(isLoading = false, draft = draft, isDirty = draft != original) }
        }
        viewModelScope.launch {
            state.map { it.draft.name }
                .distinctUntilChanged()
                .debounce(NAME_CHECK_DEBOUNCE_MS)
                .collect { name ->
                    val same = repository.findSameName(name, excludeId = route.exerciseId)
                    state.update { it.copy(sameNameAs = same?.name) }
                }
        }
    }

    fun onNameChange(name: String) = edit { it.copy(name = name) }

    fun onEquipmentChange(equipment: Equipment) = edit { draft ->
        val tracking = if (trackingChosenByUser) draft.trackingType else suggestedTracking(equipment)
        draft.copy(equipment = equipment, trackingType = tracking)
    }

    fun onTrackingChange(trackingType: ExerciseTrackingType) {
        trackingChosenByUser = true
        savedStateHandle[KEY_TRACKING_CHOSEN] = true
        edit { it.copy(trackingType = trackingType) }
    }

    /** Il primo muscolo principale scelto è il target; un muscolo non può essere anche secondario. */
    fun onTogglePrimary(muscle: Muscle) = edit { draft ->
        if (muscle in draft.primaryMuscles) {
            draft.copy(primaryMuscles = draft.primaryMuscles - muscle)
        } else {
            draft.copy(primaryMuscles = draft.primaryMuscles + muscle, secondaryMuscles = draft.secondaryMuscles - muscle)
        }
    }

    fun onToggleSecondary(muscle: Muscle) = edit { draft ->
        when {
            muscle in draft.primaryMuscles -> draft
            muscle in draft.secondaryMuscles -> draft.copy(secondaryMuscles = draft.secondaryMuscles - muscle)
            else -> draft.copy(secondaryMuscles = draft.secondaryMuscles + muscle)
        }
    }

    /** Tocco sul tipo già scelto = nessuna indicazione. */
    fun onMechanicChange(mechanic: Mechanic) = edit { it.copy(mechanic = if (it.mechanic == mechanic) null else mechanic) }

    fun onUnilateralChange(unilateral: Boolean) = edit { it.copy(isUnilateral = unilateral) }

    fun onRestAdjust(deltaSeconds: Int) = edit { draft ->
        draft.copy(
            defaultRestSeconds = (draft.defaultRestSeconds + deltaSeconds)
                .coerceIn(CustomExerciseDraft.MIN_REST_SECONDS, CustomExerciseDraft.MAX_REST_SECONDS),
        )
    }

    fun onAddSetupHint(hint: String) {
        val clean = hint.trim()
        if (clean.isEmpty()) return
        edit { draft ->
            if (draft.setupParamHints.any { it.equals(clean, ignoreCase = true) }) draft else draft.copy(setupParamHints = draft.setupParamHints + clean)
        }
    }

    fun onRemoveSetupHint(hint: String) = edit { it.copy(setupParamHints = it.setupParamHints - hint) }

    fun onNotesChange(notes: String) = edit { it.copy(notes = notes) }

    fun onSave() {
        val current = state.value
        if (current.isSaving || current.isLoading) return
        if (!current.draft.isValid) {
            state.update { it.copy(showErrors = true) }
            return
        }
        state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val existingId = route.exerciseId
            val id = if (existingId == null) repository.create(current.draft) else existingId.also { repository.update(it, current.draft) }
            savedStateHandle.remove<String>(KEY_DRAFT)
            state.update { it.copy(isSaving = false, isDirty = false) }
            eventChannel.send(ExerciseEditorEvent.Saved(id, isNew = existingId == null))
        }
    }

    private fun edit(transform: (CustomExerciseDraft) -> CustomExerciseDraft) {
        if (state.value.isLoading) return
        val draft = transform(state.value.draft)
        state.update { it.copy(draft = draft, isDirty = draft != original) }
        savedStateHandle[KEY_DRAFT] = Json.encodeToString(CustomExerciseDraft.serializer(), draft)
    }

    private fun suggestedTracking(equipment: Equipment): ExerciseTrackingType = when (equipment) {
        Equipment.BODYWEIGHT, Equipment.RESISTANCE_BAND -> ExerciseTrackingType.BODYWEIGHT_REPS
        Equipment.CARDIO_MACHINE -> ExerciseTrackingType.DISTANCE_DURATION
        else -> ExerciseTrackingType.WEIGHT_REPS
    }

    private companion object {
        const val KEY_DRAFT = "draft"
        const val KEY_TRACKING_CHOSEN = "tracking_chosen"
        const val NAME_CHECK_DEBOUNCE_MS = 300L
    }
}
