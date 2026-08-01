package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

object SrtParser {
    private val timeLine = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

    fun parse(input: InputStream): List<SrtCue> = parseText(decode(input.readBytes()))

    fun parseText(raw: String): List<SrtCue> {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isBlank()) return emptyList()
        return normalized.split(Regex("\\n\\s*\\n")).mapNotNull { block ->
            val lines = block.lines().filter { it.isNotBlank() }
            val timeIndex = lines.indexOfFirst { timeLine.containsMatchIn(it) }
            if (timeIndex < 0) return@mapNotNull null
            val match = timeLine.find(lines[timeIndex]) ?: return@mapNotNull null
            val v = match.groupValues.drop(1).map(String::toLong)
            val start = toMs(v[0], v[1], v[2], v[3])
            val end = toMs(v[4], v[5], v[6], v[7])
            if (end <= start) return@mapNotNull null
            val index = lines.firstOrNull()?.toIntOrNull() ?: 0
            val text = lines.drop(timeIndex + 1).joinToString("\n").replace(Regex("<[^>]+>"), "").trim()
            SrtCue(index, start, end, text)
        }.sortedBy { it.startMs }
    }

    private fun toMs(h: Long, m: Long, s: Long, ms: Long) = (((h * 60 + m) * 60 + s) * 1000 + ms)

    private fun decode(bytes: ByteArray): String {
        val clean = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) bytes.copyOfRange(3, bytes.size) else bytes
        return runCatching {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(clean)).toString()
        }.getOrElse { Charset.forName("GB18030").decode(ByteBuffer.wrap(clean)).toString() }
    }
}
