package com.echoenglish.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDiagnosticsTest {
    @Test fun eachDiagnosticFileIsLimitedToOneMegabyte() {
        assertEquals(1024L * 1024L, PlaybackDiagnostics.MAX_FILE_BYTES)
    }
}
