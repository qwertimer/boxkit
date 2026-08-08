package com.qwertimer.forge.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.qwertimer.forge.domain.model.BlockType
import com.qwertimer.forge.domain.model.BodyRegion
import com.qwertimer.forge.domain.model.ExerciseCategory
import com.qwertimer.forge.domain.model.FoodSource
import com.qwertimer.forge.domain.model.MealType
import com.qwertimer.forge.domain.model.PlanStatus
import com.qwertimer.forge.domain.model.SessionFocus

/**
 * A food, stored once and referenced by every diary entry that uses it.
 *
 * Nutrition is always held per 100 g regardless of where it came from; portion maths happens at
 * log time. [barcode] is unique when present, which makes the scanner's cache lookup a single
 * indexed hit and keeps repeat scans of the same product from piling up duplicates.
 */
@Entity(
    tableName = "food_items",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["name"]),
        Index(value = ["lastUsedAt"]),
    ],
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingSizeG: Double? = null,
    val servingLabel: String? = null,
    val source: FoodSource,
    val imageUrl: String? = null,
    val note: String? = null,
    val lastUsedAt: Long = 0L,
)

/**
 * One logged food. Macros are snapshotted rather than recomputed from [foodId] so that correcting
 * a product's numbers tomorrow does not silently rewrite what yesterday's diary said.
 */
@Entity(
    tableName = "diary_entries",
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["epochDay"]), Index(value = ["foodId"])],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val meal: MealType,
    val foodId: Long,
    val grams: Double,
    val portionLabel: String,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val loggedAt: Long,
)

/** The movement library. Seeded on first run and never mutated by the app. */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["category"]), Index(value = ["region"])],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: ExerciseCategory,
    val region: BodyRegion,
    val minLevel: Int,
    val impact: Int,
    val timeBased: Boolean,
    val defaultReps: Int,
    val defaultSeconds: Int,
    val cue: String,
)

@Entity(
    tableName = "workout_plans",
    indices = [Index(value = ["epochDay"], unique = true)],
)
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val focus: SessionFocus,
    val status: PlanStatus,
    val estimatedMinutes: Int,
    val skipReason: String? = null,
    val generatedAt: Long,
    val closedAt: Long? = null,
)

@Entity(
    tableName = "workout_blocks",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["planId"]), Index(value = ["exerciseId"])],
)
data class WorkoutBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val planId: Long,
    val exerciseId: String,
    val blockType: BlockType,
    val orderIndex: Int,
    val sets: Int,
    val reps: Int,
    val seconds: Int,
    val restSeconds: Int,
    val completed: Boolean = false,
)

data class PlanWithBlocks(
    @Embedded val plan: WorkoutPlanEntity,
    @Relation(parentColumn = "id", entityColumn = "planId")
    val blocks: List<WorkoutBlockEntity>,
)

/** Projection used to build the diary without a second round-trip for names. */
data class DiaryEntryWithFood(
    @Embedded val entry: DiaryEntryEntity,
    val name: String,
    val brand: String?,
    val source: FoodSource,
)
