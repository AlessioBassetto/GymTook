package com.example.sport.feature.workout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.R
import com.example.sport.core.data.CalorieRepository
import com.example.sport.core.data.WorkoutRepository
import com.example.sport.core.data.WorkoutSummary
import com.example.sport.core.database.entity.ExerciseSetEntity
import com.example.sport.core.designsystem.component.PrimaryActionButton
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.component.StatValue
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.ui.format.Formatters
import com.example.sport.navigation.WorkoutSummaryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import javax.inject.Inject

@Immutable
data class SummaryExerciseUi(val name: String, val sets: Int, val bestSet: String?)

@Immutable
data class SummaryRecordUi(val name: String, val value: String, val previous: String)

sealed interface WorkoutSummaryUiState {
    data object Loading : WorkoutSummaryUiState

    data class Content(
        val title: String,
        val duration: String,
        val sets: String,
        val volume: String,
        /** "312 kcal"; null se manca una pesata (la stima non è possibile). */
        val calories: String?,
        /** Nessuna altezza nel profilo: la stima usa valori medi, si suggerisce di completarlo. */
        val suggestProfile: Boolean,
        val records: List<SummaryRecordUi>,
        val exercises: List<SummaryExerciseUi>,
    ) : WorkoutSummaryUiState
}

@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    calories: CalorieRepository,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<WorkoutSummaryRoute>().sessionId

    val uiState: StateFlow<WorkoutSummaryUiState> = flow {
        val unit = settingsRepository.current().weightUnit
        val hasHeight = calories.profile()?.heightCm != null
        workoutRepository.summary(sessionId)?.let { emit(it.toUi(unit, hasHeight)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState.Loading)

    private fun WorkoutSummary.toUi(unit: WeightUnit, hasHeight: Boolean) = WorkoutSummaryUiState.Content(
        title = title,
        duration = endedAt?.let { Formatters.durationHuman(Duration.between(startedAt, it)) } ?: "—",
        sets = completedSets.toString(),
        volume = Formatters.weightKg(totalVolumeKg, unit),
        calories = caloriesBurned?.let(Formatters::kcal),
        suggestProfile = caloriesBurned != null && !hasHeight,
        records = records.map { record ->
            SummaryRecordUi(
                name = record.name,
                value = Formatters.weightKg(record.bestSet?.weightKg, unit),
                previous = Formatters.weightKg(record.previousBestKg, unit),
            )
        },
        exercises = exercises.map { exercise ->
            SummaryExerciseUi(exercise.name, exercise.completedSets, exercise.bestSet?.let { describe(exercise.trackingType, it, unit) })
        },
    )

    private fun describe(type: ExerciseTrackingType, set: ExerciseSetEntity, unit: WeightUnit): String? = SetFormat.previous(type, set, unit)
}

@Composable
fun WorkoutSummaryScreen(
    onDone: () -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        WorkoutSummaryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is WorkoutSummaryUiState.Content -> WorkoutSummaryContent(current, onDone)
    }
}

@Composable
internal fun WorkoutSummaryContent(state: WorkoutSummaryUiState.Content, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(SportDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            item {
                Column {
                    Text(
                        stringResource(R.string.summary_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(state.title, style = MaterialTheme.typography.displaySmall)
                }
            }
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(SportDimens.CardPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatValue(state.duration, stringResource(R.string.summary_duration))
                        StatValue(state.sets, stringResource(R.string.summary_sets))
                        StatValue(state.volume, stringResource(R.string.summary_volume))
                        StatValue(state.calories ?: "—", stringResource(R.string.summary_calories))
                    }
                }
            }
            caloriesNoteRes(state)?.let { note ->
                item {
                    Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.records.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.summary_records)) }
                items(state.records) { record ->
                    Surface(shape = MaterialTheme.shapes.medium, color = SportTheme.extendedColors.recordContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(SportDimens.SpacingM),
                            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SportTheme.extendedColors.record)
                            Column(Modifier.weight(1f)) {
                                Text(record.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.summary_record_line, record.value, record.previous),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.summary_exercises)) }
            items(state.exercises) { exercise ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                        exercise.bestSet?.let {
                            Text(
                                stringResource(R.string.summary_best_set, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text("× ${exercise.sets}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        PrimaryActionButton(
            text = stringResource(R.string.summary_done),
            onClick = onDone,
            modifier = Modifier.padding(SportDimens.ScreenPadding),
        )
    }
}

/** Nota sotto i numeri: perché manca la stima, o come renderla più precisa. */
@StringRes
internal fun caloriesNoteRes(state: WorkoutSummaryUiState.Content): Int? = when {
    state.calories == null -> R.string.calories_missing_weight
    state.suggestProfile -> R.string.calories_add_profile_hint
    else -> null
}
