package com.qwertimer.forge.enforce

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.qwertimer.forge.data.prefs.SettingsRepository
import com.qwertimer.forge.data.prefs.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every alarm the app sets.
 *
 * There is exactly one pending alarm at a time. Each firing decides what the next one should be,
 * which keeps the escalation chain self-healing: change the nag interval mid-chain and the next
 * hop picks up the new value.
 */
@Singleton
class EnforcementScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    /** Arm the first reminder for the next scheduled training day. */
    suspend fun scheduleNextReminder(now: LocalDateTime = LocalDateTime.now()) {
        val config = settings.current()
        val next = ReminderSchedule.nextReminder(now, config)
        if (next == null) {
            cancel()
            return
        }
        armAt(next, stage = 0, firstReminder = next)
    }

    /** Arm the next escalation step in an in-progress chain. */
    fun scheduleNag(firstReminder: LocalDateTime, stage: Int, config: UserSettings) {
        armAt(
            time = ReminderSchedule.nagTime(firstReminder, stage, config),
            stage = stage,
            firstReminder = firstReminder,
        )
    }

    /**
     * Arm a specific stage at a specific time, keeping [firstReminder] as the chain's anchor.
     * Used by snooze, which moves the clock without resetting the escalation.
     */
    fun scheduleNagAt(time: LocalDateTime, stage: Int, firstReminder: LocalDateTime) {
        armAt(time, stage, firstReminder)
    }

    fun cancel() {
        alarmManager?.cancel(pendingIntent(stage = 0, firstReminder = null))
    }

    private fun armAt(time: LocalDateTime, stage: Int, firstReminder: LocalDateTime) {
        val manager = alarmManager ?: return
        val triggerAt = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = pendingIntent(stage, firstReminder)

        try {
            if (canScheduleExact()) {
                // setAlarmClock survives Doze and shows the user an alarm indicator, which is
                // honest about what this app is doing to their evening.
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent()), intent)
            } else {
                // Without the exact-alarm permission the OS may delay this by minutes. Nagging a
                // little late beats not nagging at all.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    private fun pendingIntent(stage: Int, firstReminder: LocalDateTime?): PendingIntent {
        val intent = Intent(context, NagAlarmReceiver::class.java)
            .setAction(NagAlarmReceiver.ACTION_FIRE)
            .putExtra(NagAlarmReceiver.EXTRA_STAGE, stage)
            .putExtra(NagAlarmReceiver.EXTRA_FIRST_REMINDER, firstReminder?.toString())

        // A single request code means a newly armed alarm always replaces the pending one, so the
        // chain can never fork into two competing escalations.
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_SHOW,
        Intent(context, com.qwertimer.forge.MainActivity::class.java)
            .setAction(com.qwertimer.forge.MainActivity.ACTION_OPEN_WORKOUT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val TAG = "EnforcementScheduler"
        const val REQUEST_CODE = 3001
        const val REQUEST_SHOW = 3002
    }
}
