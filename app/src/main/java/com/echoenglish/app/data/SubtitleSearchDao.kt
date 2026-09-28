package com.echoenglish.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.echoenglish.app.model.SrtCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SubtitleSearchResult(
    val trackId: Long,
    val trackTitle: String,
    val folderName: String?,
    val cueOrdinal: Int,
    val cueId: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

class SubtitleSearchDao(private val database: AppDatabase) {
    suspend fun indexedUri(trackId: Long): String? = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            "subtitle_search_state", arrayOf("subtitleUri"), "trackId=?",
            arrayOf(trackId.toString()), null, null, null
        ).use { if (it.moveToFirst()) it.getString(0) else null }
    }

    suspend fun replace(trackId: Long, subtitleUri: String, cues: List<SrtCue>) = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("subtitle_search", "trackId=?", arrayOf(trackId.toString()))
            cues.forEachIndexed { ordinal, cue ->
                db.insertOrThrow("subtitle_search", null, ContentValues().apply {
                    put("trackId", trackId)
                    put("cueOrdinal", ordinal + 1)
                    put("cueId", cue.index)
                    put("startMs", cue.startMs)
                    put("endMs", cue.endMs)
                    put("text", cue.text)
                })
            }
            db.insertWithOnConflict("subtitle_search_state", null, ContentValues().apply {
                put("trackId", trackId)
                put("subtitleUri", subtitleUri)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun search(query: String, folderId: Long?, trackId: Long?, limit: Int = 300): List<SubtitleSearchResult> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext emptyList()
            val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val where = buildString {
                append("s.text LIKE ? ESCAPE '\\'")
                if (folderId != null) append(" AND t.folderId=?")
                if (trackId != null) append(" AND t.id=?")
            }
            val args = buildList {
                add("%$escaped%")
                if (folderId != null) add(folderId.toString())
                if (trackId != null) add(trackId.toString())
            }.toTypedArray()
            val results = mutableListOf<SubtitleSearchResult>()
            database.readableDatabase.rawQuery(
                """SELECT s.trackId, t.title, f.name, s.cueOrdinal, s.cueId,
                    s.startMs, s.endMs, s.text
                    FROM subtitle_search s JOIN tracks t ON t.id=s.trackId
                    LEFT JOIN playlist_folders f ON f.id=t.folderId
                    WHERE $where
                    ORDER BY CASE WHEN s.text LIKE ? ESCAPE '\' THEN 0 ELSE 1 END,
                    t.title COLLATE NOCASE, s.cueOrdinal LIMIT $limit""",
                args + (escaped + "%")
            ).use { cursor ->
                while (cursor.moveToNext()) results += SubtitleSearchResult(
                    cursor.getLong(0), cursor.getString(1),
                    if (cursor.isNull(2)) null else cursor.getString(2),
                    cursor.getInt(3), cursor.getInt(4), cursor.getLong(5),
                    cursor.getLong(6), cursor.getString(7)
                )
            }
            results
        }
}
