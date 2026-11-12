package com.example.sport.feature.routines

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sport.core.data.RoutineBuilderRepository
import com.example.sport.core.data.RoutineRepository
import com.example.sport.core.data.template.RoutineTemplate
import com.example.sport.core.data.template.RoutineTemplates
import com.example.sport.core.ui.labels.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

@Immutable
data class RoutineUi(
    val id: Long,
    val name: String,
    @StringRes val splitLabelRes: Int,
    val isActive: Boolean,
    val dayNames: List<String>,
    val scheduledDays: Set<DayOfWeek>,
    /** Scheda da cui è stata duplicata (variante), se esiste ancora. */
    val parentName: String? = null,
)

@Immutable
data class TemplateUi(
    val template: RoutineTemplate,
    @StringRes val levelLabelRes: Int,
    val dayNames: List<String>,
    val scheduledDays: Set<DayOfWeek>,
)

sealed interface RoutinesUiState {
    data object Loading : RoutinesUiState

    data class Content(
        val routines: List<RoutineUi>,
        val templates: List<TemplateUi>,
    ) : RoutinesUiState
}

/** Eventi una tantum per la UI (snackbar). */
sealed interface RoutinesEvent {
    data class TemplateCreated(val routineId: Long, val routineName: String) : RoutinesEvent
    data class RoutineCreated(val routineId: Long) : RoutinesEvent
}

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val builder: RoutineBuilderRepository,
) : ViewModel() {

    private val templates = RoutineTemplates.all.map { template ->
        TemplateUi(
            template = template,
            levelLabelRes = template.level.labelRes(),
            dayNames = template.days.map { it.name },
            scheduledDays = template.days.flatMap { it.weekdays }.toSet(),
        )
    }

    val uiState: StateFlow<RoutinesUiState> = routineRepository.observeRoutines()
        .map { routines ->
            val names = routines.associate { it.routine.id to it.routine.name }
            RoutinesUiState.Content(
                routines = routines.map { routine ->
                    RoutineUi(
                        id = routine.routine.id,
                        name = routine.routine.name,
                        splitLabelRes = routine.routine.splitType.labelRes(),
                        isActive = routine.routine.isActive,
                        dayNames = routine.orderedDays.map { it.day.name },
                        scheduledDays = routine.schedule.map { it.dayOfWeek }.toSet(),
                        parentName = routine.routine.parentRoutineId?.let(names::get),
                    )
                },
                templates = templates,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState.Loading)

    private val eventChannel = Channel<RoutinesEvent>(Channel.BUFFERED)
    val events: Flow<RoutinesEvent> = eventChannel.receiveAsFlow()

    fun createFromTemplate(template: RoutineTemplate) {
        viewModelScope.launch {
            val routineId = routineRepository.createFromTemplate(template, activate = true)
            eventChannel.send(RoutinesEvent.TemplateCreated(routineId, template.name))
        }
    }

    /** Scheda vuota con un primo giorno: si apre subito nel builder. */
    fun createRoutine(name: String, firstDayName: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            eventChannel.send(RoutinesEvent.RoutineCreated(builder.createRoutine(name, firstDayName)))
        }
    }

    fun setActive(routineId: Long) {
        viewModelScope.launch { routineRepository.setActive(routineId) }
    }
}
