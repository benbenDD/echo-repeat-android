package com.echoenglish.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echoenglish.app.EchoEnglishApp
import com.echoenglish.app.data.*
import com.echoenglish.app.model.*
import com.echoenglish.app.playback.*
import com.echoenglish.app.util.Segmenter
import com.echoenglish.app.util.SrtParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as EchoEnglishApp
    private val dao = app.database.trackDao
    private val library = LibraryRepository(app, dao)
    val tracks = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = app.settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackSettings())
    val playback = PlaybackBus.state
    private val mutableCurrent = MutableStateFlow<TrackEntity?>(null)
    val current = mutableCurrent.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private var completionHandled = false

    init {
        viewModelScope.launch {
            playback.collect { state ->
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
        val files = library.collectTree(uri)
        importUris(files)
    }

    fun openTrack(track: TrackEntity) = viewModelScope.launch {
        val activeSettings = settings.value
        val cues = track.subtitleUri?.let { uri ->
            runCatching { app.contentResolver.openInputStream(Uri.parse(uri))?.use(SrtParser::parse) }.getOrNull()
        }.orEmpty()
        val useSubtitle = activeSettings.segmentMode == SegmentMode.SUBTITLE && cues.isNotEmpty()
        val segments = if (useSubtitle) Segmenter.fromCues(cues, track.durationMs, activeSettings.leadInMs, activeSettings.leadOutMs)
        else Segmenter.fixed(track.durationMs, activeSettings.segmentSeconds * 1000L)
        if (segments.isEmpty()) {
            mutableMessage.value = "无法生成播放片段，请检查音频时长或字幕"
            return@launch
        }
        mutableCurrent.value = track
        app.settingsRepository.saveLastTrack(track.id)
        val targetSegment = track.currentSegment.coerceIn(0, segments.lastIndex)
        val intent = Intent(app, PlaybackService::class.java).apply {
            action = PlaybackContract.ACTION_LOAD
            putExtra(PlaybackContract.EXTRA_URI, track.audioUri)
            putExtra(PlaybackContract.EXTRA_TITLE, track.title)
            putExtra(PlaybackContract.EXTRA_STARTS, segments.map { it.startMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_ENDS, segments.map { it.endMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_TEXTS, segments.map { it.text }.toTypedArray())
            putExtra(PlaybackContract.EXTRA_REPEATS, activeSettings.repeatCount)
            putExtra(PlaybackContract.EXTRA_INDEX, targetSegment)
            putExtra(PlaybackContract.EXTRA_SPEED, activeSettings.speed)
        }
        app.startService(intent)
    }

    fun command(action: String, position: Long? = null) {
        app.startService(Intent(app, PlaybackService::class.java).apply {
            this.action = action
            position?.let { putExtra(PlaybackContract.EXTRA_POSITION, it) }
        })
    }

    fun setSleepTimer(minutes: Int) {
        app.startService(Intent(app, PlaybackService::class.java).apply {
            action = if (minutes <= 0) PlaybackContract.ACTION_CANCEL_TIMER else PlaybackContract.ACTION_TIMER
            putExtra(PlaybackContract.EXTRA_TIMER_MINUTES, minutes)
            putExtra(PlaybackContract.EXTRA_STOP_AT_END, settings.value.stopAtSegmentEnd)
        })
    }

    fun updateSettings(value: PlaybackSettings) = viewModelScope.launch {
        app.settingsRepository.save(value)
        mutableCurrent.value?.let { dao.update(it.copy(segmentMode=value.segmentMode.name, segmentSeconds=value.segmentSeconds, repeatCount=value.repeatCount, speed=value.speed)) }
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
        when (settings.value.playlistMode) {
            PlaylistMode.STOP_AFTER_TRACK -> Unit
            PlaylistMode.SEQUENTIAL -> list.getOrNull(index + 1)?.let { openTrack(it) }
            PlaylistMode.LOOP_LIST -> if (list.isNotEmpty()) openTrack(list[(index + 1).mod(list.size)])
        }
    }
}


