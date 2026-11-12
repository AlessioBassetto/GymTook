package com.example.sport.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.entity.MachineSetupEntity
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.database.query.ExerciseFilter
import com.example.sport.core.database.seed.AssetExerciseCatalogSource
import com.example.sport.core.model.catalog.CatalogExerciseDto
import com.example.sport.core.database.seed.CatalogSeedResult
import com.example.sport.core.model.catalog.ExerciseCatalogDto
import com.example.sport.core.model.catalog.validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseCatalogTest : DatabaseTest() {

    @Test
    fun bundledCatalog_isValid_andCoversWholeTaxonomy() = runTest {
        val catalog = AssetExerciseCatalogSource(context, Dispatchers.IO).load()
        catalog.validate()

        val primaryGroups = catalog.exercises.flatMap { it.primaryMuscles }.map { it.group }.toSet()
        assertEquals("Ogni macro-gruppo deve avere almeno un esercizio target", MuscleGroup.entries.toSet(), primaryGroups)

        val allRegions = catalog.exercises.flatMap { it.primaryMuscles + it.secondaryMuscles }.toSet()
        assertEquals("Ogni regione muscolare deve comparire nel catalogo", Muscle.entries.toSet(), allRegions)

        val equipment = catalog.exercises.map { it.equipment }.toSet()
        assertEquals("Ogni categoria di attrezzatura deve essere rappresentata", Equipment.entries.toSet(), equipment)
    }

    @Test
    fun bundledCatalog_meetsContentQualityBar() = runTest {
        val catalog = AssetExerciseCatalogSource(context, Dispatchers.IO).load()
        assertTrue("Catalogo enciclopedico: servono centinaia di esercizi", catalog.exercises.size >= 500)
        catalog.exercises.forEach { exercise ->
            assertTrue("${exercise.id}: manca l'esecuzione", exercise.instructions.isNotEmpty())
            assertNotNull("${exercise.id}: manca il nome inglese", exercise.nameEn)
            if (exercise.contentLocale == CatalogExerciseDto.CONTENT_LOCALE_IT) {
                // Contenuto italiano (curato o tradotto): standard completo della scheda esercizio.
                assertTrue("${exercise.id}: servono almeno 3 passaggi", exercise.instructions.size >= 3)
                assertTrue("${exercise.id}: manca un punto chiave", exercise.keyPoints.isNotEmpty())
                assertTrue("${exercise.id}: manca un errore comune", exercise.commonMistakes.isNotEmpty())
            }
        }
        // Media: quasi tutti gli esercizi con origine nel dataset hanno i fotogrammi; per i pochi
        // senza immagini la scheda mostra la mappa muscolare.
        val withOrigin = catalog.exercises.filter { it.origin != null }
        val withMedia = withOrigin.count { it.media.isNotEmpty() }
        assertTrue("Copertura media insufficiente: $withMedia/${withOrigin.size}", withMedia >= withOrigin.size * 0.99)

        val curatedMachines = catalog.exercises.filter {
            it.equipment == Equipment.MACHINE && it.contentLocale == CatalogExerciseDto.CONTENT_LOCALE_IT
        }
        curatedMachines.forEach {
            assertTrue("${it.id}: i macchinari curati devono suggerire le regolazioni", it.setupParams.isNotEmpty())
        }
    }

    @Test
    fun seeder_installsBundledCatalogOnce() = runTest {
        val catalog = AssetExerciseCatalogSource(context, Dispatchers.IO).load()

        val first = seedBundledCatalog()
        assertTrue(first is CatalogSeedResult.Installed)
        assertEquals(catalog.exercises.size, (first as CatalogSeedResult.Installed).exerciseCount)
        assertEquals(catalog.exercises.size, db.exerciseDao().count())

        val second = seedBundledCatalog()
        assertEquals(CatalogSeedResult.UpToDate(installedVersion = catalog.version), second)
        assertEquals(catalog.exercises.size, db.exerciseDao().count())
    }

    @Test
    fun seeder_storesMediaFrames_forCuratedAndImportedExercises() = runTest {
        seedBundledCatalog()
        val curated = db.exerciseDao().getDetails("leg_extension")!!
        assertEquals("it", curated.exercise.contentLocale)
        assertEquals(
            listOf("exercises/Leg_Extensions/0.jpg", "exercises/Leg_Extensions/1.jpg"),
            curated.frames.map { it.path },
        )
        assertEquals("Unlicense", curated.thumbnail!!.license)

        val imported = db.exerciseDao().getDetails("incline_dumbbell_flyes")!!
        assertEquals("Croci su panca inclinata con manubri", imported.exercise.name)
        assertEquals(listOf(Muscle.CHEST_UPPER), imported.primaryMuscles)
        assertEquals(2, imported.frames.size)
        assertEquals(null, imported.videoLoop)
    }

    @Test
    fun seeder_mapsMachineMetadata() = runTest {
        seedBundledCatalog()
        val legExtension = db.exerciseDao().getWithMuscles("leg_extension")!!
        assertTrue(legExtension.exercise.supportsMachineSetup)
        assertEquals(listOf("Schienale", "Rullo tibie", "Allineamento ginocchio"), legExtension.exercise.setupParamHints)
        assertEquals(listOf(Muscle.QUADRICEPS), legExtension.primaryMuscles)

        val pushUp = db.exerciseDao().getById("push_up")!!
        assertFalse("Il corpo libero non propone la regolazione", pushUp.supportsMachineSetup)
    }

    @Test
    fun seeder_upgrade_preservesUserData_andRetiresRemovedExercises() = runTest {
        val v1 = ExerciseCatalogDto(
            version = 1,
            exercises = listOf(
                catalogEntry("machine_a", "Macchina A"),
                catalogEntry("machine_b", "Macchina B"),
            ),
        )
        seeder(source = { v1 }, now = T0).seedIfNeeded()

        val exerciseDao = db.exerciseDao()
        exerciseDao.setFavorite("machine_a", favorite = true, updatedAt = T0)
        val setupId = db.machineSetupDao().insert(
            MachineSetupEntity(
                exerciseId = "machine_a",
                label = "Predefinita",
                params = listOf(MachineSetupParam("Altezza sedile", "4")),
                createdAt = T0,
                lastUsedAt = T0,
            ),
        )

        val v2 = ExerciseCatalogDto(
            version = 2,
            exercises = listOf(
                catalogEntry("machine_a", "Macchina A (rinominata)"),
                catalogEntry("machine_c", "Macchina C"),
            ),
        )
        val result = seeder(source = { v2 }, now = hoursAfterT0(24)).seedIfNeeded()

        assertEquals(
            CatalogSeedResult.Installed(previousVersion = 1, version = 2, exerciseCount = 2, retiredCount = 1),
            result,
        )
        val upgraded = exerciseDao.getById("machine_a")!!
        assertEquals("Macchina A (rinominata)", upgraded.name)
        assertTrue("Il preferito deve sopravvivere all'aggiornamento", upgraded.isFavorite)
        assertEquals(T0, upgraded.createdAt)
        assertNotNull(
            "L'upsert non deve cancellare a cascata le regolazioni salvate",
            db.machineSetupDao().getById(setupId),
        )
        assertTrue(exerciseDao.getById("machine_b")!!.isRetired)
        assertEquals(
            listOf("machine_a", "machine_c"),
            exerciseDao.searchExercises(ExerciseFilter()).map { it.id }.sorted(),
        )
    }

    private fun catalogEntry(id: String, name: String) = CatalogExerciseDto(
        id = id,
        name = name,
        equipment = Equipment.MACHINE,
        primaryMuscles = listOf(Muscle.QUADRICEPS),
        setupParams = listOf("Altezza sedile"),
    )
}
