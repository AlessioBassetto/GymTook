package com.example.sport.core.database

import com.example.sport.core.database.converter.Converters
import com.example.sport.core.database.dao.buildPrefilledSets
import com.example.sport.core.database.entity.ExerciseSetEntity
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.normalized
import com.example.sport.core.model.catalog.CatalogExerciseDto
import com.example.sport.core.model.catalog.ExerciseCatalogDto
import com.example.sport.core.database.seed.defaultRestSecondsFor
import com.example.sport.core.model.catalog.validate
import com.example.sport.core.database.util.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Test JVM puri (nessun database). */
class DataUtilsTest {

    private val converters = Converters()

    @Test
    fun searchText_normalizesCaseAccentsAndPunctuation() {
        assertEquals("alzate laterali manubri", SearchText.normalize("  Alzate Laterali (Manubri) "))
        assertEquals("piu facile e gia", SearchText.normalize("Più facile è già"))
        assertEquals(listOf("panca", "piana"), SearchText.tokenize("Panca, PIANA panca"))
        assertEquals("100\\%\\_", SearchText.escapeLike("100%_"))
        assertEquals(
            "panca piana | bench press | distensioni",
            SearchText.build("Panca piana", "Bench Press", listOf("Distensioni", "bench press")),
        )
    }

    @Test
    fun machineSetupParams_areCleanedBeforeSaving() {
        val input = listOf(
            MachineSetupParam(" Altezza sedile ", " 4 "),
            MachineSetupParam("Schienale", ""),
            MachineSetupParam("", "3"),
            MachineSetupParam("Rullo", "2"),
            MachineSetupParam("altezza SEDILE", "5"),
        )
        assertEquals(
            listOf(MachineSetupParam("Rullo", "2"), MachineSetupParam("altezza SEDILE", "5")),
            input.normalized(),
        )
    }

    @Test
    fun converters_roundTrip() {
        val params = listOf(MachineSetupParam("Altezza sedile", "4"), MachineSetupParam("Schienale", "foro \"2\""))
        assertEquals(params, converters.jsonToSetupParams(converters.setupParamsToJson(params)))
        assertEquals(listOf("a", "b, c"), converters.jsonToStringList(converters.stringListToJson(listOf("a", "b, c"))))
        assertEquals(DayOfWeek.SUNDAY, converters.isoToDayOfWeek(converters.dayOfWeekToIso(DayOfWeek.SUNDAY)))
        assertEquals(7, converters.dayOfWeekToIso(DayOfWeek.SUNDAY))
        val date = LocalDate.of(2026, 11, 9)
        assertEquals(date, converters.epochDayToLocalDate(converters.localDateToEpochDay(date)))
        assertEquals(LocalTime.of(13, 15), converters.secondOfDayToLocalTime(converters.localTimeToSecondOfDay(LocalTime.of(13, 15))))
        assertEquals(T0, converters.epochMillisToInstant(converters.instantToEpochMillis(T0)))
    }

    @Test
    fun prefilledSets_followPreviousSessionSetBySet() {
        val previous = listOf(
            ExerciseSetEntity(sessionExerciseId = 1, setIndex = 0, weightKg = 80.0, reps = 8),
            ExerciseSetEntity(sessionExerciseId = 1, setIndex = 1, weightKg = 85.0, reps = 6),
        )
        val sets = buildPrefilledSets(sessionExerciseId = 9, count = 3, previous = previous, fallbackReps = 10)
        assertEquals(listOf(80.0, 85.0, 85.0), sets.map { it.weightKg })
        assertEquals(listOf(8, 6, 6), sets.map { it.reps })
        assertTrue(sets.all { it.sessionExerciseId == 9L && !it.isCompleted })

        val firstTime = buildPrefilledSets(sessionExerciseId = 9, count = 2, previous = emptyList(), fallbackReps = 10)
        assertEquals(listOf(10, 10), firstTime.map { it.reps })
        assertNull(firstTime.first().weightKg)
    }

    @Test
    fun prefilledTreadmillSets_recallInclineAndTime_butNotALegacyDistance() {
        // Serie del tapis registrata prima della v5 (distanza e tempo) e una nuova (pendenza e tempo).
        val previous = listOf(
            ExerciseSetEntity(sessionExerciseId = 1, setIndex = 0, durationSeconds = 1800, distanceMeters = 3000.0),
            ExerciseSetEntity(sessionExerciseId = 1, setIndex = 1, durationSeconds = 600, inclinePercent = 12.5),
        )
        val sets = buildPrefilledSets(9, count = 2, previous = previous, fallbackReps = null, trackingType = ExerciseTrackingType.INCLINE_TIME)
        assertEquals(listOf(1800, 600), sets.map { it.durationSeconds })
        assertEquals(listOf(null, 12.5), sets.map { it.inclinePercent })
        assertTrue("La distanza non si vede nel logger: non va ricopiata", sets.all { it.distanceMeters == null })

        val running = buildPrefilledSets(9, count = 1, previous = previous, fallbackReps = null, trackingType = ExerciseTrackingType.DISTANCE_DURATION)
        assertEquals(3000.0, running.single().distanceMeters!!, 0.0)
    }

    @Test
    fun defaultRest_dependsOnMechanicAndTracking() {
        assertEquals(150, defaultRestSecondsFor(Mechanic.COMPOUND, ExerciseTrackingType.WEIGHT_REPS))
        assertEquals(90, defaultRestSecondsFor(Mechanic.ISOLATION, ExerciseTrackingType.WEIGHT_REPS))
        assertEquals(60, defaultRestSecondsFor(null, ExerciseTrackingType.DURATION))
    }

    @Test
    fun catalogValidation_reportsAllProblems() {
        val broken = ExerciseCatalogDto(
            version = 1,
            exercises = listOf(
                CatalogExerciseDto(id = "dup", name = "A", equipment = Equipment.MACHINE, primaryMuscles = listOf(Muscle.CALVES)),
                CatalogExerciseDto(id = "dup", name = "B", equipment = Equipment.MACHINE, primaryMuscles = emptyList()),
                CatalogExerciseDto(
                    id = "custom_x",
                    name = " ",
                    equipment = Equipment.CABLE,
                    primaryMuscles = listOf(Muscle.BICEPS),
                    secondaryMuscles = listOf(Muscle.BICEPS),
                ),
            ),
        )
        val message = try {
            broken.validate()
            null
        } catch (error: IllegalStateException) {
            error.message
        }!!
        listOf("id duplicati", "nessun muscolo primario", "riservato", "nome vuoto", "muscoli ripetuti").forEach {
            assertTrue("Manca il problema '$it' in:\n$message", it in message)
        }
    }
}
