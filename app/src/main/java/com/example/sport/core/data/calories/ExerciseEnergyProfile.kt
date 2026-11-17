package com.example.sport.core.data.calories

import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.ForceType
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup

/** Anagrafica di un esercizio, così come serve al calcolo calorico (catalogo o personalizzato). */
data class CalorieExercise(
    val id: String,
    /** Nome italiano: per gli esercizi personalizzati è l'unico indizio sul tipo di movimento. */
    val name: String,
    val nameEn: String? = null,
    val equipment: Equipment,
    val primaryMuscle: Muscle,
    val trackingType: ExerciseTrackingType,
    val category: ExerciseCategory = ExerciseCategory.STRENGTH,
    val mechanic: Mechanic? = null,
    val force: ForceType? = null,
    val isUnilateral: Boolean = false,
)

/**
 * Classe di dispendio di un esercizio. Ogni classe ha due coefficienti MET:
 * - [activeMet]: durante la serie, ESCLUSO il lavoro meccanico sul carico (che il calcolatore
 *   aggiunge a parte dalla fisica del sollevamento). Rappresenta stabilizzazione, contrazioni
 *   isometriche, postura e movimento degli arti;
 * - [recoveryMet]: durante il recupero fra le serie. Dopo un multiarticolare pesante la
 *   frequenza cardiaca e il consumo di ossigeno restano alti più a lungo che dopo un curl.
 *
 * Valori ricavati dal Compendium of Physical Activities (Ainsworth 2011, aggiornamento 2024) e
 * calibrati perché una sessione tipica ricada nei suoi valori complessivi: "resistance training,
 * multiple exercises, 8-15 reps" ≈ 3,5 MET, "vigorous effort (powerlifting, bodybuilding)" ≈ 6 MET.
 * [secondsPerRep] è il tempo sotto tensione medio di una ripetizione (eccentrica + concentrica).
 */
enum class EnergyClass(val activeMet: Double, val recoveryMet: Double, val secondsPerRep: Double) {
    /** Squat, stacchi, affondi, pressa, hip thrust: grandi masse muscolari, carichi alti. */
    LOWER_BODY_COMPOUND(activeMet = 5.0, recoveryMet = 2.8, secondsPerRep = 4.0),

    /** Panca, rematori, trazioni, lento avanti, dip. */
    UPPER_BODY_COMPOUND(activeMet = 4.0, recoveryMet = 2.3, secondsPerRep = 3.5),

    /** Monoarticolari di braccia, spalle, petto e gambe (curl, alzate, leg extension, croci). */
    ISOLATION(activeMet = 3.0, recoveryMet = 1.8, secondsPerRep = 3.0),

    /** Distretti piccoli: avambracci, polpacci, collo, addominali. */
    SMALL_MUSCLE(activeMet = 2.5, recoveryMet = 1.6, secondsPerRep = 2.5),

    /** Sollevamento olimpico e pliometria: potenza massima, ripetizioni brevi. */
    EXPLOSIVE(activeMet = 8.0, recoveryMet = 3.0, secondsPerRep = 2.0),

    /** Tenute isometriche a tempo (plank, isometrie): Compendium "calisthenics, moderate" 3,8. */
    ISOMETRIC(activeMet = 3.8, recoveryMet = 1.8, secondsPerRep = 0.0),

    /** Cardio: il MET reale si ricava dalla velocità quando ci sono distanza e durata. */
    CARDIO(activeMet = 7.0, recoveryMet = 2.0, secondsPerRep = 0.0),
}

/** Tipo di attività cardio: sceglie l'equazione (ACSM, Compendium o potenza al vogatore). */
enum class CardioKind {
    RUN_OR_WALK,
    CYCLING,
    ROWING,
    ELLIPTICAL,
    STAIRS,
    JUMP_ROPE,
    SLED,
    OTHER,
}

/**
 * Profilo biomeccanico ed energetico di un esercizio.
 *
 * @param romMeters spostamento verticale del carico in una ripetizione, per una persona alta
 *   [REFERENCE_HEIGHT_CM]: il calcolatore lo scala con l'altezza reale (arti più lunghi = più corsa).
 * @param bodyMassFraction quota del peso corporeo sollevata a ogni ripetizione: 1,0 per trazioni e
 *   dip, ~0,65 per i piegamenti, ~0,5 per lo squat a corpo libero, 0 se il corpo resta fermo (panca,
 *   macchinari). Si somma al carico esterno: uno squat con 100 kg sposta anche il busto.
 * @param implements attrezzi mossi nella ripetizione registrata: i manubri si registrano "da X kg"
 *   ciascuno, quindi una distensione con due manubri da 20 kg sposta 40 kg.
 * @param sides lati allenati per ogni ripetizione registrata: negli esercizi a un braccio o a una
 *   gamba le ripetizioni si contano per lato e si allenano entrambi i lati (2).
 */
data class ExerciseEnergyProfile(
    val energyClass: EnergyClass,
    val romMeters: Double,
    val bodyMassFraction: Double,
    val implements: Int = 1,
    val sides: Int = 1,
    val cardioKind: CardioKind? = null,
) {
    companion object {
        /** Statura di riferimento per le corse di [romMeters] (adulto medio). */
        const val REFERENCE_HEIGHT_CM = 175.0
    }
}

/**
 * Riconosce il tipo di movimento dai dati strutturati del catalogo (tipo di registrazione,
 * categoria, attrezzatura, muscolo principale, mono/multiarticolare) e, per il pattern
 * biomeccanico, da parole chiave del nome (inglese e italiano, così funziona anche con gli
 * esercizi personalizzati). Funzione pura: nessun accesso a database o Android.
 */
object ExerciseEnergyProfiles {

    fun of(exercise: CalorieExercise): ExerciseEnergyProfile {
        val text = keyText(exercise)
        val energyClass = energyClassOf(exercise)
        if (energyClass == EnergyClass.CARDIO) {
            return ExerciseEnergyProfile(energyClass, romMeters = 0.0, bodyMassFraction = 0.0, cardioKind = cardioKindOf(text))
        }
        if (energyClass == EnergyClass.ISOMETRIC) {
            // A tempo non si conta lavoro meccanico: niente si sposta.
            return ExerciseEnergyProfile(energyClass, romMeters = 0.0, bodyMassFraction = 0.0)
        }
        val (rom, bodyFraction) = movementOf(exercise, energyClass, text)
        val oneSideAtATime = exercise.isUnilateral || SINGLE_SIDE.containsMatchIn(text)
        val implements = when {
            oneSideAtATime -> 1
            exercise.equipment == Equipment.DUMBBELL && !SINGLE_IMPLEMENT.containsMatchIn(text) -> 2
            exercise.equipment == Equipment.KETTLEBELL && DOUBLE_KETTLEBELL.containsMatchIn(text) -> 2
            else -> 1
        }
        return ExerciseEnergyProfile(
            energyClass = energyClass,
            romMeters = rom,
            bodyMassFraction = bodyFraction,
            implements = implements,
            sides = if (oneSideAtATime) 2 else 1,
        )
    }

    /** Classe di dispendio: prima il tipo di registrazione e la categoria, poi i muscoli. */
    internal fun energyClassOf(exercise: CalorieExercise): EnergyClass {
        val group = exercise.primaryMuscle.group
        return when {
            exercise.trackingType == ExerciseTrackingType.DISTANCE_DURATION ||
                exercise.category == ExerciseCategory.CARDIO ||
                exercise.equipment == Equipment.CARDIO_MACHINE ||
                exercise.primaryMuscle == Muscle.CARDIO -> EnergyClass.CARDIO
            exercise.trackingType == ExerciseTrackingType.DURATION -> EnergyClass.ISOMETRIC
            exercise.category == ExerciseCategory.OLYMPIC || exercise.category == ExerciseCategory.PLYOMETRIC -> EnergyClass.EXPLOSIVE
            exercise.primaryMuscle == Muscle.FULL_BODY -> EnergyClass.LOWER_BODY_COMPOUND
            group in SMALL_GROUPS -> EnergyClass.SMALL_MUSCLE
            exercise.mechanic == Mechanic.ISOLATION -> EnergyClass.ISOLATION
            group in LOWER_BODY_GROUPS -> EnergyClass.LOWER_BODY_COMPOUND
            else -> EnergyClass.UPPER_BODY_COMPOUND
        }
    }

    internal fun cardioKindOf(text: String): CardioKind = when {
        ROWING.containsMatchIn(text) -> CardioKind.ROWING
        CYCLING.containsMatchIn(text) -> CardioKind.CYCLING
        ELLIPTICAL.containsMatchIn(text) -> CardioKind.ELLIPTICAL
        STAIRS.containsMatchIn(text) -> CardioKind.STAIRS
        JUMP_ROPE.containsMatchIn(text) -> CardioKind.JUMP_ROPE
        SLED.containsMatchIn(text) -> CardioKind.SLED
        RUN_OR_WALK.containsMatchIn(text) -> CardioKind.RUN_OR_WALK
        else -> CardioKind.OTHER
    }

    /**
     * Corsa del carico (m) e quota di peso corporeo sollevata. L'ordine dei controlli conta:
     * i pattern più specifici ("inverted row", "scapular pull-up") vengono prima dei generici.
     */
    private fun movementOf(exercise: CalorieExercise, energyClass: EnergyClass, text: String): Pair<Double, Double> {
        if (exercise.category == ExerciseCategory.OLYMPIC) {
            val rom = when {
                SNATCH.containsMatchIn(text) -> 1.3
                CLEAN_OR_JERK.containsMatchIn(text) -> 1.0
                else -> 0.9
            }
            // Nella tirata e nella ricezione si sposta anche il baricentro del corpo.
            return rom to 0.2
        }
        val usesBand = BAND.containsMatchIn(text) || exercise.equipment == Equipment.RESISTANCE_BAND
        return when {
            BURPEE.containsMatchIn(text) -> 0.8 to 1.0
            INVERTED_ROW.containsMatchIn(text) -> 0.35 to 0.6
            SCAPULAR.containsMatchIn(text) -> 0.06 to 1.0
            PULL_UP.containsMatchIn(text) -> 0.50 to 1.0
            HANDSTAND.containsMatchIn(text) -> 0.40 to 0.9
            DIP.containsMatchIn(text) -> 0.40 to if (BENCH.containsMatchIn(text)) 0.65 else 1.0
            PUSH_UP.containsMatchIn(text) -> 0.35 to when {
                INCLINE.containsMatchIn(text) -> 0.45
                DECLINE.containsMatchIn(text) -> 0.75
                KNEES.containsMatchIn(text) -> 0.5
                else -> 0.65
            }
            JUMP.containsMatchIn(text) -> 0.40 to 1.0
            LEG_PRESS.containsMatchIn(text) -> 0.35 to 0.0
            SQUAT_OR_LUNGE.containsMatchIn(text) -> when {
                LUNGE.containsMatchIn(text) -> 0.45
                STEP_UP.containsMatchIn(text) -> 0.40
                else -> 0.55
            } to 0.5
            HINGE.containsMatchIn(text) -> when {
                SWING.containsMatchIn(text) -> 0.8 to 0.2
                STIFF_LEG.containsMatchIn(text) -> 0.45 to 0.25
                else -> 0.50 to 0.25
            }
            HIP_BRIDGE.containsMatchIn(text) -> 0.25 to if (usesBand) 0.0 else 0.4
            BACK_EXTENSION.containsMatchIn(text) -> 0.40 to 0.5
            CALF.containsMatchIn(text) -> 0.08 to if (SEATED.containsMatchIn(text) || LEG_PRESS.containsMatchIn(text)) 0.0 else 1.0
            LEG_RAISE.containsMatchIn(text) -> 0.45 to 0.3
            CRUNCH.containsMatchIn(text) -> 0.25 to 0.3
            SHRUG.containsMatchIn(text) -> 0.08 to 0.0
            WRIST.containsMatchIn(text) -> 0.06 to 0.0
            else -> defaultMovement(exercise, energyClass, usesBand)
        }
    }

    /** Nessun pattern riconosciuto: valori tipici della classe; il corpo si muove solo a corpo libero. */
    private fun defaultMovement(exercise: CalorieExercise, energyClass: EnergyClass, usesBand: Boolean): Pair<Double, Double> {
        val bodyweight = exercise.equipment == Equipment.BODYWEIGHT && !usesBand
        return when (energyClass) {
            EnergyClass.LOWER_BODY_COMPOUND -> 0.50 to if (bodyweight) 0.5 else 0.0
            EnergyClass.UPPER_BODY_COMPOUND -> 0.45 to if (bodyweight) 0.6 else 0.0
            EnergyClass.ISOLATION -> 0.40 to if (bodyweight) 0.2 else 0.0
            EnergyClass.SMALL_MUSCLE -> 0.15 to if (bodyweight) 0.3 else 0.0
            EnergyClass.EXPLOSIVE -> 0.50 to if (bodyweight) 1.0 else 0.2
            EnergyClass.ISOMETRIC, EnergyClass.CARDIO -> 0.0 to 0.0
        }
    }

    /** id + nome inglese + nome italiano, minuscoli, con spazi al posto di trattini e underscore. */
    internal fun keyText(exercise: CalorieExercise): String =
        listOfNotNull(exercise.id, exercise.nameEn, exercise.name)
            .joinToString(" ")
            .lowercase()
            .replace(NON_WORD, " ")

    private val SMALL_GROUPS = setOf(MuscleGroup.FOREARMS, MuscleGroup.CALVES, MuscleGroup.NECK, MuscleGroup.ABS)
    private val LOWER_BODY_GROUPS = setOf(
        MuscleGroup.QUADRICEPS,
        MuscleGroup.GLUTES,
        MuscleGroup.HAMSTRINGS,
        MuscleGroup.LOWER_BACK,
        MuscleGroup.ADDUCTORS,
        MuscleGroup.ABDUCTORS,
        MuscleGroup.CARDIO_FULL_BODY,
    )

    private val NON_WORD = Regex("[^a-z0-9àèéìòù]+")
    private fun words(pattern: String) = Regex("\\b(?:$pattern)\\b")

    // Cardio
    private val ROWING = words("rowing|rower|row ergometer|vogatore|canottaggio")
    private val CYCLING = words("bike|bicycling|bicycle|cycling|cyclette|spinning|bici\\w*")
    private val ELLIPTICAL = words("elliptical|ellittic\\w*|cross trainer")
    private val STAIRS = words("stair\\w*|step ?mill|scale")
    private val JUMP_ROPE = words("rope jumping|jump rope|skipping rope|corda")
    private val SLED = words("prowler|sled|slitta")
    private val RUN_OR_WALK = words("run\\w*|jog\\w*|sprint\\w*|treadmill|tapis|walk\\w*|camminat\\w*|cors[ao]|skating")

    // Pattern biomeccanici
    private val SNATCH = words("snatch\\w*|strapp\\w*")
    private val CLEAN_OR_JERK = words("clean\\w*|jerk\\w*|girat\\w*|slanc\\w*|thruster\\w*")
    private val BAND = words("bands?|elastic\\w*")
    private val BURPEE = words("burpees?")
    private val INVERTED_ROW = words("inverted rows?|body rows?|australian pull ?ups?|rematore inverso")
    private val SCAPULAR = words("scapular")
    private val PULL_UP = words("pull ?ups?|chin ?ups?|muscle ?ups?|trazion\\w*|rope climb\\w*")
    private val HANDSTAND = words("handstand\\w*|verticale")
    private val DIP = words("dips?|parallele")
    private val BENCH = words("bench|panca")
    private val PUSH_UP = words("push ?ups?|press ?ups?|piegament\\w*|flession\\w*")
    private val INCLINE = words("incline|inclinat\\w*")
    private val DECLINE = words("decline|declinat\\w*|feet elevated|piedi rialzati")
    private val KNEES = words("knees?|kneeling|ginocchi\\w*")
    private val JUMP = words("jumps?|jumping|hops?|bounds?|leaps?|skip|skipping|salt\\w*|balz\\w*")
    private val LEG_PRESS = words("leg press|pressa")
    private val SQUAT_OR_LUNGE = words("squats?|lunges?|affond\\w*|step ?ups?|pistol|sissy")
    private val LUNGE = words("lunges?|affond\\w*|split")
    private val STEP_UP = words("step ?ups?")
    private val HINGE = words("deadlifts?|stacc\\w*|good ?mornings?|romanian|rdl|rack pulls?|pull ?through|swings?")
    private val SWING = words("swings?")
    private val STIFF_LEG = words("romanian|rdl|stiff\\w*|good ?mornings?|gambe tese")
    private val HIP_BRIDGE = words("hip thrusts?|glute bridges?|bridges?|hip (?:raises?|lifts?|extensions?)|butt (?:lifts?|ups?)|ponte")
    private val BACK_EXTENSION = words("hyperextensions?|back extensions?|iperestension\\w*|glute ham\\w*|nordic")
    private val CALF = words("calf|calves|polpacc\\w*")
    private val SEATED = words("seated|sedut\\w*")
    private val LEG_RAISE = words("leg (?:raises?|lifts?|pull ?ins?|tucks?)|knee (?:raises?|tucks?)|tucks?|pike|flutter kicks?|toes to bar|scissors?")
    private val CRUNCH = words("crunch\\w*|sit ?ups?|v ?ups?|jackknife|addominal\\w*|russian twist|heel touch\\w*|elbow to knee|cocoons?")
    private val SHRUG = words("shrugs?|scrollat\\w*")
    private val WRIST = words("wrist|polsi|polso|grip|squeeze")

    // Attrezzi e lati
    private val SINGLE_SIDE = words("(?:one|single) (?:arm|leg|legged)|alternat\\w*|unilateral\\w*|monolateral\\w*|un braccio|una gamba")
    private val SINGLE_IMPLEMENT = words("goblet|pullover|single dumbbell|one dumbbell|un manubrio")
    private val DOUBLE_KETTLEBELL = words("(?:double|two) kettlebells?|due kettlebell")
}
