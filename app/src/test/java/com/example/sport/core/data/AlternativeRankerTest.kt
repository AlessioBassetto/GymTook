package com.example.sport.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.workout.TestClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Alternative per la sostituzione al volo, sul catalogo reale. */
@RunWith(AndroidJUnit4::class)
class AlternativeRankerTest : DatabaseTest() {

    private lateinit var repository: ExerciseRepository

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        repository = ExerciseRepository(db.exerciseDao(), db.mediaDao(), TestClock(T0))
    }

    private suspend fun top(exerciseId: String, limit: Int = 8): List<String> =
        repository.alternatives(exerciseId, limit).map { it.id }.also { println("$exerciseId → $it") }

    @Test
    fun romanianDeadlift_suggestsHipHingeVariants_notJumpsOrOlympicLifts() = runTest {
        val ranked = top("romanian_deadlift", limit = 200)
        assertTrue(ranked.take(3).contains("stiff_legged_barbell_deadlift"))
        val explosive = setOf("power_clean", "hang_snatch", "front_box_jump", "box_jump_multiple_response", "lunge_pass_through", "linear_acceleration_wall_drill")
        assertTrue("Niente salti o girate tra i primi suggerimenti: ${ranked.take(8)}", ranked.take(8).none { it in explosive })
        assertTrue("Good morning prima della girata", ranked.indexOf("good_morning") < ranked.indexOf("power_clean"))
        assertTrue("Leg curl prima del box jump", ranked.indexOf("seated_leg_curl") < ranked.indexOf("front_box_jump"))

        val categories = repository.alternatives("romanian_deadlift", limit = 12).map { it.category }
        assertTrue("Solo esercizi di forza tra i primi 12: $categories", categories.all { it == ExerciseCategory.STRENGTH })
    }

    @Test
    fun legExtension_suggestsQuadricepsExercises_withWeightAndReps() = runTest {
        val suggestions = repository.alternatives("leg_extension", 6)
        assertEquals(6, suggestions.size)
        println("leg_extension → ${suggestions.map { it.id }}")
        assertTrue(suggestions.none { it.id == "leg_extension" })
        val metrics = SetMetric.forTracking(db.exerciseDao().getById("leg_extension")!!.trackingType, withRpe = false)
        assertTrue(suggestions.all { SetMetric.forTracking(it.trackingType, withRpe = false) == metrics })
    }

    @Test
    fun favorites_climbTheList() = runTest {
        val before = top("seated_leg_curl", limit = 40)
        val lowest = before.last()
        db.exerciseDao().setFavorite(lowest, favorite = true, updatedAt = T0)
        val after = top("seated_leg_curl", limit = 40)
        assertTrue("Un preferito sale di posizione", after.indexOf(lowest) < before.indexOf(lowest))
    }

    @Test
    fun tokens_ignoreArticlesAndPrepositions() {
        assertEquals(setOf("stacco", "rumeno", "bilanciere"), AlternativeRanker.tokens("Stacco rumeno con bilanciere"))
        assertEquals(setOf("stiff", "legged", "barbell", "deadlift"), AlternativeRanker.tokens("Stiff-Legged Barbell Deadlift"))
    }
}
