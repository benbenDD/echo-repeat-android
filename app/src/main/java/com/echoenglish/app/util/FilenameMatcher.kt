package com.echoenglish.app.util

object FilenameMatcher {
    private val extensions = Regex("(?i)\\.(mp3|m4a|aac|wav|flac|srt)$")
    private val subtitleSuffix = Regex("(?i)([._ -](en|eng|english|zh[-_ ]?en|bilingual))$")

    fun normalize(name: String): String = name
        .replace(extensions, "")
        .trim()
        .lowercase()
        .replace(Regex("[_-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun subtitleBase(name: String): String = normalize(name).replace(subtitleSuffix, "").trim()

    fun findSubtitle(audioName: String, subtitleNames: List<String>): String? {
        val audio = normalize(audioName)
        val exact = subtitleNames.filter { normalize(it) == audio }
        if (exact.size == 1) return exact.first()
        val relaxed = subtitleNames.filter { subtitleBase(it) == audio }
        return relaxed.singleOrNull()
    }
}
