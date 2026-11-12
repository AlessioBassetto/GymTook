package com.example.sport.feature.exercises

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.database.dao.CustomExerciseRemoval
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.ui.anatomy.MuscleMap
import com.example.sport.core.ui.exercise.ExerciseRowUi
import com.example.sport.core.ui.labels.labelRes
import com.example.sport.core.ui.media.ExerciseThumbnail
import com.example.sport.core.ui.media.FrameLoop
import com.example.sport.core.ui.media.VideoLoop
import java.io.File

/** Azioni della scheda esercizio (separate dal ViewModel per i test di interfaccia). */
internal data class DetailActions(
    val onBack: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onEdit: () -> Unit,
    val onDeleteRequest: () -> Unit,
    val onRecordVideo: () -> Unit,
    val onPickVideo: () -> Unit,
    val onRemoveVideo: () -> Unit,
    val onAddToRoutineDay: (dayId: Long) -> Unit = {},
    val onOpenProgress: () -> Unit = {},
)

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenProgress: (String) -> Unit = {},
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val routineTargets by viewModel.routineTargets.collectAsStateWithLifecycle()
    val hasProgress by viewModel.hasProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onVideoPicked)
    }
    // Percorso del file in cui la fotocamera sta registrando: sopravvive alla ricreazione dell'Activity.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val recordVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        val path = capturePath ?: return@rememberLauncherForActivityResult
        capturePath = null
        if (success) viewModel.onVideoRecorded(File(path)) else File(path).delete()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExerciseDetailEvent.Message -> snackbarHostState.showSnackbar(
                    context.getString(event.messageRes, *event.args.toTypedArray()),
                )
                is ExerciseDetailEvent.Removed -> {
                    val message = if (event.result == CustomExerciseRemoval.DELETED) R.string.exercise_deleted else R.string.exercise_hidden
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        }
    }

    val content = state as? ExerciseDetailUiState.Content
    ExerciseDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        routineTargets = routineTargets,
        hasProgress = hasProgress,
        actions = DetailActions(
            onBack = onBack,
            onToggleFavorite = viewModel::toggleFavorite,
            onOpenExercise = onOpenExercise,
            onEdit = { content?.let { onEdit(it.exercise.id) } },
            onOpenProgress = { content?.let { onOpenProgress(it.exercise.id) } },
            onDeleteRequest = { confirmDelete = true },
            onRecordVideo = {
                val directory = File(context.cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
                val file = File(directory, "capture-${System.currentTimeMillis()}.mp4")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                capturePath = file.path
                try {
                    recordVideo.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    capturePath = null
                    Toast.makeText(context, R.string.exercise_video_no_camera, Toast.LENGTH_SHORT).show()
                }
            },
            onPickVideo = {
                pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            onRemoveVideo = viewModel::onRemoveVideo,
            onAddToRoutineDay = viewModel::onAddToRoutineDay,
        ),
    )

    if (confirmDelete && content != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.exercise_delete_title, content.exercise.name)) },
            text = { Text(stringResource(R.string.exercise_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.onDelete()
                }) { Text(stringResource(R.string.exercise_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.editor_keep_editing)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseDetailContent(
    state: ExerciseDetailUiState,
    actions: DetailActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    routineTargets: List<RoutineTargetUi> = emptyList(),
    hasProgress: Boolean = false,
) {
    var showAddToRoutine by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state is ExerciseDetailUiState.Content) {
                        if (hasProgress) {
                            IconButton(onClick = actions.onOpenProgress, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                                Icon(Icons.Outlined.Insights, contentDescription = stringResource(R.string.exercise_progress_open))
                            }
                        }
                        IconButton(onClick = { showAddToRoutine = true }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.exercise_add_to_routine))
                        }
                        val favorite = state.exercise.isFavorite
                        IconButton(onClick = actions.onToggleFavorite, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                            Icon(
                                imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = stringResource(
                                    if (favorite) R.string.exercise_favorite_remove else R.string.exercise_favorite_add,
                                ),
                                tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (state.exercise.isCustom) CustomExerciseMenu(actions)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            when (state) {
                ExerciseDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ExerciseDetailUiState.NotFound -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.exercise_not_found),
                )
                is ExerciseDetailUiState.Content -> ExerciseDetailBody(state.exercise, state.isImportingVideo, actions)
            }
        }
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
    if (showAddToRoutine && state is ExerciseDetailUiState.Content) {
        AddToRoutineSheet(
            exerciseName = state.exercise.name,
            routines = routineTargets,
            onPick = actions.onAddToRoutineDay,
            onDismiss = { showAddToRoutine = false },
        )
    }
}

/** Schede e giorni in cui aggiungere l'esercizio: un tocco sul giorno e fatto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToRoutineSheet(
    exerciseName: String,
    routines: List<RoutineTargetUi>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = SportDimens.SpacingL),
        ) {
            Text(
                stringResource(R.string.exercise_add_to_routine_title, exerciseName),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
            )
            if (routines.isEmpty()) {
                Text(
                    stringResource(R.string.exercise_no_routines),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(SportDimens.ScreenPadding),
                )
            }
            routines.forEach { routine ->
                Row(
                    Modifier.padding(start = SportDimens.ScreenPadding, end = SportDimens.ScreenPadding, top = SportDimens.SpacingM),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
                ) {
                    Text(routine.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (routine.isActive) Pill(stringResource(R.string.routines_active_badge), highlighted = true)
                }
                routine.days.forEach { day ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = SportDimens.TouchTargetMin)
                            .clickable {
                                onPick(day.id)
                                onDismiss()
                            }
                            .padding(horizontal = SportDimens.ScreenPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(day.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomExerciseMenu(actions: DetailActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.exercise_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exercise_edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exercise_delete)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.onDeleteRequest()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseDetailBody(exercise: ExerciseDetailUi, isImportingVideo: Boolean, actions: DetailActions) {
    val primaryLabels = exercise.primaryMuscles.map { stringResource(it.labelRes()) }
    val secondaryLabels = exercise.secondaryMuscles.map { stringResource(it.labelRes()) }
    val none = stringResource(R.string.exercise_none)
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(
            start = SportDimens.ScreenPadding,
            end = SportDimens.ScreenPadding,
            bottom = SportDimens.SpacingXxl,
        ),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
    ) {
        item {
            Column {
                Text(exercise.name, style = MaterialTheme.typography.headlineMedium)
                exercise.nameEn?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
                verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
            ) {
                if (exercise.isCustom) Pill(stringResource(R.string.exercise_custom_note), highlighted = true)
                Pill(stringResource(exercise.equipmentLabelRes))
                exercise.categoryLabelRes?.let { Pill(stringResource(it)) }
                if (!exercise.isCustom) Pill(stringResource(exercise.levelLabelRes))
                exercise.mechanicLabelRes?.let { Pill(stringResource(it)) }
            }
        }
        item { MediaSection(exercise, isImportingVideo, actions) }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
                    MuscleMap(
                        primary = exercise.primaryMuscles.toSet(),
                        secondary = exercise.secondaryMuscles.toSet(),
                        modifier = Modifier.fillMaxWidth(),
                        contentDescription = stringResource(
                            R.string.exercise_muscle_map_description,
                            primaryLabels.joinToString().ifEmpty { none },
                            secondaryLabels.joinToString().ifEmpty { none },
                        ),
                    )
                    MuscleLegend(stringResource(R.string.exercise_primary_muscles), primaryLabels, MaterialTheme.colorScheme.primary)
                    if (secondaryLabels.isNotEmpty()) {
                        MuscleLegend(
                            stringResource(R.string.exercise_secondary_muscles),
                            secondaryLabels,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
        if (exercise.savedSetups.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.exercise_my_setups)) }
            items(exercise.savedSetups) { setup -> SavedSetupRow(setup) }
        }
        if (exercise.setupHints.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.exercise_setup_hints)) }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
                    verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
                ) {
                    exercise.setupHints.forEach { Pill(it) }
                }
            }
        }
        if (exercise.isEnglishContent) {
            item { Notice(stringResource(R.string.exercise_content_english)) }
        }
        if (exercise.instructions.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.exercise_execution)) }
            itemsIndexed(exercise.instructions) { index, step -> NumberedStep(index + 1, step) }
        }
        if (exercise.keyPoints.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.exercise_key_points)) }
            itemsIndexed(exercise.keyPoints) { _, point ->
                IconLine(Icons.Outlined.CheckCircle, point, MaterialTheme.colorScheme.primary)
            }
        }
        if (exercise.commonMistakes.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.exercise_common_mistakes)) }
            itemsIndexed(exercise.commonMistakes) { _, mistake ->
                IconLine(Icons.Outlined.WarningAmber, mistake, MaterialTheme.colorScheme.error)
            }
        }
        if (exercise.alternatives.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.exercise_alternatives)) }
            items(exercise.alternatives, key = { "alt-${it.id}" }) { alternative ->
                AlternativeRow(alternative, onClick = { actions.onOpenExercise(alternative.id) })
            }
        }
        exercise.mediaCredit?.let { credit ->
            item {
                Text(
                    text = stringResource(R.string.exercise_media_credit, credit.source, credit.license),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class MediaSource { MINE, CATALOG }

/**
 * Video personale, video di catalogo o fotogrammi animati, con il pulsante per registrare o scegliere
 * il proprio video. Se un video non è riproducibile si torna ai fotogrammi.
 */
@Composable
private fun MediaSection(exercise: ExerciseDetailUi, isImportingVideo: Boolean, actions: DetailActions) {
    val hasCatalogMedia = exercise.catalogVideo != null || exercise.frames.isNotEmpty()
    var source by rememberSaveable(exercise.userVideo) { mutableStateOf(if (exercise.userVideo != null) MediaSource.MINE else MediaSource.CATALOG) }
    var failedVideos by remember { mutableStateOf(emptySet<String>()) }
    val description = stringResource(R.string.exercise_video_description, exercise.name)

    Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
        if (exercise.userVideo != null && hasCatalogMedia) {
            Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
                FilterChip(
                    selected = source == MediaSource.MINE,
                    onClick = { source = MediaSource.MINE },
                    label = { Text(stringResource(R.string.exercise_video_mine)) },
                )
                FilterChip(
                    selected = source == MediaSource.CATALOG,
                    onClick = { source = MediaSource.CATALOG },
                    label = { Text(stringResource(R.string.exercise_video_catalog)) },
                )
            }
        }
        val userVideo = exercise.userVideo?.takeIf { source == MediaSource.MINE && it !in failedVideos }
        val catalogVideo = exercise.catalogVideo?.takeIf { it !in failedVideos }
        when {
            userVideo != null -> VideoLoop(uri = userVideo, contentDescription = description, onError = { failedVideos = failedVideos + userVideo })
            // Video personale illeggibile: avviso, e sotto i fotogrammi del catalogo se ci sono.
            exercise.userVideo != null && source == MediaSource.MINE -> {
                VideoUnavailable()
                if (exercise.frames.isNotEmpty()) {
                    FrameLoop(frames = exercise.frames, contentDescription = stringResource(R.string.exercise_frames_description, exercise.name))
                }
            }
            catalogVideo != null -> VideoLoop(uri = catalogVideo, contentDescription = description, onError = { failedVideos = failedVideos + catalogVideo })
            exercise.frames.isNotEmpty() -> FrameLoop(
                frames = exercise.frames,
                contentDescription = stringResource(R.string.exercise_frames_description, exercise.name),
            )
        }
        if (isImportingVideo) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.exercise_video_importing), style = MaterialTheme.typography.bodySmall)
        } else {
            MyVideoButton(hasVideo = exercise.userVideo != null, actions = actions)
        }
    }
}

@Composable
private fun VideoUnavailable() {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(SportDimens.CardPadding), horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            Icon(Icons.Outlined.VideocamOff, contentDescription = null)
            Text(stringResource(R.string.exercise_video_unplayable), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MyVideoButton(hasVideo: Boolean, actions: DetailActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.Videocam, contentDescription = null)
            Text(
                stringResource(if (hasVideo) R.string.exercise_video_replace else R.string.exercise_video_add),
                modifier = Modifier.padding(start = SportDimens.SpacingS),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exercise_video_record)) },
                leadingIcon = { Icon(Icons.Filled.Videocam, contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.onRecordVideo()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exercise_video_pick)) },
                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.onPickVideo()
                },
            )
            if (hasVideo) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exercise_video_remove)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = {
                        expanded = false
                        actions.onRemoveVideo()
                    },
                )
            }
        }
    }
}

@Composable
private fun SavedSetupRow(setup: SetupProfileSummaryUi) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(SportDimens.SpacingM),
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(setup.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(setup.summary, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun AlternativeRow(exercise: ExerciseRowUi, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = SportDimens.SpacingXs),
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseThumbnail(model = exercise.thumbnail, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(exercise.muscleLabelRes) + " · " + stringResource(exercise.equipmentLabelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, highlighted: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SportDimens.SpacingM, vertical = SportDimens.SpacingS),
        )
    }
}

@Composable
private fun MuscleLegend(title: String, muscles: List<String>, color: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.padding(top = 4.dp).size(12.dp)) {}
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(muscles.joinToString(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Notice(text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SportDimens.SpacingM),
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

private const val CAPTURE_DIRECTORY = "captures"
