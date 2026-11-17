package com.example.sport.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.entity.ExerciseUserVideoEntity
import com.example.sport.core.database.migration.MANUAL_MIGRATIONS
import com.example.sport.core.database.seed.AssetExerciseCatalogSource
import com.example.sport.core.database.seed.CatalogSeedResult
import com.example.sport.core.database.seed.ExerciseCatalogSeeder
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.MachineSetupParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.ZoneOffset

/**
 * Aggiornamento reale dell'app dalla Fase 1 (schema v1) alla Fase 2 (schema v2): crea un database
 * v1 dallo schema esportato, lo riempie con dati utente, lo apre con Room v2 (che esegue
 * l'AutoMigration e valida lo schema risultante) e poi installa il nuovo catalogo.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "migration-test.db"

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun v1ToV2_preservesUserData_thenCatalogUpgradeAddsMedia() = runTest {
        createV1Database()

        val db = Room.databaseBuilder(context, SportDatabase::class.java, databaseName)
            .addMigrations(*MANUAL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Apertura = migrazione 1 → 2 + validazione dello schema da parte di Room.
            val migrated = db.exerciseDao().getDetails("leg_extension")!!
            assertEquals("it", migrated.exercise.contentLocale)
            assertTrue(migrated.exercise.isFavorite)
            assertTrue(migrated.media.isEmpty())
            assertEquals(1, migrated.muscles.size)

            val setup = db.machineSetupDao().getLastUsedSetup("leg_extension")!!
            assertEquals(listOf(MachineSetupParam("Schienale", "3")), setup.params)

            val session = db.workoutSessionDao().getSessionWithExercises(1)!!
            val loggedExercise = session.exercises.single()
            assertEquals("La ricostruzione della tabella exercises non deve cancellare a cascata", 1, loggedExercise.sets.size)
            assertEquals(42.5, loggedExercise.sets.single().weightKg!!, 0.0)
            assertEquals(listOf(MachineSetupParam("Schienale", "3")), loggedExercise.sessionExercise.machineSetup)
            assertEquals(1, db.routineDao().getRoutineExercises(1).size)

            // L'app aggiornata installa il catalogo v2 sopra i dati esistenti.
            val result = ExerciseCatalogSeeder(
                database = db,
                source = AssetExerciseCatalogSource(context, Dispatchers.IO),
                clock = Clock.fixed(T0, ZoneOffset.UTC),
                applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            ).seedIfNeeded()
            assertTrue(result is CatalogSeedResult.Installed)
            assertEquals(1, (result as CatalogSeedResult.Installed).previousVersion)

            val upgraded = db.exerciseDao().getDetails("leg_extension")!!
            assertTrue("Il preferito sopravvive all'aggiornamento", upgraded.exercise.isFavorite)
            assertEquals(2, upgraded.frames.size)
            assertEquals(setup.id, db.machineSetupDao().getLastUsedSetup("leg_extension")!!.id)
            assertEquals(1, db.workoutSessionDao().getSets(loggedExercise.sessionExercise.id).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun v2ToV3_addsCategoryAndUserVideos_thenCatalogFillsTheCategory() = runTest {
        createDatabase(version = 2) { db ->
            val now = T0.toEpochMilli()
            db.execSQL(
                """
                INSERT INTO exercises (id, name, name_en, aliases, search_text, equipment, primary_muscle,
                    primary_muscle_group, tracking_type, mechanic, force, level, is_unilateral, instructions,
                    key_points, common_mistakes, content_locale, supports_machine_setup, setup_param_hints,
                    default_rest_seconds, is_custom, is_favorite, is_hidden, is_retired, catalog_version,
                    created_at, updated_at)
                VALUES ('power_clean', 'Power clean', 'Power Clean', '[]', 'power clean', 'BARBELL',
                    'HAMSTRINGS', 'HAMSTRINGS', 'WEIGHT_REPS', 'COMPOUND', 'PULL', 'INTERMEDIATE', 0, '[]',
                    '[]', '[]', 'en', 0, '[]', 150, 0, 1, 0, 0, 2, $now, $now)
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO exercise_muscles (exercise_id, muscle, muscle_group, role) VALUES ('power_clean', 'HAMSTRINGS', 'HAMSTRINGS', 'PRIMARY')")
            db.execSQL("INSERT INTO app_metadata (`key`, `value`) VALUES ('exercise_catalog_version', '2')")
        }

        val db = Room.databaseBuilder(context, SportDatabase::class.java, databaseName)
            .addMigrations(*MANUAL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val migrated = db.exerciseDao().getDetails("power_clean")!!
            assertEquals("Default della nuova colonna", ExerciseCategory.STRENGTH, migrated.exercise.category)
            assertNull(migrated.userVideo)
            db.exerciseDao().upsertUserVideo(ExerciseUserVideoEntity("power_clean", "video.mp4", 1_024, T0))
            assertEquals("video.mp4", db.exerciseDao().getDetails("power_clean")!!.userVideo!!.fileName)

            val result = ExerciseCatalogSeeder(
                database = db,
                source = AssetExerciseCatalogSource(context, Dispatchers.IO),
                clock = Clock.fixed(T0, ZoneOffset.UTC),
                applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            ).seedIfNeeded()
            assertEquals(2, (result as CatalogSeedResult.Installed).previousVersion)

            val upgraded = db.exerciseDao().getDetails("power_clean")!!
            assertEquals("Il catalogo v3 compila la categoria", ExerciseCategory.OLYMPIC, upgraded.exercise.category)
            assertTrue(upgraded.exercise.isFavorite)
            assertEquals("L'aggiornamento del catalogo non tocca i video dell'utente", "video.mp4", upgraded.userVideo!!.fileName)
        } finally {
            db.close()
        }
    }

    @Test
    fun v3ToV4_keepsTheHistory_thenEstimatesTheCaloriesOfPastWorkouts() = runTest {
        createDatabase(version = 3) { db ->
            val start = T0.toEpochMilli()
            val end = T0.plusSeconds(45 * 60).toEpochMilli()
            db.execSQL(
                """
                INSERT INTO exercises (id, name, name_en, aliases, search_text, equipment, primary_muscle,
                    primary_muscle_group, tracking_type, category, mechanic, force, level, is_unilateral, instructions,
                    key_points, common_mistakes, content_locale, supports_machine_setup, setup_param_hints,
                    default_rest_seconds, is_custom, is_favorite, is_hidden, is_retired, catalog_version,
                    created_at, updated_at)
                VALUES ('barbell_back_squat', 'Squat con bilanciere', 'Barbell Back Squat', '[]', 'squat', 'BARBELL',
                    'QUADRICEPS', 'QUADRICEPS', 'WEIGHT_REPS', 'STRENGTH', 'COMPOUND', 'PUSH', 'INTERMEDIATE', 0, '[]',
                    '[]', '[]', 'it', 0, '[]', 180, 0, 0, 0, 0, 3, $start, $start)
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO workout_sessions (id, routine_id, routine_day_id, title, status, started_at, ended_at, body_weight_kg, notes) " +
                    "VALUES (1, NULL, NULL, 'Gambe', 'COMPLETED', $start, $end, NULL, 'Ottima giornata')",
            )
            db.execSQL(
                "INSERT INTO session_exercises (id, session_id, exercise_id, routine_exercise_id, position, superset_group, " +
                    "rest_seconds, machine_setup_id, machine_setup, replaced_exercise_id, notes) " +
                    "VALUES (1, 1, 'barbell_back_squat', NULL, 0, NULL, 180, NULL, '[]', NULL, NULL)",
            )
            (0 until 4).forEach { index ->
                db.execSQL(
                    "INSERT INTO exercise_sets (session_exercise_id, set_index, set_type, weight_kg, reps, rpe, rir, " +
                        "duration_seconds, distance_meters, is_completed, completed_at, notes) " +
                        "VALUES (1, $index, 'WORKING', 100.0, 5, 8.0, NULL, NULL, NULL, 1, $end, NULL)",
                )
            }
            db.execSQL(
                "INSERT INTO body_weight_entries (date, weight_kg, body_fat_percent, note, recorded_at) " +
                    "VALUES (${T0.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()}, 81.5, NULL, NULL, $start)",
            )
        }

        val db = Room.databaseBuilder(context, SportDatabase::class.java, databaseName)
            .addMigrations(*MANUAL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Apertura = MIGRATION_3_4 + validazione dello schema v4 da parte di Room.
            val migrated = db.workoutSessionDao().getSessionWithExercises(1)!!
            assertEquals("Ottima giornata", migrated.session.notes)
            assertEquals(4, migrated.exercises.single().sets.size)
            assertNull("Le sessioni salvate prima della v4 non hanno ancora una stima", migrated.session.caloriesBurned)
            assertNull("Profilo corporeo vuoto", db.bodyProfileDao().get())
            assertEquals(81.5, db.bodyWeightDao().getLatest()!!.weightKg, 0.0)

            // Primo avvio dopo l'aggiornamento: le stime mancanti si calcolano con le pesate esistenti.
            db.calorieRepository(Clock.fixed(T0, ZoneOffset.UTC)).recalculateMissing()

            val estimated = db.workoutSessionDao().getSession(1)!!
            assertTrue("Stima ${estimated.caloriesBurned}", (estimated.caloriesBurned ?: 0) > 100)
            assertEquals(81.5, estimated.bodyWeightKg!!, 0.0)
            assertEquals("Storico intatto", 4, db.workoutSessionDao().getSessionWithExercises(1)!!.exercises.single().sets.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun v4ToV5_addsTheIncline_thenTheItalianCatalogTurnsTheTreadmillWalkIntoInclineAndTime() = runTest {
        createDatabase(version = 4) { db ->
            val start = T0.toEpochMilli()
            val end = T0.plusSeconds(40 * 60).toEpochMilli()
            db.execSQL(
                """
                INSERT INTO exercises (id, name, name_en, aliases, search_text, equipment, primary_muscle,
                    primary_muscle_group, tracking_type, category, mechanic, force, level, is_unilateral, instructions,
                    key_points, common_mistakes, content_locale, supports_machine_setup, setup_param_hints,
                    default_rest_seconds, is_custom, is_favorite, is_hidden, is_retired, catalog_version,
                    created_at, updated_at)
                VALUES ('walking_treadmill', 'Camminata su tapis roulant', 'Walking, Treadmill', '[]', 'walking treadmill',
                    'CARDIO_MACHINE', 'CARDIO', 'CARDIO_FULL_BODY', 'DISTANCE_DURATION', 'CARDIO', NULL, NULL, 'BEGINNER', 0,
                    '["To begin, step onto the treadmill"]', '[]', '[]', 'en', 0, '[]', 60, 0, 1, 0, 0, 3, $start, $start)
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO exercise_muscles (exercise_id, muscle, muscle_group, role) VALUES ('walking_treadmill', 'CARDIO', 'CARDIO_FULL_BODY', 'PRIMARY')")
            db.execSQL(
                "INSERT INTO workout_sessions (id, routine_id, routine_day_id, title, status, started_at, ended_at, body_weight_kg, notes) " +
                    "VALUES (1, NULL, NULL, 'Cardio', 'COMPLETED', $start, $end, NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO session_exercises (id, session_id, exercise_id, routine_exercise_id, position, superset_group, " +
                    "rest_seconds, machine_setup_id, machine_setup, replaced_exercise_id, notes) " +
                    "VALUES (1, 1, 'walking_treadmill', NULL, 0, NULL, 60, NULL, '[]', NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO exercise_sets (id, session_exercise_id, set_index, set_type, weight_kg, reps, rpe, rir, " +
                    "duration_seconds, distance_meters, is_completed, completed_at, notes) " +
                    "VALUES (1, 1, 0, 'WORKING', NULL, NULL, NULL, NULL, 1800, 3000.0, 1, $end, NULL)",
            )
            db.execSQL("INSERT INTO app_metadata (`key`, `value`) VALUES ('exercise_catalog_version', '3')")
        }

        val db = Room.databaseBuilder(context, SportDatabase::class.java, databaseName)
            .addMigrations(*MANUAL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Apertura = MIGRATION_4_5 + validazione dello schema v5 da parte di Room.
            val set = db.workoutSessionDao().getSets(1).single()
            assertNull("Le serie esistenti non hanno una pendenza", set.inclinePercent)
            assertEquals(1800, set.durationSeconds)
            assertEquals(3000.0, set.distanceMeters!!, 0.0)
            db.workoutSessionDao().updateSetIncline(1, 7.5)
            assertEquals(7.5, db.workoutSessionDao().getSets(1).single().inclinePercent!!, 0.0)

            // Primo avvio: il catalogo v4 in italiano sostituisce i testi inglesi.
            val result = ExerciseCatalogSeeder(
                database = db,
                source = AssetExerciseCatalogSource(context, Dispatchers.IO),
                clock = Clock.fixed(T0, ZoneOffset.UTC),
                applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            ).seedIfNeeded()
            assertEquals(3, (result as CatalogSeedResult.Installed).previousVersion)

            val treadmill = db.exerciseDao().getDetails("walking_treadmill")!!.exercise
            assertEquals(ExerciseTrackingType.INCLINE_TIME, treadmill.trackingType)
            assertEquals("Camminata in salita sul tapis roulant", treadmill.name)
            assertEquals("it", treadmill.contentLocale)
            assertTrue(treadmill.instructions.first().startsWith("Sali sul tapis roulant"))
            assertTrue("Il preferito sopravvive", treadmill.isFavorite)
            val kept = db.workoutSessionDao().getSets(1).single()
            assertEquals("Lo storico resta intatto", 1800, kept.durationSeconds)
            assertEquals(3000.0, kept.distanceMeters!!, 0.0)
        } finally {
            db.close()
        }
    }

    /** Crea il database esattamente come lo creava la versione 1 dell'app (dallo schema esportato). */
    private fun createV1Database() = createDatabase(version = 1, ::insertV1UserData)

    /** Crea il database dallo schema esportato di [version] e lo riempie con [insertData]. */
    private fun createDatabase(version: Int, insertData: (SQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(
            File("schemas/com.example.sport.core.database.SportDatabase/$version.json").readText(),
        ).jsonObject.getValue("database").jsonObject
        val file = context.getDatabasePath(databaseName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.getValue("entities").jsonArray.forEach { entityElement ->
                val entity = entityElement.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            insertData(db)
            db.version = version
        }
    }

    private fun insertV1UserData(db: SQLiteDatabase) {
        val now = T0.toEpochMilli()
        db.execSQL(
            """
            INSERT INTO exercises (id, name, name_en, aliases, search_text, equipment, primary_muscle,
                primary_muscle_group, tracking_type, mechanic, force, level, is_unilateral, instructions,
                key_points, common_mistakes, video_path, thumbnail_path, supports_machine_setup,
                setup_param_hints, default_rest_seconds, is_custom, is_favorite, is_hidden, is_retired,
                catalog_version, created_at, updated_at)
            VALUES ('leg_extension', 'Leg extension', 'Leg Extension', '[]', 'leg extension', 'MACHINE',
                'QUADRICEPS', 'QUADRICEPS', 'WEIGHT_REPS', 'ISOLATION', 'PUSH', 'BEGINNER', 0, '["Siediti"]',
                '[]', '[]', NULL, NULL, 1, '["Schienale"]', 75, 0, 1, 0, 0, 1, $now, $now)
            """.trimIndent(),
        )
        db.execSQL("INSERT INTO exercise_muscles (exercise_id, muscle, muscle_group, role) VALUES ('leg_extension', 'QUADRICEPS', 'QUADRICEPS', 'PRIMARY')")
        db.execSQL(
            "INSERT INTO machine_setups (id, exercise_id, label, params, notes, created_at, last_used_at) " +
                "VALUES (1, 'leg_extension', 'Predefinita', '[{\"name\":\"Schienale\",\"value\":\"3\"}]', NULL, $now, $now)",
        )
        db.execSQL(
            "INSERT INTO routines (id, name, description, split_type, is_active, is_archived, parent_routine_id, created_at, updated_at) " +
                "VALUES (1, 'Gambe', NULL, 'CUSTOM', 1, 0, NULL, $now, $now)",
        )
        db.execSQL("INSERT INTO routine_days (id, routine_id, name, position, notes) VALUES (1, 1, 'Gambe A', 0, NULL)")
        db.execSQL(
            "INSERT INTO routine_exercises (id, routine_day_id, exercise_id, position, target_sets, target_reps_min, " +
                "target_reps_max, target_rpe, target_rir, rest_seconds, superset_group, notes) " +
                "VALUES (1, 1, 'leg_extension', 0, 3, 10, 12, NULL, NULL, NULL, NULL, NULL)",
        )
        db.execSQL(
            "INSERT INTO workout_sessions (id, routine_id, routine_day_id, title, status, started_at, ended_at, body_weight_kg, notes) " +
                "VALUES (1, 1, 1, 'Gambe A', 'COMPLETED', $now, $now, NULL, NULL)",
        )
        db.execSQL(
            "INSERT INTO session_exercises (id, session_id, exercise_id, routine_exercise_id, position, superset_group, " +
                "rest_seconds, machine_setup_id, machine_setup, replaced_exercise_id, notes) " +
                "VALUES (1, 1, 'leg_extension', 1, 0, NULL, 75, 1, '[{\"name\":\"Schienale\",\"value\":\"3\"}]', NULL, NULL)",
        )
        db.execSQL(
            "INSERT INTO exercise_sets (id, session_exercise_id, set_index, set_type, weight_kg, reps, rpe, rir, " +
                "duration_seconds, distance_meters, is_completed, completed_at, notes) " +
                "VALUES (1, 1, 0, 'WORKING', 42.5, 12, NULL, NULL, NULL, NULL, 1, $now, NULL)",
        )
        db.execSQL("INSERT INTO app_metadata (`key`, `value`) VALUES ('exercise_catalog_version', '1')")
    }
}
