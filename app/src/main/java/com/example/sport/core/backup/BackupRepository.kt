package com.example.sport.core.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sport.core.data.CalorieRepository
import com.example.sport.core.database.SportDatabase
import com.example.sport.core.di.IoDispatcher
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.settings.UserSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Esercizio di catalogo segnato come preferito o nascosto (il catalogo non entra nel backup). */
@Serializable
data class CatalogFlag(val id: String, val favorite: Boolean, val hidden: Boolean)

/**
 * File di backup: JSON leggibile, una lista di righe per ogni tabella dei dati dell'utente.
 * Il formato è generico (colonna → valore) per sopravvivere ai cambi di schema: le colonne
 * aggiunte nelle versioni successive prendono il loro valore predefinito.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = FORMAT_VERSION,
    /** Versione del database che ha prodotto il file. */
    val schemaVersion: Int,
    val exportedAt: String,
    val settings: UserSettings = UserSettings(),
    val tables: Map<String, List<JsonObject>>,
    val catalogFlags: List<CatalogFlag> = emptyList(),
) {
    companion object {
        const val FORMAT = "sport-backup"
        const val FORMAT_VERSION = 1
    }
}

/** Cosa contiene un backup: mostrato dopo l'esportazione e prima/dopo il ripristino. */
data class BackupSummary(
    val sessions: Int,
    val routines: Int,
    val bodyWeights: Int,
    val dietPlans: Int,
    val customExercises: Int,
)

/** Il file non è un backup dell'app, è di una versione più recente o non è coerente. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Esportazione e ripristino di tutti i dati dell'utente: schede, allenamenti, regolazioni,
 * peso corporeo e profilo corporeo, piani alimentari, esercizi personalizzati, preferiti e impostazioni.
 * Restano fuori il catalogo (è nell'app) e i video personali (file pesanti, solo su questo telefono).
 */
@Singleton
class BackupRepository @Inject constructor(
    private val database: SportDatabase,
    private val settingsRepository: SettingsRepository,
    private val calories: CalorieRepository,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Serializza tutti i dati dell'utente. */
    suspend fun export(): String = withContext(ioDispatcher) {
        val db = database.openHelper.readableDatabase
        val tables = buildMap {
            put(EXERCISES, db.dump("SELECT * FROM exercises WHERE is_custom = 1 ORDER BY id"))
            put(
                EXERCISE_MUSCLES,
                db.dump(
                    "SELECT m.* FROM exercise_muscles m INNER JOIN exercises e ON e.id = m.exercise_id " +
                        "WHERE e.is_custom = 1 ORDER BY m.exercise_id",
                ),
            )
            USER_TABLES.forEach { table -> put(table, db.dump("SELECT * FROM $table")) }
        }
        val flags = db.query("SELECT id, is_favorite, is_hidden FROM exercises WHERE is_custom = 0 AND (is_favorite = 1 OR is_hidden = 1) ORDER BY id")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(CatalogFlag(cursor.getString(0), cursor.getInt(1) == 1, cursor.getInt(2) == 1))
                }
            }
        val file = BackupFile(
            schemaVersion = database.openHelper.readableDatabase.version,
            exportedAt = Instant.now(clock).toString(),
            settings = settingsRepository.current(),
            tables = tables,
            catalogFlags = flags,
        )
        json.encodeToString(BackupFile.serializer(), file)
    }

    /** Legge e controlla un backup senza scrivere nulla (anteprima prima della conferma). */
    fun parse(content: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), content)
        } catch (error: SerializationException) {
            throw InvalidBackupException("File non riconosciuto", error)
        } catch (error: IllegalArgumentException) {
            throw InvalidBackupException("File non riconosciuto", error)
        }
        if (file.format != BackupFile.FORMAT) throw InvalidBackupException("Formato sconosciuto: ${file.format}")
        if (file.version > BackupFile.FORMAT_VERSION) throw InvalidBackupException("Backup di una versione più recente dell'app")
        if (file.schemaVersion > database.openHelper.readableDatabase.version) {
            throw InvalidBackupException("Backup di una versione più recente dell'app")
        }
        val unknown = file.tables.keys - KNOWN_TABLES
        if (unknown.isNotEmpty()) throw InvalidBackupException("Tabelle sconosciute: $unknown")
        return file
    }

    fun summaryOf(file: BackupFile) = BackupSummary(
        sessions = file.tables[SESSIONS].orEmpty().size,
        routines = file.tables[ROUTINES].orEmpty().size,
        bodyWeights = file.tables[BODY_WEIGHTS].orEmpty().size,
        dietPlans = file.tables[DIET_PLANS].orEmpty().size,
        customExercises = file.tables[EXERCISES].orEmpty().size,
    )

    /**
     * Sostituisce i dati dell'utente con quelli del backup, in un'unica transazione: se qualcosa
     * non torna (es. un esercizio di catalogo che non esiste più) non cambia nulla.
     */
    suspend fun restore(file: BackupFile): BackupSummary {
        withContext(ioDispatcher) {
            try {
                database.withTransaction {
                    val db = database.openHelper.writableDatabase
                    // I vincoli si verificano al commit: l'ordine di cancellazione e inserimento è libero.
                    db.execSQL("PRAGMA defer_foreign_keys = ON")
                    USER_TABLES.asReversed().forEach { db.delete(it, null, null) }
                    restoreCustomExercises(db, file)
                    restoreCatalogFlags(db, file.catalogFlags)
                    USER_TABLES.forEach { table -> db.insertRows(table, file.tables[table].orEmpty()) }
                    // Riferimenti rotti (es. un esercizio che qui non esiste): si annulla tutto.
                    db.query("PRAGMA foreign_key_check").use { cursor ->
                        if (cursor.moveToFirst()) throw InvalidBackupException("Riferimento mancante nella tabella ${cursor.getString(0)}")
                    }
                }
            } catch (error: android.database.SQLException) {
                throw InvalidBackupException("Dati del backup non coerenti", error)
            }
        }
        settingsRepository.replace(file.settings)
        // I backup precedenti alla v4 non hanno le calorie; quelli nuovi le ricalcolano con le pesate ripristinate.
        calories.recalculateAll()
        return summaryOf(file)
    }

    /**
     * Gli esercizi personalizzati si aggiornano senza cancellarli, così il video personale
     * collegato resta; quelli assenti dal backup vengono eliminati.
     */
    private fun restoreCustomExercises(db: SupportSQLiteDatabase, file: BackupFile) {
        val rows = file.tables[EXERCISES].orEmpty()
        val keepIds = rows.mapNotNull { (it["id"] as? JsonPrimitive)?.content }.toSet()
        db.delete("exercise_muscles", "exercise_id IN (SELECT id FROM exercises WHERE is_custom = 1)", null)
        val existing = db.query("SELECT id FROM exercises WHERE is_custom = 1").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        (existing - keepIds).forEach { id -> db.delete("exercises", "id = ? AND is_custom = 1", arrayOf(id)) }
        val columns = db.columnsOf("exercises")
        rows.forEach { row ->
            val values = row.toContentValues(columns)
            val id = values.getAsString("id") ?: throw InvalidBackupException("Esercizio senza id")
            val updated = db.update("exercises", SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(id))
            if (updated == 0) db.insert("exercises", SQLiteDatabase.CONFLICT_ABORT, values)
        }
        db.insertRows("exercise_muscles", file.tables[EXERCISE_MUSCLES].orEmpty())
    }

    private fun restoreCatalogFlags(db: SupportSQLiteDatabase, flags: List<CatalogFlag>) {
        db.execSQL("UPDATE exercises SET is_favorite = 0, is_hidden = 0 WHERE is_custom = 0")
        flags.forEach { flag ->
            db.execSQL(
                "UPDATE exercises SET is_favorite = ?, is_hidden = ? WHERE id = ? AND is_custom = 0",
                arrayOf<Any>(if (flag.favorite) 1 else 0, if (flag.hidden) 1 else 0, flag.id),
            )
        }
    }

    private fun SupportSQLiteDatabase.insertRows(table: String, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        val columns = columnsOf(table)
        rows.forEach { row -> insert(table, SQLiteDatabase.CONFLICT_ABORT, row.toContentValues(columns)) }
    }

    /** Solo le colonne che esistono in questa versione del database. */
    private fun JsonObject.toContentValues(columns: Set<String>): ContentValues = ContentValues().apply {
        this@toContentValues.forEach { (name, value) ->
            if (name !in columns) return@forEach
            when {
                value is JsonNull -> putNull(name)
                value !is JsonPrimitive -> throw InvalidBackupException("Valore non valido in $name")
                value.isString -> put(name, value.content)
                value.longOrNull != null -> put(name, value.longOrNull)
                value.doubleOrNull != null -> put(name, value.doubleOrNull)
                else -> put(name, value.content)
            }
        }
    }

    private fun SupportSQLiteDatabase.columnsOf(table: String): Set<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
        }

    private fun SupportSQLiteDatabase.dump(sql: String): List<JsonObject> = query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    buildJsonObject {
                        for (index in 0 until cursor.columnCount) {
                            val name = cursor.getColumnName(index)
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> put(name, JsonNull)
                                Cursor.FIELD_TYPE_INTEGER -> put(name, JsonPrimitive(cursor.getLong(index)))
                                Cursor.FIELD_TYPE_FLOAT -> put(name, JsonPrimitive(cursor.getDouble(index)))
                                else -> put(name, JsonPrimitive(cursor.getString(index)))
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        private const val EXERCISES = "exercises"
        private const val EXERCISE_MUSCLES = "exercise_muscles"
        private const val ROUTINES = "routines"
        private const val SESSIONS = "workout_sessions"
        private const val BODY_WEIGHTS = "body_weight_entries"
        private const val DIET_PLANS = "diet_plans"

        /** Tabelle interamente dell'utente, in ordine di dipendenza (genitori prima dei figli). */
        val USER_TABLES = listOf(
            ROUTINES,
            "routine_days",
            "routine_exercises",
            "weekly_schedule",
            "machine_setups",
            SESSIONS,
            "session_exercises",
            "exercise_sets",
            BODY_WEIGHTS,
            "body_profile",
            DIET_PLANS,
            "meals",
            "meal_items",
            "meal_logs",
        )

        private val KNOWN_TABLES = USER_TABLES.toSet() + EXERCISES + EXERCISE_MUSCLES
    }
}
