package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sport.core.model.DifficultyLevel
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.ForceType
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import java.time.Instant

/**
 * Esercizio del catalogo (pre-popolato da asset JSON) o creato dall'utente.
 *
 * La chiave primaria è uno slug stabile (`barbell_bench_press`, `custom_<uuid>`): permette di
 * aggiornare il catalogo con un upsert senza perdere i riferimenti di storico, schede e regolazioni.
 *
 * ATTENZIONE: non usare mai `OnConflictStrategy.REPLACE` su questa tabella. REPLACE esegue
 * DELETE + INSERT e attiverebbe i CASCADE (regolazioni macchinario, muscoli) e i RESTRICT (storico).
 * Usare sempre `@Upsert` o `@Update`.
 */
@Entity(
    tableName = "exercises",
    indices = [
        Index("equipment"),
        Index("primary_muscle_group"),
        Index("is_favorite"),
    ],
)
data class ExerciseEntity(
    @PrimaryKey
    val id: String,

    /** Nome visualizzato (italiano). */
    val name: String,

    /** Nome inglese: utile in ricerca, molti utenti conoscono gli esercizi in inglese. */
    @ColumnInfo(name = "name_en")
    val nameEn: String?,

    /** Sinonimi ("distensioni su panca", "bench"). */
    val aliases: List<String>,

    /** Testo normalizzato (minuscolo, senza accenti) usato dalla ricerca: nome + nome EN + alias. */
    @ColumnInfo(name = "search_text")
    val searchText: String,

    val equipment: Equipment,

    /** Muscolo target principale per liste e ordinamenti; l'elenco completo è in `exercise_muscles`. */
    @ColumnInfo(name = "primary_muscle")
    val primaryMuscle: Muscle,

    /** Denormalizzato da [primaryMuscle] per filtri e "esercizi alternativi" senza join. */
    @ColumnInfo(name = "primary_muscle_group")
    val primaryMuscleGroup: MuscleGroup = primaryMuscle.group,

    @ColumnInfo(name = "tracking_type")
    val trackingType: ExerciseTrackingType,

    /** Forza, olimpico, pliometrico o cardio: filtro della libreria e criterio per le alternative. */
    @ColumnInfo(defaultValue = "STRENGTH")
    val category: ExerciseCategory = ExerciseCategory.STRENGTH,

    val mechanic: Mechanic?,

    val force: ForceType?,

    val level: DifficultyLevel,

    @ColumnInfo(name = "is_unilateral")
    val isUnilateral: Boolean,

    /** Passaggi di esecuzione, in ordine. */
    val instructions: List<String>,

    /** Punti chiave tecnici (cue). */
    @ColumnInfo(name = "key_points")
    val keyPoints: List<String>,

    /** Errori comuni e avvertenze. */
    @ColumnInfo(name = "common_mistakes")
    val commonMistakes: List<String>,

    /**
     * Lingua di istruzioni, punti chiave ed errori ("it" o "en" per gli importati non ancora
     * tradotti): la UI mostra un'etichetta "Testo in inglese" quando serve.
     * Video e immagini sono nella tabella `exercise_media`.
     */
    @ColumnInfo(name = "content_locale", defaultValue = "it")
    val contentLocale: String = "it",

    /**
     * Se true il logger mostra subito la card "Regolazione macchinario" espansa.
     * La regolazione resta comunque aggiungibile su qualsiasi esercizio (es. inclinazione panca).
     */
    @ColumnInfo(name = "supports_machine_setup")
    val supportsMachineSetup: Boolean,

    /** Nomi dei parametri suggeriti al primo utilizzo (es. "Altezza sedile", "Schienale"). */
    @ColumnInfo(name = "setup_param_hints")
    val setupParamHints: List<String>,

    /** Recupero proposto quando l'esercizio è aggiunto a una scheda o a una sessione libera. */
    @ColumnInfo(name = "default_rest_seconds")
    val defaultRestSeconds: Int,

    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    /** Nascosto dall'utente: non compare in libreria ma resta nello storico. */
    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean = false,

    /** Rimosso da una versione successiva del catalogo: conservato solo per lo storico. */
    @ColumnInfo(name = "is_retired")
    val isRetired: Boolean = false,

    /** Versione del catalogo che ha scritto la riga; 0 per gli esercizi custom. */
    @ColumnInfo(name = "catalog_version")
    val catalogVersion: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
