package com.qwertimer.forge.domain.model

import kotlin.math.roundToInt

/**
 * Energy and macronutrients for some quantity of food.
 *
 * Everything in the app is stored per 100 g and scaled at the point of logging, which keeps a
 * single source of truth for a product no matter what portion sizes get entered against it.
 */
data class Macros(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
) {
    operator fun plus(other: Macros) = Macros(
        kcal = kcal + other.kcal,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
    )

    operator fun times(factor: Double) = Macros(
        kcal = kcal * factor,
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
    )

    /** Scale a per-100 g figure to [grams]. */
    fun forGrams(grams: Double) = this * (grams / 100.0)

    val kcalRounded: Int get() = kcal.roundToInt()

    companion object {
        val ZERO = Macros()

        /**
         * Energy derived from the macros using Atwater factors. Used to sanity-check AI estimates,
         * which sometimes return macros that do not add up to the calories they also returned.
         */
        fun kcalFromMacros(proteinG: Double, carbsG: Double, fatG: Double): Double =
            proteinG * 4.0 + carbsG * 4.0 + fatG * 9.0
    }
}

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack"),
    ;

    companion object {
        /** Best guess at which meal is being logged, so the picker starts on the right one. */
        fun forHour(hour: Int): MealType = when (hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 16..21 -> DINNER
            else -> SNACK
        }
    }
}

/** Where a food's numbers came from. Surfaced in the UI so estimates are never mistaken for labels. */
enum class FoodSource {
    /** Off-the-packet data from the Open Food Facts database. */
    OPEN_FOOD_FACTS,

    /** Estimated by Gemini with web grounding. Approximate by nature. */
    AI_ESTIMATE,

    /** Typed in by hand. */
    MANUAL,
    ;

    val isEstimate: Boolean get() = this == AI_ESTIMATE
}

data class FoodItem(
    val id: Long = 0L,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val per100g: Macros,
    val servingSizeG: Double? = null,
    val servingLabel: String? = null,
    val source: FoodSource,
    val imageUrl: String? = null,
    val note: String? = null,
) {
    val displayName: String
        get() = if (brand.isNullOrBlank()) name else "$name · $brand"
}

/** A food as it appears in the diary, with the portion already applied. */
data class DiaryEntry(
    val id: Long,
    val foodId: Long,
    val name: String,
    val brand: String?,
    val meal: MealType,
    val grams: Double,
    val portionLabel: String,
    val macros: Macros,
    val source: FoodSource,
)

data class DayTotals(
    val consumed: Macros = Macros.ZERO,
    val target: Macros = Macros.ZERO,
) {
    val remainingKcal: Double get() = target.kcal - consumed.kcal
    val kcalProgress: Float
        get() = if (target.kcal <= 0.0) 0f else (consumed.kcal / target.kcal).toFloat()
}
