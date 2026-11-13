package com.example.sport.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.relation.SessionExerciseWithSets
import com.example.sport.core.model.MachineSetupParam
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settaggi e note del macchinario gestiti "a freddo" dalla scheda esercizio: si salvano senza
 * avviare un allenamento e il logger li richiama da solo alla sessione successiva.
 */
@RunWith(AndroidJUnit4::class)
class MachineSetupLibraryTest : DatabaseTest() {

    private val sessionDao get() = db.workoutSessionDao()
    private val setupDao get() = db.machineSetupDao()
    private var dayId: Long = 0

    private val seat = listOf(MachineSetupParam("Schienale", "3"), MachineSetupParam("Rullo tibie", "5"))

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        dayId = createRoutineWithDay("Gambe", listOf(LEG_EXTENSION to 3)).second
    }

    private suspend fun legExtensionOf(sessionId: Long): SessionExerciseWithSets =
        sessionDao.getSessionWithExercises(sessionId)!!.orderedExercises.first { it.exercise.id == LEG_EXTENSION }

    @Test
    fun settingsSavedCold_areRecalledByTheNextWorkout() = runTest {
        // Nessun allenamento avviato: la regolazione nasce dalla scheda esercizio.
        val setupId = setupDao.saveFromLibrary(
            exerciseId = LEG_EXTENSION,
            setupId = null,
            label = " Predefinita ",
            params = seat + MachineSetupParam("Blocco", " "),
            notes = "  Carico abituale 40 kg ",
            savedAt = hoursAfterT0(0),
        )
        val saved = setupDao.getById(setupId)!!
        assertEquals("Predefinita", saved.label)
        assertEquals("Le voci senza valore vengono scartate", seat, saved.params)
        assertEquals("Carico abituale 40 kg", saved.notes)

        val exercise = legExtensionOf(sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(24)))

        assertEquals(setupId, exercise.sessionExercise.machineSetupId)
        assertEquals(seat, exercise.sessionExercise.machineSetup)
        assertEquals("Le note arrivano al logger con il profilo", "Carico abituale 40 kg", exercise.setupProfile?.notes)
    }

    @Test
    fun editingCold_changesWhatTheLoggerProposes_butNotThePastWorkouts() = runTest {
        val setupId = setupDao.saveFromLibrary(LEG_EXTENSION, null, "Predefinita", seat, null, hoursAfterT0(0))
        val past = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(1))
        completeSet(legExtensionOf(past).orderedSets.first(), weightKg = 40.0, reps = 12, at = hoursAfterT0(1))
        assertTrue(sessionDao.finishSession(past, endedAt = hoursAfterT0(2), discardIncompleteSets = true))

        val updated = listOf(MachineSetupParam("Schienale", "4"), MachineSetupParam("Rullo tibie", "5"))
        assertEquals(
            "Si aggiorna lo stesso profilo",
            setupId,
            setupDao.saveFromLibrary(LEG_EXTENSION, setupId, "Palestra centro", updated, "Perno su B", hoursAfterT0(10)),
        )

        assertEquals("Lo storico conserva i valori di quel giorno", seat, legExtensionOf(past).sessionExercise.machineSetup)
        val next = legExtensionOf(sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(24)))
        assertEquals(updated, next.sessionExercise.machineSetup)
        assertEquals("Palestra centro", next.setupProfile?.label)
        assertEquals("Perno su B", next.setupProfile?.notes)
    }

    @Test
    fun editingCold_duringAWorkout_updatesTheLoggerAtOnce() = runTest {
        val setupId = setupDao.saveFromLibrary(LEG_EXTENSION, null, "Predefinita", seat, null, hoursAfterT0(0))
        val active = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(1))
        val updated = listOf(MachineSetupParam("Schienale", "5"))

        setupDao.saveFromLibrary(LEG_EXTENSION, setupId, "Predefinita", updated, null, hoursAfterT0(1))

        assertEquals(updated, legExtensionOf(active).sessionExercise.machineSetup)
    }

    @Test
    fun aProfileCreatedDuringAWorkout_isAttachedToTheExerciseWithoutOne() = runTest {
        val active = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(1))
        assertNull(legExtensionOf(active).sessionExercise.machineSetupId)

        val setupId = setupDao.saveFromLibrary(LEG_EXTENSION, null, "Predefinita", seat, null, hoursAfterT0(1))

        val exercise = legExtensionOf(active)
        assertEquals(setupId, exercise.sessionExercise.machineSetupId)
        assertEquals(seat, exercise.sessionExercise.machineSetup)
    }

    @Test
    fun theProfileSavedLast_isTheOneProposed() = runTest {
        val home = setupDao.saveFromLibrary(LEG_EXTENSION, null, "Casa", seat, null, hoursAfterT0(0))
        val gym = setupDao.saveFromLibrary(LEG_EXTENSION, null, "Palestra", listOf(MachineSetupParam("Schienale", "2")), null, hoursAfterT0(1))
        assertEquals(gym, setupDao.getLastUsedSetup(LEG_EXTENSION)!!.id)

        // Ritoccare "Casa" a freddo la riporta in cima.
        setupDao.saveFromLibrary(LEG_EXTENSION, home, "Casa", seat, "Sedile basso", hoursAfterT0(2))

        assertEquals(listOf(home, gym), setupDao.observeSetupsForExercise(LEG_EXTENSION).first().map { it.id })
    }

    @Test
    fun notesAloneAreEnough_emptyProfilesAreRejected_andDeletingKeepsTheHistory() = runTest {
        val setupId = setupDao.saveFromLibrary(LEG_EXTENSION, null, "Predefinita", emptyList(), "Usare la macchina vicino alla finestra", hoursAfterT0(0))
        assertTrue(setupDao.getById(setupId)!!.params.isEmpty())
        expectThrows<IllegalArgumentException> {
            setupDao.saveFromLibrary(LEG_EXTENSION, null, "Vuoto", listOf(MachineSetupParam("Schienale", "")), "  ", hoursAfterT0(1))
        }
        expectThrows<IllegalArgumentException> {
            setupDao.saveFromLibrary(LEG_PRESS, setupId, "Altro esercizio", seat, null, hoursAfterT0(1))
        }

        setupDao.saveFromLibrary(LEG_EXTENSION, setupId, "Predefinita", seat, null, hoursAfterT0(2))
        assertNull("Note svuotate dall'editor", setupDao.getById(setupId)!!.notes)
        val past = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(3))
        completeSet(legExtensionOf(past).orderedSets.first(), weightKg = 40.0, reps = 12, at = hoursAfterT0(3))
        sessionDao.finishSession(past, endedAt = hoursAfterT0(4), discardIncompleteSets = true)

        setupDao.deleteById(setupId)

        val exercise = legExtensionOf(past)
        assertNull(exercise.sessionExercise.machineSetupId)
        assertNull(exercise.setupProfile)
        assertEquals("Lo snapshot resta nello storico", seat, exercise.sessionExercise.machineSetup)
        assertNull(setupDao.getLastUsedSetup(LEG_EXTENSION))
    }

    private companion object {
        const val LEG_EXTENSION = "leg_extension"
        const val LEG_PRESS = "leg_press"
    }
}
