package com.echoenglish.app.util

object SubtitleSearch {
    fun matches(text: String, query: String): Boolean {
        val normalizedQuery = normalize(query)
        return normalizedQuery.isEmpty() || normalize(text).contains(normalizedQuery, ignoreCase = true)
    }

    private fun normalize(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")
}
