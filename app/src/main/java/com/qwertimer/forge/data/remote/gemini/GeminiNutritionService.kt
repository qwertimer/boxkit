package com.qwertimer.forge.data.remote.gemini

import com.qwertimer.forge.data.prefs.SettingsRepository
import com.qwertimer.forge.domain.model.FoodItem
import com.qwertimer.forge.domain.model.FoodSource
import com.qwertimer.forge.domain.model.Macros
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An AI-sourced food, with the extra context that makes it obvious this is an estimate. */
data class AiFoodEstimate(
    val food: FoodItem,
    /** The model's guess at how much of it you ate, pre-filled into the portion sheet. */
    val suggestedGrams: Double?,
    val confidence: String?,
    val sources: List<String>,
)

sealed class GeminiException(message: String) : Exception(message) {
    object MissingKey : GeminiException("No Gemini API key set. Add one in Settings.")
    object Disabled : GeminiException("AI lookups are turned off in Settings.")
    class Blocked(reason: String) : GeminiException("Gemini declined the request: $reason")
    object Unparseable : GeminiException("Gemini did not return usable nutrition data.")
}

/**
 * Wraps Gemini into the two questions the calorie tracker actually needs to ask:
 * "what is this barcode?" and "how many calories are in this thing I typed?".
 *
 * Both go out with the Google Search tool enabled, so answers are grounded in current web data
 * rather than whatever the model happened to memorise.
 */
@Singleton
class GeminiNutritionService @Inject constructor(
    private val api: GeminiApi,
    private val settings: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun identifyBarcode(barcode: String): AiFoodEstimate = ask(
        prompt = """
        A barcode scanner read EAN/UPC $barcode. Search the web to identify the exact packaged
        food product with this barcode, then report its nutrition panel.
        If you cannot confidently identify the product from this barcode, set "name" to "" and
        explain in "note". Do not guess a product.
        """.trimIndent(),
    )

    suspend fun estimateFood(description: String): AiFoodEstimate = ask(
        prompt = """
        Estimate the nutrition for this food, as eaten: "$description".
        Search the web for a reliable nutrition reference (a manufacturer panel, a national food
        composition database, or a well-known restaurant's published data) and base the numbers on
        that. If the description implies a quantity, put the total weight of that quantity in grams
        into "assumed_portion_grams".
        """.trimIndent(),
    )

    private suspend fun ask(prompt: String): AiFoodEstimate {
        val config = settings.current()
        if (!config.aiFallbackEnabled) throw GeminiException.Disabled
        if (!config.hasGeminiKey) throw GeminiException.MissingKey

        val response = api.generateContent(
            url = GeminiApi.endpointFor(config.geminiModel),
            apiKey = config.geminiApiKey,
            body = GeminiRequest(
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(SYSTEM_PROMPT))),
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)), role = "user")),
                tools = listOf(GeminiTool(googleSearch = GoogleSearchTool())),
                generationConfig = GeminiGenerationConfig(temperature = 0.1, maxOutputTokens = 1024),
            ),
        )

        response.promptFeedback?.blockReason?.let { throw GeminiException.Blocked(it) }

        val payloadJson = JsonExtract.firstJsonObject(response.text) ?: throw GeminiException.Unparseable
        val payload = runCatching { json.decodeFromString<AiFoodPayload>(payloadJson) }
            .getOrElse { throw GeminiException.Unparseable }

        if (payload.name.isBlank()) throw GeminiException.Unparseable

        return payload.toEstimate(response.sources)
    }

    private fun AiFoodPayload.toEstimate(sources: List<String>): AiFoodEstimate {
        val macros = per100g ?: throw GeminiException.Unparseable
        val protein = macros.proteinG.orZero()
        val carbs = macros.carbsG.orZero()
        val fat = macros.fatG.orZero()
        val reportedKcal = macros.kcal.orZero()
        val atwater = Macros.kcalFromMacros(protein, carbs, fat)

        // Models occasionally return macros that do not add up to the calories they also returned.
        // When the two disagree badly, the macro-derived figure is the more defensible one.
        val inconsistent = reportedKcal > 0 && atwater > 0 &&
            abs(reportedKcal - atwater) / reportedKcal > KCAL_TOLERANCE
        val kcal = when {
            reportedKcal <= 0 -> atwater
            inconsistent -> atwater
            else -> reportedKcal
        }
        if (kcal <= 0) throw GeminiException.Unparseable

        val notes = buildList {
            note?.takeIf { it.isNotBlank() }?.let(::add)
            if (inconsistent) {
                add("Calories recalculated from the macros, which disagreed with the stated energy.")
            }
            if (sources.isNotEmpty()) add("Sources: ${sources.take(3).joinToString(", ")}")
        }

        return AiFoodEstimate(
            food = FoodItem(
                barcode = barcode?.takeIf { it.isNotBlank() },
                name = name.trim(),
                brand = brand?.trim()?.takeIf { it.isNotBlank() },
                per100g = Macros(kcal, protein, carbs, fat),
                servingSizeG = servingGrams?.takeIf { it > 0 },
                servingLabel = servingLabel?.trim()?.takeIf { it.isNotBlank() },
                source = FoodSource.AI_ESTIMATE,
                note = notes.joinToString(" ").takeIf { it.isNotBlank() },
            ),
            suggestedGrams = assumedPortionGrams?.takeIf { it > 0 },
            confidence = confidence?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
            sources = sources,
        )
    }

    private fun Double?.orZero() = (this ?: 0.0).coerceAtLeast(0.0)

    private companion object {
        /** Relative gap between stated and macro-derived calories that triggers a recalculation. */
        const val KCAL_TOLERANCE = 0.25

        val SYSTEM_PROMPT = """
            You are a nutrition data extractor. You always answer with a single JSON object and
            nothing else — no prose, no markdown fences, no citations outside the JSON.

            Schema:
            {
              "name": string,                     // product or dish name, "" if you cannot identify it
              "brand": string|null,
              "barcode": string|null,
              "serving_label": string|null,       // e.g. "1 slice", "250 ml"
              "serving_grams": number|null,       // weight of one serving
              "assumed_portion_grams": number|null, // total grams implied by the user's description
              "per_100g": {
                "kcal": number, "protein_g": number, "carbs_g": number, "fat_g": number
              },
              "confidence": "high"|"medium"|"low",
              "note": string|null                 // assumptions, or why identification failed
            }

            Rules:
            - per_100g is always per 100 grams of the food as eaten, never per serving.
            - For liquids treat 100 ml as 100 g unless you know the density.
            - Never invent a product to satisfy a barcode. An honest "" name beats a wrong answer.
            - Set confidence to "low" whenever you are interpolating from similar products.
        """.trimIndent()
    }
}

@Serializable
private data class AiFoodPayload(
    val name: String = "",
    val brand: String? = null,
    val barcode: String? = null,
    @SerialName("serving_label") val servingLabel: String? = null,
    @SerialName("serving_grams") val servingGrams: Double? = null,
    @SerialName("assumed_portion_grams") val assumedPortionGrams: Double? = null,
    @SerialName("per_100g") val per100g: AiMacrosPayload? = null,
    val confidence: String? = null,
    val note: String? = null,
)

@Serializable
private data class AiMacrosPayload(
    val kcal: Double? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
)
