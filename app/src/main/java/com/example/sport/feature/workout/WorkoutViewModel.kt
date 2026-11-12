package com.example.sport.feature.workout

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sport.core.data.ExerciseRepository
import com.example.sport.core.data.SetMetric
import com.example.sport.core.data.WorkoutRepository
import com.example.sport.core.database.entity.ExerciseSetEntity
import com.example.sport.core.database.relation.SessionExerciseWithSets
import com.example.sport.core.database.relation.WorkoutSessionWithExercises
import com.example.sport.core.designsystem.input.KeypadKey
import com.example.sport.core.designsystem.input.NumericInputState
import com.example.sport.core.media.MediaRepository
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.SetType
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.settings.UserSettings
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.labels.labelRes
import com.example.sport.core.workout.RestTimerController
import com.example.sport.core.workout.RestTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@Immutable
data class SetRowUi(
    val setId: Long,
    /** Numero tra le serie allenanti (le serie di riscaldamento non contano). */
    val number: Int,
    val position: Int,
    val type: SetType,
    val values: Map<SetMetric, String>,
    val previous: String?,
    val isCompleted: Boolean,
    val isRecord: Boolean,
)

@Immutable
data class SetupUi(
    val params: List<MachineSetupParam>,
    val hints: List<String>,
)

@Immutable
data class WorkoutExerciseUi(
    val sessionExerciseId: Long,
    val exerciseId: String,
    val name: String,
    @StringRes val muscleLabelRes: Int,
    val thumbnail: Any?,
    val restLabel: String,
    val trackingType: ExerciseTrackingType,
    val metrics: List<SetMetric>,
    val showSetup: Boolean,
    val setup: SetupUi,
    val showRpe: Boolean,
    val wasReplaced: Boolean,
    val sets: List<SetRowUi>,
    /** Lettera della superserie (A, B…) se l'esercizio è collegato ad altri. */
    val supersetLabel: String? = null,
)

@Immutable
data class EditingUi(
    val setId: Long,
    val metric: SetMetric,
    val text: String,
    val exerciseName: String,
    val setPosition: Int,
    val allowDecimal: Boolean,
    val stepLabel: String,
)

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object NoActiveSession : WorkoutUiState

    data class Active(
        val sessionId: Long,
        val title: String,
        val startedAt: Instant,
        val exercises: List<WorkoutExerciseUi>,
        val completedSets: Int,
        val totalSets: Int,
        val editing: EditingUi?,
        val rest: RestTimerState?,
        /** Durante il recupero: invito a concedere le sveglie esatte (avviso puntuale a schermo spento). */
        val exactAlarmHint: Boolean = false,
    ) : WorkoutUiState
}

sealed interface WorkoutEvent {
    data class Finished(val sessionId: Long) : WorkoutEvent
    data object Discarded : WorkoutEvent

    /** Recupero terminato mentre il logger è visibile: avviso aptico. */
    data object RestFinished : WorkoutEvent
}

@Immutable
data class SetupProfileUi(val id: Long, val label: String, val summary: String)

@Immutable
data class SetupSheetUi(
    val sessionExerciseId: Long,
    val exerciseName: String,
    val params: List<MachineSetupParam>,
    val hints: List<String>,
    val currentSetupId: Long?,
    val profiles: List<SetupProfileUi>,
)

private data class EditingField(val setId: Long, val metric: SetMetric, val input: NumericInputState, val unit: WeightUnit)

private data class Decorations(
    val rpeEnabled: Set<Long>,
    val setupShown: Set<Long>,
    val previousSets: Map<String, List<ExerciseSetEntity>>,
    val bestWeights: Map<String, Double>,
    val settings: UserSettings,
)

/** Esito della spunta di una serie per il timer di recupero. */
internal sealed interface RestPlan {
    data object Finished : RestPlan
    data object SupersetPartner : RestPlan
    data class Rest(val seconds: Int, val nextExercise: String) : RestPlan
}

private data class RestPanel(
    val timer: RestTimerState?,
    val exactAlarmHint: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    private val mediaRepository: MediaRepository,
    private val restTimer: RestTimerController,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Unità di peso e scala dello sforzo: servono subito anche ai comandi del tastierino. */
    private val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    private val editing = MutableStateFlow<EditingField?>(null)
    private val rpeEnabled = MutableStateFlow<Set<Long>>(emptySet())
    private val setupShown = MutableStateFlow<Set<Long>>(emptySet())
    private val previousSets = MutableStateFlow<Map<String, List<ExerciseSetEntity>>>(emptyMap())
    private val bestWeights = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val historyRequested = mutableSetOf<String>()
    private var latest: WorkoutSessionWithExercises? = null

    private val eventChannel = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = eventChannel.receiveAsFlow()

    private val session: Flow<WorkoutSessionWithExercises?> = workoutRepository.observeActiveSession()
        .onEach { current ->
            latest = current
            if (current != null) loadHistory(current)
            // Se la serie in modifica è stata eliminata (o l'esercizio sostituito), chiude il tastierino.
            val editedSet = editing.value?.setId
            if (editedSet != null && current?.exercises?.none { e -> e.sets.any { it.id == editedSet } } != false) {
                editing.value = null
            }
        }

    private val decorations = combine(rpeEnabled, setupShown, previousSets, bestWeights, settings, ::Decorations)

    private val restPanel = combine(restTimer.state, restTimer.exactAlarmHint, ::RestPanel)

    val uiState: StateFlow<WorkoutUiState> = combine(
        session,
        exerciseRepository.observeThumbnailPaths(),
        editing,
        decorations,
        restPanel,
    ) { current, thumbnails, edit, deco, rest ->
        if (current == null) WorkoutUiState.NoActiveSession else current.toUiState(thumbnails, edit, deco, rest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState.Loading)

    private val setupSheetTarget = MutableStateFlow<Long?>(null)

    val setupSheet: StateFlow<SetupSheetUi?> = setupSheetTarget
        .flatMapLatest { sessionExerciseId ->
            val item = sessionExerciseId?.let(::findExercise) ?: return@flatMapLatest flowOf(null)
            workoutRepository.observeSetups(item.exercise.id).map { profiles ->
                SetupSheetUi(
                    sessionExerciseId = item.sessionExercise.id,
                    exerciseName = item.exercise.name,
                    params = item.sessionExercise.machineSetup,
                    hints = item.exercise.setupParamHints,
                    currentSetupId = item.sessionExercise.machineSetupId,
                    profiles = profiles.map { profile ->
                        SetupProfileUi(profile.id, profile.label, profile.params.joinToString(" · ") { "${it.name} ${it.value}" })
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ------------------------------------------------------------------ Tastierino

    /** Il campo mostra il valore nell'unità scelta (kg o lb); il database resta in kg. */
    fun onFieldClick(setId: Long, metric: SetMetric) {
        val set = findSet(setId) ?: return
        val unit = settings.value.weightUnit
        val shown = metric.read(set)?.let { metric.toDisplay(it, unit) }
        editing.value = EditingField(setId, metric, NumericInputState.of(shown, metric.inputConfig(unit)), unit)
    }

    fun onKey(key: KeypadKey) {
        val current = editing.value ?: return
        val next = current.input.reduce(key)
        editing.value = current.copy(input = next)
        val stored = next.value?.let { current.metric.toStorage(it, current.unit) }
        viewModelScope.launch { workoutRepository.updateMetric(current.setId, current.metric, stored) }
    }

    /** "Avanti": peso → ripetizioni → (RPE) → peso della serie successiva → … → chiude. */
    fun onNext() {
        val current = editing.value ?: return
        val order = fieldOrder()
        val index = order.indexOf(current.setId to current.metric)
        val next = order.getOrNull(index + 1)
        if (index < 0 || next == null) editing.value = null else onFieldClick(next.first, next.second)
    }

    fun onCloseKeypad() {
        editing.value = null
    }

    // ------------------------------------------------------------------ Serie

    fun onToggleComplete(setId: Long) {
        val exercise = findExerciseOfSet(setId) ?: return
        val set = exercise.sets.firstOrNull { it.id == setId } ?: return
        val completing = !set.isCompleted
        if (completing && editing.value?.setId == setId) editing.value = null
        viewModelScope.launch {
            if (completing) fillMissingFromPrevious(exercise, set)
            workoutRepository.setCompleted(setId, completing)
            if (completing) {
                when (val plan = restPlanAfter(setId)) {
                    RestPlan.Finished, RestPlan.SupersetPartner -> restTimer.cancel()
                    is RestPlan.Rest -> restTimer.start(plan.seconds, plan.nextExercise)
                }
            }
        }
    }

    fun onSetTypeChange(setId: Long, type: SetType) {
        viewModelScope.launch { workoutRepository.setSetType(setId, type) }
    }

    fun onRemoveSet(setId: Long) {
        if (editing.value?.setId == setId) editing.value = null
        viewModelScope.launch { workoutRepository.removeSet(setId) }
    }

    fun onAddSet(sessionExerciseId: Long) {
        viewModelScope.launch { workoutRepository.addSet(sessionExerciseId) }
    }

    // ------------------------------------------------------------------ Esercizi

    fun onToggleRpe(sessionExerciseId: Long) = rpeEnabled.update { it.toggle(sessionExerciseId) }

    fun onToggleSetupCard(sessionExerciseId: Long) = setupShown.update { it.toggle(sessionExerciseId) }

    fun onRemoveExercise(sessionExerciseId: Long) {
        viewModelScope.launch { workoutRepository.removeExercise(sessionExerciseId) }
    }

    // ------------------------------------------------------------------ Regolazione macchinario

    fun onOpenSetup(sessionExerciseId: Long) {
        setupSheetTarget.value = sessionExerciseId
    }

    fun onCloseSetup() {
        setupSheetTarget.value = null
    }

    fun onSaveSetup(params: List<MachineSetupParam>, defaultLabel: String) {
        val target = setupSheetTarget.value ?: return
        setupSheetTarget.value = null
        viewModelScope.launch { workoutRepository.saveSetup(target, params, defaultLabel) }
    }

    fun onSaveSetupAsNew(label: String, params: List<MachineSetupParam>) {
        val target = setupSheetTarget.value ?: return
        setupSheetTarget.value = null
        viewModelScope.launch { workoutRepository.saveSetupAsNew(target, label, params) }
    }

    fun onSelectSetup(setupId: Long) {
        val target = setupSheetTarget.value ?: return
        setupSheetTarget.value = null
        viewModelScope.launch { workoutRepository.selectSetup(target, setupId) }
    }

    // ------------------------------------------------------------------ Recupero

    fun onRestAdjust(deltaSeconds: Int) = restTimer.adjust(deltaSeconds)

    fun onRestSkip() = restTimer.cancel()

    fun onRestElapsed() {
        if (restTimer.state.value == null) return
        restTimer.complete(fromAlarm = false)
        eventChannel.trySend(WorkoutEvent.RestFinished)
    }

    /** Il logger torna in primo piano: il permesso per le sveglie esatte può essere cambiato. */
    fun onResume() = restTimer.refreshAlarmAccess()

    fun onDismissExactAlarmHint() = restTimer.dismissExactAlarmHint()

    fun now(): Instant = workoutRepository.now()

    // ------------------------------------------------------------------ Fine sessione

    fun onFinish() {
        val sessionId = latest?.session?.id ?: return
        viewModelScope.launch {
            restTimer.cancel()
            workoutRepository.finish(sessionId)
            eventChannel.send(WorkoutEvent.Finished(sessionId))
        }
    }

    fun onDiscard() {
        val sessionId = latest?.session?.id ?: return
        viewModelScope.launch {
            restTimer.cancel()
            workoutRepository.discard(sessionId)
            eventChannel.send(WorkoutEvent.Discarded)
        }
    }

    // ------------------------------------------------------------------ Mappatura

    private fun WorkoutSessionWithExercises.toUiState(
        thumbnails: Map<String, String>,
        edit: EditingField?,
        deco: Decorations,
        rest: RestPanel,
    ): WorkoutUiState.Active {
        // Superserie etichettate A, B, C… nell'ordine in cui compaiono.
        val supersetLetters = orderedExercises.mapNotNull { it.sessionExercise.supersetGroup }
            .distinct()
            .withIndex()
            .associate { (index, group) -> group to ('A' + index).toString() }
        val exercises = orderedExercises.map { item ->
            item.toUi(thumbnails, deco).copy(supersetLabel = item.sessionExercise.supersetGroup?.let(supersetLetters::get))
        }
        val allSets = exercises.flatMap { it.sets }
        return WorkoutUiState.Active(
            sessionId = session.id,
            title = session.title,
            startedAt = session.startedAt,
            exercises = exercises,
            completedSets = allSets.count { it.isCompleted },
            totalSets = allSets.size,
            editing = edit?.let { field ->
                val owner = exercises.firstOrNull { e -> e.sets.any { it.setId == field.setId } } ?: return@let null
                EditingUi(
                    setId = field.setId,
                    metric = field.metric,
                    text = field.input.displayText,
                    exerciseName = owner.name,
                    setPosition = owner.sets.first { it.setId == field.setId }.position,
                    allowDecimal = field.input.config.allowDecimal,
                    stepLabel = Formatters.number(field.input.config.step),
                )
            },
            rest = rest.timer,
            exactAlarmHint = rest.timer != null && rest.exactAlarmHint,
        )
    }

    private fun SessionExerciseWithSets.toUi(thumbnails: Map<String, String>, deco: Decorations): WorkoutExerciseUi {
        val sessionExerciseId = sessionExercise.id
        val effort = deco.settings.effortScale
        val unit = deco.settings.weightUnit
        val effortMetric = SetMetric.effortMetric(effort)
        // Colonna dello sforzo: attivata dal menu o già compilata in questa sessione.
        val showRpe = sessionExerciseId in deco.rpeEnabled || sets.any { effortMetric.read(it) != null }
        val metrics = SetMetric.forTracking(exercise.trackingType, showRpe, effort)
        val previous = deco.previousSets[exercise.id].orEmpty()
        val best = deco.bestWeights[exercise.id]
        var workingNumber = 0
        val rows = orderedSets.mapIndexed { index, set ->
            val isWarmup = set.setType == SetType.WARMUP
            if (!isWarmup) workingNumber++
            val reference = if (isWarmup) null else previous.getOrNull(workingNumber - 1)
            SetRowUi(
                setId = set.id,
                number = if (isWarmup) 0 else workingNumber,
                position = index + 1,
                type = set.setType,
                values = metrics.associateWith { metric -> metric.read(set)?.let { formatMetric(metric, it, unit) }.orEmpty() },
                previous = SetFormat.previous(exercise.trackingType, reference, unit),
                isCompleted = set.isCompleted,
                isRecord = set.isCompleted && !isWarmup && best != null && (set.weightKg ?: 0.0) > best,
            )
        }
        return WorkoutExerciseUi(
            sessionExerciseId = sessionExerciseId,
            exerciseId = exercise.id,
            name = exercise.name,
            muscleLabelRes = exercise.primaryMuscle.labelRes(),
            thumbnail = thumbnails[exercise.id]?.let(mediaRepository::resolve),
            restLabel = Formatters.duration(sessionExercise.restSeconds.toLong()),
            trackingType = exercise.trackingType,
            metrics = metrics,
            showSetup = exercise.supportsMachineSetup ||
                sessionExercise.machineSetup.isNotEmpty() ||
                sessionExerciseId in deco.setupShown,
            setup = SetupUi(
                params = sessionExercise.machineSetup,
                hints = exercise.setupParamHints,
            ),
            showRpe = showRpe,
            wasReplaced = sessionExercise.replacedExerciseId != null,
            sets = rows,
        )
    }

    // ------------------------------------------------------------------ Supporto

    private fun loadHistory(current: WorkoutSessionWithExercises) {
        val missing = current.exercises.map { it.exercise.id }.filter { historyRequested.add(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val previous = missing.associateWith { workoutRepository.previousWorkingSets(it) }
            previousSets.update { it + previous }
            val best = workoutRepository.bestWeights(missing, excludeSessionId = current.session.id)
            bestWeights.update { it + best }
        }
    }

    /**
     * Spunta su una riga vuota = "come prima": ogni valore mancante viene copiato dalla serie
     * precedente di oggi (60 kg sulla 1ª → 60 kg sulla 2ª) e, se oggi non c'è, dallo storico.
     */
    private suspend fun fillMissingFromPrevious(exercise: SessionExerciseWithSets, set: ExerciseSetEntity) {
        if (set.setType == SetType.WARMUP) return
        val working = exercise.orderedSets.filter { it.setType != SetType.WARMUP }
        val ordinal = working.indexOfFirst { it.id == set.id }
        if (ordinal < 0) return
        val earlierToday = working.take(ordinal).asReversed()
        val history = previousSets.value[exercise.exercise.id].orEmpty()
        val fromHistory = history.getOrNull(ordinal) ?: history.lastOrNull()
        SetMetric.forTracking(exercise.exercise.trackingType, withRpe = false).forEach { metric ->
            if (metric.read(set) != null) return@forEach
            val value = earlierToday.firstNotNullOfOrNull { metric.read(it) } ?: fromHistory?.let { metric.read(it) }
            if (value != null) workoutRepository.updateMetric(set.id, metric, value)
        }
    }

    /**
     * Cosa succede dopo la serie [completedSetId]:
     * - superserie con un altro esercizio ancora da fare in questo giro → si passa subito a quello;
     * - fine del giro (o esercizio normale) → recupero, indicando il prossimo esercizio;
     * - nessuna serie rimasta → niente recupero.
     */
    internal fun restPlanAfter(completedSetId: Long): RestPlan {
        val exercises = latest?.orderedExercises.orEmpty()
        val owner = exercises.firstOrNull { item -> item.sets.any { it.id == completedSetId } } ?: return RestPlan.Finished
        fun SessionExerciseWithSets.hasPending() = sets.any { !it.isCompleted && it.id != completedSetId }

        val group = owner.sessionExercise.supersetGroup
        if (group != null) {
            val members = exercises.filter { it.sessionExercise.supersetGroup == group }
            val ownerIndex = members.indexOf(owner)
            if (members.drop(ownerIndex + 1).any { it.hasPending() }) return RestPlan.SupersetPartner
            members.firstOrNull { it.hasPending() }?.let { return RestPlan.Rest(owner.sessionExercise.restSeconds, it.exercise.name) }
        }
        val ordered = exercises.flatMap { item -> item.orderedSets.map { item to it } }
        val start = ordered.indexOfFirst { it.second.id == completedSetId }
        val next = (ordered.drop(start + 1) + ordered.take(start.coerceAtLeast(0)))
            .firstOrNull { !it.second.isCompleted && it.second.id != completedSetId }
            ?.first ?: return RestPlan.Finished
        return RestPlan.Rest(owner.sessionExercise.restSeconds, next.exercise.name)
    }

    private fun fieldOrder(): List<Pair<Long, SetMetric>> =
        (uiState.value as? WorkoutUiState.Active)?.exercises.orEmpty().flatMap { exercise ->
            exercise.sets.filterNot { it.isCompleted }.flatMap { row -> exercise.metrics.map { row.setId to it } }
        }.let { pending ->
            val current = editing.value
            // La serie in modifica può essere già spuntata: resta comunque nel percorso di "Avanti".
            if (current != null && pending.none { it.first == current.setId }) {
                val exercise = (uiState.value as? WorkoutUiState.Active)?.exercises?.firstOrNull { e -> e.sets.any { it.setId == current.setId } }
                val own = exercise?.metrics?.map { current.setId to it }.orEmpty()
                own + pending
            } else {
                pending
            }
        }

    private fun findExercise(sessionExerciseId: Long): SessionExerciseWithSets? =
        latest?.exercises?.firstOrNull { it.sessionExercise.id == sessionExerciseId }

    private fun findExerciseOfSet(setId: Long): SessionExerciseWithSets? =
        latest?.exercises?.firstOrNull { item -> item.sets.any { it.id == setId } }

    private fun findSet(setId: Long): ExerciseSetEntity? = findExerciseOfSet(setId)?.sets?.firstOrNull { it.id == setId }

    private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

    private fun formatMetric(metric: SetMetric, value: Double, unit: WeightUnit): String = when (metric) {
        SetMetric.REPS, SetMetric.RIR, SetMetric.DURATION_SECONDS, SetMetric.DURATION_MINUTES -> value.toLong().toString()
        SetMetric.WEIGHT -> Formatters.weightNumber(value, unit)
        SetMetric.RPE, SetMetric.DISTANCE_KM -> Formatters.number(value)
    }
}

/** Testi compatti della riga serie. */
internal object SetFormat {
    fun previous(type: ExerciseTrackingType, set: ExerciseSetEntity?, unit: WeightUnit = WeightUnit.KG): String? {
        if (set == null) return null
        val weight = set.weightKg?.takeIf { it > 0 }?.let { Formatters.weightNumber(it, unit) }
        val reps = set.reps?.toString()
        return when (type) {
            ExerciseTrackingType.WEIGHT_REPS -> listOfNotNull(weight, reps).joinToString(" × ").ifEmpty { null }
            ExerciseTrackingType.BODYWEIGHT_REPS -> reps?.let { r -> weight?.let { "+$it × $r" } ?: "× $r" }
            ExerciseTrackingType.ASSISTED_BODYWEIGHT -> reps?.let { r -> weight?.let { "−$it × $r" } ?: "× $r" }
            ExerciseTrackingType.DURATION -> set.durationSeconds?.let { "$it s" }
            ExerciseTrackingType.DISTANCE_DURATION -> listOfNotNull(
                set.distanceMeters?.let { "${Formatters.number(it / 1000)} km" },
                set.durationSeconds?.let { "${it / 60} min" },
            ).joinToString(" · ").ifEmpty { null }
        }
    }
}
