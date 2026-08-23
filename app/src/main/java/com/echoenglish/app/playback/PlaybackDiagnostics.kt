package com.echoenglish.app.playback

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small, bounded on-device journal for diagnosing intermittent playback stops. */
class PlaybackDiagnostics(
    context: Context,
    private val scope: CoroutineScope
) {
    private val directory = File(context.filesDir, "playback_diagnostics")
    private val current = File(directory, "playback.log")
    private val previous = File(directory, "playback.previous.log")
    private val lock = Any()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun record(event: String) {
        val wallTime = formatter.format(Date())
        val elapsed = SystemClock.elapsedRealtime()
        scope.launch {
            runCatching {
                synchronized(lock) {
                    directory.mkdirs()
                    rotateIfNeeded(event.length)
                    current.appendText("$wallTime elapsed=$elapsed $event\n")
                }
            }.onFailure { Log.w(TAG, "Unable to persist playback diagnostic", it) }
        }
    }

    private fun rotateIfNeeded(incomingCharacters: Int) {
        if (current.length() + incomingCharacters + ENTRY_OVERHEAD_BYTES <= MAX_FILE_BYTES) return
        if (previous.exists()) previous.delete()
        current.renameTo(previous)
    }

    companion object {
        internal const val MAX_FILE_BYTES = 256 * 1024L
        private const val ENTRY_OVERHEAD_BYTES = 96
        private const val TAG = "EchoDiagnostics"
    }
}
