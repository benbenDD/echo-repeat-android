package com.echoenglish.app.data

object DatabaseSchema {
    const val VERSION = 4
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
}
