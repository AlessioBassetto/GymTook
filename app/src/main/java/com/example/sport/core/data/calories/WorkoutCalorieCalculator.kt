package com.example.sport.core.data.calories

import com.example.sport.core.model.BiologicalSex
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.SetType
import java.time.Duration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Dati antropometrici dell'utente al momento dell'allenamento.
 *
 * @param weightKg ultimo peso registrato (obbligatorio: senza peso non c'è stima).
 * @param heightCm altezza; se manca si usa la convenzione standard del MET (3,5 ml O₂/kg/min).
 * @param ageYears età; se manca [DEFAULT_AGE_YEARS].
 * @param sex sesso biologico; se manca si usa la media delle costanti di Mifflin-St Jeor.
 */
data class BodyMetrics(
    val weightKg: Double,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val sex: BiologicalSex? = null,
) {
    init {
        require(weightKg > 0) { "Peso non valido: $weightKg" }
    }

    companion object {
        /** Età mediana di chi si allena in palestra: usata solo se l'utente non indica l'anno di nascita. */
        const val DEFAULT_AGE_YEARS = 30
    }
}

/** Una serie completata, con i valori registrati (peso sempre in kg) e il recupero impostato. */
data class CalorieSet(
    val exercise: CalorieExercise,
    val setType: SetType = SetType.WORKING,
    /** Carico in kg: zavorra per BODYWEIGHT_REPS, contrappeso di aiuto per ASSISTED_BODYWEIGHT. */
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val rpe: Double? = null,
    val rir: Int? = null,
    /** Pendenza del tapis roulant (%), per gli esercizi INCLINE_TIME. */
    val inclinePercent: Double? = null,
    /** Velocità del tapis roulant (km/h), per gli esercizi INCLINE_TIME. */
    val speedKmh: Double? = null,
    /** Recupero impostato dopo la serie (timer del logger): pesa la ripartizione del recupero reale. */
    val plannedRestSeconds: Int = DEFAULT_PLANNED_REST_SECONDS,
) {
    companion object {
        const val DEFAULT_PLANNED_REST_SECONDS = 90
    }
}

/** Un allenamento concluso: durata reale (inizio → "Termina") e serie in ordine di esecuzione. */
data class CalorieSession(
    /** Null se la sessione non ha un orario di fine: si usa il tempo pianificato. */
    val elapsed: Duration?,
    val sets: List<CalorieSet>,
)

/**
 * Risultato della stima, con il dettaglio delle componenti (per i test e per spiegare il numero).
 * Tutte le energie sono in kcal; [totalKcal] è il totale lordo (metabolismo a riposo compreso).
 */
data class CalorieEstimate(
    val totalKcal: Int,
    /** Metabolismo a riposo durante il tempo effettivo di allenamento. */
    val restingKcal: Double,
    /** Costo netto delle serie, escluso il lavoro meccanico: MET della classe × tempo sotto tensione. */
    val activeKcal: Double,
    /** Costo metabolico del lavoro meccanico sul carico (Σ massa × g × corsa × ripetizioni). */
    val mechanicalWorkKcal: Double,
    /** Costo netto del recupero fra le serie. */
    val recoveryKcal: Double,
    val activeSeconds: Double,
    val recoverySeconds: Double,
    /** Metabolismo a riposo stimato (kcal/giorno). */
    val restingMetabolicRateKcalPerDay: Double,
)

/**
 * Motore di stima delle calorie bruciate in un allenamento. Puro Kotlin, deterministico, senza
 * dipendenze Android: tutto ciò che serve arriva nei parametri.
 *
 * Modello (energia lorda = riposo + attività):
 *
 * 1. **Metabolismo a riposo (RMR)** con Mifflin-St Jeor (1990), la formula più accurata nella
 *    popolazione adulta odierna: RMR = 10·peso + 6,25·altezza − 5·età + s, con s = +5 (uomo),
 *    −161 (donna), −78 (media se non indicato). Peso e altezza scalano così il consumo di base.
 *    Senza altezza si usa la convenzione del MET: 1 MET = 3,5 ml O₂/kg/min ≈ 0,0175 kcal/kg/min.
 *
 * 2. **Tempo effettivo**: per ogni serie il tempo attivo (ripetizioni × tempo sotto tensione, o la
 *    durata registrata); il resto della durata reale è recupero. La durata conteggiata è limitata
 *    a un massimo plausibile (recuperi al più doppi di quelli impostati, tra 2 e 10 minuti, più
 *    15 minuti di transizioni), così un allenamento dimenticato aperto per ore non conta come tale.
 *
 * 3. **Costo netto di ogni fase**: (MET − 1) × 0,0175 × peso × minuti, con il MET della classe
 *    dell'esercizio (multiarticolari gambe > multiarticolari busto > isolamento > piccoli
 *    distretti) per la serie e per il recupero che la segue. Per il cardio il MET si ricava dalla
 *    velocità: equazioni ACSM per camminata e corsa, Compendium per la bici, potenza al vogatore.
 *
 * 4. **Lavoro meccanico**: L = massa × g × corsa × ripetizioni, con massa = carico esterno ×
 *    attrezzi + quota biomeccanica del peso corporeo (con 0 kg una trazione sposta tutto il corpo,
 *    un piegamento circa il 65%, uno squat a corpo libero circa il 50%) e corsa scalata
 *    sull'altezza. L'energia metabolica è L × (1 + 0,30 di costo eccentrico) / 0,20 di rendimento
 *    muscolare.
 *
 * 5. **Intensità**: RPE (o RIR, convertito come RPE = 10 − RIR) moltiplica il costo netto della
 *    serie e del suo recupero: ±5% per punto rispetto a RPE 7,5, fra 0,85 e 1,15. Senza RPE si
 *    deduce dal tipo di serie (riscaldamento 5, cedimento 10).
 *
 * L'EPOC (consumo nelle ore successive) non è incluso: la stima riguarda la sola sessione.
 */
class WorkoutCalorieCalculator {

    /** @return null se non c'è nessuna serie da stimare. */
    fun estimate(body: BodyMetrics, session: CalorieSession): CalorieEstimate? {
        if (session.sets.isEmpty()) return null

        // 1. Metabolismo a riposo, per minuto.
        val rmrPerDay = restingMetabolicRate(body)
        val rmrPerMinute = rmrPerDay / MINUTES_PER_DAY

        // Profili e intensità di ogni serie: calcolati una volta sola.
        val work = session.sets.map { set ->
            val profile = ExerciseEnergyProfiles.of(set.exercise)
            // Nel cardio con velocità nota il MET misura già l'intensità: l'RPE non si applica due volte.
            val intensity = if (hasMeasuredSpeed(set, profile)) 1.0 else intensityFactor(set)
            SetWork(set, profile, intensity, activeSeconds(set, profile))
        }

        // 2. Tempo attivo e recupero effettivo.
        val activeSeconds = work.sumOf { it.activeSeconds }
        val plausibleSeconds = activeSeconds + work.sumOf { maxRecoverySeconds(it.set) } + TRANSITIONS_SECONDS
        val elapsedSeconds = session.elapsed?.seconds?.toDouble()?.coerceAtLeast(0.0)
            ?: (activeSeconds + work.sumOf { it.set.plannedRestSeconds.toDouble() })
        // Mai meno del tempo attivo registrato (es. una corsa inserita a posteriori), mai oltre il plausibile.
        val effectiveSeconds = max(activeSeconds, min(elapsedSeconds, plausibleSeconds))
        val recoverySeconds = effectiveSeconds - activeSeconds
        // Il recupero reale si distribuisce fra le serie in proporzione ai recuperi impostati.
        val restWeights = work.map { max(it.set.plannedRestSeconds, MIN_REST_WEIGHT_SECONDS).toDouble() }
        val totalRestWeight = restWeights.sum()

        var activeKcal = 0.0
        var mechanicalKcal = 0.0
        var recoveryKcal = 0.0
        work.forEachIndexed { index, item ->
            // 3. Costo netto della serie e del recupero che la segue, scalato dall'intensità.
            val activeMet = activeMetOf(item, body)
            activeKcal += netKcal(activeMet, item.activeSeconds, body.weightKg) * item.intensity
            mechanicalKcal += mechanicalWorkKcal(item, body) * item.intensity
            val recoveryShare = if (totalRestWeight > 0) recoverySeconds * restWeights[index] / totalRestWeight else 0.0
            recoveryKcal += netKcal(item.profile.energyClass.recoveryMet, recoveryShare, body.weightKg) * item.intensity
        }
        val restingKcal = rmrPerMinute * effectiveSeconds / SECONDS_PER_MINUTE
        val total = restingKcal + activeKcal + mechanicalKcal + recoveryKcal
        return CalorieEstimate(
            totalKcal = total.roundToInt().coerceAtLeast(1),
            restingKcal = restingKcal,
            activeKcal = activeKcal,
            mechanicalWorkKcal = mechanicalKcal,
            recoveryKcal = recoveryKcal,
            activeSeconds = activeSeconds,
            recoverySeconds = recoverySeconds,
            restingMetabolicRateKcalPerDay = rmrPerDay,
        )
    }

    /**
     * Metabolismo a riposo (kcal/giorno), Mifflin-St Jeor:
     * RMR = 10 × peso[kg] + 6,25 × altezza[cm] − 5 × età[anni] + s.
     * Senza altezza: 1 MET × 24 h = 0,0175 kcal/kg/min × 1440 min.
     */
    fun restingMetabolicRate(body: BodyMetrics): Double {
        val height = body.heightCm ?: return KCAL_PER_KG_MINUTE_PER_MET * body.weightKg * MINUTES_PER_DAY
        val age = body.ageYears ?: BodyMetrics.DEFAULT_AGE_YEARS
        val sexConstant = when (body.sex) {
            BiologicalSex.MALE -> MIFFLIN_MALE
            BiologicalSex.FEMALE -> MIFFLIN_FEMALE
            null -> (MIFFLIN_MALE + MIFFLIN_FEMALE) / 2
        }
        return 10.0 * body.weightKg + 6.25 * height - 5.0 * age + sexConstant
    }

    /**
     * Massa effettivamente spostata in una ripetizione (kg):
     * carico esterno × attrezzi + quota del peso corporeo. Nell'assistito il peso registrato è un
     * aiuto: si sottrae dalla quota corporea (mai sotto zero).
     */
    fun effectiveLoadKg(set: CalorieSet, profile: ExerciseEnergyProfile, bodyWeightKg: Double): Double {
        val external = (set.weightKg ?: 0.0).coerceAtLeast(0.0)
        val body = profile.bodyMassFraction * bodyWeightKg
        return when (set.exercise.trackingType) {
            ExerciseTrackingType.ASSISTED_BODYWEIGHT -> (body - external).coerceAtLeast(0.0)
            else -> external * profile.implements + body
        }
    }

    /** Tempo attivo (s): ripetizioni × tempo sotto tensione × lati, oppure la durata registrata. */
    private fun activeSeconds(set: CalorieSet, profile: ExerciseEnergyProfile): Double {
        val logged = set.durationSeconds?.takeIf { it > 0 }?.toDouble()
        if (logged != null) return logged
        val reps = (set.reps ?: 0).coerceIn(0, MAX_REPS)
        if (reps > 0) {
            val perRep = profile.energyClass.secondsPerRep.takeIf { it > 0 } ?: FALLBACK_SECONDS_PER_REP
            return reps * perRep * profile.sides
        }
        // Solo distanza (cardio senza tempo): durata ricavata da una velocità tipica dell'attività.
        val distance = set.distanceMeters?.takeIf { it > 0 } ?: return 0.0
        return distance / typicalSpeedMetersPerSecond(profile.cardioKind)
    }

    /**
     * Lavoro meccanico → energia metabolica (kcal).
     * L [J] = massa [kg] × g [m/s²] × corsa [m] × ripetizioni × lati; la corsa è scalata
     * sull'altezza (arti più lunghi spostano il carico più in alto).
     * Energia = L × (1 + quota eccentrica) / rendimento muscolare, convertita in kcal (÷ 4184).
     */
    private fun mechanicalWorkKcal(item: SetWork, body: BodyMetrics): Double {
        val reps = (item.set.reps ?: 0).coerceIn(0, MAX_REPS)
        if (reps == 0 || item.profile.romMeters <= 0.0) return 0.0
        val heightScale = body.heightCm
            ?.let { (it / ExerciseEnergyProfile.REFERENCE_HEIGHT_CM).coerceIn(MIN_HEIGHT_SCALE, MAX_HEIGHT_SCALE) }
            ?: 1.0
        val massKg = effectiveLoadKg(item.set, item.profile, body.weightKg)
        val joules = massKg * GRAVITY * item.profile.romMeters * heightScale * reps * item.profile.sides
        return joules * (1 + ECCENTRIC_COST_RATIO) / MUSCLE_EFFICIENCY / JOULES_PER_KCAL
    }

    // Nel tapis roulant in salita il MET viene da pendenza e velocità: l'RPE non si somma.
    private fun hasMeasuredSpeed(set: CalorieSet, profile: ExerciseEnergyProfile): Boolean =
        set.exercise.trackingType == ExerciseTrackingType.INCLINE_TIME ||
            profile.energyClass == EnergyClass.CARDIO &&
            profile.cardioKind in SPEED_BASED_KINDS &&
            (set.durationSeconds ?: 0) > 0 && (set.distanceMeters ?: 0.0) > 0.0

    /** MET della serie: per il cardio dalla velocità (se nota), altrimenti quello della classe. */
    private fun activeMetOf(item: SetWork, body: BodyMetrics): Double {
        if (item.profile.energyClass != EnergyClass.CARDIO) return item.profile.energyClass.activeMet
        return cardioMet(item.set, item.profile, body.weightKg)
            .coerceIn(MIN_CARDIO_MET, MAX_CARDIO_MET)
    }

    /**
     * MET del cardio.
     * - Camminata e corsa (ACSM, con pendenza G = inclinazione/100):
     *   camminata VO₂ = 3,5 + 0,1·v + 1,8·v·G (v ≤ 100 m/min), corsa VO₂ = 3,5 + 0,2·v + 0,9·v·G
     *   (v ≥ 134 m/min), raccordate linearmente nel mezzo; MET = VO₂ / 3,5. Il termine v·G è il
     *   moltiplicatore della pendenza: lavoro verticale contro la gravità.
     * - Tapis roulant (INCLINE_TIME): velocità registrata in km/h → m/min (v = km/h × 1000 / 60),
     *   pendenza G = %/100, durata esatta della serie. Serie vecchie senza velocità: da distanza e
     *   tempo se c'erano, altrimenti la velocità tipica (camminata 5 km/h, corsa 9 km/h).
     * - Bici (Compendium 2011, fasce di velocità): <16 km/h 4,0 · <19,3 6,8 · <22,5 8,0 ·
     *   <25,7 10,0 · <30,6 12,0 · oltre 15,8.
     * - Vogatore: potenza dal passo (formula Concept2: W = 2,80 / passo³, passo in s/m), costo
     *   metabolico = W / 0,20 di rendimento; MET = 1 + costo netto / (0,0175 × peso).
     * - Senza distanza (o per le altre attività): valori medi del Compendium.
     */
    private fun cardioMet(set: CalorieSet, profile: ExerciseEnergyProfile, weightKg: Double): Double {
        val kind = profile.cardioKind ?: CardioKind.OTHER
        val seconds = set.durationSeconds?.takeIf { it > 0 }?.toDouble()
        val meters = set.distanceMeters?.takeIf { it > 0 }
        val grade = (set.inclinePercent ?: 0.0).coerceIn(0.0, MAX_INCLINE_PERCENT) / 100
        if (set.exercise.trackingType == ExerciseTrackingType.INCLINE_TIME) {
            val speed = set.speedKmh?.takeIf { it > 0 }?.coerceAtMost(MAX_TREADMILL_SPEED_KMH)
            val metersPerMinute = when {
                speed != null -> kmhToMetersPerMinute(speed)
                seconds != null && meters != null -> meters / (seconds / SECONDS_PER_MINUTE)
                profile.running -> TYPICAL_RUN_M_PER_MIN
                else -> TYPICAL_INCLINE_WALK_M_PER_MIN
            }
            return runOrWalkVo2(metersPerMinute, grade) / VO2_PER_MET
        }
        if (seconds != null && meters != null) {
            val metersPerMinute = meters / (seconds / SECONDS_PER_MINUTE)
            when (kind) {
                CardioKind.RUN_OR_WALK -> return runOrWalkVo2(metersPerMinute, grade) / VO2_PER_MET
                CardioKind.CYCLING -> return cyclingMet(metersPerMinute * 60 / 1000)
                CardioKind.ROWING -> {
                    val paceSecondsPerMeter = seconds / meters
                    val watts = CONCEPT2_WATTS_CONSTANT / paceSecondsPerMeter.pow(3)
                    val netKcalPerMinute = watts / MUSCLE_EFFICIENCY * SECONDS_PER_MINUTE / JOULES_PER_KCAL
                    return 1 + netKcalPerMinute / (KCAL_PER_KG_MINUTE_PER_MET * weightKg)
                }
                else -> Unit
            }
        }
        val base = when (kind) {
            CardioKind.RUN_OR_WALK -> 8.3 // corsa a ~8 km/h
            CardioKind.CYCLING -> 6.8 // cyclette, sforzo moderato
            CardioKind.ROWING -> 7.0 // vogatore, sforzo moderato
            CardioKind.ELLIPTICAL -> 5.0
            CardioKind.STAIRS -> 9.0
            CardioKind.JUMP_ROPE -> 11.0
            CardioKind.SLED -> 8.0
            CardioKind.OTHER -> EnergyClass.CARDIO.activeMet
        }
        // Senza velocità l'intensità percepita (RPE) è l'unico indizio: la applica [estimate].
        return base
    }

    /** 5,5 km/h → 91,7 m/min: l'unità delle equazioni ACSM. */
    internal fun kmhToMetersPerMinute(kmh: Double): Double = kmh * 1000 / SECONDS_PER_MINUTE

    /**
     * VO₂ (ml/kg/min) da velocità e pendenza ([grade] = frazione, 0,08 = 8%), equazioni metaboliche ACSM:
     * camminata (≤ 100 m/min) 3,5 + 0,1·v + 1,8·v·G; corsa (≥ 134 m/min) 3,5 + 0,2·v + 0,9·v·G; in mezzo
     * (passo svelto o corsetta) raccordo lineare fra le due. Calorie nette = (VO₂/3,5 − 1) × 0,0175 × kg × min.
     */
    internal fun runOrWalkVo2(metersPerMinute: Double, grade: Double = 0.0): Double {
        val walking = VO2_PER_MET + 0.1 * metersPerMinute + 1.8 * metersPerMinute * grade
        val running = VO2_PER_MET + 0.2 * metersPerMinute + 0.9 * metersPerMinute * grade
        return when {
            metersPerMinute <= WALK_MAX_M_PER_MIN -> walking
            metersPerMinute >= RUN_MIN_M_PER_MIN -> running
            else -> {
                val t = (metersPerMinute - WALK_MAX_M_PER_MIN) / (RUN_MIN_M_PER_MIN - WALK_MAX_M_PER_MIN)
                walking + (running - walking) * t
            }
        }
    }

    private fun cyclingMet(kmPerHour: Double): Double = when {
        kmPerHour < 16.0 -> 4.0
        kmPerHour < 19.3 -> 6.8
        kmPerHour < 22.5 -> 8.0
        kmPerHour < 25.7 -> 10.0
        kmPerHour < 30.6 -> 12.0
        else -> 15.8
    }

    private fun typicalSpeedMetersPerSecond(kind: CardioKind?): Double = when (kind) {
        CardioKind.CYCLING -> 20.0 / 3.6
        CardioKind.ROWING -> 3.7 // passo 2:15/500 m
        CardioKind.RUN_OR_WALK -> 9.0 / 3.6
        else -> 8.0 / 3.6
    }

    /**
     * Moltiplicatore d'intensità del costo netto: RPE 7,5 (o RIR 2,5) = 1,0; ±5% per punto,
     * limitato a [0,85; 1,15]. Senza dato si deduce dal tipo di serie.
     */
    internal fun intensityFactor(set: CalorieSet): Double {
        val rpe = set.rpe
            ?: set.rir?.let { (10 - it).toDouble() }
            ?: when (set.setType) {
                SetType.WARMUP -> 5.0
                SetType.WORKING -> REFERENCE_RPE
                SetType.DROP -> 8.5
                SetType.FAILURE -> 10.0
            }
        return (1 + INTENSITY_PER_RPE_POINT * (rpe.coerceIn(0.0, 10.0) - REFERENCE_RPE))
            .coerceIn(MIN_INTENSITY, MAX_INTENSITY)
    }

    /** Recupero massimo conteggiato dopo una serie: il doppio di quello impostato, fra 2 e 10 minuti. */
    private fun maxRecoverySeconds(set: CalorieSet): Double =
        (2.0 * set.plannedRestSeconds).coerceIn(MIN_RECOVERY_CAP_SECONDS, MAX_RECOVERY_CAP_SECONDS)

    /** Costo netto (kcal) di [seconds] secondi a [met] MET: (MET − 1) × 0,0175 × peso × minuti. */
    private fun netKcal(met: Double, seconds: Double, weightKg: Double): Double =
        (met - 1).coerceAtLeast(0.0) * KCAL_PER_KG_MINUTE_PER_MET * weightKg * seconds / SECONDS_PER_MINUTE

    private class SetWork(
        val set: CalorieSet,
        val profile: ExerciseEnergyProfile,
        val intensity: Double,
        val activeSeconds: Double,
    )

    companion object {
        /** 1 MET = 3,5 ml O₂/kg/min × 5 kcal/l O₂ / 1000 = 0,0175 kcal/kg/min. */
        const val KCAL_PER_KG_MINUTE_PER_MET = 0.0175
        const val VO2_PER_MET = 3.5
        const val MIFFLIN_MALE = 5.0
        const val MIFFLIN_FEMALE = -161.0

        const val GRAVITY = 9.81
        const val JOULES_PER_KCAL = 4184.0

        /** Rendimento lordo del muscolo nel lavoro concentrico (~20%, come nel ciclismo). */
        const val MUSCLE_EFFICIENCY = 0.20

        /** La fase eccentrica (discesa controllata) costa circa il 30% della concentrica. */
        const val ECCENTRIC_COST_RATIO = 0.30

        /** Concept2: potenza (W) = 2,80 / passo³ con passo in secondi per metro. */
        const val CONCEPT2_WATTS_CONSTANT = 2.80

        const val REFERENCE_RPE = 7.5
        const val INTENSITY_PER_RPE_POINT = 0.05
        const val MIN_INTENSITY = 0.85
        const val MAX_INTENSITY = 1.15

        /** Transizioni non legate a una serie: riscaldamento generale, cambi di macchina. */
        const val TRANSITIONS_SECONDS = 15 * 60.0
        const val MIN_RECOVERY_CAP_SECONDS = 120.0
        const val MAX_RECOVERY_CAP_SECONDS = 600.0
        const val MIN_REST_WEIGHT_SECONDS = 30

        /** Camminata in salita tipica sul tapis roulant: 5 km/h. */
        const val TYPICAL_INCLINE_WALK_M_PER_MIN = 5000.0 / 60

        /** Corsa in salita tipica: 9 km/h. */
        const val TYPICAL_RUN_M_PER_MIN = 9000.0 / 60
        const val MAX_INCLINE_PERCENT = 40.0
        const val MAX_TREADMILL_SPEED_KMH = 30.0
        const val WALK_MAX_M_PER_MIN = 100.0
        const val RUN_MIN_M_PER_MIN = 134.0
        const val MIN_CARDIO_MET = 2.0
        const val MAX_CARDIO_MET = 20.0

        const val MIN_HEIGHT_SCALE = 0.8
        const val MAX_HEIGHT_SCALE = 1.2
        const val MAX_REPS = 999

        /** Ripetizioni registrate su un esercizio a tempo o cardio (es. un personalizzato). */
        const val FALLBACK_SECONDS_PER_REP = 3.0

        private val SPEED_BASED_KINDS = setOf(CardioKind.RUN_OR_WALK, CardioKind.CYCLING, CardioKind.ROWING)

        private const val SECONDS_PER_MINUTE = 60.0
        private const val MINUTES_PER_DAY = 1440.0
    }
}
