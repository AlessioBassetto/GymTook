package com.example.sport.core.database.migration

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 (Fase 2, pipeline media):
 * - nuova tabella `exercise_media` (creata automaticamente da Room);
 * - nuova colonna `exercises.content_locale` con default 'it' (automatica);
 * - rimosse `exercises.video_path` / `exercises.thumbnail_path`, sostituite da `exercise_media`.
 *   In v1 erano sempre NULL, quindi nessun dato da trasferire.
 */
@DeleteColumn.Entries(
    DeleteColumn(tableName = "exercises", columnName = "video_path"),
    DeleteColumn(tableName = "exercises", columnName = "thumbnail_path"),
)
class Migration1To2 : AutoMigrationSpec

/**
 * v3 → v4 (calorie bruciate):
 * - `workout_sessions.calories_burned`: stima in kcal di ogni allenamento. Nullable e senza
 *   default, così le sessioni già salvate restano intatte con NULL ("non ancora stimata"): le
 *   calcola `CalorieRepository.recalculateMissing()` al primo avvio dopo l'aggiornamento, con le
 *   pesate già registrate;
 * - nuova tabella `body_profile` (riga unica): altezza, anno di nascita e sesso biologico.
 *
 * Solo aggiunte: nessuna riga esistente viene riscritta o cancellata. Gli statement devono
 * coincidere con lo schema esportato in `app/schemas/.../4.json`, che Room verifica all'apertura.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `calories_burned` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_profile` (" +
                "`id` INTEGER NOT NULL, " +
                "`height_cm` REAL, " +
                "`birth_year` INTEGER, " +
                "`sex` TEXT, " +
                "`updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/** Migrazioni scritte a mano, da registrare in ogni builder del database (app e test). */
val MANUAL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4)
