package com.echoenglish.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSchemaTest {
    @Test fun databaseVersionIncludesSubtitleOffsetMigration() {
        assertEquals(2, DatabaseSchema.VERSION)
        assertTrue(DatabaseSchema.MIGRATION_1_TO_2.contains("ADD COLUMN subtitleOffsetMs"))
        assertTrue(DatabaseSchema.MIGRATION_1_TO_2.contains("DEFAULT 0"))
    }

    @Test fun oldTracksDefaultToZeroSubtitleOffset() {
        val track = TrackEntity(audioUri = "audio", fileName = "a.mp3", title = "A")
        assertEquals(0, track.subtitleOffsetMs)
    }

    @Test fun differentTracksCanStoreIndependentOffsets() {
        val first = TrackEntity(audioUri = "a", fileName = "a.mp3", title = "A", subtitleOffsetMs = -500)
        val second = TrackEntity(audioUri = "b", fileName = "b.mp3", title = "B", subtitleOffsetMs = 1_200)
        assertNotEquals(first.subtitleOffsetMs, second.subtitleOffsetMs)
    }
}
