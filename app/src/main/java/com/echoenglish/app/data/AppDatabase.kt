package com.echoenglish.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context) : SQLiteOpenHelper(
    context,
    "echo-english.db",
    null,
    DatabaseSchema.VERSION
) {
    val trackDao: TrackDao by lazy { TrackDao(this) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE tracks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            audioUri TEXT NOT NULL UNIQUE,
            fileName TEXT NOT NULL,
            title TEXT NOT NULL,
            subtitleUri TEXT,
            subtitleOffsetMs INTEGER NOT NULL DEFAULT 0,
            durationMs INTEGER NOT NULL,
            currentPositionMs INTEGER NOT NULL,
            currentSegment INTEGER NOT NULL,
            segmentMode TEXT NOT NULL,
            segmentSeconds INTEGER NOT NULL,
            repeatCount INTEGER NOT NULL,
            speed REAL NOT NULL,
            importedAt INTEGER NOT NULL,
            lastPlayedAt INTEGER NOT NULL,
            completed INTEGER NOT NULL,
            sortOrder INTEGER NOT NULL,
            available INTEGER NOT NULL
        )""")
        db.execSQL(DatabaseSchema.MIGRATION_2_TO_3)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL(DatabaseSchema.MIGRATION_1_TO_2)
        if (oldVersion < 3) db.execSQL(DatabaseSchema.MIGRATION_2_TO_3)
    }
}
