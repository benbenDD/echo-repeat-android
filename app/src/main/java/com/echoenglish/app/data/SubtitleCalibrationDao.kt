package com.echoenglish.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.echoenglish.app.model.SubtitleCalibration
import com.echoenglish.app.model.SubtitleCalibrationMode
import com.echoenglish.app.model.SubtitleCalibrationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubtitleCalibrationDao(private val database: AppDatabase) {
    suspend fun get(trackId: Long): SubtitleCalibration = withContext(Dispatchers.IO) {
        val db = database.readableDatabase
        val args = arrayOf(trackId.toString())
        val mode = db.query("subtitle_calibration_settings", arrayOf("mode"), "trackId=?", args,
            null, null, null).use { cursor ->
            if (cursor.moveToFirst()) SubtitleCalibrationMode.valueOf(cursor.getString(0))
            else SubtitleCalibrationMode.GRADUAL
        }
        val points = mutableListOf<SubtitleCalibrationPoint>()
        db.query("subtitle_calibration_points", arrayOf("cueId", "subtitleTimeMs", "audioTimeMs"),
            "trackId=?", args, null, null, "subtitleTimeMs ASC").use { cursor ->
            while (cursor.moveToNext()) points += SubtitleCalibrationPoint(
                cursor.getInt(0), cursor.getLong(1), cursor.getLong(2)
            )
        }
        SubtitleCalibration(mode, points)
    }

    suspend fun save(trackId: Long, value: SubtitleCalibration) = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("subtitle_calibration_settings", null, ContentValues().apply {
                put("trackId", trackId); put("mode", value.mode.name)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.delete("subtitle_calibration_points", "trackId=?", arrayOf(trackId.toString()))
            value.points.forEach { point ->
                db.insertOrThrow("subtitle_calibration_points", null, ContentValues().apply {
                    put("trackId", trackId); put("cueId", point.cueId)
                    put("subtitleTimeMs", point.subtitleTimeMs); put("audioTimeMs", point.audioTimeMs)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
