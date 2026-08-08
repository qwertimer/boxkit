package com.qwertimer.forge.data.repo

import com.qwertimer.forge.data.db.FoodDao
import com.qwertimer.forge.data.prefs.SettingsRepository
import com.qwertimer.forge.data.remote.gemini.GeminiException
import com.qwertimer.forge.data.remote.gemini.GeminiNutritionService
import com.qwertimer.forge.data.remote.off.OpenFoodFactsApi
import com.qwertimer.forge.data.remote.off.toFoodItem
import com.qwertimer.forge.domain.model.FoodItem
import com.qwertimer.forge.domain.model.FoodSource
import com.qwertimer.forge.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** Outcome of scanning a barcode. */
sealed interface BarcodeLookup {
    data class Found(
        val food: FoodItem,
        val suggestedGrams: Double? = null,
        val fromCache: Boolean = false,
    ) : BarcodeLookup

    /** Nothing anywhere recognised the barcode; [detail] explains how far the search got. */
    data class Unknown(val barcode: String, val detail: String) : BarcodeLookup

    data class Failed(val message: String) : BarcodeLookup
}

/**
 * Food lookup, in priority order: local cache, then Open Food Facts, then Gemini with web search.
 *
 * The cache matters for more than speed — scanning the same tub of yoghurt every morning should
 * not cost a network round trip or an API call.
 */
@Singleton
class FoodRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val off: OpenFoodFactsApi,
    private val gemini: GeminiNutritionService,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    val recentFoods: Flow<List<FoodItem>> =
        foodDao.recent().map { list -> list.map { it.toDomain() } }

    suspend fun lookupBarcode(barcode: String): BarcodeLookup = withContext(io) {
        val normalised = barcode.trim()
        if (normalised.isEmpty()) return@withContext BarcodeLookup.Failed("Empty barcode")

        foodDao.findByBarcode(normalised)?.let {
            return@withContext BarcodeLookup.Found(it.toDomain(), fromCache = true)
        }

        val offResult = runCatching { off.product(normalised) }
        offResult.getOrNull()?.let { response ->
            if (response.status == 1) {
                response.product?.toFoodItem()?.let { food ->
                    val id = cache(food)
                    return@withContext BarcodeLookup.Found(food.copy(id = id))
                }
            }
        }

        val config = settings.current()
        if (!config.aiAvailable) {
            val why = if (!config.aiFallbackEnabled) {
                "Not in Open Food Facts, and AI lookup is off."
            } else {
                "Not in Open Food Facts. Add a Gemini API key in Settings to try an AI lookup."
            }
            return@withContext BarcodeLookup.Unknown(normalised, why)
        }

        runCatching { gemini.identifyBarcode(normalised) }.fold(
            onSuccess = { estimate ->
                val food = estimate.food.copy(barcode = normalised)
                val id = cache(food)
                BarcodeLookup.Found(food.copy(id = id), estimate.suggestedGrams)
            },
            onFailure = { error ->
                when (error) {
                    is GeminiException.Unparseable ->
                        BarcodeLookup.Unknown(normalised, "Neither Open Food Facts nor Gemini could identify this barcode.")

                    is GeminiException -> BarcodeLookup.Unknown(normalised, error.message.orEmpty())
                    else -> BarcodeLookup.Failed(describe(error))
                }
            },
        )
    }

    /** Local cache first, then Open Food Facts full-text search. Results are not cached on search. */
    suspend fun search(query: String): Result<List<FoodItem>> = withContext(io) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext Result.success(emptyList())

        val local = foodDao.search(trimmed).map { it.toDomain() }
        val remote = runCatching { off.search(trimmed).products.mapNotNull { it.toFoodItem() } }

        remote.fold(
            onSuccess = { results ->
                val seen = local.mapNotNull { it.barcode }.toMutableSet()
                val merged = local + results.filter { item ->
                    item.barcode == null || seen.add(item.barcode)
                }
                Result.success(merged)
            },
            onFailure = { error ->
                if (local.isNotEmpty()) Result.success(local) else Result.failure(error)
            },
        )
    }

    /** Ask Gemini for a food that has no packet and no barcode — "two weetbix with milk". */
    suspend fun estimateWithAi(description: String): Result<Pair<FoodItem, Double?>> = withContext(io) {
        runCatching {
            val estimate = gemini.estimateFood(description)
            val id = cache(estimate.food)
            estimate.food.copy(id = id) to estimate.suggestedGrams
        }
    }

    /**
     * Persist a food that came back from a remote search so it can be referenced by a diary entry.
     * Search results are deliberately not cached until one is actually chosen.
     */
    suspend fun persist(food: FoodItem): FoodItem = withContext(io) {
        food.copy(id = cache(food))
    }

    suspend fun saveManual(food: FoodItem): FoodItem =
        persist(food.copy(source = FoodSource.MANUAL))

    suspend fun byId(id: Long): FoodItem? = withContext(io) { foodDao.findById(id)?.toDomain() }

    private suspend fun cache(food: FoodItem): Long = foodDao.upsertFood(food.toEntity())

    private fun describe(error: Throwable): String = when (error) {
        is HttpException -> "Lookup failed (HTTP ${error.code()})"
        else -> error.message ?: "Lookup failed"
    }
}
