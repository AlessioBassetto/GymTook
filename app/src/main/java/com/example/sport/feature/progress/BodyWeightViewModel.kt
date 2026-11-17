package com.example.sport.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sport.core.data.CalorieRepository
import com.example.sport.core.data.ProgressRepository
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.data.progress.WeightTrendPoint
import com.example.sport.core.data.progress.weeklyChange
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface BodyWeightUiState {
    data object Loading : BodyWeightUiState

    /**
     * @param points pesate del periodo in ordine cronologico, con la media mobile a 7 giorni.
     * @param weeklyChange variazione della media rispetto a una settimana prima.
     * @param periodChange variazione della media fra la prima e l'ultima pesata del periodo.
     */
    data class Content(
        val range: TimeRange,
        val latest: BodyWeightEntryEntity?,
        val points: List<WeightTrendPoint>,
        val weeklyChange: Double?,
        val periodChange: Double?,
        val profile: BodyProfileUi = BodyProfileUi(),
    ) : BodyWeightUiState {
        val hasAnyEntry: Boolean get() = latest != null
        val latestAverageKg: Double? get() = points.lastOrNull()?.takeIf { it.date == latest?.date }?.averageKg
    }
}

sealed interface BodyWeightEvent {
    data object Saved : BodyWeightEvent
    data class Deleted(val entry: BodyWeightEntryEntity) : BodyWeightEvent
    data object ProfileSaved : BodyWeightEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BodyWeightViewModel @Inject constructor(
    private val repository: ProgressRepository,
    private val calories: CalorieRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val entryEditor = BodyWeightEntryEditor(repository, settingsRepository, viewModelScope)

    private val range = MutableStateFlow(TimeRange.QUARTER)

    /** Anni di nascita accettati dal profilo (età fra 10 e 100 anni). */
    val birthYears: IntRange = calories.birthYearRange()

    private val profileEditor = MutableStateFlow(false)

    /** true mentre il dialogo del profilo corporeo è aperto. */
    val profileEditorOpen: StateFlow<Boolean> = profileEditor
    private val eventChannel = Channel<BodyWeightEvent>(Channel.BUFFERED)

    val events: Flow<BodyWeightEvent> = merge(
        eventChannel.receiveAsFlow(),
        entryEditor.saved.map { BodyWeightEvent.Saved },
    )

    val uiState: StateFlow<BodyWeightUiState> = range
        .flatMapLatest { selected ->
            combine(
                repository.observeBodyWeightTrend(selected.startDate(repository.today())),
                repository.observeLatestBodyWeight(),
                calories.observeProfile(),
            ) { points, latest, profile ->
                BodyWeightUiState.Content(
                    range = selected,
                    latest = latest,
                    points = points,
                    weeklyChange = weeklyChange(points),
                    periodChange = periodChange(points),
                    profile = BodyProfileUi.of(profile, repository.today()),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyWeightUiState.Loading)

    fun onSelectRange(selected: TimeRange) {
        range.value = selected
    }

    fun onAdd() = entryEditor.open()

    fun onEdit(date: LocalDate) = entryEditor.open(date)

    fun onDelete(date: LocalDate) {
        viewModelScope.launch {
            val entry = repository.bodyWeightOn(date) ?: return@launch
            repository.deleteBodyWeight(date)
            eventChannel.send(BodyWeightEvent.Deleted(entry))
        }
    }

    fun onUndoDelete(entry: BodyWeightEntryEntity) {
        viewModelScope.launch { repository.restoreBodyWeight(entry) }
    }

    fun onEditProfile() {
        profileEditor.value = true
    }

    fun onDismissProfile() {
        profileEditor.value = false
    }

    /** Salva altezza, anno di nascita e sesso: le calorie di tutto lo storico si ricalcolano. */
    fun onSaveProfile(input: BodyProfileInput) {
        profileEditor.value = false
        viewModelScope.launch {
            calories.saveProfile(input.heightCm, input.birthYear, input.sex)
            eventChannel.send(BodyWeightEvent.ProfileSaved)
        }
    }

    private fun periodChange(points: List<WeightTrendPoint>): Double? =
        if (points.size < 2) null else points.last().averageKg - points.first().averageKg
}
