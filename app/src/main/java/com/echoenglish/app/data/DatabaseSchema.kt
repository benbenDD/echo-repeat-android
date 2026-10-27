package com.echoenglish.app.data

object DatabaseSchema {
    const val VERSION = 6
    const val MIGRATION_1_TO_2 =
        "ALTER TABLE tracks ADD COLUMN subtitleOffsetMs INTEGER NOT NULL DEFAULT 0"
    const val MIGRATION_2_TO_3 = """CREATE TABLE subtitle_bookmarks (
        trackId INTEGER NOT NULL,
        cueIndex INTEGER NOT NULL,
        createdAt INTEGER NOT NULL,
        PRIMARY KEY (trackId, cueIndex)
    )"""
    val MIGRATION_3_TO_4 = listOf(
        """CREATE TABLE playlist_folders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL COLLATE NOCASE UNIQUE,
            createdAt INTEGER NOT NULL,
            sortOrder INTEGER NOT NULL
        )""",
        "ALTER TABLE tracks ADD COLUMN folderId INTEGER"
    )
    val MIGRATION_4_TO_5 = listOf(
        """CREATE TABLE subtitle_search (
            trackId INTEGER NOT NULL,
            cueOrdinal INTEGER NOT NULL,
            cueId INTEGER NOT NULL,
            startMs INTEGER NOT NULL,
            endMs INTEGER NOT NULL,
            text TEXT NOT NULL,
            PRIMARY KEY (trackId, cueOrdinal)
        )""",
        "CREATE INDEX subtitle_search_track ON subtitle_search(trackId)",
        """CREATE TABLE subtitle_search_state (
            trackId INTEGER PRIMARY KEY,
            subtitleUri TEXT NOT NULL
        )"""
    )
    val MIGRATION_5_TO_6 = listOf(
        """CREATE TABLE subtitle_calibration_settings (
            trackId INTEGER PRIMARY KEY,
            mode TEXT NOT NULL
        )""",
        """CREATE TABLE subtitle_calibration_points (
            trackId INTEGER NOT NULL,
            cueId INTEGER NOT NULL,
            subtitleTimeMs INTEGER NOT NULL,
            audioTimeMs INTEGER NOT NULL,
            PRIMARY KEY (trackId, cueId)
        )"""
    )
}
