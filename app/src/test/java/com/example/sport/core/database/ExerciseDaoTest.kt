package com.example.sport.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.dao.CustomExerciseRemoval
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.ExerciseMuscleEntity
import com.example.sport.core.model.DifficultyLevel
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import com.example.sport.core.model.MuscleRole
import com.example.sport.core.database.query.ExerciseFilter
import com.example.sport.core.model.catalog.newCustomExerciseId
import com.example.sport.core.database.util.SearchText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseDaoTest : DatabaseTest() {

    private val dao get() = db.exerciseDao()

    @Before
    fun seed() = runTest { seedBundledCatalog() }

    private suspend fun ids(filter: ExerciseFilter): List<String> = dao.searchExercises(filter).map { it.id }

    @Test
    fun search_ranksNamesStartingWithQueryFirst() = runTest {
        val results = dao.searchExercises(ExerciseFilter(query = "panca"))
        assertTrue(results.size > 3)
        assertTrue(results.first().name.startsWith("Panca"))
        assertTrue("Trova anche 'panca' a metà nome", "machine_preacher_curl" in results.map { it.id })
    }

    @Test
    fun search_isCaseAndAccentInsensitive_andWordOrderFree() = runTest {
        assertTrue("incline_dumbbell_press" in ids(ExerciseFilter(query = "MANUBRI inclinata")))
        assertTrue("pec_deck" in ids(ExerciseFilter(query = "pèc DECK")))

        // Nome inglese: tutte le varianti di lat pulldown, e solo quelle.
        val latPulldowns = dao.searchExercises(ExerciseFilter(query = "lat pulldown"))
        assertTrue("lat_pulldown" in latPulldowns.map { it.id })
        assertTrue(latPulldowns.size > 1)
        assertTrue(latPulldowns.all { "lat" in it.searchText && "pulldown" in it.searchText })
    }

    @Test
    fun search_neverInterpretsUserInputAsWildcards() = runTest {
        assertTrue(ids(ExerciseFilter(query = "100%")).isEmpty())
        assertEquals("Solo punteggiatura = nessun filtro testuale", dao.count(), ids(ExerciseFilter(query = "%_")).size)
    }

    @Test
    fun filter_specificMuscleRegion() = runTest {
        val upperChest = ids(ExerciseFilter(muscles = setOf(Muscle.CHEST_UPPER)))
        assertTrue(upperChest.containsAll(listOf("incline_dumbbell_press", "low_to_high_cable_fly", "smith_incline_bench_press")))
        assertFalse("La panca piana lavora il petto centrale", "barbell_bench_press" in upperChest)
        upperChest.forEach { id ->
            assertTrue(
                "$id deve avere il petto alto come primario",
                dao.getMuscles(id).any { it.muscle == Muscle.CHEST_UPPER && it.role == MuscleRole.PRIMARY },
            )
        }
    }

    @Test
    fun filter_muscleGroup_primaryOnlyVsIncludingSecondary() = runTest {
        val primaryOnly = ids(ExerciseFilter(muscleGroups = setOf(MuscleGroup.CHEST)))
        val withSecondary = ids(ExerciseFilter(muscleGroups = setOf(MuscleGroup.CHEST), includeSecondaryMuscles = true))
        assertFalse("close_grip_bench_press" in primaryOnly)
        assertTrue("close_grip_bench_press" in withSecondary)
        assertTrue(withSecondary.containsAll(primaryOnly))
    }

    @Test
    fun filter_equipmentAndMuscleAreCombinedWithAnd() = runTest {
        val quadMachines = dao.searchExercises(
            ExerciseFilter(equipment = setOf(Equipment.MACHINE), muscleGroups = setOf(MuscleGroup.QUADRICEPS)),
        )
        assertTrue(quadMachines.map { it.id }.containsAll(listOf("leg_press", "leg_extension", "hack_squat")))
        assertTrue(quadMachines.all { it.equipment == Equipment.MACHINE })
        assertFalse("Lo squat con bilanciere non è un macchinario", "barbell_back_squat" in quadMachines.map { it.id })
        val dumbbellOrKettlebell = dao.searchExercises(
            ExerciseFilter(equipment = setOf(Equipment.DUMBBELL, Equipment.KETTLEBELL)),
        )
        assertTrue(dumbbellOrKettlebell.all { it.equipment == Equipment.DUMBBELL || it.equipment == Equipment.KETTLEBELL })
        assertTrue(dumbbellOrKettlebell.any { it.equipment == Equipment.KETTLEBELL })
    }

    @Test
    fun filter_category_separatesPowerMovementsFromStrength() = runTest {
        val olympic = dao.searchExercises(ExerciseFilter(categories = setOf(ExerciseCategory.OLYMPIC)))
        assertTrue(olympic.isNotEmpty())
        assertTrue(olympic.all { it.category == ExerciseCategory.OLYMPIC })
        assertTrue("power_clean" in olympic.map { it.id })

        val hamstringStrength = ids(ExerciseFilter(categories = setOf(ExerciseCategory.STRENGTH), muscleGroups = setOf(MuscleGroup.HAMSTRINGS)))
        assertTrue("romanian_deadlift" in hamstringStrength)
        assertFalse("Il box jump è pliometria", "front_box_jump" in hamstringStrength)

        val cardioOrPlyo = dao.searchExercises(ExerciseFilter(categories = setOf(ExerciseCategory.CARDIO, ExerciseCategory.PLYOMETRIC)))
        assertEquals(setOf(ExerciseCategory.CARDIO, ExerciseCategory.PLYOMETRIC), cardioOrPlyo.map { it.category }.toSet())
    }

    @Test
    fun favorites_comeFirst_andFlowReflectsChanges() = runTest {
        val favoritesFlow = dao.observeExercises(ExerciseFilter(onlyFavorites = true))
        assertTrue(favoritesFlow.first().isEmpty())

        dao.setFavorite("hack_squat", favorite = true, updatedAt = T0)

        assertEquals(listOf("hack_squat"), favoritesFlow.first().map { it.id })
        assertEquals("hack_squat", ids(ExerciseFilter(muscleGroups = setOf(MuscleGroup.QUADRICEPS))).first())
    }

    @Test
    fun alternativeCandidates_shareThePrimaryMuscle_andExcludeSelf() = runTest {
        val candidates = dao.getAlternativeCandidates("leg_extension")
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.none { it.id == "leg_extension" })
        val quadricepsAsPrimary = dao.getMusclesFor(candidates.map { it.id })
            .filter { it.muscle == Muscle.QUADRICEPS && it.role == MuscleRole.PRIMARY }
            .map { it.exerciseId }
            .toSet()
        assertTrue(candidates.all { it.primaryMuscleGroup == MuscleGroup.QUADRICEPS || it.id in quadricepsAsPrimary })
    }

    @Test
    fun customExercise_isDeletedWhenUnused_hiddenWhenUsed() = runTest {
        val unused = customExercise(newCustomExerciseId("11111111-1111-1111-1111-111111111111"), "Pressa Panatta")
        dao.insertCustomExercise(unused, listOf(ExerciseMuscleEntity(unused.id, Muscle.QUADRICEPS, role = MuscleRole.PRIMARY)))
        assertEquals(CustomExerciseRemoval.DELETED, dao.deleteOrHideCustomExercise(unused.id, T0))
        assertEquals(null, dao.getById(unused.id))

        val used = customExercise(newCustomExerciseId("22222222-2222-2222-2222-222222222222"), "Pendulum squat")
        dao.insertCustomExercise(used, listOf(ExerciseMuscleEntity(used.id, Muscle.QUADRICEPS, role = MuscleRole.PRIMARY)))
        createRoutineWithDay("Gambe", listOf(used.id to 3))
        assertEquals(CustomExerciseRemoval.HIDDEN, dao.deleteOrHideCustomExercise(used.id, T0))
        assertTrue(dao.getById(used.id)!!.isHidden)
        assertFalse(used.id in ids(ExerciseFilter(onlyCustom = true)))
    }

    private fun customExercise(id: String, name: String) = ExerciseEntity(
        id = id,
        name = name,
        nameEn = null,
        aliases = emptyList(),
        searchText = SearchText.build(name, null, emptyList()),
        equipment = Equipment.MACHINE,
        primaryMuscle = Muscle.QUADRICEPS,
        trackingType = ExerciseTrackingType.WEIGHT_REPS,
        mechanic = null,
        force = null,
        level = DifficultyLevel.BEGINNER,
        isUnilateral = false,
        instructions = emptyList(),
        keyPoints = emptyList(),
        commonMistakes = emptyList(),
        supportsMachineSetup = true,
        setupParamHints = listOf("Altezza sedile"),
        defaultRestSeconds = 120,
        isCustom = true,
        catalogVersion = 0,
        createdAt = T0,
        updatedAt = T0,
    )
}
