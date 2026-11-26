package com.example.sport.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.sport.R
import com.example.sport.core.data.WorkoutRepository
import com.example.sport.core.database.relation.ActiveSessionHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.settings.UserSettings
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.format.LocalEffortScale
import com.example.sport.core.ui.format.LocalWeightUnit
import com.example.sport.core.ui.time.rememberTickingNow
import com.example.sport.feature.exercises.ExerciseDetailScreen
import com.example.sport.feature.exercises.ExerciseEditorScreen
import com.example.sport.feature.exercises.ExerciseListScreen
import com.example.sport.feature.nutrition.DietPlanEditorScreen
import com.example.sport.feature.nutrition.DietPlansScreen
import com.example.sport.feature.nutrition.FoodEditorScreen
import com.example.sport.feature.nutrition.FoodPickerScreen
import com.example.sport.feature.nutrition.NutritionScreen
import com.example.sport.feature.progress.BodyWeightScreen
import com.example.sport.feature.progress.ExerciseProgressScreen
import com.example.sport.feature.progress.ProgressScreen
import com.example.sport.feature.progress.SessionDetailScreen
import com.example.sport.feature.routines.RoutineEditorScreen
import com.example.sport.feature.settings.SettingsScreen
import com.example.sport.feature.routines.RoutinesScreen
import com.example.sport.feature.today.TodayScreen
import com.example.sport.feature.update.UpdateDialogHost
import com.example.sport.feature.workout.ExercisePickerScreen
import com.example.sport.feature.workout.WorkoutScreen
import com.example.sport.feature.workout.WorkoutSummaryScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Stato condiviso dal guscio: la sessione in corso, per il banner "Riprendi". */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    /** Unità di peso e scala dello sforzo, fornite a tutte le schermate. */
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val activeSession: StateFlow<ActiveSessionHeader?> = workoutRepository.observeActiveSessionHeader()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun now(): Instant = workoutRepository.now()
}

/**
 * Guscio dell'app: barra inferiore con le cinque sezioni (visibile solo al primo livello),
 * banner della sessione in corso e grafo di navigazione.
 *
 * @param openWorkoutSignal incrementato quando l'app viene aperta dalla notifica del recupero.
 */
@Composable
fun SportApp(
    openWorkoutSignal: Int = 0,
    navController: NavHostController = rememberNavController(),
    shellViewModel: AppShellViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevel = TopLevelDestination.entries.any { currentDestination.isOn(it) }
    val activeSession by shellViewModel.activeSession.collectAsStateWithLifecycle()
    val settings by shellViewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(openWorkoutSignal) {
        if (openWorkoutSignal > 0) navController.navigate(WorkoutRoute) { launchSingleTop = true }
    }

    CompositionLocalProvider(
        LocalWeightUnit provides settings.weightUnit,
        LocalEffortScale provides settings.effortScale,
    ) {
    Scaffold(
        // Le inset di sistema sono gestite dai singoli schermi (header, top bar, liste).
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isTopLevel) {
                Column {
                    val session = activeSession
                    // In "Oggi" c'è già il pulsante "Riprendi allenamento".
                    if (session != null && !currentDestination.isOn(TopLevelDestination.TODAY)) {
                        ActiveWorkoutBanner(session, now = shellViewModel::now, onResume = { navController.navigate(WorkoutRoute) })
                    }
                    SportNavigationBar(
                        currentDestination = currentDestination,
                        onNavigate = navController::navigateToTopLevel,
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> {
                TodayScreen(
                    onOpenRoutines = { navController.navigateToTopLevel(TopLevelDestination.ROUTINES) },
                    onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                    onOpenWorkout = { navController.navigate(WorkoutRoute) { launchSingleTop = true } },
                    onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                )
            }
            composable<RoutinesRoute> {
                RoutinesScreen(onOpenRoutine = { navController.navigate(RoutineEditorRoute(it)) })
            }
            composable<RoutineEditorRoute> {
                RoutineEditorScreen(
                    onBack = navController::popBackStack,
                    onAddExercise = { dayId -> navController.navigate(ExercisePickerRoute(routineDayId = dayId)) },
                    onReplaceExercise = { id -> navController.navigate(ExercisePickerRoute(replaceRoutineExerciseId = id)) },
                    onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                    // La copia si apre al posto della scheda corrente: indietro torna all'elenco.
                    onOpenRoutine = { id ->
                        navController.navigate(RoutineEditorRoute(id)) { popUpTo<RoutineEditorRoute> { inclusive = true } }
                    },
                )
            }
            composable<ExercisesRoute> {
                ExerciseListScreen(
                    onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                    onCreateExercise = { navController.navigate(ExerciseEditorRoute()) },
                )
            }
            composable<ExerciseDetailRoute> {
                ExerciseDetailScreen(
                    onBack = navController::popBackStack,
                    onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                    onEdit = { navController.navigate(ExerciseEditorRoute(exerciseId = it)) },
                    onOpenProgress = { navController.navigate(ExerciseProgressRoute(it)) },
                )
            }
            composable<ExerciseEditorRoute> { entry ->
                val route = entry.toRoute<ExerciseEditorRoute>()
                ExerciseEditorScreen(
                    onClose = navController::popBackStack,
                    onSaved = { exerciseId, isNew ->
                        when {
                            route.returnResult -> {
                                navController.previousBackStackEntry?.savedStateHandle?.set(RESULT_CREATED_EXERCISE_ID, exerciseId)
                                navController.popBackStack()
                            }
                            // Nuovo esercizio dalla libreria: si apre la sua scheda al posto dell'editor.
                            isNew -> navController.navigate(ExerciseDetailRoute(exerciseId)) {
                                popUpTo<ExerciseEditorRoute> { inclusive = true }
                            }
                            else -> navController.popBackStack()
                        }
                    },
                )
            }
            composable<WorkoutRoute> {
                WorkoutScreen(
                    onMinimize = navController::popBackStack,
                    onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                    onAddExercise = { sessionId -> navController.navigate(ExercisePickerRoute(sessionId = sessionId)) },
                    onReplaceExercise = { sessionId, sessionExerciseId ->
                        navController.navigate(ExercisePickerRoute(sessionId = sessionId, replaceSessionExerciseId = sessionExerciseId))
                    },
                    onFinished = { sessionId ->
                        navController.navigate(WorkoutSummaryRoute(sessionId)) {
                            popUpTo(WorkoutRoute) { inclusive = true }
                        }
                    },
                    onDiscarded = { navController.popBackStack() },
                )
            }
            composable<ExercisePickerRoute> { entry ->
                val createdExerciseId by entry.savedStateHandle
                    .getStateFlow<String?>(RESULT_CREATED_EXERCISE_ID, null)
                    .collectAsStateWithLifecycle()
                ExercisePickerScreen(
                    onBack = navController::popBackStack,
                    onCreateExercise = { name -> navController.navigate(ExerciseEditorRoute(initialName = name, returnResult = true)) },
                    createdExerciseId = createdExerciseId,
                    onCreatedExerciseConsumed = { entry.savedStateHandle[RESULT_CREATED_EXERCISE_ID] = null },
                )
            }
            composable<WorkoutSummaryRoute> {
                WorkoutSummaryScreen(
                    onDone = {
                        navController.navigate(TodayRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<ProgressRoute> {
                ProgressScreen(
                    onOpenBodyWeight = { navController.navigate(BodyWeightRoute) { launchSingleTop = true } },
                    onOpenExercise = { navController.navigate(ExerciseProgressRoute(it)) },
                    onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
                )
            }
            composable<BodyWeightRoute> {
                BodyWeightScreen(onBack = navController::popBackStack)
            }
            composable<ExerciseProgressRoute> {
                ExerciseProgressScreen(
                    onBack = navController::popBackStack,
                    onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
                )
            }
            composable<SessionDetailRoute> {
                SessionDetailScreen(
                    onBack = navController::popBackStack,
                    onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                )
            }
            composable<NutritionRoute> {
                NutritionScreen(
                    onOpenPlans = { navController.navigate(DietPlansRoute) { launchSingleTop = true } },
                    onEditPlan = { navController.navigate(DietPlanEditorRoute(it)) },
                    onAddFood = { date, meal -> navController.navigate(FoodPickerRoute(date.toEpochDay(), meal.name)) },
                )
            }
            composable<FoodPickerRoute> {
                FoodPickerScreen(
                    onBack = navController::popBackStack,
                    onNewFood = { navController.navigate(FoodEditorRoute(recipe = false)) },
                    onNewRecipe = { navController.navigate(FoodEditorRoute(recipe = true)) },
                    onEditFood = { navController.navigate(FoodEditorRoute(foodId = it)) },
                )
            }
            composable<FoodEditorRoute> {
                FoodEditorScreen(onBack = navController::popBackStack)
            }
            composable<DietPlansRoute> {
                DietPlansScreen(
                    onBack = navController::popBackStack,
                    onOpenPlan = { navController.navigate(DietPlanEditorRoute(it)) },
                )
            }
            composable<DietPlanEditorRoute> {
                DietPlanEditorScreen(
                    onBack = navController::popBackStack,
                    // La copia si apre al posto del piano corrente: indietro torna dove si era.
                    onOpenPlan = { id ->
                        navController.navigate(DietPlanEditorRoute(id)) { popUpTo<DietPlanEditorRoute> { inclusive = true } }
                    },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onBack = navController::popBackStack)
            }
        }
    }
    // Sopra ogni schermata: proposta di aggiornamento, download e installazione (solo se trovato).
    UpdateDialogHost()
    }
}

/** Banner sopra la barra di navigazione: la sessione resta raggiungibile da ogni sezione. */
@Composable
private fun ActiveWorkoutBanner(session: ActiveSessionHeader, now: () -> Instant, onResume: () -> Unit) {
    val current by rememberTickingNow(periodMillis = 1_000, now = now)
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.banner_active),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${session.title} · ${Formatters.duration(Duration.between(session.startedAt, current).seconds)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onResume, modifier = Modifier.height(SportDimens.TouchTargetMin)) {
                Text(stringResource(R.string.banner_resume))
            }
        }
    }
}

@Composable
private fun SportNavigationBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination.isOn(destination)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

/** Cambio sezione: una sola copia per tab, stato salvato e ripristinato (pattern Material). */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
