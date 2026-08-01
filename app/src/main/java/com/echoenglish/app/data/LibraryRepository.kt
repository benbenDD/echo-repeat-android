package com.echoenglish.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.echoenglish.app.util.FilenameMatcher

class LibraryRepository(private val context: Context, private val dao: TrackDao) {
    data class ImportResult(val audioCount: Int, val matchedCount: Int, val unmatchedAudio: Int, val duplicateCount: Int, val errors: List<String>)

    suspend fun importUris(uris: List<Uri>): ImportResult {
        val resolver = context.contentResolver
        val named = uris.distinct().mapNotNull { uri ->
            runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = displayName(uri) ?: uri.lastPathSegment ?: return@mapNotNull null
            name to uri
        }
        val subtitles = named.filter { it.first.endsWith(".srt", true) }
        val audios = named.filter { isAudioName(it.first) }
        var matched = 0
        var duplicates = 0
        val errors = mutableListOf<String>()
        val existing = dao.getAll()
        var order = existing.size

        for ((audioName, audioUri) in audios) {
            if (dao.getByUri(audioUri.toString()) != null) { duplicates++; continue }
            val subtitleName = FilenameMatcher.findSubtitle(audioName, subtitles.map { it.first })
            val subtitleUri = subtitles.firstOrNull { it.first == subtitleName }?.second
            if (subtitleUri != null) matched++
            val duration = runCatching { duration(audioUri) }.getOrElse { errors += "$audioName：无法读取时长"; 0L }
            dao.insert(TrackEntity(
                audioUri = audioUri.toString(),
                fileName = audioName,
                title = audioName.substringBeforeLast('.'),
                subtitleUri = subtitleUri?.toString(),
                durationMs = duration,
                sortOrder = order++
            ))
        }

        val all = dao.getAll()
        for ((subtitleName, subtitleUri) in subtitles) {
            if (all.any { it.subtitleUri == subtitleUri.toString() }) continue
            val candidateNames = all.filter { it.subtitleUri == null }.map { it.fileName }
            val audioName = candidateNames.singleOrNull { FilenameMatcher.findSubtitle(it, listOf(subtitleName)) == subtitleName }
            val track = all.singleOrNull { it.fileName == audioName }
            if (track != null) { dao.attachSubtitle(track.id, subtitleUri.toString()); matched++ }
        }
        return ImportResult(audios.size - duplicates, matched, (audios.size - duplicates - matched).coerceAtLeast(0), duplicates, errors)
    }

    fun collectTree(uri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
        val result = mutableListOf<Uri>()
        fun walk(file: DocumentFile, depth: Int) {
            if (depth > 8) return
            if (file.isDirectory) file.listFiles().forEach { walk(it, depth + 1) }
            else if (file.name?.let { isAudioName(it) || it.endsWith(".srt", true) } == true) result += file.uri
        }
        walk(root, 0)
        return result
    }

    private fun isAudioName(name: String) = listOf("mp3", "m4a", "aac", "wav", "flac").any { name.endsWith(".$it", true) }

    private fun displayName(uri: Uri): String? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

    private fun duration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } finally { retriever.release() }
    }
}
