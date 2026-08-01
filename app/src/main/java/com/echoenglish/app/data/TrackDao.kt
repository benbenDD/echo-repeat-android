package com.echoenglish.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class TrackDao(private val db: AppDatabase) {
    private val state = MutableStateFlow(readAll())
    fun observeAll(): Flow<List<TrackEntity>> = state.asStateFlow()
    suspend fun getAll(): List<TrackEntity> = withContext(Dispatchers.IO) { readAll() }
    suspend fun getById(id: Long): TrackEntity? = withContext(Dispatchers.IO) { query("id=?", arrayOf(id.toString())).firstOrNull() }
    suspend fun getByUri(uri: String): TrackEntity? = withContext(Dispatchers.IO) { query("audioUri=?", arrayOf(uri)).firstOrNull() }

    suspend fun insert(track: TrackEntity): Long = withContext(Dispatchers.IO) {
        val id = db.writableDatabase.insertWithOnConflict("tracks", null, track.values(false), SQLiteDatabase.CONFLICT_IGNORE)
        refresh(); id
    }

    suspend fun update(track: TrackEntity) = withContext(Dispatchers.IO) {
        db.writableDatabase.update("tracks", track.values(false), "id=?", arrayOf(track.id.toString())); refresh()
    }

    suspend fun attachSubtitle(id: Long, subtitleUri: String?) = withContext(Dispatchers.IO) {
        db.writableDatabase.update("tracks", ContentValues().apply { put("subtitleUri", subtitleUri) }, "id=?", arrayOf(id.toString())); refresh()
    }

    suspend fun updateSubtitleOffset(id: Long, subtitleOffsetMs: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            "tracks",
            ContentValues().apply { put("subtitleOffsetMs", subtitleOffsetMs) },
            "id=?",
            arrayOf(id.toString())
        )
        refresh()
    }

    suspend fun updateProgress(id: Long, positionMs: Long, segment: Int, playedAt: Long, completed: Boolean) = withContext(Dispatchers.IO) {
        db.writableDatabase.update("tracks", ContentValues().apply {
            put("currentPositionMs", positionMs); put("currentSegment", segment); put("lastPlayedAt", playedAt); put("completed", if (completed) 1 else 0)
        }, "id=?", arrayOf(id.toString())); refresh()
    }

    suspend fun delete(track: TrackEntity) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("tracks", "id=?", arrayOf(track.id.toString())); refresh()
    }

    private fun refresh() { state.value = readAll() }
    private fun readAll(): List<TrackEntity> = query(null, null)
    private fun query(selection: String?, args: Array<String>?): List<TrackEntity> {
        val result = mutableListOf<TrackEntity>()
        db.readableDatabase.query("tracks", null, selection, args, null, null, "sortOrder ASC, importedAt ASC").use { c -> while (c.moveToNext()) result += c.toTrack() }
        return result
    }

    private fun Cursor.toTrack() = TrackEntity(
        id=getLong(getColumnIndexOrThrow("id")), audioUri=getString(getColumnIndexOrThrow("audioUri")), fileName=getString(getColumnIndexOrThrow("fileName")),
        title=getString(getColumnIndexOrThrow("title")), subtitleUri=getStringOrNull("subtitleUri"), subtitleOffsetMs=getLong(getColumnIndexOrThrow("subtitleOffsetMs")), durationMs=getLong(getColumnIndexOrThrow("durationMs")),
        currentPositionMs=getLong(getColumnIndexOrThrow("currentPositionMs")), currentSegment=getInt(getColumnIndexOrThrow("currentSegment")), segmentMode=getString(getColumnIndexOrThrow("segmentMode")),
        segmentSeconds=getInt(getColumnIndexOrThrow("segmentSeconds")), repeatCount=getInt(getColumnIndexOrThrow("repeatCount")), speed=getFloat(getColumnIndexOrThrow("speed")),
        importedAt=getLong(getColumnIndexOrThrow("importedAt")), lastPlayedAt=getLong(getColumnIndexOrThrow("lastPlayedAt")), completed=getInt(getColumnIndexOrThrow("completed"))!=0,
        sortOrder=getInt(getColumnIndexOrThrow("sortOrder")), available=getInt(getColumnIndexOrThrow("available"))!=0
    )
    private fun Cursor.getStringOrNull(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
    private fun TrackEntity.values(includeId: Boolean) = ContentValues().apply {
        if(includeId) put("id",id);put("audioUri",audioUri);put("fileName",fileName);put("title",title);put("subtitleUri",subtitleUri);put("subtitleOffsetMs",subtitleOffsetMs);put("durationMs",durationMs)
        put("currentPositionMs",currentPositionMs);put("currentSegment",currentSegment);put("segmentMode",segmentMode);put("segmentSeconds",segmentSeconds);put("repeatCount",repeatCount)
        put("speed",speed);put("importedAt",importedAt);put("lastPlayedAt",lastPlayedAt);put("completed",if(completed)1 else 0);put("sortOrder",sortOrder);put("available",if(available)1 else 0)
    }
}

