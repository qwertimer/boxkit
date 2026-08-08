package com.qwertimer.forge.domain.model

import java.time.LocalDate

enum class ExerciseCategory(val label: String) {
    CARDIO("Cardio"),
    PLYOMETRIC("Plyometrics"),
    BODYWEIGHT("Bodyweight"),
    MOBILITY("Mobility"),
}

enum class BodyRegion {
    UPPER_PUSH,
    UPPER_PULL,
    LOWER,
    CORE,
    FULL_BODY,
}

/**
 * Position of a block within a session. The generator always lays a session out in this order.
 */
enum class BlockType(val label: String) {
    WARMUP("Warm-up"),
    MAIN("Main circuit"),
    PLYO("Power"),
    FINISHER("Finisher"),
    COOLDOWN("Cool-down"),
}

enum class PlanStatus {
    PENDING,
    COMPLETED,
    SKIPPED,
}

/**
 * Where a session came from.
 *
 * The distinction matters for the stats: only the programmed session on a training day can be
 * missed or skipped, so only [SCHEDULED] feeds streaks and compliance. Extra sessions are credit,
 * never obligation — training on a rest day should not create something new to fail at.
 */
enum class PlanOrigin {
    /** The day's programmed session. At most one per date, and the only one that gets nagged. */
    SCHEDULED,

    /** Added by hand from the Train screen. Any number, on any day, including rest days. */
    AD_HOC,
}

/** Rotating emphasis so consecutive sessions do not hammer the same tissue. */
enum class SessionFocus(val label: String) {
    FULL_BODY("Full body"),
    UPPER("Upper body"),
    LOWER("Lower body"),
    CONDITIONING("Conditioning"),
}

enum class FitnessLevel(val level: Int, val label: String) {
    BEGINNER(1, "Beginner"),
    INTERMEDIATE(2, "Intermediate"),
    ADVANCED(3, "Advanced"),
}

data class Exercise(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    val region: BodyRegion,
    /** Lowest fitness level this movement is appropriate for. */
    val minLevel: Int,
    /** 0 = no impact, 1 = moderate, 2 = high impact (jumping). */
    val impact: Int,
    val timeBased: Boolean,
    val defaultReps: Int,
    val defaultSeconds: Int,
    val cue: String,
)

/** One line in a session: an exercise plus its prescription. */
data class WorkoutBlock(
    val id: Long = 0L,
    val exercise: Exercise,
    val blockType: BlockType,
    val orderIndex: Int,
    val sets: Int,
    val reps: Int,
    val seconds: Int,
    val restSeconds: Int,
    val completed: Boolean = false,
) {
    val prescription: String
        get() = buildString {
            if (sets > 1) append("$sets × ")
            if (exercise.timeBased) append("${seconds}s") else append("$reps reps")
        }
}

data class WorkoutPlan(
    val id: Long = 0L,
    val date: LocalDate,
    val focus: SessionFocus,
    val status: PlanStatus,
    val estimatedMinutes: Int,
    val origin: PlanOrigin = PlanOrigin.SCHEDULED,
    val skipReason: String? = null,
    val blocks: List<WorkoutBlock> = emptyList(),
) {
    val isScheduled: Boolean get() = origin == PlanOrigin.SCHEDULED
    val isDone: Boolean get() = status != PlanStatus.PENDING
    val completedBlocks: Int get() = blocks.count { it.completed }
    val progress: Float
        get() = if (blocks.isEmpty()) 0f else completedBlocks.toFloat() / blocks.size
}

data class ComplianceStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val completedLast30: Int,
    val scheduledLast30: Int,
    val skippedLast30: Int,
    val missedLast30: Int,
    /** Completed sessions beyond the programme — rest-day work and second sessions. */
    val bonusLast30: Int = 0,
) {
    /** Percentage of scheduled sessions in the last 30 days that were actually completed. */
    val compliancePercent: Int
        get() = if (scheduledLast30 == 0) 0 else (completedLast30 * 100) / scheduledLast30
}
