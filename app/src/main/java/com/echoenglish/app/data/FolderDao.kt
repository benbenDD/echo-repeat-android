package com.echoenglish.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class FolderDao(private val db: AppDatabase) {
    private val state = MutableStateFlow(readAll())

    fun observeAll(): Flow<List<PlaylistFolder>> = state.asStateFlow()

    suspend fun create(name: String): Long = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withContext -1L
        val id = db.writableDatabase.insertWithOnConflict(
            "playlist_folders",
            null,
            ContentValues().apply {
                put("name", cleanName)
                put("createdAt", System.currentTimeMillis())
                put("sortOrder", readAll().size)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        refresh()
        id
    }

    suspend fun rename(id: Long, name: String): Boolean = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withContext false
        val updated = db.writableDatabase.update(
            "playlist_folders",
            ContentValues().apply { put("name", cleanName) },
            "id=?",
            arrayOf(id.toString())
        ) > 0
        refresh()
        updated
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.beginTransaction()
        try {
            db.writableDatabase.update(
                "tracks",
                ContentValues().apply { putNull("folderId") },
                "folderId=?",
                arrayOf(id.toString())
            )
            db.writableDatabase.delete("playlist_folders", "id=?", arrayOf(id.toString()))
            db.writableDatabase.setTransactionSuccessful()
        } finally {
            db.writableDatabase.endTransaction()
        }
        refresh()
    }

    private fun refresh() { state.value = readAll() }

    private fun readAll(): List<PlaylistFolder> {
        val result = mutableListOf<PlaylistFolder>()
        db.readableDatabase.query(
            "playlist_folders",
            null,
            null,
            null,
            null,
            null,
            "sortOrder ASC, createdAt ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += PlaylistFolder(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                    sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sortOrder"))
                )
            }
        }
        return result
    }
}
