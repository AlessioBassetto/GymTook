package com.example.sport.feature.today

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.PrimaryActionButton
import com.example.sport.core.designsystem.component.ScreenHeader
import com.example.sport.core.designsystem.component.SecondaryActionButton
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.ui.media.ExerciseThumbnail

@Composable
fun TodayScreen(
    onOpenRoutines: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasActiveSession by viewModel.hasActiveSession.collectAsStateWithLifecycle()
    val freeTitle = stringResource(R.string.workout_free_title)
    val withNotificationPermission = rememberNotificationPermissionGate()

    LaunchedEffect(viewModel) { viewModel.openWorkout.collect { onOpenWorkout() } }

    TodayContent(
        state = state,
        onOpenRoutines = onOpenRoutines,
        onOpenExercise = onOpenExercise,
        hasActiveSession = hasActiveSession,
        onStartWorkout = { dayId -> withNotificationPermission { viewModel.startWorkout(dayId) } },
        onStartFree = { withNotificationPermission { viewModel.startFreeWorkout(freeTitle) } },
        onResume = onOpenWorkout,
        onOpenSettings = onOpenSettings,
    )
}

/**
 * Prima di avviare il primo allenamento chiede il permesso per le notifiche (timer di recupero a
 * schermo spento). L'azione parte comunque, anche se il permesso viene negato.
 */
@Composable
private fun rememberNotificationPermissionGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.invoke()
        pending = null
    }
    return { action ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }
}

@Composable
internal fun TodayContent(
    state: TodayUiState,
    onOpenRoutines: () -> Unit,
    onOpenExercise: (String) -> Unit,
    hasActiveSession: Boolean = false,
    onStartWorkout: (Long) -> Unit = {},
    onStartFree: () -> Unit = {},
    onResume: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val settingsAction: @Composable () -> Unit = { SettingsButton(onOpenSettings) }
    Column(Modifier.fillMaxSize()) {
        when (state) {
            TodayUiState.Loading -> {
                ScreenHeader(title = stringResource(R.string.nav_today), actions = settingsAction)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is TodayUiState.NoRoutine -> {
                ScreenHeader(title = state.dateLabel, actions = settingsAction)
                if (hasActiveSession) ResumeButton(onResume)
                EmptyState(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.today_no_routine_title),
                    message = stringResource(R.string.today_no_routine_message),
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
                            PrimaryActionButton(text = stringResource(R.string.today_no_routine_action), onClick = onOpenRoutines)
                            if (!hasActiveSession) {
                                SecondaryActionButton(
                                    text = stringResource(R.string.workout_free),
                                    onClick = onStartFree,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    },
                )
            }
            is TodayUiState.Rest -> {
                ScreenHeader(title = state.dateLabel, subtitle = stringResource(R.string.today_routine_label, state.routineName), actions = settingsAction)
                if (hasActiveSession) ResumeButton(onResume)
                EmptyState(
                    icon = Icons.Outlined.SelfImprovement,
                    title = stringResource(R.string.today_rest_title),
                    message = if (state.nextDayName != null && state.nextDateLabel != null) {
                        stringResource(R.string.today_rest_message) + "\n" +
                            stringResource(R.string.today_next_workout, state.nextDayName, state.nextDateLabel)
                    } else {
                        stringResource(R.string.today_rest_message)
                    },
                    action = if (hasActiveSession) {
                        null
                    } else {
                        { SecondaryActionButton(text = stringResource(R.string.workout_free), onClick = onStartFree) }
                    },
                )
            }
            is TodayUiState.Workout -> WorkoutPlan(
                state = state,
                hasActiveSession = hasActiveSession,
                onStart = { onStartWorkout(state.routineDayId) },
                onResume = onResume,
                onOpenExercise = onOpenExercise,
                settingsAction = settingsAction,
            )
        }
    }
}

/** Ingranaggio in alto a destra: impostazioni (unità, sforzo) e backup dei dati. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
    }
}

@Composable
private fun ResumeButton(onResume: () -> Unit) {
    PrimaryActionButton(
        text = stringResource(R.string.workout_resume),
        onClick = onResume,
        icon = Icons.Filled.PlayArrow,
        modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
    )
}

@Composable
private fun WorkoutPlan(
    state: TodayUiState.Workout,
    hasActiveSession: Boolean,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onOpenExercise: (String) -> Unit,
    settingsAction: @Composable () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = SportDimens.SpacingXl),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
    ) {
        item {
            ScreenHeader(title = state.dateLabel, subtitle = stringResource(R.string.today_routine_label, state.routineName), actions = settingsAction)
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(Modifier.padding(SportDimens.CardPadding)) {
                    Text(
                        text = state.dayName,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(
                            R.string.today_workout_summary,
                            state.exercises.size,
                            state.totalSets,
                            state.estimatedMinutes,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(SportDimens.SpacingL))
                    PrimaryActionButton(
                        text = stringResource(if (hasActiveSession) R.string.workout_resume else R.string.workout_start),
                        onClick = if (hasActiveSession) onResume else onStart,
                        icon = Icons.Filled.PlayArrow,
                    )
                }
            }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.today_plan_header),
                modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
            )
        }
        items(state.exercises, key = { it.exerciseId }) { exercise ->
            PlannedExerciseRow(exercise, onClick = { onOpenExercise(exercise.exerciseId) })
        }
    }
}

@Composable
private fun PlannedExerciseRow(exercise: PlannedExerciseUi, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(SportDimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            ExerciseThumbnail(model = exercise.thumbnail)
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(exercise.muscleLabelRes) + " · " + stringResource(exercise.equipmentLabelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.plan_target, exercise.sets, exercise.reps),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.plan_rest, exercise.rest),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
