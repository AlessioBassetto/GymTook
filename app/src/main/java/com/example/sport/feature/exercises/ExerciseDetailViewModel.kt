package com.example.sport.feature.exercises

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.R
import com.example.sport.core.data.CustomExerciseRepository
import com.example.sport.core.data.ExerciseRepository
import com.example.sport.core.data.ProgressRepository
import com.example.sport.core.data.RoutineBuilderRepository
import com.example.sport.core.data.WorkoutRepository
import com.example.sport.core.database.dao.CustomExerciseRemoval
import com.example.sport.core.media.MediaRepository
import com.example.sport.core.media.UserVideoStore
import com.example.sport.core.media.VideoImportResult
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.catalog.CatalogExerciseDto
import com.example.sport.core.ui.exercise.ExerciseRowUi
import com.example.sport.core.ui.labels.labelRes
import com.example.sport.feature.progress.ProgressMetric
import com.example.sport.navigation.ExerciseDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@Immutable
data class MediaCreditUi(val source: String, val license: String)

/**
 * Profilo di regolazione del macchinario per questo esercizio. Si legge e si modifica dalla scheda
 * in qualsiasi momento, senza avviare un allenamento.
 */
@Immutable
data class SavedSetupUi(
    val id: Long,
    val label: String,
    val params: List<MachineSetupParam>,
    val notes: String?,
    /** Il più recente: è quello che il logger richiama da solo al prossimo allenamento. */
    val isLatest: Boolean,
)

@Immutable
data class ExerciseDetailUi(
    val id: String,
    val name: String,
    val nameEn: String?,
    val isFavorite: Boolean,
    val isCustom: Boolean,
    @StringRes val equipmentLabelRes: Int,
    @StringRes val levelLabelRes: Int,
    @StringRes val mechanicLabelRes: Int?,
    @StringRes val categoryLabelRes: Int?,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    val frames: List<Any>,
    /** Video personale (Uri `file://`), ha la precedenza sul catalogo. */
    val userVideo: String?,
    /** Video loop di catalogo (URL CDN o file offline). */
    val catalogVideo: String?,
    val setupHints: List<String>,
    val savedSetups: List<SavedSetupUi>,
    val instructions: List<String>,
    val keyPoints: List<String>,
    val commonMistakes: List<String>,
    val isEnglishContent: Boolean,
    val mediaCredit: MediaCreditUi?,
    val alternatives: List<ExerciseRowUi>,
)

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState
    data object NotFound : ExerciseDetailUiState
    data class Content(val exercise: ExerciseDetailUi, val isImportingVideo: Boolean = false) : ExerciseDetailUiState
}

sealed interface ExerciseDetailEvent {
    data class Message(@StringRes val messageRes: Int, val args: List<Any> = emptyList()) : ExerciseDetailEvent
    data class Removed(val result: CustomExerciseRemoval) : ExerciseDetailEvent
}

/** Scheda e suoi giorni, destinazioni di "Aggiungi a una scheda". */
@Immutable
data class RoutineTargetUi(val id: Long, val name: String, val isActive: Boolean, val days: List<RoutineDayTargetUi>)

@Immutable
data class RoutineDayTargetUi(val id: Long, val name: String)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val customExerciseRepository: CustomExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val routineBuilder: RoutineBuilderRepository,
    private val mediaRepository: MediaRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    val routineTargets: StateFlow<List<RoutineTargetUi>> = routineBuilder.observeRoutines()
        .map { routines ->
            routines.map { routine ->
                RoutineTargetUi(
                    id = routine.routine.id,
                    name = routine.routine.name,
                    isActive = routine.routine.isActive,
                    days = routine.orderedDays.map { RoutineDayTargetUi(it.day.id, it.day.name) },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val exerciseId: String = savedStateHandle.toRoute<ExerciseDetailRoute>().exerciseId
    private val importingVideo = MutableStateFlow(false)

    /** Pulsante "Progressi": esercizio a ripetizioni presente in almeno un allenamento concluso. */
    val hasProgress: StateFlow<Boolean> = combine(
        progressRepository.observeHasHistory(exerciseId),
        progressRepository.observeExercise(exerciseId),
    ) { hasHistory, exercise ->
        hasHistory && exercise != null && ProgressMetric.hasChart(exercise.trackingType)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** L'esercizio compare in almeno un allenamento concluso: abilita "Azzera storico". */
    val hasHistory: StateFlow<Boolean> = progressRepository.observeHasHistory(exerciseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val eventChannel = Channel<ExerciseDetailEvent>(Channel.BUFFERED)
    val events: Flow<ExerciseDetailEvent> = eventChannel.receiveAsFlow()

    private val alternatives: Flow<List<ExerciseRowUi>> = combine(
        flow { emit(exerciseRepository.alternatives(exerciseId, ALTERNATIVES_LIMIT)) },
        exerciseRepository.observeThumbnailPaths(),
    ) { exercises, thumbs ->
        exercises.map { ExerciseRowUi.from(it, thumbs[it.id]?.let(mediaRepository::resolve)) }
    }

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        exerciseRepository.observeDetails(exerciseId),
        workoutRepository.observeSetups(exerciseId),
        alternatives,
        importingVideo,
    ) { details, setups, alternativeRows, importing ->
        if (details == null) return@combine ExerciseDetailUiState.NotFound
        val exercise = details.exercise
        val firstMedia = details.media.firstOrNull()
        ExerciseDetailUiState.Content(
            exercise = ExerciseDetailUi(
                id = exercise.id,
                name = exercise.name,
                nameEn = exercise.nameEn?.takeUnless { it.equals(exercise.name, ignoreCase = true) },
                isFavorite = exercise.isFavorite,
                isCustom = exercise.isCustom,
                equipmentLabelRes = exercise.equipment.labelRes(),
                levelLabelRes = exercise.level.labelRes(),
                mechanicLabelRes = exercise.mechanic?.labelRes(),
                // "Forza" è la norma: l'etichetta compare solo per i tipi particolari.
                categoryLabelRes = exercise.category.takeIf { it != ExerciseCategory.STRENGTH }?.labelRes(),
                // Il muscolo target per primo, come nelle liste.
                primaryMuscles = details.primaryMuscles.sortedBy { if (it == exercise.primaryMuscle) 0 else 1 },
                secondaryMuscles = details.secondaryMuscles,
                frames = details.frames.mapNotNull { mediaRepository.resolve(it.path) },
                userVideo = details.userVideo?.let { customExerciseRepository.videoUri(it.fileName) },
                catalogVideo = details.videoLoop?.let { mediaRepository.resolve(it.path)?.toString() },
                setupHints = exercise.setupParamHints,
                // Già ordinati dal più recente: il primo è quello proposto dal logger.
                savedSetups = setups.mapIndexed { index, setup ->
                    SavedSetupUi(
                        id = setup.id,
                        label = setup.label,
                        params = setup.params,
                        notes = setup.notes?.takeIf { it.isNotBlank() },
                        isLatest = index == 0,
                    )
                },
                instructions = exercise.instructions,
                keyPoints = exercise.keyPoints,
                commonMistakes = exercise.commonMistakes,
                isEnglishContent = exercise.contentLocale == CatalogExerciseDto.CONTENT_LOCALE_EN,
                mediaCredit = firstMedia?.source?.let { MediaCreditUi(it, firstMedia.license ?: "—") },
                alternatives = alternativeRows,
            ),
            isImportingVideo = importing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState.Loading)

    fun toggleFavorite() {
        val current = (uiState.value as? ExerciseDetailUiState.Content)?.exercise ?: return
        viewModelScope.launch { exerciseRepository.setFavorite(current.id, !current.isFavorite) }
    }

    /** Video scelto dalla galleria (selettore di sistema, nessun permesso richiesto). */
    fun onVideoPicked(uri: Uri) = importVideo { customExerciseRepository.importVideo(exerciseId, uri) }

    /** Video appena registrato con la fotocamera nel file temporaneo [recorded]. */
    fun onVideoRecorded(recorded: File) = importVideo { customExerciseRepository.adoptRecordedVideo(exerciseId, recorded) }

    fun onRemoveVideo() {
        viewModelScope.launch {
            customExerciseRepository.removeVideo(exerciseId)
            eventChannel.send(ExerciseDetailEvent.Message(R.string.exercise_video_removed))
        }
    }

    /** In coda al giorno scelto, con obiettivi predefiniti (modificabili nel builder). */
    fun onAddToRoutineDay(dayId: Long) {
        val target = routineTargets.value.firstNotNullOfOrNull { routine ->
            routine.days.firstOrNull { it.id == dayId }?.let { day -> routine to day }
        } ?: return
        viewModelScope.launch {
            routineBuilder.addExercise(dayId, exerciseId)
            eventChannel.send(ExerciseDetailEvent.Message(R.string.exercise_added_to_routine, listOf(target.second.name, target.first.name)))
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            eventChannel.send(ExerciseDetailEvent.Removed(customExerciseRepository.delete(exerciseId)))
        }
    }

    /**
     * Salva la regolazione "a freddo", senza un allenamento avviato: nuovo profilo se [setupId] è
     * null, altrimenti aggiorna quello indicato. Il logger la ritrova da solo alla prossima sessione.
     */
    fun onSaveSetup(setupId: Long?, label: String, params: List<MachineSetupParam>, notes: String) {
        viewModelScope.launch {
            workoutRepository.saveSetupFromLibrary(exerciseId, setupId, label, params, notes)
            eventChannel.send(ExerciseDetailEvent.Message(R.string.exercise_setup_saved))
        }
    }

    fun onDeleteSetup(setupId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteSetup(setupId)
            eventChannel.send(ExerciseDetailEvent.Message(R.string.exercise_setup_deleted))
        }
    }

    /** Azzera lo storico di questo solo esercizio; tutti gli altri dati restano. */
    fun onResetHistory() {
        viewModelScope.launch {
            progressRepository.resetExerciseHistory(exerciseId)
            eventChannel.send(ExerciseDetailEvent.Message(R.string.exercise_history_reset_done))
        }
    }

    private fun importVideo(block: suspend () -> VideoImportResult) {
        if (importingVideo.value) return
        importingVideo.value = true
        viewModelScope.launch {
            val message = when (block()) {
                is VideoImportResult.Success -> ExerciseDetailEvent.Message(R.string.exercise_video_saved)
                VideoImportResult.TooLarge -> ExerciseDetailEvent.Message(R.string.exercise_video_too_large, listOf(UserVideoStore.MAX_MEGABYTES))
                VideoImportResult.Failed -> ExerciseDetailEvent.Message(R.string.exercise_video_import_failed)
            }
            importingVideo.value = false
            eventChannel.send(message)
        }
    }

    private companion object {
        const val ALTERNATIVES_LIMIT = 5
    }
}
