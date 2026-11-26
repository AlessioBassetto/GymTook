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

/**
 * v4 → v5 (tapis roulant in salita):
 * - `exercise_sets.incline_percent`: pendenza in % delle serie INCLINE_TIME. Nullable e senza
 *   default: le serie esistenti restano identiche (NULL = nessuna pendenza registrata).
 *
 * Il nuovo tipo di registrazione `INCLINE_TIME` non tocca lo schema (gli enum sono TEXT); il tempo
 * usa la colonna esistente `duration_seconds`. I media restano in `exercise_media` (IMAGE_FRAME =
 * anteprima, VIDEO_LOOP = video o GIF, locali o remoti): nessuna colonna duplicata su `exercises`.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exercise_sets` ADD COLUMN `incline_percent` REAL")
    }
}

/**
 * v5 → v6 (tapis roulant con velocità + catalogo dieta):
 * - `exercise_sets.speed_kmh`: velocità in km/h delle serie del tapis. Nullable: le serie esistenti
 *   restano identiche (nessuna velocità registrata → le calorie usano distanza o velocità tipica).
 * - `food_items`: catalogo italiano (lo installa il seeder al primo avvio), piatti e ricette
 *   dell'utente, prodotti salvati da Open Food Facts; valori per 100 g.
 * - `food_recipe_ingredients`: ingredienti delle ricette, con i valori copiati.
 * - `meal_entries`: diario alimentare (grammi + valori per 100 g copiati; calorie calcolate in lettura).
 *
 * Solo aggiunte: piani alimentari, pasti e spunte esistenti non cambiano.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exercise_sets` ADD COLUMN `speed_kmh` REAL")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_items` (" +
                "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, `category` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, `kcal_per_100g` REAL NOT NULL, `protein_per_100g` REAL NOT NULL, " +
                "`carbs_per_100g` REAL NOT NULL, `fat_per_100g` REAL NOT NULL, `default_grams` REAL NOT NULL, " +
                "`search_text` TEXT NOT NULL, `catalog_version` INTEGER, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_category` ON `food_items` (`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_source` ON `food_items` (`source`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_recipe_ingredients` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipe_id` TEXT NOT NULL, `ingredient_id` TEXT, " +
                "`name` TEXT NOT NULL, `grams` REAL NOT NULL, `kcal_per_100g` REAL NOT NULL, " +
                "`protein_per_100g` REAL NOT NULL, `carbs_per_100g` REAL NOT NULL, `fat_per_100g` REAL NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "FOREIGN KEY(`recipe_id`) REFERENCES `food_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`ingredient_id`) REFERENCES `food_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_food_recipe_ingredients_recipe_id_position` " +
                "ON `food_recipe_ingredients` (`recipe_id`, `position`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_food_recipe_ingredients_ingredient_id` ON `food_recipe_ingredients` (`ingredient_id`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `meal_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `meal_type` TEXT NOT NULL, " +
                "`food_item_id` TEXT, `name` TEXT NOT NULL, `brand` TEXT, `grams` REAL NOT NULL, " +
                "`kcal_per_100g` REAL NOT NULL, `protein_per_100g` REAL NOT NULL, `carbs_per_100g` REAL NOT NULL, " +
                "`fat_per_100g` REAL NOT NULL, `created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`food_item_id`) REFERENCES `food_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_entries_date_meal_type` ON `meal_entries` (`date`, `meal_type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_entries_food_item_id` ON `meal_entries` (`food_item_id`)")
    }
}

/** Migrazioni scritte a mano, da registrare in ogni builder del database (app e test). */
val MANUAL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
