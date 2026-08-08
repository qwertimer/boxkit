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

    @Transaction
    @Query("SELECT * FROM workout_plans WHERE epochDay = :epochDay LIMIT 1")
    fun planForDay(epochDay: Long): Flow<PlanWithBlocks?>

    @Transaction
    @Query("SELECT * FROM workout_plans WHERE epochDay = :epochDay LIMIT 1")
    suspend fun planForDayOnce(epochDay: Long): PlanWithBlocks?

    @Query("SELECT epochDay, status FROM workout_plans WHERE epochDay >= :from ORDER BY epochDay")
    fun statusesSince(from: Long): Flow<List<PlanStatusRow>>

    @Query("SELECT epochDay, status FROM workout_plans WHERE epochDay >= :from ORDER BY epochDay")
    suspend fun statusesSinceOnce(from: Long): List<PlanStatusRow>

    @Query("SELECT MIN(epochDay) FROM workout_plans")
    suspend fun firstPlanDay(): Long?

    /** Exercise ids from the most recent [planLimit] sessions, used to steer variety. */
    @Query(
        """
        SELECT DISTINCT b.exerciseId FROM workout_blocks b
        WHERE b.planId IN (
            SELECT id FROM workout_plans WHERE epochDay < :beforeEpochDay
            ORDER BY epochDay DESC LIMIT :planLimit
        )
        """,
    )
    suspend fun recentExerciseIds(beforeEpochDay: Long, planLimit: Int = 2): List<String>

    @Insert
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    @Insert
    suspend fun insertBlocks(blocks: List<WorkoutBlockEntity>)

    @Query("DELETE FROM workout_plans WHERE epochDay = :epochDay")
    suspend fun deletePlanForDay(epochDay: Long)

    @Query("UPDATE workout_blocks SET completed = :completed WHERE id = :blockId")
    suspend fun setBlockCompleted(blockId: Long, completed: Boolean)

    @Query("UPDATE workout_plans SET status = :status, skipReason = :reason, closedAt = :closedAt WHERE id = :planId")
    suspend fun setPlanStatus(planId: Long, status: PlanStatus, reason: String?, closedAt: Long?)

    @Transaction
    suspend fun replacePlan(plan: WorkoutPlanEntity, blocks: (Long) -> List<WorkoutBlockEntity>): Long {
        deletePlanForDay(plan.epochDay)
        val id = insertPlan(plan)
        insertBlocks(blocks(id))
        return id
    }
}

data class PlanStatusRow(val epochDay: Long, val status: PlanStatus)
