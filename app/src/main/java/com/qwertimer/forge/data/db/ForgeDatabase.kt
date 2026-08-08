package com.qwertimer.forge.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.qwertimer.forge.domain.model.BlockType
import com.qwertimer.forge.domain.model.BodyRegion
import com.qwertimer.forge.domain.model.ExerciseCategory
import com.qwertimer.forge.domain.model.FoodSource
import com.qwertimer.forge.domain.model.MealType
import com.qwertimer.forge.domain.model.PlanStatus
import com.qwertimer.forge.domain.model.SessionFocus

/**
 * Enums are persisted by name rather than ordinal so reordering a declaration cannot corrupt
 * existing rows.
 */
class ForgeConverters {
    @TypeConverter fun mealToString(value: MealType): String = value.name

    @TypeConverter fun stringToMeal(value: String): MealType = MealType.valueOf(value)

    @TypeConverter fun sourceToString(value: FoodSource): String = value.name

    @TypeConverter fun stringToSource(value: String): FoodSource = FoodSource.valueOf(value)

    @TypeConverter fun categoryToString(value: ExerciseCategory): String = value.name

    @TypeConverter fun stringToCategory(value: String): ExerciseCategory = ExerciseCategory.valueOf(value)

    @TypeConverter fun regionToString(value: BodyRegion): String = value.name

    @TypeConverter fun stringToRegion(value: String): BodyRegion = BodyRegion.valueOf(value)

    @TypeConverter fun blockTypeToString(value: BlockType): String = value.name

    @TypeConverter fun stringToBlockType(value: String): BlockType = BlockType.valueOf(value)

    @TypeConverter fun statusToString(value: PlanStatus): String = value.name

    @TypeConverter fun stringToStatus(value: String): PlanStatus = PlanStatus.valueOf(value)

    @TypeConverter fun focusToString(value: SessionFocus): String = value.name

    @TypeConverter fun stringToFocus(value: String): SessionFocus = SessionFocus.valueOf(value)
}

@Database(
    entities = [
        FoodItemEntity::class,
        DiaryEntryEntity::class,
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        WorkoutBlockEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(ForgeConverters::class)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val NAME = "forge.db"
    }
}
