package com.qwertimer.forge.data.remote.gemini

/**
 * Pulls the first complete JSON object out of a model reply.
 *
 * Grounded Gemini calls cannot also request `application/json` as the response type, so the model
 * is asked for JSON in the prompt and answers in prose-adjacent ways: fenced code blocks, a
 * leading "Here you go:", trailing citation markers. Rather than trusting any of that, scan for a
 * balanced `{...}` while respecting string literals and escapes.
 */
object JsonExtract {

    fun firstJsonObject(raw: String): String? {
        val text = raw.trim()
        val start = text.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until text.length) {
            val ch = text[index]
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }
}
