package com.qwertimer.forge.data.remote.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Minimal Gemini `generateContent` binding.
 *
 * The key travels in the `x-goog-api-key` header rather than a query parameter so it never lands
 * in a URL that might get logged.
 */
interface GeminiApi {

    @POST
    suspend fun generateContent(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body body: GeminiRequest,
    ): GeminiResponse

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"

        fun endpointFor(model: String): String = "v1beta/models/$model:generateContent"
    }
}

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("systemInstruction") val systemInstruction: GeminiContent? = null,
    val tools: List<GeminiTool>? = null,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null,
)

@Serializable
data class GeminiPart(val text: String? = null)

/** Web grounding. An empty object turns the Google Search tool on. */
@Serializable
data class GeminiTool(
    @SerialName("googleSearch") val googleSearch: GoogleSearchTool? = null,
)

@Serializable
class GoogleSearchTool

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    @SerialName("responseMimeType") val responseMimeType: String? = null,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int? = null,
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    @SerialName("promptFeedback") val promptFeedback: GeminiPromptFeedback? = null,
) {
    /** All text parts of the first candidate, joined. Grounded replies often arrive in pieces. */
    val text: String
        get() = candidates.firstOrNull()
            ?.content
            ?.parts
            ?.mapNotNull { it.text }
            ?.joinToString("")
            .orEmpty()

    /** Domains behind a grounded answer, shown in the UI so an estimate can be sanity-checked. */
    val sources: List<String>
        get() = candidates.firstOrNull()
            ?.groundingMetadata
            ?.groundingChunks
            ?.mapNotNull { it.web?.title ?: it.web?.uri }
            ?.distinct()
            .orEmpty()
}

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason") val finishReason: String? = null,
    @SerialName("groundingMetadata") val groundingMetadata: GeminiGroundingMetadata? = null,
)

@Serializable
data class GeminiGroundingMetadata(
    @SerialName("groundingChunks") val groundingChunks: List<GeminiGroundingChunk> = emptyList(),
    @SerialName("webSearchQueries") val webSearchQueries: List<String> = emptyList(),
)

@Serializable
data class GeminiGroundingChunk(val web: GeminiWebChunk? = null)

@Serializable
data class GeminiWebChunk(val uri: String? = null, val title: String? = null)

@Serializable
data class GeminiPromptFeedback(
    @SerialName("blockReason") val blockReason: String? = null,
)
