package com.echoenglish.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echoenglish.app.EchoEnglishApp
import com.echoenglish.app.data.LibraryRepository
import com.echoenglish.app.data.TrackEntity
import com.echoenglish.app.model.PlaybackSettings
import com.echoenglish.app.model.PlaylistMode
import com.echoenglish.app.model.Segment
import com.echoenglish.app.model.SegmentMode
import com.echoenglish.app.model.SrtCue
import com.echoenglish.app.playback.PlaybackBus
import com.echoenglish.app.playback.PlaybackContract
import com.echoenglish.app.playback.PlaybackService
import com.echoenglish.app.playback.PlaybackServicePolicy
import com.echoenglish.app.util.Segmenter
import com.echoenglish.app.util.SrtParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as EchoEnglishApp
    private val dao = app.database.trackDao
    private val library = LibraryRepository(app, dao)
    val tracks = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableSettings = MutableStateFlow(PlaybackSettings())
    val settings = mutableSettings.asStateFlow()
    val playback = PlaybackBus.state
    private val mutableCurrent = MutableStateFlow<TrackEntity?>(null)
    val current = mutableCurrent.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private var currentCues: List<SrtCue> = emptyList()
    private var currentSegments: List<Segment> = emptyList()
    private var completionHandled = false
    private var lastPlaybackError = ""

    init {
        viewModelScope.launch { app.settingsRepository.settings.collect { mutableSettings.value = it } }
        viewModelScope.launch {
            playback.collect { state ->
                if (state.errorMessage.isNotBlank() && state.errorMessage != lastPlaybackError) {
                    lastPlaybackError = state.errorMessage
                    mutableMessage.value = state.errorMessage
                } else if (state.errorMessage.isBlank()) {
                    lastPlaybackError = ""
                }
                if (state.completed && !completionHandled) {
                    completionHandled = true
                    persistProgress(true)
                    advancePlaylist()
                } else if (!state.completed) completionHandled = false
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1_500)
                if (playback.value.isPlaying) persistProgress(false)
            }
        }
    }

    fun importUris(uris: List<Uri>) = viewModelScope.launch {
        mutableMessage.value = "正在导入…"
        val result = library.importUris(uris)
        mutableMessage.value = "已导入 ${result.audioCount} 个音频，匹配 ${result.matchedCount} 个字幕，重复 ${result.duplicateCount} 个"
    }

    fun importTree(uri: Uri) = viewModelScope.launch {
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        importUris(library.collectTree(uri))
    }

    private fun sendService(intent: Intent) {
        if (PlaybackServicePolicy.requiresForegroundStart(intent.action)) {
            ContextCompat.startForegroundService(app, intent)
        } else {
            app.startService(intent)
        }
    }

    fun openTrack(track: TrackEntity) = viewModelScope.launch {
        val activeSettings = mutableSettings.value
        currentCues = readCues(track)
        currentSegments = buildSegments(activeSettings, track.durationMs)
        if (currentSegments.isEmpty()) {
            mutableMessage.value = "无法生成播放片段，请检查音频时长或字幕"
            return@launch
        }
        mutableCurrent.value = track
        app.settingsRepository.saveLastTrack(track.id)
        val targetSegment = track.currentSegment.coerceIn(0, currentSegments.lastIndex)
        sendService(Intent(app, PlaybackService::class.java).apply {
            action = PlaybackContract.ACTION_LOAD
            putExtra(PlaybackContract.EXTRA_URI, track.audioUri)
            putExtra(PlaybackContract.EXTRA_MEDIA_ID, track.id.toString())
            putExtra(PlaybackContract.EXTRA_TITLE, track.title)
            putExtra(PlaybackContract.EXTRA_DURATION, track.durationMs)
            putExtra(PlaybackContract.EXTRA_STARTS, currentSegments.map { it.startMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_ENDS, currentSegments.map { it.endMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_TEXTS, currentSegments.map { it.text }.toTypedArray())
            putExtra(PlaybackContract.EXTRA_CUE_STARTS, currentCues.map { it.startMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_CUE_ENDS, currentCues.map { it.endMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_CUE_TEXTS, currentCues.map { it.text }.toTypedArray())
            putExtra(PlaybackContract.EXTRA_REPEATS, activeSettings.repeatCount)
            putExtra(PlaybackContract.EXTRA_INDEX, targetSegment)
            putExtra(PlaybackContract.EXTRA_POSITION, track.currentPositionMs)
            putExtra(PlaybackContract.EXTRA_SPEED, activeSettings.speed)
        })
    }

    fun command(action: String, position: Long? = null) {
        sendService(Intent(app, PlaybackService::class.java).apply {
            this.action = action
            position?.let { putExtra(PlaybackContract.EXTRA_POSITION, it) }
        })
    }

    fun seekAbsolute(positionMs: Long) = command(PlaybackContract.ACTION_SEEK_ABSOLUTE, positionMs)

    fun seekToSegment(index: Int) {
        sendService(Intent(app, PlaybackService::class.java).apply {
            action = PlaybackContract.ACTION_SEEK_SEGMENT
            putExtra(PlaybackContract.EXTRA_INDEX, index)
        })
    }

    fun updateSettings(value: PlaybackSettings) {
        if (value.segmentMode == SegmentMode.SUBTITLE && mutableCurrent.value != null && currentCues.isEmpty()) {
            mutableMessage.value = "当前音频没有匹配的有效字幕，无法切换为按字幕分段"
            return
        }
        val previous = mutableSettings.value
        mutableSettings.value = value
        viewModelScope.launch {
            app.settingsRepository.save(value)
            mutableCurrent.value?.let { track ->
                val updated = track.copy(segmentMode = value.segmentMode.name, segmentSeconds = value.segmentSeconds, repeatCount = value.repeatCount, speed = value.speed)
                mutableCurrent.value = updated
                dao.update(updated)
            }
        }
        if (mutableCurrent.value != null && (previous.segmentSeconds != value.segmentSeconds || previous.segmentMode != value.segmentMode)) {
            rebuildActiveSegments(value)
        }
        if (previous.repeatCount != value.repeatCount) {
            sendService(Intent(app, PlaybackService::class.java).apply {
                action = PlaybackContract.ACTION_UPDATE_REPEATS
                putExtra(PlaybackContract.EXTRA_REPEATS, value.repeatCount)
            })
        }
        if (previous.speed != value.speed) {
            sendService(Intent(app, PlaybackService::class.java).apply {
                action = PlaybackContract.ACTION_UPDATE_SPEED
                putExtra(PlaybackContract.EXTRA_SPEED, value.speed)
            })
        }
    }

    private fun rebuildActiveSegments(value: PlaybackSettings) {
        val track = mutableCurrent.value ?: return
        if (value.segmentMode == SegmentMode.SUBTITLE && currentCues.isEmpty()) {
            val fallback = value.copy(segmentMode = SegmentMode.FIXED)
            mutableSettings.value = fallback
            mutableMessage.value = "当前音频没有匹配的有效字幕，已保持固定时长分段"
            viewModelScope.launch { app.settingsRepository.save(fallback) }
            return
        }
        currentSegments = buildSegments(value, track.durationMs)
        if (currentSegments.isEmpty()) return
        sendService(Intent(app, PlaybackService::class.java).apply {
            action = PlaybackContract.ACTION_UPDATE_SEGMENTS
            putExtra(PlaybackContract.EXTRA_STARTS, currentSegments.map { it.startMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_ENDS, currentSegments.map { it.endMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_TEXTS, currentSegments.map { it.text }.toTypedArray())
            putExtra(PlaybackContract.EXTRA_POSITION, playback.value.positionMs)
        })
    }

    private fun buildSegments(value: PlaybackSettings, durationMs: Long): List<Segment> {
        if (value.segmentMode == SegmentMode.SUBTITLE && currentCues.isNotEmpty()) {
            return Segmenter.fromCues(currentCues, durationMs, value.leadInMs, value.leadOutMs)
        }
        return Segmenter.fixed(durationMs, value.segmentSeconds * 1000L).map { segment ->
            val text = currentCues.filter { it.startMs < segment.endMs && it.endMs > segment.startMs }.joinToString(" ") { it.text.replace('\n', ' ') }
            segment.copy(text = text)
        }
    }

    private fun readCues(track: TrackEntity): List<SrtCue> = track.subtitleUri?.let { uri ->
        runCatching { app.contentResolver.openInputStream(Uri.parse(uri))?.use(SrtParser::parse) }.getOrNull()
    }.orEmpty()

    fun setSleepTimer(minutes: Int) {
        sendService(Intent(app, PlaybackService::class.java).apply {
            action = if (minutes <= 0) PlaybackContract.ACTION_CANCEL_TIMER else PlaybackContract.ACTION_TIMER
            putExtra(PlaybackContract.EXTRA_TIMER_MINUTES, minutes)
            putExtra(PlaybackContract.EXTRA_STOP_AT_END, mutableSettings.value.stopAtSegmentEnd)
        })
    }

    fun delete(track: TrackEntity) = viewModelScope.launch { dao.delete(track) }
    fun clearMessage() { mutableMessage.value = null }

    private suspend fun persistProgress(completed: Boolean) {
        val track = mutableCurrent.value ?: return
        val state = playback.value
        dao.updateProgress(track.id, state.positionMs, state.segmentIndex, System.currentTimeMillis(), completed)
    }

    private suspend fun advancePlaylist() {
        val list = tracks.value
        val currentTrack = mutableCurrent.value ?: return
        val index = list.indexOfFirst { it.id == currentTrack.id }
        when (mutableSettings.value.playlistMode) {
            PlaylistMode.STOP_AFTER_TRACK -> Unit
            PlaylistMode.SEQUENTIAL -> list.getOrNull(index + 1)?.let { openTrack(it) }
            PlaylistMode.LOOP_LIST -> if (list.isNotEmpty()) openTrack(list[(index + 1).mod(list.size)])
        }
    }
}


