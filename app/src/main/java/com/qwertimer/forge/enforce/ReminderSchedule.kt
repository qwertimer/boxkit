package com.qwertimer.forge.enforce

import com.qwertimer.forge.data.prefs.UserSettings
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Pure scheduling arithmetic, kept away from [android.app.AlarmManager] so it can be tested.
 */
object ReminderSchedule {

    /** How many days ahead to look for the next training day before giving up. */
    private const val SEARCH_HORIZON_DAYS = 8

    /**
     * The next moment a first-reminder should fire: the reminder time on the next scheduled
     * training day at or after [now]. Null when no training days are selected at all.
     */
    fun nextReminder(now: LocalDateTime, settings: UserSettings): LocalDateTime? {
        if (settings.trainingDays.isEmpty()) return null
        var day: LocalDate = now.toLocalDate()
        repeat(SEARCH_HORIZON_DAYS) {
            if (settings.isTrainingDay(day.dayOfWeek)) {
                val candidate = LocalDateTime.of(day, settings.reminderTime)
                if (candidate.isAfter(now)) return candidate
            }
            day = day.plusDays(1)
        }
        return null
    }

    /**
     * When the nag at [stage] should fire, given the first reminder went out at [firstReminder].
     * Stage 0 is the first reminder itself; every stage after that is one interval later.
     */
    fun nagTime(firstReminder: LocalDateTime, stage: Int, settings: UserSettings): LocalDateTime =
        firstReminder.plusMinutes(stage.toLong() * settings.nagIntervalMinutes)

    /**
     * True once the polite escalation is exhausted and the full-screen alarm should take over.
     * Honours the settings toggle, so someone who wants nagging without hijacking never gets it.
     */
    fun shouldEnforce(stage: Int, settings: UserSettings): Boolean =
        settings.fullScreenEnforcement && stage > settings.maxNags

    /** True when [stage] is past the last nag and enforcement is off — nothing more to schedule. */
    fun isExhausted(stage: Int, settings: UserSettings): Boolean =
        stage > settings.maxNags && !settings.fullScreenEnforcement

    /** Escalation copy. Deliberately blunt by the end — that is the entire point of the feature. */
    fun message(stage: Int, streak: Int, focus: String, minutes: Int): String = when (stage) {
        0 -> "Today's session: $focus, about $minutes minutes."
        1 -> "Still not logged. $focus, $minutes minutes. Start it now."
        2 -> if (streak > 0) {
            "$streak-day streak on the line. $minutes minutes is all it takes."
        } else {
            "Second reminder. $minutes minutes, $focus. No excuses queued up yet."
        }

        else -> if (streak > 0) {
            "Last call — your $streak-day streak ends at midnight unless you log this."
        } else {
            "Last call. Log the session or record why you're skipping it."
        }
    }
}
