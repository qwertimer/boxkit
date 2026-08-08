package com.qwertimer.forge.data.repo

import com.qwertimer.forge.data.db.ExerciseDao
import com.qwertimer.forge.data.db.ExerciseLibrary
import com.qwertimer.forge.data.db.WorkoutBlockEntity
import com.qwertimer.forge.data.db.WorkoutDao
import com.qwertimer.forge.data.db.WorkoutPlanEntity
import com.qwertimer.forge.data.prefs.SettingsRepository
import com.qwertimer.forge.domain.model.ComplianceStats
import com.qwertimer.forge.domain.model.Exercise
import com.qwertimer.forge.domain.model.PlanStatus
import com.qwertimer.forge.domain.model.WorkoutPlan
import com.qwertimer.forge.domain.program.RoutineGenerator
import com.qwertimer.forge.domain.program.RoutineRequest
import com.qwertimer.forge.domain.program.StreakCalculator
import java.time.LocalDate
import com.qwertimer.forge.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class TrainingRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private var libraryCache: List<Exercise>? = null

    /** Populates the movement library on first run and keeps it current across app updates. */
    suspend fun ensureLibrary(): List<Exercise> = withContext(io) {
        libraryCache?.let { return@withContext it }
        exerciseDao.upsertAll(ExerciseLibrary.all)
        exerciseDao.all().map { it.toDomain() }.also { libraryCache = it }
    }

    fun planFor(date: LocalDate): Flow<WorkoutPlan?> =
        workoutDao.planForDay(date.toEpochDay()).map { row ->
            if (row == null) {
                null
            } else {
                val byId = ensureLibrary().associateBy { it.id }
                row.toDomain(byId)
            }
        }

    /**
     * Return the plan for [date], generating one if the day is scheduled and nothing exists yet.
     * Returns null on a rest day — the absence of a plan is what makes it a rest day.
     */
    suspend fun ensurePlanFor(date: LocalDate): WorkoutPlan? = withContext(io) {
        val library = ensureLibrary()
        val byId = library.associateBy { it.id }
        workoutDao.planForDayOnce(date.toEpochDay())?.let { return@withContext it.toDomain(byId) }

        val config = settings.current()
        if (!config.isTrainingDay(date.dayOfWeek)) return@withContext null

        val recent = workoutDao.recentExerciseIds(date.toEpochDay()).toSet()
        val focus = RoutineGenerator.focusFor(
            StreakCalculator.trainingDayIndex(date, config.trainingDays),
        )
        val routine = RoutineGenerator(library).generate(
            RoutineRequest(
                date = date,
                level = config.fitnessLevel,
                sessionMinutes = config.sessionMinutes,
                focus = focus,
                recentlyUsed = recent,
                allowHighImpact = config.allowHighImpact,
            ),
        )

        val planId = workoutDao.replacePlan(
            plan = WorkoutPlanEntity(
                epochDay = date.toEpochDay(),
                focus = routine.focus,
                status = PlanStatus.PENDING,
                estimatedMinutes = routine.estimatedMinutes,
                generatedAt = System.currentTimeMillis(),
            ),
        ) { id ->
            routine.blocks.map { block ->
                WorkoutBlockEntity(
                    planId = id,
                    exerciseId = block.exercise.id,
                    blockType = block.blockType,
                    orderIndex = block.orderIndex,
                    sets = block.sets,
                    reps = block.reps,
                    seconds = block.seconds,
                    restSeconds = block.restSeconds,
                )
            }
        }

        workoutDao.planForDayOnce(date.toEpochDay())?.toDomain(byId)
            ?: error("Plan $planId vanished immediately after being written")
    }

    /** Throw away today's plan and roll a fresh one. */
    suspend fun regenerate(date: LocalDate): WorkoutPlan? = withContext(io) {
        workoutDao.deletePlanForDay(date.toEpochDay())
        ensurePlanFor(date)
    }

    suspend fun setBlockCompleted(blockId: Long, completed: Boolean) = withContext(io) {
        workoutDao.setBlockCompleted(blockId, completed)
    }

    suspend fun markCompleted(planId: Long) = withContext(io) {
        workoutDao.setPlanStatus(planId, PlanStatus.COMPLETED, null, System.currentTimeMillis())
    }

    /** A skip is allowed, but never silent: the reason is recorded and shown in the stats. */
    suspend fun markSkipped(planId: Long, reason: String) = withContext(io) {
        workoutDao.setPlanStatus(
            planId = planId,
            status = PlanStatus.SKIPPED,
            reason = reason.trim().ifBlank { "No reason given" },
            closedAt = System.currentTimeMillis(),
        )
    }

    suspend fun statusFor(date: LocalDate): PlanStatus? = withContext(io) {
        workoutDao.planForDayOnce(date.toEpochDay())?.plan?.status
    }

    fun stats(today: LocalDate): Flow<ComplianceStats> = combine(
        workoutDao.statusesSince(today.minusDays(HISTORY_DAYS).toEpochDay()),
        settings.settings,
    ) { rows, config ->
        StreakCalculator.compute(
            today = today,
            trainingDays = config.trainingDays,
            statuses = rows.associate { LocalDate.ofEpochDay(it.epochDay) to it.status },
            historyStart = today.minusDays(HISTORY_DAYS),
        )
    }

    suspend fun statsOnce(today: LocalDate): ComplianceStats = withContext(io) {
        stats(today).first()
    }

    private companion object {
        const val HISTORY_DAYS = 365L
    }
}
