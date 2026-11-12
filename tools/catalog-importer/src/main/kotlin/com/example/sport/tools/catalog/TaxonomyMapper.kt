package com.example.sport.tools.catalog

import com.example.sport.core.model.DifficultyLevel
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.ForceType
import com.example.sport.core.model.MediaKind
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.catalog.CatalogExerciseDto
import com.example.sport.core.model.catalog.CatalogMediaDto

/** Esito della mappatura: [exercise] è null se l'esercizio non è utilizzabile (vedi [warnings]). */
data class MappedExercise(
    val exercise: CatalogExerciseDto?,
    val warnings: List<String>,
)

/** Quali voci del dataset entrano nel catalogo di un'app da palestra. */
object SourceFilter {
    val excludedCategories = setOf("stretching", "strongman")
    val excludedEquipment = setOf("foam roll")

    /** Esclusi anche gli esercizi senza istruzioni: una scheda senza esecuzione non è utile. */
    fun accept(source: SourceExercise): Boolean =
        source.category !in excludedCategories &&
            source.equipment !in excludedEquipment &&
            source.instructions.any { it.isNotBlank() }
}

/**
 * Converte la tassonomia di free-exercise-db (17 muscoli generici, 12 attrezzi) nella tassonomia
 * dell'app (25 regioni muscolari, 10 attrezzi). Le regioni fini (petto alto/basso, deltoide
 * anteriore/laterale/posteriore, addome alto/basso/obliqui) sono dedotte dal nome dell'esercizio.
 *
 * Il contenuto testuale resta in inglese: nomi e traduzioni sono applicati dal [CatalogBuilder].
 */
class TaxonomyMapper {

    fun map(source: SourceExercise): MappedExercise {
        val warnings = mutableListOf<String>()
        val name = source.name.lowercase()
        val equipment = equipmentOf(source, name)
        val isCardio = source.category == "cardio"

        val sourcePrimary = source.primaryMuscles.mapNotNull { regionOf(it, name, source, warnings) }
        val sourceSecondary = source.secondaryMuscles.mapNotNull { regionOf(it, name, source, warnings) }
        val primary = (if (isCardio) listOf(Muscle.CARDIO) else sourcePrimary).distinct()
        val secondary = ((if (isCardio) sourcePrimary else emptyList()) + sourceSecondary)
            .distinct()
            .filterNot { it in primary }

        if (primary.isEmpty()) {
            warnings += "${source.name}: nessun muscolo primario mappabile (${source.primaryMuscles}), escluso"
            return MappedExercise(exercise = null, warnings = warnings)
        }

        val trackingType = trackingTypeOf(source, name, equipment)
        val exercise = CatalogExerciseDto(
            id = slugOf(source.id),
            name = source.name,
            nameEn = source.name,
            equipment = equipment,
            trackingType = trackingType,
            category = categoryOf(source),
            mechanic = when (source.mechanic) {
                "compound" -> Mechanic.COMPOUND
                "isolation" -> Mechanic.ISOLATION
                else -> null
            },
            force = when (source.force) {
                "push" -> ForceType.PUSH
                "pull" -> ForceType.PULL
                "static" -> ForceType.STATIC
                else -> null
            },
            level = when (source.level) {
                "intermediate" -> DifficultyLevel.INTERMEDIATE
                "expert" -> DifficultyLevel.ADVANCED
                else -> DifficultyLevel.BEGINNER
            },
            unilateral = UNILATERAL.containsMatchIn(name),
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            instructions = source.instructions.map { it.trim() }.filter { it.isNotEmpty() },
            setupParams = setupParamsOf(name, equipment),
            contentLocale = CatalogExerciseDto.CONTENT_LOCALE_EN,
            media = mediaOf(source),
            origin = FreeExerciseDb.origin(source),
        )
        return MappedExercise(exercise, warnings)
    }

    fun mediaOf(source: SourceExercise): List<CatalogMediaDto> = source.images.map { image ->
        CatalogMediaDto(
            kind = MediaKind.IMAGE_FRAME,
            path = FreeExerciseDb.mediaPath(image),
            source = FreeExerciseDb.SOURCE_NAME,
            license = FreeExerciseDb.LICENSE_SPDX,
        )
    }

    /**
     * Olimpico = girate, strappi e slanci (anche col kettlebell), riconosciuti dal nome: nel dataset
     * la categoria "olympic weightlifting" include accessori lenti (stacco rumeno in deficit, squat
     * overhead) e il power clean è classificato "strength". Gli stacchi di girata/strappo sono tirate
     * lente: restano forza. Il powerlifting (box squat, board press) è forza.
     */
    fun categoryOf(source: SourceExercise): ExerciseCategory {
        val name = source.name.lowercase()
        return when {
            source.category == "cardio" -> ExerciseCategory.CARDIO
            source.category == "plyometrics" -> ExerciseCategory.PLYOMETRIC
            OLYMPIC_LIFT.containsMatchIn(name) && "deadlift" !in name -> ExerciseCategory.OLYMPIC
            else -> ExerciseCategory.STRENGTH
        }
    }

    internal fun equipmentOf(source: SourceExercise, name: String): Equipment = when {
        "smith" in name -> Equipment.SMITH_MACHINE
        source.category == "cardio" && CARDIO_MACHINE.containsMatchIn(name) -> Equipment.CARDIO_MACHINE
        else -> when (source.equipment) {
            "barbell", "e-z curl bar" -> Equipment.BARBELL
            "dumbbell" -> Equipment.DUMBBELL
            "cable" -> Equipment.CABLE
            "machine" -> Equipment.MACHINE
            "kettlebells" -> Equipment.KETTLEBELL
            "bands" -> Equipment.RESISTANCE_BAND
            null, "", "body only" -> Equipment.BODYWEIGHT
            else -> Equipment.OTHER
        }
    }

    internal fun regionOf(
        group: String,
        name: String,
        source: SourceExercise,
        warnings: MutableList<String>,
    ): Muscle? = when (group) {
        "abdominals" -> when {
            OBLIQUES.containsMatchIn(name) -> Muscle.OBLIQUES
            LOWER_ABS.containsMatchIn(name) -> Muscle.ABS_LOWER
            else -> Muscle.ABS_UPPER
        }
        "chest" -> chestRegion(name)
        "shoulders" -> when {
            REAR_DELT.containsMatchIn(name) -> Muscle.SHOULDERS_REAR
            SIDE_DELT.containsMatchIn(name) -> Muscle.SHOULDERS_SIDE
            else -> Muscle.SHOULDERS_FRONT
        }
        "middle back" -> Muscle.UPPER_BACK
        "lats" -> Muscle.LATS
        "lower back" -> Muscle.LOWER_BACK
        "traps" -> Muscle.TRAPS
        "neck" -> Muscle.NECK
        "biceps" -> Muscle.BICEPS
        "triceps" -> Muscle.TRICEPS
        "forearms" -> Muscle.FOREARMS
        "quadriceps" -> Muscle.QUADRICEPS
        "hamstrings" -> Muscle.HAMSTRINGS
        "glutes" -> Muscle.GLUTES
        "calves" -> Muscle.CALVES
        "adductors" -> Muscle.ADDUCTORS
        "abductors" -> Muscle.ABDUCTORS
        else -> {
            warnings += "${source.name}: muscolo sconosciuto '$group' ignorato"
            null
        }
    }

    /**
     * Nei piegamenti l'inclinazione è invertita rispetto alla panca: mani rialzate (incline)
     * enfatizzano il petto basso, piedi rialzati (decline) il petto alto.
     */
    private fun chestRegion(name: String): Muscle {
        val isPushUp = PUSH_UP.containsMatchIn(name)
        return when {
            isPushUp && ("decline" in name || "feet elevated" in name) -> Muscle.CHEST_UPPER
            isPushUp && "incline" in name -> Muscle.CHEST_LOWER
            "incline" in name || "low cable crossover" in name || "low-to-high" in name -> Muscle.CHEST_UPPER
            "decline" in name || DIP.containsMatchIn(name) -> Muscle.CHEST_LOWER
            else -> Muscle.CHEST_MIDDLE
        }
    }

    internal fun trackingTypeOf(source: SourceExercise, name: String, equipment: Equipment): ExerciseTrackingType = when {
        source.category == "cardio" ->
            if (DISTANCE_CARDIO.containsMatchIn(name)) ExerciseTrackingType.DISTANCE_DURATION else ExerciseTrackingType.DURATION
        STATIC_HOLD.containsMatchIn(name) && (source.force == "static" || "isometric" in name) -> ExerciseTrackingType.DURATION
        "assisted" in name -> ExerciseTrackingType.ASSISTED_BODYWEIGHT
        equipment == Equipment.BODYWEIGHT || equipment == Equipment.RESISTANCE_BAND -> ExerciseTrackingType.BODYWEIGHT_REPS
        equipment == Equipment.OTHER && source.category == "plyometrics" -> ExerciseTrackingType.BODYWEIGHT_REPS
        else -> ExerciseTrackingType.WEIGHT_REPS
    }

    /** Suggerimenti di regolazione: solo quando sono quasi certi, altrimenti l'utente li aggiunge. */
    internal fun setupParamsOf(name: String, equipment: Equipment): List<String> = when (equipment) {
        Equipment.SMITH_MACHINE -> buildList {
            if (BENCH_ANGLE.containsMatchIn(name)) add("Inclinazione panca")
            add("Altezza fermi di sicurezza")
        }
        Equipment.CABLE -> listOf("Altezza carrucola")
        Equipment.MACHINE -> when {
            "leg press" in name -> listOf("Inclinazione schienale", "Posizione piedi sulla pedana")
            "leg extension" in name -> listOf("Schienale", "Rullo tibie")
            "leg curl" in name -> listOf("Rullo caviglie")
            "hack squat" in name -> listOf("Altezza spalliere")
            "calf" in name -> listOf("Altezza spalliere")
            "glute ham" in name -> listOf("Posizione blocca-caviglie")
            SEATED_MACHINE.containsMatchIn(name) -> listOf("Altezza sedile")
            else -> emptyList()
        }
        Equipment.BARBELL -> when {
            "bench press" in name || (SQUAT.containsMatchIn(name) && "jump" !in name) -> buildList {
                if (BENCH_ANGLE.containsMatchIn(name)) add("Inclinazione panca")
                add("Altezza supporti")
            }
            BENCH_ANGLE.containsMatchIn(name) -> listOf("Inclinazione panca")
            else -> emptyList()
        }
        Equipment.DUMBBELL -> if (BENCH_ANGLE.containsMatchIn(name)) listOf("Inclinazione panca") else emptyList()
        Equipment.CARDIO_MACHINE -> when {
            "bike" in name || "bicycl" in name -> listOf("Altezza sella")
            "rowing" in name -> listOf("Resistenza (damper)")
            else -> emptyList()
        }
        else -> emptyList()
    }

    companion object {
        /** Id stabile dall'id sorgente: `Barbell_Bench_Press_-_Medium_Grip` → `barbell_bench_press_medium_grip`. */
        fun slugOf(sourceId: String): String =
            sourceId.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

        /** "Clean grip" / "snatch grip" indicano solo la presa (front squat clean grip è uno squat). */
        private val OLYMPIC_LIFT = Regex("\\b(clean|snatch|jerk)\\b(?![- ]grip)")
        private val CARDIO_MACHINE = Regex("treadmill|stationary|elliptical|recumbent|stairmaster|step mill")
        private val DISTANCE_CARDIO = Regex("run|jog|walk|bicycl|bike|rowing|elliptical|skating")
        private val STATIC_HOLD = Regex("plank|bridge|isometric|hold|wall sit|squeeze")
        private val UNILATERAL = Regex("(one|single)[- ]?(arm|leg)|pistol")
        private val PUSH_UP = Regex("push-?ups?")
        private val DIP = Regex("\\bdips?\\b")
        private val BENCH_ANGLE = Regex("incline|decline")
        private val SQUAT = Regex("\\bsquats?\\b")
        private val SEATED_MACHINE = Regex("chest press|bench press|shoulder|military|row|preacher|curl|triceps|dip|butterfly|fly|crunch|shrug")
        private val OBLIQUES = Regex(
            "oblique|twist|side bend|wood ?chop|windmill|side bridge|side jackknife|russian|pallof|cable lift|" +
                "judo flip|heel touch|wiper|figure 8|halo|pirate|landmine 180|elbow to knee|cross-body crunch|air bike",
        )
        private val LOWER_ABS = Regex(
            "leg raise|knee raise|leg lift|reverse crunch|pull-in|flutter|scissor|knee tuck|pike|leg tuck|" +
                "hip raise|butt-ups|bottoms up|jackknife sit|tuck crunch|dead bug|knee/hip",
        )
        private val REAR_DELT = Regex(
            "rear|reverse fly|reverse flye|back fly|face pull|external rotation|internal rotation|" +
                "bent over .*(lateral|raise)|bent-over .*raise|sled reverse",
        )
        private val SIDE_DELT = Regex("lateral|upright|deltoid raise|iron cross|side raise|power partials|scaption")
    }
}
