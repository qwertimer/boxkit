package com.qwertimer.forge.domain

import com.google.common.truth.Truth.assertThat
import com.qwertimer.forge.data.db.ExerciseLibrary
import com.qwertimer.forge.data.repo.toDomain
import com.qwertimer.forge.domain.model.BlockType
import com.qwertimer.forge.domain.model.ExerciseCategory
import com.qwertimer.forge.domain.model.FitnessLevel
import com.qwertimer.forge.domain.model.SessionFocus
import com.qwertimer.forge.domain.program.RoutineGenerator
import com.qwertimer.forge.domain.program.RoutineRequest
import java.time.LocalDate
import org.junit.Test

class RoutineGeneratorTest {

    private val library = ExerciseLibrary.all.map { it.toDomain() }
    private val generator = RoutineGenerator(library)

    private fun request(
        date: LocalDate = LocalDate.of(2026, 8, 10),
        level: FitnessLevel = FitnessLevel.INTERMEDIATE,
        minutes: Int = 30,
        focus: SessionFocus = SessionFocus.FULL_BODY,
        recentlyUsed: Set<String> = emptySet(),
        allowHighImpact: Boolean = true,
        variant: Int = 0,
    ) = RoutineRequest(date, level, minutes, focus, recentlyUsed, allowHighImpact, variant)

    @Test
    fun `session covers cardio, plyometrics and bodyweight`() {
        val routine = generator.generate(request())
        val categories = routine.blocks.map { it.exercise.category }.toSet()

        assertThat(categories).containsAtLeast(
            ExerciseCategory.CARDIO,
            ExerciseCategory.PLYOMETRIC,
            ExerciseCategory.BODYWEIGHT,
        )
    }

    @Test
    fun `session is warmed up first and cooled down last`() {
        val types = generator.generate(request()).blocks.map { it.blockType }

        assertThat(types.first()).isEqualTo(BlockType.WARMUP)
        assertThat(types.last()).isEqualTo(BlockType.COOLDOWN)
    }

    @Test
    fun `same date produces the same routine`() {
        val first = generator.generate(request())
        val second = generator.generate(request())

        assertThat(first.blocks.map { it.exercise.id })
            .isEqualTo(second.blocks.map { it.exercise.id })
    }

    @Test
    fun `a re-roll on the same date produces a different routine`() {
        // The bug this guards: seeding on the date alone made "new routine" hand back the
        // identical session, so the button looked broken.
        val original = generator.generate(request())
        val rerolled = generator.generate(request(variant = 1))

        assertThat(rerolled.blocks.map { it.exercise.id })
            .isNotEqualTo(original.blocks.map { it.exercise.id })
    }

    @Test
    fun `each variant on a date is distinct`() {
        val routines = (0..4).map { variant ->
            generator.generate(request(variant = variant)).blocks.map { it.exercise.id }
        }

        assertThat(routines.toSet()).hasSize(routines.size)
    }

    @Test
    fun `a variant cannot wander into another date's seed`() {
        // Variants are offset from the date seed by a wide stride; even an absurd number of
        // re-rolls must not reproduce the next day's session.
        val tomorrow = generator.generate(request(date = LocalDate.of(2026, 8, 11)))
            .blocks.map { it.exercise.id }

        (1..50).forEach { variant ->
            val rolled = generator.generate(request(variant = variant)).blocks.map { it.exercise.id }
            assertThat(rolled).isNotEqualTo(tomorrow)
        }
    }

    @Test
    fun `different dates produce different routines`() {
        val monday = generator.generate(request(date = LocalDate.of(2026, 8, 10)))
        val tuesday = generator.generate(request(date = LocalDate.of(2026, 8, 11)))

        assertThat(monday.blocks.map { it.exercise.id })
            .isNotEqualTo(tuesday.blocks.map { it.exercise.id })
    }

    @Test
    fun `no exercise appears twice in a session`() {
        val ids = generator.generate(request()).blocks.map { it.exercise.id }

        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `high impact movements are excluded when the user opts out`() {
        val routine = generator.generate(request(allowHighImpact = false))

        assertThat(routine.blocks.map { it.exercise.impact }.max()).isLessThan(2)
    }

    @Test
    fun `beginners are never given advanced movements`() {
        val routine = generator.generate(request(level = FitnessLevel.BEGINNER))

        assertThat(routine.blocks.map { it.exercise.minLevel }.max()).isEqualTo(1)
    }

    @Test
    fun `longer sessions contain more work`() {
        val short = generator.generate(request(minutes = 15))
        val long = generator.generate(request(minutes = 45))

        assertThat(long.blocks.size).isGreaterThan(short.blocks.size)
        assertThat(long.estimatedMinutes).isGreaterThan(short.estimatedMinutes)
    }

    @Test
    fun `recently used movements are avoided when alternatives exist`() {
        val baseline = generator.generate(request())
        val avoid = baseline.blocks.map { it.exercise.id }.toSet()

        val fresh = generator.generate(request(recentlyUsed = avoid))
        val overlap = fresh.blocks.count { it.exercise.id in avoid }

        assertThat(overlap).isLessThan(baseline.blocks.size / 2)
    }

    @Test
    fun `upper focus loads the main circuit with upper body work`() {
        val routine = generator.generate(request(focus = SessionFocus.UPPER))
        val mainRegions = routine.blocks
            .filter { it.blockType == BlockType.MAIN }
            .map { it.exercise.region.name }

        assertThat(mainRegions.count { it.startsWith("UPPER") })
            .isAtLeast(mainRegions.size - 1)
    }

    @Test
    fun `focus rotates across consecutive training days`() {
        val focuses = (0..3).map { RoutineGenerator.focusFor(it) }

        assertThat(focuses).containsNoDuplicates()
        assertThat(RoutineGenerator.focusFor(4)).isEqualTo(RoutineGenerator.focusFor(0))
        // Negative indices are possible before the epoch; they must not blow up.
        assertThat(RoutineGenerator.focusFor(-1)).isEqualTo(RoutineGenerator.focusFor(3))
    }

    @Test
    fun `estimated duration stays in the neighbourhood of the requested budget`() {
        listOf(15, 30, 45, 60).forEach { minutes ->
            val estimate = generator.generate(request(minutes = minutes)).estimatedMinutes
            assertThat(estimate).isGreaterThan(0)
            assertThat(estimate).isLessThan(minutes * 3)
        }
    }
}
