package com.example.sport.tools.catalog

import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.MediaKind
import com.example.sport.core.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxonomyMapperTest {

    private val mapper = TaxonomyMapper()

    private fun source(
        name: String,
        equipment: String? = "dumbbell",
        primary: List<String> = listOf("chest"),
        secondary: List<String> = emptyList(),
        category: String = "strength",
        force: String? = "push",
    ) = SourceExercise(
        id = name.replace(" ", "_"),
        name = name,
        force = force,
        level = "beginner",
        mechanic = "compound",
        equipment = equipment,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        instructions = listOf("Do it.", "  "),
        category = category,
        images = listOf("${name.replace(" ", "_")}/0.jpg", "${name.replace(" ", "_")}/1.jpg"),
    )

    private fun primaryOf(name: String, vararg muscles: String, equipment: String? = "dumbbell") =
        mapper.map(source(name, equipment = equipment, primary = muscles.toList())).exercise!!.primaryMuscles

    @Test
    fun chestRegion_followsBenchAngle_andIsInvertedForPushUps() {
        assertEquals(listOf(Muscle.CHEST_UPPER), primaryOf("Incline Dumbbell Press", "chest"))
        assertEquals(listOf(Muscle.CHEST_LOWER), primaryOf("Decline Dumbbell Bench Press", "chest"))
        assertEquals(listOf(Muscle.CHEST_MIDDLE), primaryOf("Dumbbell Bench Press", "chest"))
        assertEquals(listOf(Muscle.CHEST_LOWER), primaryOf("Incline Push-Up", "chest", equipment = "body only"))
        assertEquals(listOf(Muscle.CHEST_UPPER), primaryOf("Decline Push-Up", "chest", equipment = "body only"))
    }

    @Test
    fun shoulderAndAbsRegions_areInferredFromTheName() {
        assertEquals(listOf(Muscle.SHOULDERS_SIDE), primaryOf("Side Lateral Raise", "shoulders"))
        assertEquals(listOf(Muscle.SHOULDERS_REAR), primaryOf("Reverse Flyes", "shoulders"))
        assertEquals(listOf(Muscle.SHOULDERS_FRONT), primaryOf("Arnold Dumbbell Press", "shoulders"))
        assertEquals(listOf(Muscle.OBLIQUES), primaryOf("Russian Twist", "abdominals"))
        assertEquals(listOf(Muscle.ABS_LOWER), primaryOf("Hanging Leg Raise", "abdominals"))
        assertEquals(listOf(Muscle.ABS_UPPER), primaryOf("Sit-Up", "abdominals"))
        assertEquals(listOf(Muscle.UPPER_BACK), primaryOf("Bent Over Two-Dumbbell Row", "middle back"))
    }

    @Test
    fun treadmillWalking_tracksInclineAndTime_whileRunningKeepsDistance() {
        fun tracking(name: String) = mapper.trackingTypeOf(
            source(name, equipment = "machine", primary = listOf("quadriceps"), category = "cardio"),
            name.lowercase(),
            Equipment.CARDIO_MACHINE,
        )
        assertEquals(ExerciseTrackingType.INCLINE_TIME, tracking("Walking, Treadmill"))
        assertEquals(ExerciseTrackingType.DISTANCE_DURATION, tracking("Running, Treadmill"))
        assertEquals(ExerciseTrackingType.DISTANCE_DURATION, tracking("Jogging, Treadmill"))
    }

    @Test
    fun secondaryMuscles_excludeDuplicatesOfPrimary() {
        val mapped = mapper.map(
            source("Dumbbell Bench Press", primary = listOf("chest"), secondary = listOf("chest", "triceps", "shoulders")),
        ).exercise!!
        assertEquals(listOf(Muscle.CHEST_MIDDLE), mapped.primaryMuscles)
        assertEquals(listOf(Muscle.TRICEPS, Muscle.SHOULDERS_FRONT), mapped.secondaryMuscles)
    }

    @Test
    fun cardioMachines_trackDistance_andTargetCardio() {
        val treadmill = mapper.map(
            source("Running, Treadmill", equipment = "machine", primary = listOf("quadriceps"), category = "cardio"),
        ).exercise!!
        assertEquals(Equipment.CARDIO_MACHINE, treadmill.equipment)
        assertEquals(ExerciseTrackingType.DISTANCE_DURATION, treadmill.trackingType)
        assertEquals(listOf(Muscle.CARDIO), treadmill.primaryMuscles)
        assertEquals(listOf(Muscle.QUADRICEPS), treadmill.secondaryMuscles)
    }

    @Test
    fun category_separatesPowerMovementsFromStrength() {
        fun categoryOf(name: String, category: String) =
            mapper.map(source(name, primary = listOf("hamstrings"), category = category)).exercise!!.category
        assertEquals("Classificato 'strength' nel dataset", ExerciseCategory.OLYMPIC, categoryOf("Power Clean", "strength"))
        assertEquals(ExerciseCategory.OLYMPIC, categoryOf("Hang Snatch", "olympic weightlifting"))
        assertEquals(ExerciseCategory.OLYMPIC, categoryOf("Split Jerk", "olympic weightlifting"))
        assertEquals(ExerciseCategory.OLYMPIC, categoryOf("Kettlebell Hang Clean", "strength"))
        assertEquals("Accessorio lento", ExerciseCategory.STRENGTH, categoryOf("Romanian Deadlift from Deficit", "olympic weightlifting"))
        assertEquals("Tirata lenta", ExerciseCategory.STRENGTH, categoryOf("Clean Deadlift", "olympic weightlifting"))
        assertEquals("Indica solo la presa", ExerciseCategory.STRENGTH, categoryOf("Front Squat (Clean Grip)", "strength"))
        assertEquals(ExerciseCategory.PLYOMETRIC, categoryOf("Front Box Jump", "plyometrics"))
        assertEquals(ExerciseCategory.CARDIO, categoryOf("Jogging, Treadmill", "cardio"))
        assertEquals("Il powerlifting resta forza", ExerciseCategory.STRENGTH, categoryOf("Box Squat", "powerlifting"))
    }

    @Test
    fun equipmentTrackingAndSetupHints() {
        val smith = mapper.map(source("Smith Machine Incline Bench Press", equipment = "machine")).exercise!!
        assertEquals(Equipment.SMITH_MACHINE, smith.equipment)
        assertEquals(listOf("Inclinazione panca", "Altezza fermi di sicurezza"), smith.setupParams)

        val plank = mapper.map(source("Plank", equipment = "body only", primary = listOf("abdominals"), force = "static")).exercise!!
        assertEquals(ExerciseTrackingType.DURATION, plank.trackingType)

        val pushUp = mapper.map(source("Pushups", equipment = "body only")).exercise!!
        assertEquals(ExerciseTrackingType.BODYWEIGHT_REPS, pushUp.trackingType)
        assertTrue(pushUp.setupParams.isEmpty())

        val legPress = mapper.map(source("Narrow Stance Leg Press", equipment = "machine", primary = listOf("quadriceps"))).exercise!!
        assertEquals(listOf("Inclinazione schienale", "Posizione piedi sulla pedana"), legPress.setupParams)

        assertTrue(mapper.map(source("One-Arm Dumbbell Row", primary = listOf("lats"))).exercise!!.unilateral)
        assertFalse(mapper.map(source("Dumbbell Bench Press")).exercise!!.unilateral)
    }

    @Test
    fun contentAndMedia_areCarriedOverWithLicense() {
        val mapped = mapper.map(source("Dumbbell Bench Press")).exercise!!
        assertEquals("dumbbell_bench_press", mapped.id)
        assertEquals(listOf("Do it."), mapped.instructions)
        assertEquals("en", mapped.contentLocale)
        assertEquals(listOf("exercises/Dumbbell_Bench_Press/0.jpg", "exercises/Dumbbell_Bench_Press/1.jpg"), mapped.media.map { it.path })
        assertTrue(mapped.media.all { it.kind == MediaKind.IMAGE_FRAME && it.license == "Unlicense" })
        assertEquals("free-exercise-db:Dumbbell_Bench_Press", mapped.origin)
    }

    @Test
    fun unknownPrimaryMuscle_excludesTheExerciseWithAWarning() {
        val mapped = mapper.map(source("Mystery Move", primary = listOf("spleen")))
        assertNull(mapped.exercise)
        assertTrue(mapped.warnings.any { "spleen" in it })
    }

    @Test
    fun slug_isStableAndValid() {
        assertEquals("barbell_bench_press_medium_grip", TaxonomyMapper.slugOf("Barbell_Bench_Press_-_Medium_Grip"))
        assertEquals("3_4_sit_up", TaxonomyMapper.slugOf("3_4_Sit-Up"))
    }
}
