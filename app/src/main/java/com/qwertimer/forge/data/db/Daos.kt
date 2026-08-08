package com.qwertimer.forge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.qwertimer.forge.domain.model.PlanStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Query("SELECT * FROM food_items WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun findById(id: Long): FoodItemEntity?

    @Query(
        """
        SELECT * FROM food_items
        WHERE barcode IS NULL AND name = :name AND IFNULL(brand, '') = IFNULL(:brand, '')
        LIMIT 1
        """,
    )
    suspend fun findUnbarcoded(name: String, brand: String?): FoodItemEntity?

    @Query(
        """
        SELECT * FROM food_items
        WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%'
        ORDER BY lastUsedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 25): List<FoodItemEntity>

    @Query("SELECT * FROM food_items WHERE lastUsedAt > 0 ORDER BY lastUsedAt DESC LIMIT :limit")
    fun recent(limit: Int = 20): Flow<List<FoodItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodItemEntity): Long

    @Update
    suspend fun update(food: FoodItemEntity)

    @Query("UPDATE food_items SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long)

    /**
     * Insert, or refresh the existing row for the same food and return its id.
     *
     * Room's REPLACE strategy would delete-and-reinsert, which cascades away every diary entry
     * pointing at the food, so a collision has to be resolved by hand. Foods without a barcode
     * (AI estimates, manual entries) are matched on name and brand so that logging "chicken curry"
     * every Tuesday does not accumulate a row per Tuesday.
     */
    @Transaction
    suspend fun upsertFood(food: FoodItemEntity): Long {
        val barcode = food.barcode
        val existing = if (barcode != null) {
            findByBarcode(barcode)
        } else {
            findUnbarcoded(food.name, food.brand)
        } ?: return insert(food)

        update(
            food.copy(
                id = existing.id,
                // Never let a refresh walk the recency stamp backwards.
                lastUsedAt = maxOf(food.lastUsedAt, existing.lastUsedAt),
            ),
        )
        return existing.id
    }
}

@Dao
interface DiaryDao {

    @Query(
        """
        SELECT e.*, f.name AS name, f.brand AS brand, f.source AS source
        FROM diary_entries e
        JOIN food_items f ON f.id = e.foodId
        WHERE e.epochDay = :epochDay
        ORDER BY e.meal, e.loggedAt
        """,
    )
    fun entriesForDay(epochDay: Long): Flow<List<DiaryEntryWithFood>>

    @Query("SELECT COALESCE(SUM(kcal), 0) FROM diary_entries WHERE epochDay = :epochDay")
    fun kcalForDay(epochDay: Long): Flow<Double>

    @Query(
        """
        SELECT epochDay, SUM(kcal) AS kcal
        FROM diary_entries
        WHERE epochDay BETWEEN :from AND :to
        GROUP BY epochDay
        ORDER BY epochDay
        """,
    )
    fun dailyKcal(from: Long, to: Long): Flow<List<DailyKcal>>

    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Update
    suspend fun update(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

data class DailyKcal(val epochDay: Long, val kcal: Double)

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises")
    suspend fun all(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)
}

@Dao
interface WorkoutDao {

    /**
     * Every session on a date, the programmed one first. Ordering on `origin` directly would sort
     * alphabetically and put AD_HOC above SCHEDULED, so the intent is spelled out.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_plans
        WHERE epochDay = :epochDay
        ORDER BY CASE origin WHEN 'SCHEDULED' THEN 0 ELSE 1 END, id
        """,
    )
    fun plansForDay(epochDay: Long): Flow<List<PlanWithBlocks>>

    @Transaction
    @Query("SELECT * FROM workout_plans WHERE id = :planId")
    suspend fun planById(planId: Long): PlanWithBlocks?

    @Transaction
    @Query(
        """
        SELECT * FROM workout_plans
        WHERE epochDay = :epochDay AND origin = 'SCHEDULED'
        LIMIT 1
        """,
    )
    fun scheduledPlanForDay(epochDay: Long): Flow<PlanWithBlocks?>

    @Transaction
    @Query(
        """
        SELECT * FROM workout_plans
        WHERE epochDay = :epochDay AND origin = 'SCHEDULED'
        LIMIT 1
        """,
    )
    suspend fun scheduledPlanForDayOnce(epochDay: Long): PlanWithBlocks?

    /** Highest variant used on a date, so the next session on that date differs from all of them. */
    @Query("SELECT COALESCE(MAX(variant), -1) FROM workout_plans WHERE epochDay = :epochDay")
    suspend fun maxVariantForDay(epochDay: Long): Int

    // Only the programmed session can be missed or skipped, so compliance ignores everything else.
    @Query(
        """
        SELECT epochDay, status FROM workout_plans
        WHERE epochDay >= :from AND origin = 'SCHEDULED'
        ORDER BY epochDay
        """,
    )
    fun scheduledStatusesSince(from: Long): Flow<List<PlanStatusRow>>

    @Query(
        """
        SELECT COUNT(*) FROM workout_plans
        WHERE epochDay >= :from AND origin = 'AD_HOC' AND status = 'COMPLETED'
        """,
    )
    fun bonusCompletedSince(from: Long): Flow<Int>

    /**
     * Exercise ids from the most recent [planLimit] sessions, used to steer variety. Sessions on
     * [onEpochDay] itself count too, so a second session in a day does not repeat the first.
     */
    @Query(
        """
        SELECT DISTINCT b.exerciseId FROM workout_blocks b
        WHERE b.planId IN (
            SELECT id FROM workout_plans WHERE epochDay <= :onEpochDay
            ORDER BY epochDay DESC, id DESC LIMIT :planLimit
        )
        """,
    )
    suspend fun recentExerciseIds(onEpochDay: Long, planLimit: Int = 2): List<String>

    @Insert
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    @Insert
    suspend fun insertBlocks(blocks: List<WorkoutBlockEntity>)

    @Query("DELETE FROM workout_plans WHERE id = :planId")
    suspend fun deletePlan(planId: Long)

    @Query("UPDATE workout_blocks SET completed = :completed WHERE id = :blockId")
    suspend fun setBlockCompleted(blockId: Long, completed: Boolean)

    @Query("UPDATE workout_plans SET status = :status, skipReason = :reason, closedAt = :closedAt WHERE id = :planId")
    suspend fun setPlanStatus(planId: Long, status: PlanStatus, reason: String?, closedAt: Long?)

    /** Write a plan and its blocks together, so a session is never half-persisted. */
    @Transaction
    suspend fun insertPlanWithBlocks(
        plan: WorkoutPlanEntity,
        blocks: (Long) -> List<WorkoutBlockEntity>,
    ): Long {
        val id = insertPlan(plan)
        insertBlocks(blocks(id))
        return id
    }
}

data class PlanStatusRow(val epochDay: Long, val status: PlanStatus)
