package com.qwertimer.forge.data

import com.google.common.truth.Truth.assertThat
import com.qwertimer.forge.data.remote.gemini.GeminiResponse
import com.qwertimer.forge.data.remote.gemini.JsonExtract
import com.qwertimer.forge.domain.model.Macros
import kotlinx.serialization.json.Json
import org.junit.Test

class GeminiParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `extracts a bare object`() {
        val extracted = JsonExtract.firstJsonObject("""{"name":"Oats"}""")

        assertThat(extracted).isEqualTo("""{"name":"Oats"}""")
    }

    @Test
    fun `extracts an object out of a fenced code block`() {
        val raw = """
            Here you go:
            ```json
            {"name":"Oats","per_100g":{"kcal":379}}
            ```
            Hope that helps.
        """.trimIndent()

        val extracted = JsonExtract.firstJsonObject(raw)

        assertThat(extracted).isEqualTo("""{"name":"Oats","per_100g":{"kcal":379}}""")
    }

    @Test
    fun `handles nested objects`() {
        val raw = """{"a":{"b":{"c":1}},"d":2} trailing junk"""

        assertThat(JsonExtract.firstJsonObject(raw)).isEqualTo("""{"a":{"b":{"c":1}},"d":2}""")
    }

    @Test
    fun `braces inside strings do not end the object`() {
        val raw = """{"note":"weird } brace","kcal":10}"""

        assertThat(JsonExtract.firstJsonObject(raw)).isEqualTo(raw)
    }

    @Test
    fun `escaped quotes inside strings are respected`() {
        val raw = """{"note":"he said \"} \" here","kcal":10}"""

        assertThat(JsonExtract.firstJsonObject(raw)).isEqualTo(raw)
    }

    @Test
    fun `returns null when there is no object`() {
        assertThat(JsonExtract.firstJsonObject("I could not find that product.")).isNull()
        assertThat(JsonExtract.firstJsonObject("""{"unterminated": 1""")).isNull()
    }

    @Test
    fun `joins multi part candidate text`() {
        val payload = """
            {
              "candidates": [{
                "content": {"parts": [{"text": "{\"name\":"}, {"text": "\"Oats\"}"}], "role": "model"},
                "finishReason": "STOP",
                "groundingMetadata": {
                  "groundingChunks": [
                    {"web": {"uri": "https://example.com", "title": "example.com"}}
                  ]
                }
              }]
            }
        """.trimIndent()

        val response = json.decodeFromString<GeminiResponse>(payload)

        assertThat(response.text).isEqualTo("""{"name":"Oats"}""")
        assertThat(response.sources).containsExactly("example.com")
    }

    @Test
    fun `empty candidate list yields empty text rather than an exception`() {
        val response = json.decodeFromString<GeminiResponse>("""{"candidates":[]}""")

        assertThat(response.text).isEmpty()
        assertThat(response.sources).isEmpty()
    }

    @Test
    fun `atwater energy is used to sanity check macros`() {
        // 13.2 g protein + 67.7 g carbs + 6.5 g fat should land near the label's 379 kcal.
        val derived = Macros.kcalFromMacros(proteinG = 13.2, carbsG = 67.7, fatG = 6.5)

        assertThat(derived).isWithin(15.0).of(379.0)
    }
}
