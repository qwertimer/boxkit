package com.qwertimer.forge.domain

import com.google.common.truth.Truth.assertThat
import com.qwertimer.forge.domain.model.PlanStatus
import com.qwertimer.forge.domain.program.StreakCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test

class StreakCalculatorTest {

    // Monday 3 August 2026.
    private val monday = LocalDate.of(2026, 8, 3)
    private val weekdays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )

    @Test
    fun `no training days means no stats`() {
        val stats = StreakCalculator.compute(
            today = monday,
            trainingDays = emptySet(),
            statuses = emptyMap(),
        )

        assertThat(stats.currentStreak).isEqualTo(0)
        assertThat(stats.scheduledLast30).isEqualTo(0)
    }

    @Test
    fun `consecutive completions build a streak`() {
        val today = monday.plusDays(4) // Friday
        val statuses = (0..4).associate { monday.plusDays(it.toLong()) to PlanStatus.COMPLETED }

        val stats = StreakCalculator.compute(today, weekdays, statuses, historyStart = monday)

        assertThat(stats.currentStreak).isEqualTo(5)
        assertThat(stats.longestStreak).isEqualTo(5)
    }

    @Test
    fun `a rest day does not break a streak`() {
        val trainingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val statuses = mapOf(
            monday to PlanStatus.COMPLETED,
            monday.plusDays(2) to PlanStatus.COMPLETED,
            monday.plusDays(4) to PlanStatus.COMPLETED,
        )

        val stats = StreakCalculator.compute(
            today = monday.plusDays(4),
            trainingDays = trainingDays,
            statuses = statuses,
            historyStart = monday,
        )

        assertThat(stats.currentStreak).isEqualTo(3)
    }

    @Test
    fun `a skipped session breaks the streak`() {
        val statuses = mapOf(
            monday to PlanStatus.COMPLETED,
            monday.plusDays(1) to PlanStatus.SKIPPED,
            monday.plusDays(2) to PlanStatus.COMPLETED,
            monday.plusDays(3) to PlanStatus.COMPLETED,
        )

        val stats = StreakCalculator.compute(
            today = monday.plusDays(3),
            trainingDays = weekdays,
            statuses = statuses,
            historyStart = monday,
        )

        assertThat(stats.currentStreak).isEqualTo(2)
        assertThat(stats.longestStreak).isEqualTo(2)
        assertThat(stats.skippedLast30).isEqualTo(1)
    }

    @Test
    fun `a scheduled day with nothing logged counts as missed`() {
        val statuses = mapOf(monday to PlanStatus.COMPLETED)

        val stats = StreakCalculator.compute(
            today = monday.plusDays(3),
            trainingDays = weekdays,
            statuses = statuses,
            historyStart = monday,
        )

        assertThat(stats.missedLast30).isEqualTo(2) // Tuesday and Wednesday
        assertThat(stats.currentStreak).isEqualTo(0)
    }

    @Test
    fun `today being pending does not break the streak yet`() {
        val statuses = mapOf(
            monday to PlanStatus.COMPLETED,
            monday.plusDays(1) to PlanStatus.COMPLETED,
            monday.plusDays(2) to PlanStatus.PENDING,
        )

        val stats = StreakCalculator.compute(
            today = monday.plusDays(2),
            trainingDays = weekdays,
            statuses = statuses,
            historyStart = monday,
        )

        assertThat(stats.currentStreak).isEqualTo(2)
        assertThat(stats.missedLast30).isEqualTo(0)
    }

    @Test
    fun `completing today extends the streak immediately`() {
        val statuses = mapOf(
            monday to PlanStatus.COMPLETED,
            monday.plusDays(1) to PlanStatus.COMPLETED,
            monday.plusDays(2) to PlanStatus.COMPLETED,
        )

        val stats = StreakCalculator.compute(
            today = monday.plusDays(2),
            trainingDays = weekdays,
            statuses = statuses,
            historyStart = monday,
        )

        assertThat(stats.currentStreak).isEqualTo(3)
    }

    @Test
    fun `longest streak survives a later break`() {
        val statuses = buildMap {
            (0..4).forEach { put(monday.plusDays(it.toLong()), PlanStatus.COMPLETED) }
            put(monday.plusDays(7), PlanStatus.SKIPPED)
            put(monday.plusDays(8), PlanStatus.COMPLETED)
        }

        val stats = StreakCalculator.compute(
            today = monday.plusDays(8),
            trainingDays = weekdays,
            statuses = statuses,
            historyStart = monday,
        )

        assertThat(stats.longestStreak).isEqualTo(5)
        assertThat(stats.currentStreak).isEqualTo(1)
    }

    @Test
    fun `compliance is completed over scheduled`() {
        val statuses = mapOf(
            monday to PlanStatus.COMPLETED,
            monday.plusDays(1) to PlanStatus.COMPLETED,
            monday.plusDays(2) to PlanStatus.SKIPPED,
            monday.plusDays(3) to PlanStatus.COMPLETED,
        )

        val stats = StreakCalculator.compute(
            today = monday.plusDays(3),
            trainingDays = weekdays,
            statuses = statuses,
            historyStart = monday,
        )

        assertThat(stats.scheduledLast30).isEqualTo(4)
        assertThat(stats.completedLast30).isEqualTo(3)
        assertThat(stats.compliancePercent).isEqualTo(75)
    }

    @Test
    fun `training day index advances once per training day, not per calendar day`() {
        val trainingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)

        val mondayIndex = StreakCalculator.trainingDayIndex(monday, trainingDays)
        val tuesdayIndex = StreakCalculator.trainingDayIndex(monday.plusDays(1), trainingDays)
        val thursdayIndex = StreakCalculator.trainingDayIndex(monday.plusDays(3), trainingDays)
        val nextMondayIndex = StreakCalculator.trainingDayIndex(monday.plusDays(7), trainingDays)

        assertThat(tuesdayIndex).isEqualTo(mondayIndex)
        assertThat(thursdayIndex).isEqualTo(mondayIndex + 1)
        assertThat(nextMondayIndex).isEqualTo(mondayIndex + 2)
    }
}
