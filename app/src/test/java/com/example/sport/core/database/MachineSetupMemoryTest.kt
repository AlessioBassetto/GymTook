package com.example.sport.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.database.relation.SessionExerciseWithSets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Requisito chiave: "l'app deve ricordare automaticamente l'ultimo settaggio usato per quell'esercizio".
 * Simula tre allenamenti consecutivi dello stesso giorno di scheda.
 */
@RunWith(AndroidJUnit4::class)
class MachineSetupMemoryTest : DatabaseTest() {

    private val sessionDao get() = db.workoutSessionDao()
    private val setupDao get() = db.machineSetupDao()
    private var dayId: Long = 0

    private val homeGymSetup = listOf(
        MachineSetupParam("Schienale", "3"),
        MachineSetupParam("Rullo tibie", "5"),
        MachineSetupParam("Allineamento ginocchio", "B"),
    )

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        dayId = createRoutineWithDay("Gambe", listOf("leg_extension" to 3, "leg_press" to 2)).second
    }

    private suspend fun legExtensionOf(sessionId: Long): SessionExerciseWithSets =
        sessionDao.getSessionWithExercises(sessionId)!!.orderedExercises
            .first { it.sessionExercise.exerciseId == "leg_extension" }

    @Test
    fun lastUsedSetup_isRecalledInNextSession_withPrefilledSets() = runTest {
        // --- Sessione 1: primo utilizzo, nessuna regolazione nota.
        val session1 = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(0))
        val first = legExtensionOf(session1)
        assertNull(first.sessionExercise.machineSetupId)
        assertTrue(first.sessionExercise.machineSetup.isEmpty())

        val setupId = setupDao.saveForSessionExercise(
            sessionExerciseId = first.sessionExercise.id,
            params = homeGymSetup + MachineSetupParam("Blocco", "  "),
            defaultLabel = "Predefinita",
            usedAt = hoursAfterT0(0),
        )
        assertEquals("Le voci senza valore vengono scartate", homeGymSetup, legExtensionOf(session1).sessionExercise.machineSetup)

        val sets = first.orderedSets
        completeSet(sets[0], weightKg = 40.0, reps = 12, at = hoursAfterT0(0))
        completeSet(sets[1], weightKg = 45.0, reps = 10, at = hoursAfterT0(0))
        completeSet(sets[2], weightKg = 45.0, reps = 9, at = hoursAfterT0(0))
        sessionDao.finishSession(session1, endedAt = hoursAfterT0(1), discardIncompleteSets = true)

        // --- Sessione 2: regolazione e carichi richiamati automaticamente.
        val session2 = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(48))
        val second = legExtensionOf(session2)
        assertEquals(setupId, second.sessionExercise.machineSetupId)
        assertEquals(homeGymSetup, second.sessionExercise.machineSetup)
        assertEquals(listOf(40.0, 45.0, 45.0), second.orderedSets.map { it.weightKg })
        assertEquals(listOf(12, 10, 9), second.orderedSets.map { it.reps })
        assertTrue("Le serie pre-compilate vanno solo spuntate", second.orderedSets.none { it.isCompleted })
        assertEquals("Leg press senza storico: reps target, peso vuoto", listOf(12, 12), sessionDao
            .getSessionWithExercises(session2)!!.orderedExercises[1].orderedSets.map { it.reps })

        // Modifica in palestra: aggiorna lo stesso profilo, lo storico resta intatto.
        val adjusted = listOf(MachineSetupParam("Schienale", "4")) + homeGymSetup.drop(1)
        assertEquals(setupId, setupDao.saveForSessionExercise(second.sessionExercise.id, adjusted, "Predefinita", hoursAfterT0(48)))
        assertEquals(adjusted, setupDao.getById(setupId)!!.params)
        assertEquals("Snapshot della sessione 1 invariato", homeGymSetup, legExtensionOf(session1).sessionExercise.machineSetup)
        sessionDao.finishSession(session2, endedAt = hoursAfterT0(49), discardIncompleteSets = true)

        // --- Sessione 3: viene proposto il valore aggiornato.
        val session3 = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(96))
        assertEquals(adjusted, legExtensionOf(session3).sessionExercise.machineSetup)
    }

    @Test
    fun multipleProfiles_mostRecentlyUsedWins() = runTest {
        val session1 = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(0))
        val homeId = setupDao.saveForSessionExercise(
            legExtensionOf(session1).sessionExercise.id, homeGymSetup, "Palestra Casa", hoursAfterT0(0),
        )
        sessionDao.finishSession(session1, endedAt = hoursAfterT0(1), discardIncompleteSets = false)

        // In trasferta: macchina diversa, nuovo profilo.
        val session2 = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(24))
        val officeParams = listOf(MachineSetupParam("Schienale", "6"), MachineSetupParam("Rullo tibie", "2"))
        val officeId = setupDao.saveAsNewForSessionExercise(
            legExtensionOf(session2).sessionExercise.id, "Palestra Ufficio", officeParams, hoursAfterT0(24),
        )
        sessionDao.finishSession(session2, endedAt = hoursAfterT0(25), discardIncompleteSets = false)

        assertEquals(officeId, setupDao.getLastUsedSetup("leg_extension")!!.id)
        assertEquals(listOf("Palestra Ufficio", "Palestra Casa"), setupDao.observeSetupsForExercise("leg_extension").first().map { it.label })

        // Di nuovo a casa: l'utente seleziona il profilo dal menu.
        val session3 = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(48))
        val third = legExtensionOf(session3)
        assertEquals(officeParams, third.sessionExercise.machineSetup)
        setupDao.selectForSessionExercise(third.sessionExercise.id, homeId, hoursAfterT0(48))
        assertEquals(homeGymSetup, legExtensionOf(session3).sessionExercise.machineSetup)
        assertEquals(homeId, setupDao.getLastUsedSetup("leg_extension")!!.id)
    }

    @Test
    fun selectingSetupOfAnotherExercise_isRejected() = runTest {
        val session = sessionDao.startSessionFromRoutineDay(dayId, startedAt = T0)
        val exercises = sessionDao.getSessionWithExercises(session)!!.orderedExercises
        val legPressSetup = setupDao.saveForSessionExercise(
            exercises[1].sessionExercise.id, listOf(MachineSetupParam("Schienale", "2")), "Predefinita", T0,
        )
        expectThrows<IllegalArgumentException> {
            setupDao.selectForSessionExercise(exercises[0].sessionExercise.id, legPressSetup, T0)
        }
    }

    @Test
    fun replacingExercise_recallsSetupOfTheNewExercise() = runTest {
        val session1 = sessionDao.startSessionFromRoutineDay(dayId, startedAt = T0)
        val hackSquatSession = sessionDao.addExerciseToSession(session1, "hack_squat", setCount = 1)
        val hackParams = listOf(MachineSetupParam("Altezza spalliere", "7"))
        setupDao.saveForSessionExercise(hackSquatSession, hackParams, "Predefinita", T0)
        sessionDao.finishSession(session1, endedAt = hoursAfterT0(1), discardIncompleteSets = false)

        // Leg press occupata: sostituita al volo con l'hack squat.
        val session2 = sessionDao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(24))
        val legPress = sessionDao.getSessionWithExercises(session2)!!.orderedExercises[1]
        val replacedId = sessionDao.replaceExercise(legPress.sessionExercise.id, "hack_squat")

        val replaced = sessionDao.getSessionExercise(replacedId)!!
        assertEquals("hack_squat", replaced.exerciseId)
        assertEquals("leg_press", replaced.replacedExerciseId)
        assertEquals(hackParams, replaced.machineSetup)
    }
}
