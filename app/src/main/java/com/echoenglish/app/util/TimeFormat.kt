package com.echoenglish.app.util

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

fun formatSubtitleTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    val totalSeconds = safe / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val milliseconds = safe % 1000
    return if (hours > 0) {
        "%d:%02d:%02d.%03d".format(hours, minutes, seconds, milliseconds)
    } else {
        "%02d:%02d.%03d".format(minutes, seconds, milliseconds)
    }
}
