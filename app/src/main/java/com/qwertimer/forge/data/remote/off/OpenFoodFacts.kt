package com.qwertimer.forge.data.remote.off

import com.qwertimer.forge.domain.model.FoodItem
import com.qwertimer.forge.domain.model.FoodSource
import com.qwertimer.forge.domain.model.Macros
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Open Food Facts is a free, key-less product database with good coverage of packaged groceries.
 * It is the first stop for every barcode; Gemini only gets involved when a lookup comes back empty.
 */
interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS,
    ): OffProductResponse

    @GET("cgi/search.pl")
    suspend fun search(
        @Query("search_terms") terms: String,
        @Query("page_size") pageSize: Int = 20,
        @Query("fields") fields: String = FIELDS,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
    ): OffSearchResponse

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"
        const val FIELDS =
            "code,product_name,product_name_en,brands,nutriments,serving_size,serving_quantity," +
                "image_front_small_url,quantity"
    }
}

@Serializable
data class OffProductResponse(
    val code: String? = null,
    val status: Int = 0,
    @SerialName("status_verbose") val statusVerbose: String? = null,
    val product: OffProduct? = null,
)

@Serializable
data class OffSearchResponse(
    val count: Int = 0,
    val products: List<OffProduct> = emptyList(),
)

@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_en") val productNameEn: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    @SerialName("serving_quantity") val servingQuantity: String? = null,
    @SerialName("image_front_small_url") val imageUrl: String? = null,
    val nutriments: OffNutriments? = null,
)

@Serializable
data class OffNutriments(
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerialName("energy_100g") val energy100g: Double? = null,
    @SerialName("energy-kj_100g") val energyKj100g: Double? = null,
    @SerialName("proteins_100g") val proteins100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
)

/** Grams parsed out of free-text serving strings like `30 g`, `1 slice (33g)`, `250ml`. */
internal val SERVING_GRAMS = Regex("""([\d.,]+)\s*(g|ml)\b""", RegexOption.IGNORE_CASE)

private const val KJ_PER_KCAL = 4.184

fun OffProduct.toFoodItem(): FoodItem? {
    val name = listOfNotNull(productName, productNameEn)
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?: return null
    val nutriments = nutriments ?: return null

    val kcal = nutriments.energyKcal100g
        ?: nutriments.energyKj100g?.let { it / KJ_PER_KCAL }
        // `energy_100g` is kJ for most of the database; treating it as kcal would be a 4x error.
        ?: nutriments.energy100g?.let { it / KJ_PER_KCAL }
        ?: return null

    val servingGrams = servingQuantity?.toDoubleOrNull()
        ?: servingSize?.let { text ->
            SERVING_GRAMS.find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        }

    return FoodItem(
        barcode = code?.takeIf { it.isNotBlank() },
        name = name,
        brand = brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
        per100g = Macros(
            kcal = kcal,
            proteinG = nutriments.proteins100g ?: 0.0,
            carbsG = nutriments.carbohydrates100g ?: 0.0,
            fatG = nutriments.fat100g ?: 0.0,
        ),
        servingSizeG = servingGrams?.takeIf { it > 0 },
        servingLabel = servingSize?.trim()?.takeIf { it.isNotBlank() },
        source = FoodSource.OPEN_FOOD_FACTS,
        imageUrl = imageUrl?.takeIf { it.isNotBlank() },
    )
}
