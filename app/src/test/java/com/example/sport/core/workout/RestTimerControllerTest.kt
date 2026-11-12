package com.example.sport.core.workout

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.T0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Orologio controllabile dai test. */
class TestClock(var current: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = current
}

@RunWith(AndroidJUnit4::class)
class RestTimerControllerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val clock = TestClock(T0)
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    private fun controller() = RestTimerController(context, clock, RestTimerNotifier(context))

    @Before
    fun grantNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun start_postsCountdownNotification_andSchedulesTheAlarm() {
        val timer = controller()
        timer.start(90, "Panca piana")

        val state = timer.state.value!!
        assertEquals(T0.plusSeconds(90), state.endsAt)
        assertEquals(90L, state.remainingSeconds(T0))

        val countdown = notifications.getNotification(RestTimerNotifier.NOTIFICATION_COUNTDOWN)
        assertNotNull("Notifica col conto alla rovescia", countdown)
        assertEquals(T0.plusSeconds(90).toEpochMilli(), countdown.`when`)
        assertTrue(countdown.extras.getBoolean(NotificationCompat.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(T0.plusSeconds(90).toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtMs)
    }

    @Test
    fun adjust_movesTheEnd_andReschedules() {
        val timer = controller()
        timer.start(60, "Rematore")
        timer.adjust(15)
        assertEquals(T0.plusSeconds(75), timer.state.value!!.endsAt)
        assertEquals(T0.plusSeconds(75).toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtMs)

        timer.adjust(-120)
        assertNull("Togliere più del residuo chiude il recupero", timer.state.value)
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun state_survivesProcessRestart_untilItExpires() {
        controller().start(120, "Squat")

        clock.current = T0.plusSeconds(30)
        val restored = controller().state.value!!
        assertEquals(T0.plusSeconds(120), restored.endsAt)
        assertEquals("Squat", restored.nextExercise)
        assertEquals(90L, restored.remainingSeconds(clock.current))

        clock.current = T0.plusSeconds(121)
        assertNull("Un recupero già scaduto non viene ripristinato", controller().state.value)
    }

    @Test
    fun alarm_showsTheFinishedNotification_andClearsEverything() {
        val timer = controller()
        timer.start(90, "Leg press")

        timer.complete(fromAlarm = true)

        assertNull(timer.state.value)
        assertNull(notifications.getNotification(RestTimerNotifier.NOTIFICATION_COUNTDOWN))
        assertNotNull(notifications.getNotification(RestTimerNotifier.NOTIFICATION_FINISHED))
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun completionInsideTheApp_isSilent_andSkipCancelsEverything() {
        val timer = controller()
        timer.start(90, "Leg press")
        timer.complete(fromAlarm = false)
        assertTrue("Dall'app l'avviso è aptico, non una notifica", notifications.allNotifications.isEmpty())

        timer.start(60, "Croci")
        timer.cancel()
        assertNull(timer.state.value)
        assertTrue(notifications.allNotifications.isEmpty())
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun withoutExactAlarms_theHintIsShown_untilDismissed_andSurvivesTheTimer() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val timer = controller()
        assertTrue("Avviso a schermo spento impreciso: invito a concedere il permesso", timer.exactAlarmHint.value)

        timer.start(90, "Panca piana")
        assertNotNull("L'allarme inesatto è comunque programmato", alarms.peekNextScheduledAlarm())
        timer.dismissExactAlarmHint()
        assertFalse(timer.exactAlarmHint.value)

        timer.cancel()
        assertFalse("Chiudere il recupero non cancella la scelta \"Non ora\"", controller().exactAlarmHint.value)
    }

    @Test
    fun grantingExactAlarms_hidesTheHint_andKeepsTheRunningAlarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val timer = controller()
        timer.start(120, "Squat")
        assertTrue(timer.exactAlarmHint.value)

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        timer.refreshAlarmAccess()

        assertFalse(timer.exactAlarmHint.value)
        assertEquals("Riprogrammato alla stessa ora", T0.plusSeconds(120).toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtMs)
        assertEquals(1, alarms.scheduledAlarms.size)
    }

    @Test
    fun withoutNotificationPermission_theTimerStillWorks() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val timer = controller()
        timer.start(45, "Plank")
        assertNotNull(timer.state.value)
        assertTrue(notifications.allNotifications.isEmpty())
        assertNotNull("L'allarme resta programmato", alarms.peekNextScheduledAlarm())
    }
}
