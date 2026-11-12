package com.example.sport.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.R
import com.example.sport.core.data.ExerciseRepository
import com.example.sport.core.data.RoutineBuilderRepository
import com.example.sport.core.data.WorkoutRepository
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.query.ExerciseFilter
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.media.MediaRepository
import com.example.sport.core.ui.exercise.ExerciseListItem
import com.example.sport.core.ui.exercise.ExerciseRowUi
import com.example.sport.navigation.ExercisePickerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ExercisePickerUiState(
    val isReplacing: Boolean,
    /** Alternative (sostituzione) o usati di recente (aggiunta); vuota durante la ricerca. */
    val suggestions: List<ExerciseRowUi> = emptyList(),
    val results: List<ExerciseRowUi> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val routineBuilder: RoutineBuilderRepository,
    exerciseRepository: ExerciseRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ExercisePickerRoute>()
    private val isReplacing = route.replaceSessionExerciseId != null || route.replaceRoutineExerciseId != null
    val query = MutableStateFlow("")

    private val doneChannel = Channel<Unit>(Channel.CONFLATED)
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    /** Sostituzione: alternative all'esercizio di partenza. Aggiunta: usati di recente. */
    private val suggestions: Flow<List<ExerciseEntity>> =
        if (isReplacing) {
            flow {
                val exerciseId = route.replaceSessionExerciseId?.let { workoutRepository.exerciseIdOf(it) }
                    ?: route.replaceRoutineExerciseId?.let { routineBuilder.exerciseIdOf(it) }
                emit(if (exerciseId == null) emptyList() else exerciseRepository.alternatives(exerciseId, SUGGESTION_LIMIT))
            }
        } else {
            exerciseRepository.observeRecentlyUsed(SUGGESTION_LIMIT)
        }

    val uiState: StateFlow<ExercisePickerUiState> = combine(
        query.debounce(SEARCH_DEBOUNCE_MS).flatMapLatest { text -> exerciseRepository.observeExercises(ExerciseFilter(query = text)) },
        suggestions,
        exerciseRepository.observeThumbnailPaths(),
        query,
    ) { results, suggested, thumbs, text ->
        val toRow = { e: ExerciseEntity ->
            ExerciseRowUi.from(e, thumbs[e.id]?.let(mediaRepository::resolve))
        }
        ExercisePickerUiState(
            isReplacing = isReplacing,
            suggestions = if (text.isBlank()) suggested.map(toRow) else emptyList(),
            results = results.map(toRow),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisePickerUiState(isReplacing = isReplacing))

    fun onPick(exerciseId: String) {
        viewModelScope.launch {
            when {
                route.replaceRoutineExerciseId != null -> routineBuilder.replaceExercise(route.replaceRoutineExerciseId, exerciseId)
                route.routineDayId != null -> routineBuilder.addExercise(route.routineDayId, exerciseId)
                route.replaceSessionExerciseId != null -> workoutRepository.replaceExercise(route.replaceSessionExerciseId, exerciseId)
                route.sessionId != null -> workoutRepository.addExercise(route.sessionId, exerciseId)
            }
            doneChannel.send(Unit)
        }
    }

    private companion object {
        const val SUGGESTION_LIMIT = 8
        const val SEARCH_DEBOUNCE_MS = 150L
    }
}

/**
 * @param onCreateExercise apre l'editor con il testo cercato come nome.
 * @param createdExerciseId esercizio appena creato dall'editor: viene scelto subito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    onBack: () -> Unit,
    onCreateExercise: (name: String) -> Unit,
    createdExerciseId: String?,
    onCreatedExerciseConsumed: () -> Unit,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(viewModel) { viewModel.done.collect { onBack() } }
    LaunchedEffect(createdExerciseId) {
        if (createdExerciseId != null) {
            onCreatedExerciseConsumed()
            viewModel.onPick(createdExerciseId)
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).imePadding()) {
        TopAppBar(
            title = { Text(stringResource(if (state.isReplacing) R.string.picker_replace_title else R.string.picker_add_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SportDimens.ScreenPadding)
                .heightIn(min = SportDimens.TouchTargetMin),
            placeholder = { Text(stringResource(R.string.exercises_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.query.value = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.exercises_clear_search))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )
        LazyColumn(contentPadding = PaddingValues(vertical = SportDimens.SpacingS)) {
            if (state.suggestions.isNotEmpty()) {
                item {
                    SectionHeader(
                        stringResource(if (state.isReplacing) R.string.picker_alternatives else R.string.picker_recent),
                        modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
                    )
                }
                items(state.suggestions, key = { "s-${it.id}" }) { exercise ->
                    ExerciseListItem(exercise, onClick = { viewModel.onPick(exercise.id) })
                }
                item {
                    SectionHeader(stringResource(R.string.picker_all), modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding))
                }
            }
            items(state.results, key = { it.id }) { exercise ->
                ExerciseListItem(exercise, onClick = { viewModel.onPick(exercise.id) })
            }
            if (query.isNotBlank()) {
                item(key = "create") { CreateExerciseRow(query.trim(), onClick = { onCreateExercise(query.trim()) }) }
            }
        }
    }
}

/** In fondo ai risultati (o al loro posto): l'esercizio cercato non c'è, lo si crea al volo. */
@Composable
private fun CreateExerciseRow(name: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(SportDimens.ThumbnailSize)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.picker_create, name), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.picker_create_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
