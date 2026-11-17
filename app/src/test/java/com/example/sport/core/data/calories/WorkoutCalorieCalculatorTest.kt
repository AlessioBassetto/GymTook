package com.example.sport.core.data.calories

import com.example.sport.core.model.BiologicalSex
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.ForceType
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * Il motore delle calorie è puro Kotlin: qui si verificano le formule (Mifflin-St Jeor, ACSM,
 * lavoro meccanico), le quote di peso corporeo e la plausibilità dei totali rispetto al
 * Compendium of Physical Activities.
 */
class WorkoutCalorieCalculatorTest {

    private val calculator = WorkoutCalorieCalculator()
    private val athlete = BodyMetrics(weightKg = 80.0, heightCm = 180.0, ageYears = 30, sex = BiologicalSex.MALE)

    private fun estimate(body: BodyMetrics, minutes: Long, sets: List<CalorieSet>) =
        calculator.estimate(body, CalorieSession(Duration.ofMinutes(minutes), sets))!!

    private fun sets(exercise: CalorieExercise, count: Int, weightKg: Double?, reps: Int, restSeconds: Int = 90, rpe: Double? = null) =
        List(count) { CalorieSet(exercise, weightKg = weightKg, reps = reps, plannedRestSeconds = restSeconds, rpe = rpe) }

    /** Petto, schiena, spalle e braccia in un'ora: 20 serie da 10-12 ripetizioni. */
    private fun upperBodySession() =
        sets(BENCH, 4, 80.0, 10, restSeconds = 180) +
            sets(ROW, 4, 70.0, 10, restSeconds = 120) +
            sets(MILITARY_PRESS, 3, 40.0, 10, restSeconds = 120) +
            sets(LATERAL_RAISE, 3, 10.0, 12, restSeconds = 60) +
            sets(CURL, 3, 14.0, 10, restSeconds = 60) +
            sets(PUSHDOWN, 3, 30.0, 12, restSeconds = 60)

    // ------------------------------------------------------------------ Metabolismo e antropometria

    @Test
    fun restingMetabolicRate_followsMifflinStJeor() {
        // 10 × 80 + 6,25 × 180 − 5 × 30 + 5 = 1780 kcal/giorno.
        assertEquals(1780.0, calculator.restingMetabolicRate(athlete), 1e-9)
        assertEquals(1614.0, calculator.restingMetabolicRate(athlete.copy(sex = BiologicalSex.FEMALE)), 1e-9)
        assertEquals("Sesso non indicato: media delle costanti", 1697.0, calculator.restingMetabolicRate(athlete.copy(sex = null)), 1e-9)
        assertEquals("Età non indicata: 30 anni", 1780.0, calculator.restingMetabolicRate(athlete.copy(ageYears = null)), 1e-9)
        // Senza altezza: 1 MET = 0,0175 kcal/kg/min per 24 ore.
        assertEquals(0.0175 * 80 * 1440, calculator.restingMetabolicRate(BodyMetrics(80.0)), 1e-9)
    }

    @Test
    fun bodySize_sexAndAge_scaleTheEstimate() {
        val session = upperBodySession()
        val reference = estimate(athlete, 60, session).totalKcal
        assertTrue("Più pesante", estimate(athlete.copy(weightKg = 95.0), 60, session).totalKcal > reference)
        assertTrue("Più alto: metabolismo e corsa dei movimenti", estimate(athlete.copy(heightCm = 192.0), 60, session).totalKcal > reference)
        assertTrue("Donna, stesso peso", estimate(athlete.copy(sex = BiologicalSex.FEMALE), 60, session).totalKcal < reference)
        assertTrue("Più anziano", estimate(athlete.copy(ageYears = 60), 60, session).totalKcal < reference)
    }

    // ------------------------------------------------------------------ Plausibilità

    @Test
    fun typicalSession_landsInTheCompendiumRange() {
        val result = estimate(athlete, 60, upperBodySession())
        // Compendium: allenamento con i pesi 3,5 MET (moderato) – 6 MET (vigoroso) → 280–480 kcal/h a 80 kg.
        // Con il metabolismo reale (Mifflin) il valore moderato scende a ~260.
        assertTrue("Stima ${result.totalKcal} kcal", result.totalKcal in 220..330)
        assertEquals(
            "Il totale è la somma delle componenti",
            result.totalKcal.toDouble(),
            result.restingKcal + result.activeKcal + result.mechanicalWorkKcal + result.recoveryKcal,
            0.5,
        )
        assertEquals("Un'ora intera conteggiata", 3600.0, result.activeSeconds + result.recoverySeconds, 1e-6)
    }

    @Test
    fun heavyCompoundLegs_burnMoreThanArmIsolation_inTheSameTime() {
        val legs = estimate(athlete, 45, sets(SQUAT, 5, 120.0, 6, restSeconds = 180) + sets(LEG_PRESS, 4, 200.0, 12, restSeconds = 120))
        val arms = estimate(athlete, 45, sets(CURL, 5, 14.0, 6, restSeconds = 180) + sets(PUSHDOWN, 4, 30.0, 12, restSeconds = 120))
        assertTrue("Gambe ${legs.totalKcal} vs braccia ${arms.totalKcal}", legs.totalKcal > arms.totalKcal * 1.4)
    }

    @Test
    fun metValues_differBetweenCompoundAndIsolation() {
        assertEquals(EnergyClass.LOWER_BODY_COMPOUND, ExerciseEnergyProfiles.of(SQUAT).energyClass)
        assertEquals(EnergyClass.UPPER_BODY_COMPOUND, ExerciseEnergyProfiles.of(BENCH).energyClass)
        assertEquals(EnergyClass.ISOLATION, ExerciseEnergyProfiles.of(CURL).energyClass)
        assertEquals(EnergyClass.SMALL_MUSCLE, ExerciseEnergyProfiles.of(CRUNCH).energyClass)
        assertEquals(EnergyClass.CARDIO, ExerciseEnergyProfiles.of(TREADMILL).energyClass)
        assertEquals(EnergyClass.ISOMETRIC, ExerciseEnergyProfiles.of(PLANK).energyClass)
        val order = listOf(EnergyClass.LOWER_BODY_COMPOUND, EnergyClass.UPPER_BODY_COMPOUND, EnergyClass.ISOLATION, EnergyClass.SMALL_MUSCLE)
        assertEquals(order, order.sortedByDescending { it.activeMet })
        assertEquals(order, order.sortedByDescending { it.recoveryMet })
    }

    // ------------------------------------------------------------------ Corpo libero e carichi

    @Test
    fun bodyweightExercises_useABiomechanicalShareOfBodyWeight() {
        fun load(exercise: CalorieExercise, weightKg: Double?) =
            calculator.effectiveLoadKg(CalorieSet(exercise, weightKg = weightKg, reps = 8), ExerciseEnergyProfiles.of(exercise), 80.0)

        assertEquals("Trazioni a 0 kg: tutto il corpo", 80.0, load(PULL_UP, 0.0), 1e-9)
        assertEquals("Dip: tutto il corpo", 80.0, load(DIP, null), 1e-9)
        assertEquals("Piegamenti: ~65%", 52.0, load(PUSH_UP, 0.0), 1e-9)
        assertEquals("Squat a corpo libero: ~50%", 40.0, load(BODYWEIGHT_SQUAT, 0.0), 1e-9)
        assertEquals("Trazioni zavorrate: corpo + zavorra", 90.0, load(PULL_UP, 10.0), 1e-9)
        assertEquals("Lat machine assistita: il contrappeso aiuta", 50.0, load(ASSISTED_PULL_UP, 30.0), 1e-9)
        assertEquals("Squat con bilanciere: si solleva anche il busto", 140.0, load(SQUAT, 100.0), 1e-9)
        assertEquals("Panca: il corpo resta fermo", 80.0, load(BENCH, 80.0), 1e-9)
    }

    @Test
    fun zeroKgBodyweightSets_stillProduceMechanicalWork() {
        val pullUps = estimate(athlete, 10, sets(PULL_UP, 3, 0.0, 8))
        assertTrue(pullUps.mechanicalWorkKcal > 0)
        // 80 kg × 9,81 × 0,50 m × (180/175) × 24 ripetizioni × 1,3 / 0,20 / 4184 ≈ 15 kcal.
        assertEquals(80 * 9.81 * 0.50 * (180.0 / 175.0) * 24 * 1.3 / 0.20 / 4184, pullUps.mechanicalWorkKcal, 1e-6)
    }

    @Test
    fun dumbbellsCountBothHands_andOneSidedSetsCountBothSides() {
        assertEquals(2, ExerciseEnergyProfiles.of(CURL).implements)
        val oneArmRow = CalorieExercise(
            "one_arm_dumbbell_row", "Rematore con manubrio", "One-Arm Dumbbell Row", Equipment.DUMBBELL,
            Muscle.LATS, ExerciseTrackingType.WEIGHT_REPS, mechanic = Mechanic.COMPOUND, isUnilateral = true,
        )
        with(ExerciseEnergyProfiles.of(oneArmRow)) {
            assertEquals(1, implements)
            assertEquals(2, sides)
        }
        val goblet = CalorieExercise(
            "goblet_squat", "Goblet squat", "Goblet Squat", Equipment.DUMBBELL, Muscle.QUADRICEPS,
            ExerciseTrackingType.WEIGHT_REPS, mechanic = Mechanic.COMPOUND,
        )
        assertEquals("Un solo manubrio", 1, ExerciseEnergyProfiles.of(goblet).implements)
    }

    // ------------------------------------------------------------------ Tempo e intensità

    @Test
    fun activeTimeAndRecovery_areSeparated() {
        // 3 × 10 panca in 10 minuti: 3 × 10 × 3,5 s sotto tensione, il resto è recupero.
        val result = estimate(athlete, 10, sets(BENCH, 3, 80.0, 10))
        assertEquals(105.0, result.activeSeconds, 1e-9)
        assertEquals(495.0, result.recoverySeconds, 1e-9)
    }

    @Test
    fun aWorkoutLeftOpenForHours_countsOnlyAPlausibleDuration() {
        val session = sets(BENCH, 3, 80.0, 10, restSeconds = 120)
        val forgotten = estimate(athlete, 10 * 60, session)
        // 105 s attivi + 3 × 240 s di recupero massimo + 15 minuti di transizioni.
        assertEquals(105.0 + 3 * 240 + 900, forgotten.activeSeconds + forgotten.recoverySeconds, 1e-9)
        assertTrue("Dieci ore a riposo varrebbero oltre 700 kcal", forgotten.totalKcal < 150)
    }

    @Test
    fun aRunLoggedAfterTheFact_keepsItsWholeDuration() {
        val run = CalorieSet(TREADMILL, durationSeconds = 30 * 60, distanceMeters = 5000.0)
        val result = calculator.estimate(athlete, CalorieSession(Duration.ofMinutes(2), listOf(run)))!!
        assertEquals(1800.0, result.activeSeconds, 1e-9)
    }

    @Test
    fun higherEffort_costsMore_andRirMatchesRpe() {
        val easy = estimate(athlete, 30, sets(SQUAT, 4, 100.0, 8, rpe = 6.0))
        val hard = estimate(athlete, 30, sets(SQUAT, 4, 100.0, 8, rpe = 10.0))
        val zeroInReserve = estimate(athlete, 30, List(4) { CalorieSet(SQUAT, weightKg = 100.0, reps = 8, rir = 0) })
        assertTrue(hard.totalKcal > easy.totalKcal)
        assertEquals(hard.totalKcal, zeroInReserve.totalKcal)
        assertEquals(0.85, calculator.intensityFactor(CalorieSet(SQUAT, rpe = 3.0)), 1e-9)
        assertEquals("Cedimento senza RPE = RPE 10", 1.125, calculator.intensityFactor(CalorieSet(SQUAT, setType = SetType.FAILURE)), 1e-9)
        assertTrue(calculator.intensityFactor(CalorieSet(SQUAT, setType = SetType.WARMUP)) < 1.0)
    }

    // ------------------------------------------------------------------ Cardio e isometrie

    @Test
    fun running_followsTheAcsmEquation() {
        // 10 km in 50 minuti = 200 m/min → VO₂ = 3,5 + 0,2 × 200 = 43,5 ml/kg/min ≈ 12,4 MET.
        val run = CalorieSet(TREADMILL, durationSeconds = 50 * 60, distanceMeters = 10_000.0)
        val result = calculator.estimate(athlete, CalorieSession(Duration.ofMinutes(50), listOf(run)))!!
        val met = 43.5 / 3.5
        val expected = 1780.0 / 1440 * 50 + (met - 1) * 0.0175 * 80 * 50
        assertEquals(expected, result.restingKcal + result.activeKcal, 1e-6)
        // Regola pratica della corsa: ~1 kcal per kg per km di costo netto.
        assertEquals(800.0, result.activeKcal, 20.0)
    }

    @Test
    fun plank_countsTimeButNoMechanicalWork() {
        val result = estimate(athlete, 5, listOf(CalorieSet(PLANK, durationSeconds = 60), CalorieSet(PLANK, durationSeconds = 60)))
        assertEquals(120.0, result.activeSeconds, 1e-9)
        assertEquals(0.0, result.mechanicalWorkKcal, 0.0)
    }

    @Test
    fun noSets_noEstimate() {
        assertNull(calculator.estimate(athlete, CalorieSession(Duration.ofMinutes(30), emptyList())))
    }

    // ------------------------------------------------------------------ Tapis roulant in salita

    @Test
    fun acsmGradeTerm_isTheInclineMultiplier() {
        // Camminata 80 m/min al 10%: 3,5 + 0,1 × 80 + 1,8 × 80 × 0,10 = 25,9 ml/kg/min.
        assertEquals(25.9, calculator.runOrWalkVo2(80.0, grade = 0.10), 1e-9)
        assertEquals("In piano resta l'equazione orizzontale", 11.5, calculator.runOrWalkVo2(80.0), 1e-9)
        // Corsa 160 m/min al 5%: 3,5 + 0,2 × 160 + 0,9 × 160 × 0,05 = 42,7.
        assertEquals(42.7, calculator.runOrWalkVo2(160.0, grade = 0.05), 1e-9)
    }

    @Test
    fun inclineWalk_usesBodyWeightExactTimeAndGrade() {
        // 30 minuti al 12% a 5 km/h (83,3 m/min): VO₂ = 3,5 + 8,33 + 1,8 × 83,3 × 0,12 = 29,83 → 8,52 MET.
        val walk = CalorieSet(INCLINE_WALK, durationSeconds = 30 * 60, inclinePercent = 12.0)
        val result = calculator.estimate(athlete, CalorieSession(Duration.ofMinutes(30), listOf(walk)))!!
        val metersPerMinute = 5000.0 / 60
        val met = (3.5 + 0.1 * metersPerMinute + 1.8 * metersPerMinute * 0.12) / 3.5
        assertEquals((met - 1) * 0.0175 * 80 * 30, result.activeKcal, 1e-6)
        assertEquals(1800.0, result.activeSeconds, 1e-9)
        assertEquals(0.0, result.mechanicalWorkKcal, 0.0)
        assertEquals(353, result.totalKcal)
    }

    @Test
    fun inclineWalk_scalesWithGradeTimeAndWeight_notWithRpe() {
        fun kcal(incline: Double, minutes: Int, body: BodyMetrics = athlete, rpe: Double? = null) = calculator.estimate(
            body,
            CalorieSession(Duration.ofMinutes(minutes.toLong()), listOf(CalorieSet(INCLINE_WALK, durationSeconds = minutes * 60, inclinePercent = incline, rpe = rpe))),
        )!!.totalKcal

        val reference = kcal(10.0, 30)
        assertTrue("Più pendenza", kcal(15.0, 30) > reference)
        assertTrue("In piano costa meno", kcal(0.0, 30) < reference)
        assertTrue("Più tempo", kcal(10.0, 40) > reference)
        assertTrue("Più peso corporeo", kcal(10.0, 30, athlete.copy(weightKg = 95.0)) > reference)
        assertEquals("La pendenza misura già lo sforzo: l'RPE non si somma", reference, kcal(10.0, 30, rpe = 10.0))
        assertEquals("Oltre il 40% si considera il 40%", kcal(40.0, 30), kcal(60.0, 30))
    }

    @Test
    fun inclineRun_usesTheRunningEquation_andAKnownDistanceWins() {
        val run = CalorieSet(INCLINE_RUN, durationSeconds = 20 * 60, inclinePercent = 4.0)
        val result = calculator.estimate(athlete, CalorieSession(Duration.ofMinutes(20), listOf(run)))!!
        // 9 km/h = 150 m/min: 3,5 + 0,2 × 150 + 0,9 × 150 × 0,04 = 38,9 ml/kg/min.
        assertEquals((38.9 / 3.5 - 1) * 0.0175 * 80 * 20, result.activeKcal, 1e-6)

        // Vecchie serie con la distanza (prima della v5 il tapis registrava distanza e tempo).
        val measured = CalorieSet(INCLINE_WALK, durationSeconds = 30 * 60, distanceMeters = 3000.0, inclinePercent = 0.0)
        val flat = calculator.estimate(athlete, CalorieSession(Duration.ofMinutes(30), listOf(measured)))!!
        assertEquals((calculator.runOrWalkVo2(100.0) / 3.5 - 1) * 0.0175 * 80 * 30, flat.activeKcal, 1e-6)
    }

    private companion object {
        fun strength(
            id: String,
            nameEn: String,
            equipment: Equipment,
            muscle: Muscle,
            mechanic: Mechanic,
            tracking: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS,
        ) = CalorieExercise(id, nameEn, nameEn, equipment, muscle, tracking, ExerciseCategory.STRENGTH, mechanic, ForceType.PUSH)

        val BENCH = strength("barbell_bench_press", "Barbell Bench Press", Equipment.BARBELL, Muscle.CHEST_MIDDLE, Mechanic.COMPOUND)
        val ROW = strength("bent_over_barbell_row", "Bent Over Barbell Row", Equipment.BARBELL, Muscle.LATS, Mechanic.COMPOUND)
        val MILITARY_PRESS = strength("standing_military_press", "Standing Military Press", Equipment.BARBELL, Muscle.SHOULDERS_FRONT, Mechanic.COMPOUND)
        val LATERAL_RAISE = strength("side_lateral_raise", "Side Lateral Raise", Equipment.DUMBBELL, Muscle.SHOULDERS_SIDE, Mechanic.ISOLATION)
        val CURL = strength("dumbbell_bicep_curl", "Dumbbell Bicep Curl", Equipment.DUMBBELL, Muscle.BICEPS, Mechanic.ISOLATION)
        val PUSHDOWN = strength("triceps_pushdown", "Triceps Pushdown", Equipment.CABLE, Muscle.TRICEPS, Mechanic.ISOLATION)
        val SQUAT = strength("barbell_back_squat", "Barbell Back Squat", Equipment.BARBELL, Muscle.QUADRICEPS, Mechanic.COMPOUND)
        val LEG_PRESS = strength("leg_press", "Leg Press", Equipment.MACHINE, Muscle.QUADRICEPS, Mechanic.COMPOUND)
        val CRUNCH = strength("crunch", "Crunch", Equipment.BODYWEIGHT, Muscle.ABS_UPPER, Mechanic.ISOLATION, ExerciseTrackingType.BODYWEIGHT_REPS)
        val PULL_UP = strength("pull_up", "Pull-up", Equipment.BODYWEIGHT, Muscle.LATS, Mechanic.COMPOUND, ExerciseTrackingType.BODYWEIGHT_REPS)
        val DIP = strength("chest_dip", "Chest Dip", Equipment.BODYWEIGHT, Muscle.CHEST_LOWER, Mechanic.COMPOUND, ExerciseTrackingType.BODYWEIGHT_REPS)
        val PUSH_UP = strength("push_up", "Push-up", Equipment.BODYWEIGHT, Muscle.CHEST_MIDDLE, Mechanic.COMPOUND, ExerciseTrackingType.BODYWEIGHT_REPS)
        val BODYWEIGHT_SQUAT = strength("bodyweight_squat", "Bodyweight Squat", Equipment.BODYWEIGHT, Muscle.QUADRICEPS, Mechanic.COMPOUND, ExerciseTrackingType.BODYWEIGHT_REPS)
        val ASSISTED_PULL_UP = strength("assisted_pull_up", "Assisted Pull-up", Equipment.MACHINE, Muscle.LATS, Mechanic.COMPOUND, ExerciseTrackingType.ASSISTED_BODYWEIGHT)
        val PLANK = strength("plank", "Plank", Equipment.BODYWEIGHT, Muscle.ABS_UPPER, Mechanic.ISOLATION, ExerciseTrackingType.DURATION)
        val TREADMILL = CalorieExercise(
            "treadmill_running", "Corsa sul tapis roulant", "Treadmill Running", Equipment.CARDIO_MACHINE, Muscle.CARDIO,
            ExerciseTrackingType.DISTANCE_DURATION, ExerciseCategory.CARDIO,
        )
        val INCLINE_WALK = CalorieExercise(
            "walking_treadmill", "Camminata in salita sul tapis roulant", "Walking, Treadmill", Equipment.CARDIO_MACHINE, Muscle.CARDIO,
            ExerciseTrackingType.INCLINE_TIME, ExerciseCategory.CARDIO,
        )
        val INCLINE_RUN = CalorieExercise(
            "custom_incline_run", "Corsa in salita sul tapis", null, Equipment.CARDIO_MACHINE, Muscle.CARDIO,
            ExerciseTrackingType.INCLINE_TIME, ExerciseCategory.CARDIO,
        )
    }
}
