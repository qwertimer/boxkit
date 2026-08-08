package com.qwertimer.forge.data.repo

import com.qwertimer.forge.data.db.DiaryEntryWithFood
import com.qwertimer.forge.data.db.ExerciseEntity
import com.qwertimer.forge.data.db.FoodItemEntity
import com.qwertimer.forge.data.db.PlanWithBlocks
import com.qwertimer.forge.data.db.WorkoutBlockEntity
import com.qwertimer.forge.domain.model.DiaryEntry
import com.qwertimer.forge.domain.model.Exercise
import com.qwertimer.forge.domain.model.FoodItem
import com.qwertimer.forge.domain.model.Macros
import com.qwertimer.forge.domain.model.WorkoutBlock
import com.qwertimer.forge.domain.model.WorkoutPlan
import java.time.LocalDate

fun FoodItemEntity.toDomain() = FoodItem(
    id = id,
    barcode = barcode,
    name = name,
    brand = brand,
    per100g = Macros(kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g),
    servingSizeG = servingSizeG,
    servingLabel = servingLabel,
    source = source,
    imageUrl = imageUrl,
    note = note,
)

fun FoodItem.toEntity(lastUsedAt: Long = 0L) = FoodItemEntity(
    id = id,
    barcode = barcode,
    name = name,
    brand = brand,
    kcalPer100g = per100g.kcal,
    proteinPer100g = per100g.proteinG,
    carbsPer100g = per100g.carbsG,
    fatPer100g = per100g.fatG,
    servingSizeG = servingSizeG,
    servingLabel = servingLabel,
    source = source,
    imageUrl = imageUrl,
    note = note,
    lastUsedAt = lastUsedAt,
)

fun DiaryEntryWithFood.toDomain() = DiaryEntry(
    id = entry.id,
    foodId = entry.foodId,
    name = name,
    brand = brand,
    meal = entry.meal,
    grams = entry.grams,
    portionLabel = entry.portionLabel,
    macros = Macros(entry.kcal, entry.proteinG, entry.carbsG, entry.fatG),
    source = source,
)

fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    name = name,
    category = category,
    region = region,
    minLevel = minLevel,
    impact = impact,
    timeBased = timeBased,
    defaultReps = defaultReps,
    defaultSeconds = defaultSeconds,
    cue = cue,
)

fun WorkoutBlockEntity.toDomain(exercise: Exercise) = WorkoutBlock(
    id = id,
    exercise = exercise,
    blockType = blockType,
    orderIndex = orderIndex,
    sets = sets,
    reps = reps,
    seconds = seconds,
    restSeconds = restSeconds,
    completed = completed,
)

fun PlanWithBlocks.toDomain(exercisesById: Map<String, Exercise>) = WorkoutPlan(
    id = plan.id,
    date = LocalDate.ofEpochDay(plan.epochDay),
    focus = plan.focus,
    status = plan.status,
    estimatedMinutes = plan.estimatedMinutes,
    origin = plan.origin,
    skipReason = plan.skipReason,
    blocks = blocks
        .sortedBy { it.orderIndex }
        .mapNotNull { block -> exercisesById[block.exerciseId]?.let(block::toDomain) },
)
