package com.qwertimer.forge.data.repo

import com.qwertimer.forge.data.db.ExerciseDao
import com.qwertimer.forge.data.db.ExerciseLibrary
import com.qwertimer.forge.data.db.WorkoutBlockEntity
import com.qwertimer.forge.data.db.WorkoutDao
import com.qwertimer.forge.data.db.WorkoutPlanEntity
import com.qwertimer.forge.data.prefs.SettingsRepository
import com.qwertimer.forge.domain.model.ComplianceStats
import com.qwertimer.forge.domain.model.Exercise
import com.qwertimer.forge.domain.model.PlanOrigin
import com.qwertimer.forge.domain.model.PlanStatus
import com.qwertimer.forge.domain.model.SessionFocus
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

    /** Every session on [date] — the programmed one, if any, plus whatever was added by hand. */
    fun sessionsFor(date: LocalDate): Flow<List<WorkoutPlan>> =
        workoutDao.plansForDay(date.toEpochDay()).map { rows ->
            val byId = ensureLibrary().associateBy { it.id }
            rows.map { it.toDomain(byId) }
        }

    /** The programmed session only. This is what enforcement and the home screen care about. */
    fun planFor(date: LocalDate): Flow<WorkoutPlan?> =
        workoutDao.scheduledPlanForDay(date.toEpochDay()).map { row ->
            row?.toDomain(ensureLibrary().associateBy { it.id })
        }

    /**
     * Return the programmed plan for [date], generating one if the day is scheduled and nothing
     * exists yet. Returns null on a rest day — the absence of a programmed plan is what makes it a
     * rest day. Ad-hoc sessions are never created here; those are always deliberate.
     */
    suspend fun ensurePlanFor(date: LocalDate): WorkoutPlan? = withContext(io) {
        val byId = ensureLibrary().associateBy { it.id }
        workoutDao.scheduledPlanForDayOnce(date.toEpochDay())
            ?.let { return@withContext it.toDomain(byId) }

        val config = settings.current()
        if (!config.isTrainingDay(date.dayOfWeek)) return@withContext null

        val focus = RoutineGenerator.focusFor(
            StreakCalculator.trainingDayIndex(date, config.trainingDays),
        )
        generate(date, focus, PlanOrigin.SCHEDULED)
    }

    /**
     * Add a session to [date] regardless of whether it is a training day. Rest-day work and second
     * sessions both land here. It never becomes the programmed session, so it cannot be nagged and
     * cannot be counted as missed — extra training is credit, not a new obligation.
     */
    suspend fun addSession(date: LocalDate, focus: SessionFocus? = null): WorkoutPlan =
        withContext(io) {
            val config = settings.current()
            // Rotate on from the day's programmed focus so a bonus session hits something else.
            val chosen = focus ?: RoutineGenerator.focusFor(
                StreakCalculator.trainingDayIndex(date, config.trainingDays) +
                    workoutDao.maxVariantForDay(date.toEpochDay()) + 1,
            )
            generate(date, chosen, PlanOrigin.AD_HOC)
        }

    /**
     * Replace a session with a freshly rolled one, keeping its origin and date. The new routine
     * gets the next unused variant for the day, so it genuinely differs from what it replaced.
     */
    suspend fun reroll(planId: Long): WorkoutPlan? = withContext(io) {
        val existing = workoutDao.planById(planId) ?: return@withContext null
        val date = LocalDate.ofEpochDay(existing.plan.epochDay)
        workoutDao.deletePlan(planId)
        generate(date, existing.plan.focus, existing.plan.origin)
    }

    suspend fun deleteSession(planId: Long) = withContext(io) { workoutDao.deletePlan(planId) }

    private suspend fun generate(
        date: LocalDate,
        focus: SessionFocus,
        origin: PlanOrigin,
    ): WorkoutPlan {
        val library = ensureLibrary()
        val config = settings.current()
        val variant = workoutDao.maxVariantForDay(date.toEpochDay()) + 1
        val routine = RoutineGenerator(library).generate(
            RoutineRequest(
                date = date,
                level = config.fitnessLevel,
                sessionMinutes = config.sessionMinutes,
                focus = focus,
                recentlyUsed = workoutDao.recentExerciseIds(date.toEpochDay()).toSet(),
                allowHighImpact = config.allowHighImpact,
                variant = variant,
            ),
        )

        val planId = workoutDao.insertPlanWithBlocks(
            plan = WorkoutPlanEntity(
                epochDay = date.toEpochDay(),
                focus = routine.focus,
                status = PlanStatus.PENDING,
                estimatedMinutes = routine.estimatedMinutes,
                origin = origin,
                variant = variant,
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

        val byId = library.associateBy { it.id }
        return workoutDao.planById(planId)?.toDomain(byId)
            ?: error("Plan $planId vanished immediately after being written")
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
        workoutDao.scheduledPlanForDayOnce(date.toEpochDay())?.plan?.status
    }

    fun stats(today: LocalDate): Flow<ComplianceStats> = combine(
        workoutDao.scheduledStatusesSince(today.minusDays(HISTORY_DAYS).toEpochDay()),
        workoutDao.bonusCompletedSince(today.minusDays(StreakCalculator.WINDOW_DAYS - 1L).toEpochDay()),
        settings.settings,
    ) { rows, bonus, config ->
        StreakCalculator.compute(
            today = today,
            trainingDays = config.trainingDays,
            statuses = rows.associate { LocalDate.ofEpochDay(it.epochDay) to it.status },
            historyStart = today.minusDays(HISTORY_DAYS),
        ).copy(bonusLast30 = bonus)
    }

    suspend fun statsOnce(today: LocalDate): ComplianceStats = withContext(io) {
        stats(today).first()
    }

    private companion object {
        const val HISTORY_DAYS = 365L
    }
}
