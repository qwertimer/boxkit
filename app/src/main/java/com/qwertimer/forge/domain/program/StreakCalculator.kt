package com.qwertimer.forge.domain.program

import com.qwertimer.forge.domain.model.ComplianceStats
import com.qwertimer.forge.domain.model.PlanStatus
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Turns the raw plan history into the numbers on the stats screen.
 *
 * Only scheduled training days count. A rest day is neither a win nor a miss, so taking Sunday off
 * cannot break a streak — but a scheduled day that quietly went by with nothing logged is a miss,
 * which is the whole point of tracking this.
 */
object StreakCalculator {

    /** How far back the stats screen looks for its rolling window. */
    const val WINDOW_DAYS = 30

    /** Days from the Monday preceding the epoch to epoch day 0 (a Thursday). */
    private const val MONDAY_OFFSET = 3L

    fun compute(
        today: LocalDate,
        trainingDays: Set<DayOfWeek>,
        statuses: Map<LocalDate, PlanStatus>,
        historyStart: LocalDate = today.minusDays(365),
    ): ComplianceStats {
        if (trainingDays.isEmpty()) return EMPTY

        val scheduled = generateSequence(historyStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .filter { it.dayOfWeek in trainingDays }
            .toList()

        // Today is still in play until it ends, so a pending session today is not yet a miss.
        val settled = scheduled.filterNot { it == today && statuses[it] != PlanStatus.COMPLETED }

        var current = 0
        for (date in settled.asReversed()) {
            if (statuses[date] == PlanStatus.COMPLETED) current++ else break
        }
        // A session completed today extends the streak even though it is not "settled" yet.
        if (statuses[today] == PlanStatus.COMPLETED && today.dayOfWeek in trainingDays) {
            current = settled.asReversed()
                .takeWhile { statuses[it] == PlanStatus.COMPLETED }
                .size
        }

        var longest = 0
        var run = 0
        for (date in settled) {
            if (statuses[date] == PlanStatus.COMPLETED) {
                run++
                if (run > longest) longest = run
            } else {
                run = 0
            }
        }
        if (current > longest) longest = current

        val windowStart = today.minusDays((WINDOW_DAYS - 1).toLong())
        val window = scheduled.filter { !it.isBefore(windowStart) }
        val completed = window.count { statuses[it] == PlanStatus.COMPLETED }
        val skipped = window.count { statuses[it] == PlanStatus.SKIPPED }
        val missed = window.count { date ->
            date != today && statuses[date].let { it == null || it == PlanStatus.PENDING }
        }

        return ComplianceStats(
            currentStreak = current,
            longestStreak = longest,
            completedLast30 = completed,
            scheduledLast30 = window.size,
            skippedLast30 = skipped,
            missedLast30 = missed,
        )
    }

    /**
     * How many training days have elapsed up to and including [date]. Drives focus rotation, so
     * that rest days do not advance the cycle.
     */
    fun trainingDayIndex(date: LocalDate, trainingDays: Set<DayOfWeek>): Int {
        if (trainingDays.isEmpty()) return date.toEpochDay().toInt()
        // Epoch day 0 is a Thursday, so shift by three to make the week buckets start on Monday —
        // otherwise the within-week count below, which is Monday-first, disagrees with the bucket
        // boundary and the index goes backwards mid-week.
        val weeks = Math.floorDiv(date.toEpochDay() + MONDAY_OFFSET, 7L).toInt()
        val perWeek = trainingDays.size
        val withinWeek = trainingDays.count { it.value <= date.dayOfWeek.value }
        return weeks * perWeek + withinWeek
    }

    private val EMPTY = ComplianceStats(
        currentStreak = 0,
        longestStreak = 0,
        completedLast30 = 0,
        scheduledLast30 = 0,
        skippedLast30 = 0,
        missedLast30 = 0,
    )
}
