package com.echoenglish.app.data

object DatabaseSchema {
    const val VERSION = 2
    const val MIGRATION_1_TO_2 =
        "ALTER TABLE tracks ADD COLUMN subtitleOffsetMs INTEGER NOT NULL DEFAULT 0"
}