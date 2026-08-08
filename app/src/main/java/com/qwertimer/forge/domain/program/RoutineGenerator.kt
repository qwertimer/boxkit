package com.qwertimer.forge.domain.program

import com.qwertimer.forge.domain.model.BlockType
import com.qwertimer.forge.domain.model.BodyRegion
import com.qwertimer.forge.domain.model.Exercise
import com.qwertimer.forge.domain.model.ExerciseCategory
import com.qwertimer.forge.domain.model.FitnessLevel
import com.qwertimer.forge.domain.model.SessionFocus
import com.qwertimer.forge.domain.model.WorkoutBlock
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Inputs that shape a session. Everything the generator needs, and nothing it does not. */
data class RoutineRequest(
    val date: LocalDate,
    val level: FitnessLevel,
    val sessionMinutes: Int,
    val focus: SessionFocus,
    /** Exercise ids used in the last couple of sessions; avoided where the library allows. */
    val recentlyUsed: Set<String> = emptySet(),
    /** Off by default for anyone with dodgy knees; gates jumping movements. */
    val allowHighImpact: Boolean = true,
)

data class GeneratedRoutine(
    val focus: SessionFocus,
    val blocks: List<WorkoutBlock>,
    val estimatedMinutes: Int,
)

/**
 * Builds a daily session out of the exercise library.
 *
 * The generator is deterministic for a given date: opening the app twice on the same day yields
 * the same routine, so a session can be half-finished and picked up later without it reshuffling
 * underneath you. Variety across days comes from seeding on the date plus steering away from
 * whatever was used in the previous sessions.
 */
class RoutineGenerator(private val library: List<Exercise>) {

    fun generate(request: RoutineRequest): GeneratedRoutine {
        val rng = Random(request.date.toEpochDay())
        val eligible = library.filter { exercise ->
            exercise.minLevel <= request.level.level &&
                (request.allowHighImpact || exercise.impact < HIGH_IMPACT)
        }
        require(eligible.isNotEmpty()) { "Exercise library has nothing for this level" }

        val used = mutableSetOf<String>()
        val blocks = mutableListOf<WorkoutBlock>()
        var order = 0

        fun add(
            exercise: Exercise,
            type: BlockType,
            sets: Int,
            reps: Int,
            seconds: Int,
            rest: Int,
        ) {
            used += exercise.id
            blocks += WorkoutBlock(
                exercise = exercise,
                blockType = type,
                orderIndex = order++,
                sets = sets,
                reps = reps,
                seconds = seconds,
                restSeconds = rest,
            )
        }

        val budget = request.sessionMinutes.coerceIn(MIN_SESSION_MINUTES, MAX_SESSION_MINUTES)
        val mainCount = when {
            budget <= 20 -> 3
            budget <= 35 -> 4
            else -> 5
        }
        val plyoCount = if (budget <= 20) 1 else 2
        val sets = when (request.level) {
            FitnessLevel.BEGINNER -> 2
            FitnessLevel.INTERMEDIATE -> 3
            FitnessLevel.ADVANCED -> 4
        }

        // Warm-up: raise the heart rate, then open the hips and shoulders.
        val warmupPool = eligible.filter {
            it.category == ExerciseCategory.MOBILITY ||
                (it.category == ExerciseCategory.CARDIO && it.impact < HIGH_IMPACT)
        }
        pick(warmupPool, WARMUP_COUNT, rng, used, request.recentlyUsed).forEach {
            add(it, BlockType.WARMUP, sets = 1, reps = it.defaultReps, seconds = WARMUP_SECONDS, rest = 0)
        }

        // Power work goes early, while the nervous system is fresh.
        val plyoPool = eligible.filter { it.category == ExerciseCategory.PLYOMETRIC }
        pick(plyoPool, plyoCount, rng, used, request.recentlyUsed).forEach {
            add(
                exercise = it,
                type = BlockType.PLYO,
                sets = max(2, sets - 1),
                reps = scaleReps(it.defaultReps, request.level),
                seconds = it.defaultSeconds,
                rest = PLYO_REST_SECONDS,
            )
        }

        // Main strength circuit, laid out against the day's focus.
        val strengthPool = eligible.filter { it.category == ExerciseCategory.BODYWEIGHT }
        regionPlan(request.focus).take(mainCount).forEach { region ->
            val candidate = pick(
                pool = strengthPool.filter { it.region == region },
                count = 1,
                rng = rng,
                used = used,
                recentlyUsed = request.recentlyUsed,
            ).firstOrNull()
                ?: pick(strengthPool, 1, rng, used, request.recentlyUsed).firstOrNull()
                ?: return@forEach
            add(
                exercise = candidate,
                type = BlockType.MAIN,
                sets = sets,
                reps = scaleReps(candidate.defaultReps, request.level),
                seconds = candidate.defaultSeconds,
                rest = MAIN_REST_SECONDS,
            )
        }

        // Conditioning finisher.
        val cardioPool = eligible.filter { it.category == ExerciseCategory.CARDIO }
        pick(cardioPool, 1, rng, used, request.recentlyUsed).forEach {
            add(
                exercise = it,
                type = BlockType.FINISHER,
                sets = if (budget <= 20) 3 else 4,
                reps = it.defaultReps,
                seconds = finisherSeconds(request.level),
                rest = FINISHER_REST_SECONDS,
            )
        }

        // Cool-down is not optional; it is the cheapest injury insurance there is.
        val cooldownPool = eligible.filter { it.category == ExerciseCategory.MOBILITY }
        pick(cooldownPool, COOLDOWN_COUNT, rng, used, request.recentlyUsed).forEach {
            add(it, BlockType.COOLDOWN, sets = 1, reps = it.defaultReps, seconds = COOLDOWN_SECONDS, rest = 0)
        }

        return GeneratedRoutine(
            focus = request.focus,
            blocks = blocks,
            estimatedMinutes = estimateMinutes(blocks),
        )
    }

    /**
     * Draw [count] distinct exercises, preferring ones that were not used in recent sessions.
     * Falls back to recently-used movements rather than returning a short list, because a session
     * with a hole in it is worse than a session that repeats a push-up.
     */
    private fun pick(
        pool: List<Exercise>,
        count: Int,
        rng: Random,
        used: MutableSet<String>,
        recentlyUsed: Set<String>,
    ): List<Exercise> {
        if (count <= 0 || pool.isEmpty()) return emptyList()
        val available = pool.filterNot { it.id in used }
        val fresh = available.filterNot { it.id in recentlyUsed }
        val ordered = fresh.shuffled(rng) + available.filter { it.id in recentlyUsed }.shuffled(rng)
        return ordered.take(count)
    }

    private fun regionPlan(focus: SessionFocus): List<BodyRegion> = when (focus) {
        SessionFocus.FULL_BODY -> listOf(
            BodyRegion.LOWER,
            BodyRegion.UPPER_PUSH,
            BodyRegion.UPPER_PULL,
            BodyRegion.CORE,
            BodyRegion.FULL_BODY,
        )

        SessionFocus.UPPER -> listOf(
            BodyRegion.UPPER_PUSH,
            BodyRegion.UPPER_PULL,
            BodyRegion.CORE,
            BodyRegion.UPPER_PUSH,
            BodyRegion.UPPER_PULL,
        )

        SessionFocus.LOWER -> listOf(
            BodyRegion.LOWER,
            BodyRegion.LOWER,
            BodyRegion.CORE,
            BodyRegion.LOWER,
            BodyRegion.FULL_BODY,
        )

        SessionFocus.CONDITIONING -> listOf(
            BodyRegion.FULL_BODY,
            BodyRegion.LOWER,
            BodyRegion.CORE,
            BodyRegion.UPPER_PUSH,
            BodyRegion.FULL_BODY,
        )
    }

    private fun scaleReps(base: Int, level: FitnessLevel): Int = when (level) {
        FitnessLevel.BEGINNER -> max(4, (base * 0.7).roundToInt())
        FitnessLevel.INTERMEDIATE -> base
        FitnessLevel.ADVANCED -> (base * 1.3).roundToInt()
    }

    private fun finisherSeconds(level: FitnessLevel): Int = when (level) {
        FitnessLevel.BEGINNER -> 30
        FitnessLevel.INTERMEDIATE -> 40
        FitnessLevel.ADVANCED -> 50
    }

    private fun estimateMinutes(blocks: List<WorkoutBlock>): Int {
        val seconds = blocks.sumOf { block ->
            val work = if (block.exercise.timeBased || block.seconds > 0) {
                block.seconds
            } else {
                block.reps * SECONDS_PER_REP
            }
            block.sets * (work + block.restSeconds)
        }
        return max(1, (seconds / 60.0).roundToInt())
    }

    companion object {
        private const val HIGH_IMPACT = 2
        private const val WARMUP_COUNT = 3
        private const val COOLDOWN_COUNT = 2
        private const val WARMUP_SECONDS = 40
        private const val COOLDOWN_SECONDS = 45
        private const val MAIN_REST_SECONDS = 45
        private const val PLYO_REST_SECONDS = 60
        private const val FINISHER_REST_SECONDS = 20
        private const val SECONDS_PER_REP = 3
        const val MIN_SESSION_MINUTES = 10
        const val MAX_SESSION_MINUTES = 90

        /**
         * Rotates focus across training days so back-to-back sessions do not repeat an emphasis.
         * [trainingDayIndex] counts training days since the epoch, not calendar days, so a
         * rest day does not advance the rotation.
         */
        fun focusFor(trainingDayIndex: Int): SessionFocus {
            val order = listOf(
                SessionFocus.FULL_BODY,
                SessionFocus.UPPER,
                SessionFocus.CONDITIONING,
                SessionFocus.LOWER,
            )
            return order[Math.floorMod(trainingDayIndex, order.size)]
        }
    }
}
