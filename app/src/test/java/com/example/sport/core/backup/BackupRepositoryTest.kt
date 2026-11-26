package com.example.sport.core.backup

import com.example.sport.core.database.calorieRepository
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.data.CustomExerciseDraft
import com.example.sport.core.data.CustomExerciseRepository
import com.example.sport.core.data.DietPlanRepository
import com.example.sport.core.data.DietTargets
import com.example.sport.core.data.LoggedSet
import com.example.sport.core.data.MealItemInput
import com.example.sport.core.data.ProgressRepository
import com.example.sport.core.data.WorkoutRepository
import com.example.sport.core.data.logSession
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.SportDatabase
import com.example.sport.core.database.T0
import com.example.sport.core.database.expectThrows
import com.example.sport.core.database.seed.AssetExerciseCatalogSource
import com.example.sport.core.database.seed.ExerciseCatalogSeeder
import com.example.sport.core.database.seed.AssetFoodCatalogSource
import com.example.sport.core.database.seed.FoodCatalogSeeder
import com.example.sport.core.data.food.FoodRepository
import com.example.sport.core.data.food.OpenFoodFactsClient
import com.example.sport.core.data.food.OpenFoodFactsConfig
import com.example.sport.core.data.food.RecipeIngredientDraft
import com.example.sport.core.data.food.nutrients
import com.example.sport.core.data.food.per100g
import com.example.sport.core.model.FoodCategory
import com.example.sport.core.model.MealType
import okhttp3.OkHttpClient
import com.example.sport.core.media.UserVideoStore
import com.example.sport.core.model.EffortScale
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.settings.testSettingsRepository
import com.example.sport.core.workout.TestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private val today: LocalDate = LocalDate.of(2026, 11, 2)
    private lateinit var settings: SettingsRepository
    private lateinit var backup: BackupRepository
    private lateinit var target: SportDatabase
    private lateinit var targetSettings: SettingsRepository
    private lateinit var targetBackup: BackupRepository
    private lateinit var porridgeId: String
    private lateinit var recipeId: String

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        settings = testSettingsRepository(context.filesDir)
        backup = BackupRepository(db, settings, db.calorieRepository(clock), clock, Dispatchers.IO)
        // Secondo telefono: database nuovo con il solo catalogo.
        target = Room.inMemoryDatabaseBuilder(context, SportDatabase::class.java).allowMainThreadQueries().build()
        ExerciseCatalogSeeder(
            database = target,
            source = AssetExerciseCatalogSource(context, Dispatchers.IO),
            clock = Clock.fixed(T0, ZoneOffset.UTC),
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ).seedIfNeeded()
        // Catalogo dei piatti su entrambi i telefoni (è nell'app, non nel backup).
        listOf(db, target).forEach { database ->
            FoodCatalogSeeder(database.foodDao(), AssetFoodCatalogSource(context, Dispatchers.IO), Clock.fixed(T0, ZoneOffset.UTC), CoroutineScope(SupervisorJob() + Dispatchers.IO))
                .seedIfNeeded()
        }
        targetSettings = testSettingsRepository(context.filesDir)
        targetBackup = BackupRepository(target, targetSettings, target.calorieRepository(clock), clock, Dispatchers.IO)
    }

    @After
    fun closeTarget() {
        target.close()
    }

    /** Una settimana tipo: scheda, due allenamenti (uno con un esercizio personalizzato), pesate, dieta, preferito. */
    private suspend fun fillSource(): String {
        val workout = WorkoutRepository(db.workoutSessionDao(), db.machineSetupDao(), db.calorieRepository(clock), clock)
        val custom = CustomExerciseRepository(db.exerciseDao(), UserVideoStore(File(context.filesDir, "backup-videos"), context, Dispatchers.IO), clock)
        val customId = custom.create(CustomExerciseDraft(name = "Pressa a pendolo", primaryMuscles = listOf(Muscle.QUADRICEPS)))
        createRoutineWithDay("Gambe", listOf("barbell_back_squat" to 3, customId to 3))
        logSession(workout, db.workoutSessionDao(), clock, 0, "barbell_bench_press" to listOf(LoggedSet(100.0, 5), LoggedSet(102.5, 4)))
        logSession(workout, db.workoutSessionDao(), clock, 2, customId to listOf(LoggedSet(120.0, 10)))
        db.exerciseDao().setFavorite("barbell_bench_press", true, T0)
        val laterClock = Clock.fixed(T0.plusSeconds(5 * 86_400), ZoneOffset.UTC)
        val progress = ProgressRepository(db.workoutSessionDao(), db.bodyWeightDao(), db.progressDao(), db.exerciseDao(), db.calorieRepository(laterClock), laterClock)
        progress.saveBodyWeight(today, 80.4)
        progress.saveBodyWeight(today.plusDays(1), 80.1)
        val diet = DietPlanRepository(db, clock)
        val planId = diet.createPlan("Massa")
        diet.updateTargets(planId, DietTargets(2800, 180.0, 350.0, 80.0))
        diet.addItem(diet.observePlan(planId).first()!!.orderedMeals.first().meal.id, MealItemInput("Avena", 80.0, "g", proteinG = 10.0, carbsG = 54.0, fatG = 6.0))
        // Diario a grammi con un piatto e una ricetta dell'utente.
        val foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(OkHttpClient(), OpenFoodFactsConfig("http://127.0.0.1:9/", "Sport/test"), Dispatchers.IO), clock)
        porridgeId = foods.saveCustomFood(null, "Porridge della casa", FoodCategory.BREAKFAST_SWEETS, null, 10.0, 20.0, 5.0, 250.0)
        val pasta = foods.food("cat:pasta_secca_cruda")!!
        recipeId = foods.saveRecipe(null, "Pasta al tonno", FoodCategory.FIRST_COURSES, listOf(RecipeIngredientDraft(pasta.id, pasta.name, 80.0, pasta.per100g)))
        foods.addEntry(today, MealType.LUNCH, foods.food(recipeId)!!, 200.0)
        foods.addEntry(today, MealType.BREAKFAST, foods.food("cat:banana")!!, 120.0)
        settings.setWeightUnit(WeightUnit.LB)
        settings.setEffortScale(EffortScale.RIR)
        return customId
    }

    @Test
    fun exportThenRestore_onAnotherDevice_bringsBackEverything() = runTest {
        val customId = fillSource()
        val json = backup.export()

        val file = targetBackup.parse(json)
        val summary = targetBackup.restore(file)
        assertEquals(BackupSummary(sessions = 2, routines = 1, bodyWeights = 2, dietPlans = 1, customExercises = 1, diaryEntries = 2, userFoods = 2), summary)

        val sessions = target.workoutSessionDao().observeCompletedSessionSummaries(10).first()
        assertEquals(2, sessions.size)
        assertEquals(500.0 + 410.0, sessions.last().totalVolumeKg, 1e-9)
        assertEquals("Pressa a pendolo", target.exerciseDao().getById(customId)!!.name)
        assertEquals(listOf(Muscle.QUADRICEPS), target.exerciseDao().getMuscles(customId).map { it.muscle })
        assertTrue(target.exerciseDao().getById("barbell_bench_press")!!.isFavorite)
        assertEquals(80.4, target.bodyWeightDao().getByDate(today)!!.weightKg, 0.0)
        val plan = target.dietDao().observeActivePlanWithMeals().first()!!
        assertEquals(2800, plan.plan.targetCaloriesKcal)
        assertEquals(310.0, plan.orderedMeals.first().orderedItems.single().caloriesKcal!!, 0.0)
        assertEquals(WeightUnit.LB, targetSettings.current().weightUnit)
        assertEquals(EffortScale.RIR, targetSettings.current().effortScale)
        assertEquals(165.0, target.foodDao().getFood(porridgeId)!!.kcalPer100g, 1e-9)
        assertEquals(1, target.foodDao().getIngredients(recipeId).size)
        val diary = target.foodDao().observeEntries(today).first()
        assertEquals(listOf(200.0, 120.0), diary.map { it.grams })
        assertEquals("200 g della ricetta (solo pasta: 353 kcal/100 g)", 706.0, diary.first().nutrients.kcal, 1e-6)

        // Il backup del secondo telefono è identico (stesse righe, stesse colonne).
        assertEquals(json.withoutTimestamp(), targetBackup.export().withoutTimestamp())
    }

    @Test
    fun restore_replacesExistingData_andKeepsTheCatalog() = runTest {
        fillSource()
        val json = backup.export()
        // Sul telefono di destinazione c'erano già dati diversi.
        target.exerciseDao().setFavorite("barbell_back_squat", true, T0)
        ProgressRepository(target.workoutSessionDao(), target.bodyWeightDao(), target.progressDao(), target.exerciseDao(), target.calorieRepository(clock), clock)
            .saveBodyWeight(today.minusDays(30), 90.0)
        val catalogSize = target.exerciseDao().count()

        targetBackup.restore(targetBackup.parse(json))

        assertEquals("La pesata vecchia è sostituita", null, target.bodyWeightDao().getByDate(today.minusDays(30)))
        assertFalse("I preferiti tornano quelli del backup", target.exerciseDao().getById("barbell_back_squat")!!.isFavorite)
        assertEquals("Catalogo + l'esercizio personalizzato", catalogSize + 1, target.exerciseDao().count())
    }

    @Test
    fun invalidFiles_areRejected_withoutTouchingTheData() = runTest {
        fillSource()
        val json = backup.export()
        expectThrows<InvalidBackupException> { targetBackup.parse("{ non è json") }
        expectThrows<InvalidBackupException> { targetBackup.parse(json.replace("\"sport-backup\"", "\"altro\"")) }
        expectThrows<InvalidBackupException> { targetBackup.parse(json.replace("\"version\": 1", "\"version\": 99")) }

        // Un allenamento con un esercizio che sul telefono di destinazione non esiste: niente viene scritto.
        ProgressRepository(target.workoutSessionDao(), target.bodyWeightDao(), target.progressDao(), target.exerciseDao(), target.calorieRepository(clock), clock)
            .saveBodyWeight(today.minusDays(30), 90.0)
        val broken = targetBackup.parse(json.replace("\"barbell_bench_press\"", "\"esercizio_inesistente\""))
        expectThrows<InvalidBackupException> { targetBackup.restore(broken) }
        assertEquals(90.0, target.bodyWeightDao().getByDate(today.minusDays(30))!!.weightKg, 0.0)
        assertTrue(target.workoutSessionDao().observeCompletedSessionSummaries(10).first().isEmpty())
    }

    private fun String.withoutTimestamp() = lines().filterNot { "exportedAt" in it }.joinToString("\n")
}
