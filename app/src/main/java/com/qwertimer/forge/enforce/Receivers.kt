package com.qwertimer.forge.enforce

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qwertimer.forge.data.prefs.SettingsRepository
import com.qwertimer.forge.data.repo.TrainingRepository
import com.qwertimer.forge.domain.model.PlanStatus
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs a suspending block from a receiver without letting the process die underneath it. */
private fun BroadcastReceiver.goAsyncScope(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("Forge", "Alarm work failed", e)
        } finally {
            pending.finish()
        }
    }
}

/**
 * Every reminder and every escalation lands here.
 *
 * The receiver re-reads state on each firing rather than trusting what was true when the chain
 * started, so finishing a session mid-escalation stops the nagging immediately, and changing the
 * interval in settings takes effect on the very next hop.
 */
@AndroidEntryPoint
class NagAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var training: TrainingRepository

    @Inject lateinit var scheduler: EnforcementScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val stage = intent.getIntExtra(EXTRA_STAGE, 0)
        val firstReminder = intent.getStringExtra(EXTRA_FIRST_REMINDER)
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?: LocalDateTime.now()

        val appContext = context.applicationContext
        goAsyncScope { handle(appContext, stage, firstReminder) }
    }

    private suspend fun handle(context: Context, stage: Int, firstReminder: LocalDateTime) {
        val config = settings.current()
        val notifier = Notifier(context)
        val date = firstReminder.toLocalDate()
        val now = LocalDateTime.now()

        // A chain from a previous day (device off overnight, say) is stale. Start a fresh one.
        if (date != now.toLocalDate()) {
            notifier.clear()
            scheduler.scheduleNextReminder(now)
            return
        }

        val plan = training.ensurePlanFor(date)
        if (plan == null || plan.status != PlanStatus.PENDING) {
            // Rest day, or already logged. Either way there is nothing to nag about.
            notifier.clear()
            scheduler.scheduleNextReminder(now)
            return
        }

        val streak = runCatching { training.statsOnce(date).currentStreak }.getOrDefault(0)
        val body = ReminderSchedule.message(
            stage = stage,
            streak = streak,
            focus = plan.focus.label,
            minutes = plan.estimatedMinutes,
        )

        if (ReminderSchedule.shouldEnforce(stage, config)) {
            notifier.postEnforcement(title = "Log your session", body = body)
        } else {
            notifier.postNag(
                stage = stage,
                firstReminder = firstReminder,
                title = if (stage == 0) "Training day" else "Still waiting",
                body = body,
                escalating = stage > 0,
            )
        }

        val nextStage = stage + 1
        val nextTime = ReminderSchedule.nagTime(firstReminder, nextStage, config)
        val exhausted = ReminderSchedule.isExhausted(nextStage, config)
        // The chain always dies at midnight; tomorrow gets its own first reminder.
        if (!exhausted && nextTime.toLocalDate() == date) {
            scheduler.scheduleNag(firstReminder, nextStage, config)
        } else {
            scheduler.scheduleNextReminder(now)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.qwertimer.forge.action.NAG_FIRE"
        const val EXTRA_STAGE = "stage"
        const val EXTRA_FIRST_REMINDER = "first_reminder"
    }
}

/** Handles the notification's own buttons. */
@AndroidEntryPoint
class NagActionReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: EnforcementScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val stage = intent.getIntExtra(NagAlarmReceiver.EXTRA_STAGE, 0)
        val anchor = intent.getStringExtra(NagAlarmReceiver.EXTRA_FIRST_REMINDER)
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?: LocalDateTime.now()

        when (intent.action) {
            ACTION_SNOOZE -> {
                Notifier(appContext).clear()
                // Snoozing moves the clock but not the escalation: the next message is still the
                // next one in the sequence, which is what makes snoozing a poor strategy.
                scheduler.scheduleNagAt(
                    time = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES.toLong()),
                    stage = stage + 1,
                    firstReminder = anchor,
                )
            }

            ACTION_DISMISS -> Notifier(appContext).clear()
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.qwertimer.forge.action.SNOOZE"
        const val ACTION_DISMISS = "com.qwertimer.forge.action.DISMISS"
        const val SNOOZE_MINUTES = 15
    }
}

/** Alarms do not survive a reboot or a clock change, so re-arm on both. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: EnforcementScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> goAsyncScope { scheduler.scheduleNextReminder() }
        }
    }
}
