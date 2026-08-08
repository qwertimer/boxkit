package com.qwertimer.forge.data.repo

import com.qwertimer.forge.data.db.DiaryDao
import com.qwertimer.forge.data.db.DiaryEntryEntity
import com.qwertimer.forge.data.db.FoodDao
import com.qwertimer.forge.data.prefs.SettingsRepository
import com.qwertimer.forge.domain.model.DayTotals
import com.qwertimer.forge.domain.model.DiaryEntry
import com.qwertimer.forge.domain.model.FoodItem
import com.qwertimer.forge.domain.model.Macros
import com.qwertimer.forge.domain.model.MealType
import java.time.LocalDate
import com.qwertimer.forge.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class DiaryRepository @Inject constructor(
    private val diaryDao: DiaryDao,
    private val foodDao: FoodDao,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun entriesFor(date: LocalDate): Flow<List<DiaryEntry>> =
        diaryDao.entriesForDay(date.toEpochDay()).map { rows -> rows.map { it.toDomain() } }

    fun totalsFor(date: LocalDate): Flow<DayTotals> = combine(
        entriesFor(date),
        settings.settings,
    ) { entries, config ->
        DayTotals(
            consumed = entries.fold(Macros.ZERO) { acc, entry -> acc + entry.macros },
            target = config.macroTargets,
        )
    }

    fun kcalBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Double>> =
        diaryDao.dailyKcal(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.associate { LocalDate.ofEpochDay(it.epochDay) to it.kcal }
        }

    /**
     * Record [grams] of [food] against a meal. Macros are computed here and stored on the entry,
     * so the diary is a ledger of what was actually eaten rather than a live view of the product
     * database.
     */
    suspend fun log(
        food: FoodItem,
        grams: Double,
        portionLabel: String,
        meal: MealType,
        date: LocalDate,
        now: Long = System.currentTimeMillis(),
    ): Long = withContext(io) {
        require(food.id != 0L) { "Food must be persisted before it can be logged" }
        val macros = food.per100g.forGrams(grams)
        foodDao.touch(food.id, now)
        diaryDao.insert(
            DiaryEntryEntity(
                epochDay = date.toEpochDay(),
                meal = meal,
                foodId = food.id,
                grams = grams,
                portionLabel = portionLabel,
                kcal = macros.kcal,
                proteinG = macros.proteinG,
                carbsG = macros.carbsG,
                fatG = macros.fatG,
                loggedAt = now,
            ),
        )
    }

    suspend fun delete(entryId: Long) = withContext(io) { diaryDao.delete(entryId) }
}
