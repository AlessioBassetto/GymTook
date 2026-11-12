package com.example.sport.core.workout

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.sport.MainActivity
import com.example.sport.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Recupero in corso: termina a [endsAt]; [nextExercise] è mostrato nella notifica di fine. */
@Immutable
data class RestTimerState(
    val endsAt: Instant,
    val totalSeconds: Int,
    val nextExercise: String,
) {
    fun remainingSeconds(now: Instant): Long = Duration.between(now, endsAt).seconds.coerceAtLeast(0)
}

/**
 * Timer di recupero tra le serie.
 *
 * Niente servizio in primo piano (batteria): il conto alla rovescia lo disegna il sistema nella
 * notifica (cronometro inverso, visibile anche a schermo bloccato) e un allarme avvisa alla fine.
 * Lo stato è salvato su disco, quindi sopravvive alla chiusura dell'app.
 *
 * L'avviso a schermo spento è puntuale solo con le sveglie esatte ("Sveglie e promemoria", negate
 * di default da Android 14): senza, il sistema può ritardarlo di minuti. [exactAlarmHint] dice al
 * logger quando invitare l'utente a concederle.
 */
@Singleton
class RestTimerController @Inject constructor(
    @ApplicationContext context: Context,
    private val clock: Clock,
    private val notifier: RestTimerNotifier,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(restore())
    val state: StateFlow<RestTimerState?> = mutableState.asStateFlow()

    private val mutableExactAlarmHint = MutableStateFlow(computeExactAlarmHint())

    /** true se l'avviso a schermo spento può tardare e l'utente non ha scelto "Non ora". */
    val exactAlarmHint: StateFlow<Boolean> = mutableExactAlarmHint.asStateFlow()

    fun now(): Instant = Instant.now(clock)

    fun start(seconds: Int, nextExercise: String) {
        if (seconds <= 0) {
            cancel()
            return
        }
        apply(RestTimerState(endsAt = now().plusSeconds(seconds.toLong()), totalSeconds = seconds, nextExercise = nextExercise))
    }

    /** ±15 s dal pannello del timer; se il tempo residuo si azzera il recupero termina. */
    fun adjust(deltaSeconds: Int) {
        val current = mutableState.value ?: return
        val endsAt = current.endsAt.plusSeconds(deltaSeconds.toLong())
        if (!endsAt.isAfter(now())) {
            cancel()
            return
        }
        apply(current.copy(endsAt = endsAt, totalSeconds = (current.totalSeconds + deltaSeconds).coerceAtLeast(1)))
    }

    /** "Salta": chiude il recupero senza avvisi. */
    fun cancel() {
        store(null)
        notifier.cancelAlarm()
        notifier.cancelAll()
    }

    /**
     * Fine del recupero. [fromAlarm] = true quando scatta l'allarme (app in background): mostra
     * la notifica di fine con vibrazione. Dall'app visibile l'avviso è aptico, senza notifica.
     */
    fun complete(fromAlarm: Boolean) {
        val finished = mutableState.value
        store(null)
        notifier.cancelAlarm()
        notifier.cancelCountdown()
        if (fromAlarm && finished != null) notifier.showFinished(finished)
    }

    /**
     * Da chiamare quando il permesso per le sveglie esatte può essere cambiato (rientro dalle
     * impostazioni, broadcast di sistema): aggiorna l'invito e riprogramma l'allarme in corso.
     */
    fun refreshAlarmAccess() {
        mutableExactAlarmHint.value = computeExactAlarmHint()
        mutableState.value?.let { notifier.scheduleAlarm(it.endsAt) }
    }

    /** "Non ora": l'invito non viene più mostrato (resta l'allarme inesatto). */
    fun dismissExactAlarmHint() {
        prefs.edit { putBoolean(KEY_EXACT_HINT_DISMISSED, true) }
        mutableExactAlarmHint.value = false
    }

    private fun computeExactAlarmHint(): Boolean =
        !notifier.exactAlarmsAllowed() && !prefs.getBoolean(KEY_EXACT_HINT_DISMISSED, false)

    private fun apply(state: RestTimerState) {
        store(state)
        notifier.showCountdown(state, now())
        notifier.scheduleAlarm(state.endsAt)
    }

    private fun store(state: RestTimerState?) {
        mutableState.value = state
        prefs.edit {
            if (state == null) {
                remove(KEY_ENDS_AT)
                remove(KEY_TOTAL)
                remove(KEY_NEXT)
            } else {
                putLong(KEY_ENDS_AT, state.endsAt.toEpochMilli())
                putInt(KEY_TOTAL, state.totalSeconds)
                putString(KEY_NEXT, state.nextExercise)
            }
        }
    }

    private fun restore(): RestTimerState? {
        val endsAt = prefs.getLong(KEY_ENDS_AT, 0L)
        if (endsAt <= clock.millis()) return null
        return RestTimerState(
            endsAt = Instant.ofEpochMilli(endsAt),
            totalSeconds = prefs.getInt(KEY_TOTAL, 0),
            nextExercise = prefs.getString(KEY_NEXT, null).orEmpty(),
        )
    }

    private companion object {
        const val PREFS_NAME = "rest_timer"
        const val KEY_ENDS_AT = "ends_at"
        const val KEY_TOTAL = "total_seconds"
        const val KEY_NEXT = "next_exercise"
        const val KEY_EXACT_HINT_DISMISSED = "exact_hint_dismissed"
    }
}

/** Notifiche e allarme del timer di recupero. */
@Singleton
class RestTimerNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notifications = NotificationManagerCompat.from(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    init {
        createChannels()
    }

    fun showCountdown(state: RestTimerState, now: Instant) {
        if (!canNotify()) return
        val remainingMillis = Duration.between(now, state.endsAt).toMillis().coerceAtLeast(0)
        val notification = NotificationCompat.Builder(context, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_stat_rest_timer)
            .setContentTitle(context.getString(R.string.notif_rest_title))
            .setContentText(context.getString(R.string.notif_rest_next, state.nextExercise))
            .setWhen(state.endsAt.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTimeoutAfter(remainingMillis + 1_000)
            .setContentIntent(openWorkoutIntent())
            .build()
        notify(NOTIFICATION_COUNTDOWN, notification)
    }

    fun showFinished(state: RestTimerState) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_stat_rest_timer)
            .setContentTitle(context.getString(R.string.notif_rest_done_title))
            .setContentText(context.getString(R.string.notif_rest_next, state.nextExercise))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(FINISHED_TIMEOUT_MILLIS)
            .setContentIntent(openWorkoutIntent())
            .build()
        notify(NOTIFICATION_FINISHED, notification)
    }

    fun cancelCountdown() = notifications.cancel(NOTIFICATION_COUNTDOWN)

    fun cancelAll() {
        notifications.cancel(NOTIFICATION_COUNTDOWN)
        notifications.cancel(NOTIFICATION_FINISHED)
    }

    /**
     * Allarme esatto se l'utente lo consente (Android 12+: "Sveglie e promemoria"). Altrimenti
     * inesatto ma attivo anche in risparmio energetico: il sistema può posticiparlo fino a ~3/4
     * dell'attesa (misurato: finestra di +2:15 su un recupero di 3:00), per questo il logger invita
     * a concedere il permesso. Riprogrammare sostituisce l'allarme precedente (stesso PendingIntent).
     */
    fun scheduleAlarm(at: Instant) {
        val trigger = at.toEpochMilli()
        val pending = alarmIntent()
        if (exactAlarmsAllowed()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun cancelAlarm() = alarmManager.cancel(alarmIntent())

    fun exactAlarmsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun canNotify(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && notifications.areNotificationsEnabled()
    }

    @Suppress("MissingPermission") // Verificato da canNotify() prima di ogni chiamata.
    private fun notify(id: Int, notification: android.app.Notification) = notifications.notify(id, notification)

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COUNTDOWN,
                context.getString(R.string.notif_channel_rest),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_rest_desc) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FINISHED,
                context.getString(R.string.notif_channel_rest_done),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_rest_done_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            },
        )
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, RestTimerAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openWorkoutIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_COUNTDOWN = "rest_timer"
        const val CHANNEL_FINISHED = "rest_timer_finished"
        const val NOTIFICATION_COUNTDOWN = 1001
        const val NOTIFICATION_FINISHED = 1002
        private const val REQUEST_ALARM = 2001
        private const val REQUEST_OPEN = 2002
        private const val FINISHED_TIMEOUT_MILLIS = 60_000L
    }
}

/** Riceve l'allarme di fine recupero, anche con l'app chiusa. */
class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EntryPointAccessors.fromApplication(context, RestTimerEntryPoint::class.java)
            .restTimerController()
            .complete(fromAlarm = true)
    }
}

/**
 * Il sistema avvisa quando l'utente concede le sveglie esatte: l'allarme in corso diventa puntuale
 * anche se l'utente non torna subito nell'app. (Alla revoca il sistema chiude l'app, nessun avviso.)
 */
class RestTimerAlarmAccessReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        EntryPointAccessors.fromApplication(context, RestTimerEntryPoint::class.java)
            .restTimerController()
            .refreshAlarmAccess()
    }
}

/** Schermata di sistema "Sveglie e promemoria" dell'app (null prima di Android 12, dove non serve). */
fun exactAlarmSettingsIntent(context: Context): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
    } else {
        null
    }

/** Accesso al grafo Hilt da un BroadcastReceiver dichiarato nel manifest. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RestTimerEntryPoint {
    fun restTimerController(): RestTimerController
}
