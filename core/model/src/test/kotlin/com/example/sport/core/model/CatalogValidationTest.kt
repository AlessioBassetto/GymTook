package com.example.sport.core.model

import com.example.sport.core.model.catalog.CatalogExerciseDto
import com.example.sport.core.model.catalog.CatalogMediaDto
import com.example.sport.core.model.catalog.ExerciseCatalogDto
import com.example.sport.core.model.catalog.isSafeRelativeMediaPath
import com.example.sport.core.model.catalog.problems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogValidationTest {

    @Test
    fun mediaPaths_mustBeRelativeAndConfined() {
        assertTrue(isSafeRelativeMediaPath("exercises/Barbell_Curl/0.jpg"))
        assertTrue(isSafeRelativeMediaPath("videos/v1/hip thrust.mp4"))
        listOf("", "/abs.jpg", "../up.jpg", "a/../../b.jpg", "a//b.jpg", "./a.jpg", "https://x.y/a.jpg", "a\\b.jpg")
            .forEach { assertFalse("'$it' deve essere rifiutato", isSafeRelativeMediaPath(it)) }
    }

    @Test
    fun validCatalog_hasNoProblems() {
        assertEquals(emptyList<String>(), catalog(exercise()).problems())
    }

    @Test
    fun problems_listEveryIssue() {
        val broken = exercise().copy(
            contentLocale = "fr",
            media = listOf(
                CatalogMediaDto(MediaKind.IMAGE_FRAME, "../x.jpg"),
                CatalogMediaDto(MediaKind.IMAGE_FRAME, "../x.jpg"),
            ),
        )
        val problems = catalog(broken).problems().joinToString("\n")
        listOf("contentLocale", "non sicuro", "media duplicati").forEach {
            assertTrue("Manca '$it' in:\n$problems", it in problems)
        }
    }

    @Test
    fun setupParamsNormalization_keepsLastValuePerName() {
        val cleaned = listOf(
            MachineSetupParam(" Sedile ", "3"),
            MachineSetupParam("Schienale", " "),
            MachineSetupParam("sedile", "4"),
        ).normalized()
        assertEquals(listOf(MachineSetupParam("sedile", "4")), cleaned)
    }

    private fun exercise() = CatalogExerciseDto(
        id = "leg_extension",
        name = "Leg extension",
        equipment = Equipment.MACHINE,
        primaryMuscles = listOf(Muscle.QUADRICEPS),
        media = listOf(CatalogMediaDto(MediaKind.IMAGE_FRAME, "exercises/Leg_Extensions/0.jpg")),
    )

    private fun catalog(vararg exercises: CatalogExerciseDto) = ExerciseCatalogDto(version = 1, exercises = exercises.toList())
}
