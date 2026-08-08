package com.qwertimer.forge.data

import com.google.common.truth.Truth.assertThat
import com.qwertimer.forge.data.remote.off.OffNutriments
import com.qwertimer.forge.data.remote.off.OffProduct
import com.qwertimer.forge.data.remote.off.OffProductResponse
import com.qwertimer.forge.data.remote.off.toFoodItem
import com.qwertimer.forge.domain.model.FoodSource
import kotlinx.serialization.json.Json
import org.junit.Test

class OpenFoodFactsMappingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `maps a well formed product`() {
        val product = OffProduct(
            code = "9300605072107",
            productName = "Weet-Bix",
            brands = "Sanitarium, Australia",
            servingSize = "2 biscuits (30 g)",
            nutriments = OffNutriments(
                energyKcal100g = 349.0,
                proteins100g = 12.0,
                carbohydrates100g = 67.0,
                fat100g = 1.3,
            ),
        )

        val food = product.toFoodItem()!!

        assertThat(food.name).isEqualTo("Weet-Bix")
        // Only the first brand is kept; OFF stores them as a comma-separated blob.
        assertThat(food.brand).isEqualTo("Sanitarium")
        assertThat(food.per100g.kcal).isEqualTo(349.0)
        assertThat(food.servingSizeG).isEqualTo(30.0)
        assertThat(food.source).isEqualTo(FoodSource.OPEN_FOOD_FACTS)
    }

    @Test
    fun `falls back to kilojoules when kcal is missing`() {
        val product = OffProduct(
            code = "1",
            productName = "Mystery bar",
            nutriments = OffNutriments(energyKj100g = 1000.0, proteins100g = 5.0),
        )

        val food = product.toFoodItem()!!

        assertThat(food.per100g.kcal).isWithin(0.5).of(239.0)
    }

    @Test
    fun `treats the generic energy field as kilojoules`() {
        // energy_100g is kJ across most of the database; reading it as kcal is a 4x error.
        val product = OffProduct(
            code = "2",
            productName = "Generic energy",
            nutriments = OffNutriments(energy100g = 2000.0),
        )

        assertThat(product.toFoodItem()!!.per100g.kcal).isWithin(0.5).of(478.0)
    }

    @Test
    fun `rejects a product with no usable energy`() {
        val product = OffProduct(
            code = "3",
            productName = "Empty",
            nutriments = OffNutriments(proteins100g = 1.0),
        )

        assertThat(product.toFoodItem()).isNull()
    }

    @Test
    fun `rejects a nameless product`() {
        val product = OffProduct(
            code = "4",
            productName = "  ",
            nutriments = OffNutriments(energyKcal100g = 100.0),
        )

        assertThat(product.toFoodItem()).isNull()
    }

    @Test
    fun `parses serving weight out of free text`() {
        fun servingFor(text: String) = OffProduct(
            code = "5",
            productName = "X",
            servingSize = text,
            nutriments = OffNutriments(energyKcal100g = 100.0),
        ).toFoodItem()!!.servingSizeG

        assertThat(servingFor("30 g")).isEqualTo(30.0)
        assertThat(servingFor("1 slice (33g)")).isEqualTo(33.0)
        assertThat(servingFor("250ml")).isEqualTo(250.0)
        assertThat(servingFor("1 handful")).isNull()
    }

    @Test
    fun `decodes a real world payload with mixed types`() {
        // serving_quantity arrives as a quoted number and there are fields we never asked for.
        val payload = """
            {
              "code": "5000112637922",
              "status": 1,
              "status_verbose": "product found",
              "product": {
                "product_name": "Coca-Cola",
                "brands": "Coca-Cola",
                "serving_size": "330 ml",
                "serving_quantity": "330",
                "unexpected_field": {"nested": true},
                "nutriments": {
                  "energy-kcal_100g": 42,
                  "proteins_100g": 0,
                  "carbohydrates_100g": 10.6,
                  "fat_100g": 0,
                  "sugars_100g": 10.6
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<OffProductResponse>(payload)
        val food = response.product!!.toFoodItem()!!

        assertThat(response.status).isEqualTo(1)
        assertThat(food.name).isEqualTo("Coca-Cola")
        assertThat(food.servingSizeG).isEqualTo(330.0)
        assertThat(food.per100g.carbsG).isEqualTo(10.6)
    }

    @Test
    fun `decodes a not found payload without throwing`() {
        val payload = """{"code":"0000","status":0,"status_verbose":"product not found"}"""

        val response = json.decodeFromString<OffProductResponse>(payload)

        assertThat(response.status).isEqualTo(0)
        assertThat(response.product).isNull()
    }

    @Test
    fun `scales macros to a portion`() {
        val food = OffProduct(
            code = "6",
            productName = "Rolled oats",
            nutriments = OffNutriments(
                energyKcal100g = 379.0,
                proteins100g = 13.2,
                carbohydrates100g = 67.7,
                fat100g = 6.5,
            ),
        ).toFoodItem()!!

        val serve = food.per100g.forGrams(45.0)

        assertThat(serve.kcal).isWithin(0.01).of(170.55)
        assertThat(serve.proteinG).isWithin(0.01).of(5.94)
    }
}
