package com.echoenglish.app.data

import android.content.Context
import android.net.Uri
import com.echoenglish.app.util.SrtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubtitleSearchIndexer(
    private val context: Context,
    private val searchDao: SubtitleSearchDao
) {
    suspend fun update(track: TrackEntity): Boolean {
        val uri = track.subtitleUri ?: return false
        if (searchDao.indexedUri(track.id) == uri) return false
        val cues = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use(SrtParser::parse)
            }.getOrNull()
        } ?: return false
        searchDao.replace(track.id, uri, cues)
        return true
    }

    suspend fun updateAll(tracks: List<TrackEntity>): Int {
        var updated = 0
        tracks.forEach { if (update(it)) updated++ }
        return updated
    }
}
