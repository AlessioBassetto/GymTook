package com.example.sport.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.sport.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/*
 * Route type-safe (Navigation Compose): gli argomenti sono proprietà tipizzate, verificate in
 * compilazione, invece di stringhe "exercise/{id}".
 */

@Serializable
data object TodayRoute

@Serializable
data object RoutinesRoute

@Serializable
data object ExercisesRoute

@Serializable
data class ExerciseDetailRoute(val exerciseId: String)

/**
 * Editor degli esercizi personalizzati: [exerciseId] null = nuovo esercizio, con [initialName]
 * pre-compilato quando arriva da una ricerca senza risultati. Con [returnResult] l'id salvato torna
 * alla schermata precedente (il selettore del logger lo aggiunge subito alla sessione).
 */
@Serializable
data class ExerciseEditorRoute(
    val exerciseId: String? = null,
    val initialName: String? = null,
    val returnResult: Boolean = false,
)

/** Chiave del `SavedStateHandle` con cui l'editor restituisce l'id dell'esercizio creato. */
const val RESULT_CREATED_EXERCISE_ID = "created_exercise_id"

/** Workout Logger a tutto schermo: mostra sempre la sessione in corso. */
@Serializable
data object WorkoutRoute

/**
 * Selettore esercizi, con un solo obiettivo valorizzato:
 * - logger: aggiunge alla sessione [sessionId] o sostituisce [replaceSessionExerciseId];
 * - builder: aggiunge al giorno di scheda [routineDayId] o sostituisce [replaceRoutineExerciseId].
 */
@Serializable
data class ExercisePickerRoute(
    val sessionId: Long? = null,
    val replaceSessionExerciseId: Long? = null,
    val routineDayId: Long? = null,
    val replaceRoutineExerciseId: Long? = null,
)

/** Builder di una scheda: giorni, esercizi, superserie, calendario. */
@Serializable
data class RoutineEditorRoute(val routineId: Long)

@Serializable
data class WorkoutSummaryRoute(val sessionId: Long)

@Serializable
data object ProgressRoute

/** Storico del peso corporeo: grafico con media a 7 giorni e pesate. */
@Serializable
data object BodyWeightRoute

/** Andamento di un esercizio: 1RM stimato, carico massimo, volume, ripetizioni e record. */
@Serializable
data class ExerciseProgressRoute(val exerciseId: String)

/** Dettaglio di un allenamento concluso (dallo storico), con eliminazione. */
@Serializable
data class SessionDetailRoute(val sessionId: Long)

@Serializable
data object NutritionRoute

/** Elenco dei piani alimentari: attivo, varianti, nuovo piano. */
@Serializable
data object DietPlansRoute

/** Editor di un piano: obiettivi, pasti, alimenti, note. */
@Serializable
data class DietPlanEditorRoute(val planId: Long)

/**
 * Selettore dei piatti per un pasto del diario: [epochDay] = giorno (LocalDate.toEpochDay),
 * [mealType] = nome di MealType.
 */
@Serializable
data class FoodPickerRoute(val epochDay: Long, val mealType: String)

/** Editor di un piatto ([recipe] false) o di una ricetta dell'utente; [foodId] null = nuovo. */
@Serializable
data class FoodEditorRoute(val foodId: String? = null, val recipe: Boolean = false)

/** Impostazioni (unità, sforzo) e backup dei dati. */
@Serializable
data object SettingsRoute

/** Le cinque sezioni della barra di navigazione inferiore. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    TODAY(TodayRoute, TodayRoute::class, R.string.nav_today, Icons.Filled.Today, Icons.Outlined.Today),
    ROUTINES(RoutinesRoute, RoutinesRoute::class, R.string.nav_routines, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    EXERCISES(ExercisesRoute, ExercisesRoute::class, R.string.nav_exercises, Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    PROGRESS(ProgressRoute, ProgressRoute::class, R.string.nav_progress, Icons.Filled.Insights, Icons.Outlined.Insights),
    NUTRITION(NutritionRoute, NutritionRoute::class, R.string.nav_nutrition, Icons.Filled.Restaurant, Icons.Outlined.Restaurant),
}
