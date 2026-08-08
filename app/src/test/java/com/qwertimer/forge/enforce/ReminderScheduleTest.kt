package com.qwertimer.forge.enforce

import com.google.common.truth.Truth.assertThat
import com.qwertimer.forge.data.prefs.UserSettings
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

class ReminderScheduleTest {

    private val settings = UserSettings(
        trainingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        reminderTime = LocalTime.of(18, 0),
        nagIntervalMinutes = 20,
        maxNags = 3,
        fullScreenEnforcement = true,
    )

    // Monday 3 August 2026.
    private val mondayMorning = LocalDateTime.of(2026, 8, 3, 9, 0)

    @Test
    fun `first reminder lands at the reminder time on a training day`() {
        val next = ReminderSchedule.nextReminder(mondayMorning, settings)

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 8, 3, 18, 0))
    }

    @Test
    fun `after the reminder time it rolls to the next training day`() {
        val mondayEvening = LocalDateTime.of(2026, 8, 3, 19, 30)

        val next = ReminderSchedule.nextReminder(mondayEvening, settings)

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 8, 5, 18, 0)) // Wednesday
    }

    @Test
    fun `rest days are skipped`() {
        val tuesday = LocalDateTime.of(2026, 8, 4, 9, 0)

        val next = ReminderSchedule.nextReminder(tuesday, settings)

        assertThat(next!!.dayOfWeek).isEqualTo(DayOfWeek.WEDNESDAY)
    }

    @Test
    fun `it wraps into the following week`() {
        val fridayEvening = LocalDateTime.of(2026, 8, 7, 20, 0)

        val next = ReminderSchedule.nextReminder(fridayEvening, settings)

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 8, 10, 18, 0)) // next Monday
    }

    @Test
    fun `no training days means nothing to schedule`() {
        val next = ReminderSchedule.nextReminder(
            mondayMorning,
            settings.copy(trainingDays = emptySet()),
        )

        assertThat(next).isNull()
    }

    @Test
    fun `nags step out by the configured interval`() {
        val first = LocalDateTime.of(2026, 8, 3, 18, 0)

        assertThat(ReminderSchedule.nagTime(first, 0, settings)).isEqualTo(first)
        assertThat(ReminderSchedule.nagTime(first, 1, settings))
            .isEqualTo(LocalDateTime.of(2026, 8, 3, 18, 20))
        assertThat(ReminderSchedule.nagTime(first, 3, settings))
            .isEqualTo(LocalDateTime.of(2026, 8, 3, 19, 0))
    }

    @Test
    fun `enforcement kicks in only after the nags are exhausted`() {
        assertThat(ReminderSchedule.shouldEnforce(0, settings)).isFalse()
        assertThat(ReminderSchedule.shouldEnforce(3, settings)).isFalse()
        assertThat(ReminderSchedule.shouldEnforce(4, settings)).isTrue()
    }

    @Test
    fun `enforcement never happens when the user turned it off`() {
        val gentle = settings.copy(fullScreenEnforcement = false)

        assertThat(ReminderSchedule.shouldEnforce(9, gentle)).isFalse()
        assertThat(ReminderSchedule.isExhausted(4, gentle)).isTrue()
        assertThat(ReminderSchedule.isExhausted(4, settings)).isFalse()
    }

    @Test
    fun `zero nags means the first reminder escalates straight to enforcement`() {
        val immediate = settings.copy(maxNags = 0)

        assertThat(ReminderSchedule.shouldEnforce(0, immediate)).isFalse()
        assertThat(ReminderSchedule.shouldEnforce(1, immediate)).isTrue()
    }

    @Test
    fun `messages escalate and mention the streak once there is one to lose`() {
        val first = ReminderSchedule.message(0, streak = 5, focus = "Full body", minutes = 30)
        val last = ReminderSchedule.message(3, streak = 5, focus = "Full body", minutes = 30)
        val noStreak = ReminderSchedule.message(3, streak = 0, focus = "Full body", minutes = 30)

        assertThat(first).contains("Full body")
        assertThat(last).contains("5-day streak")
        assertThat(noStreak).doesNotContain("streak")
    }
}
