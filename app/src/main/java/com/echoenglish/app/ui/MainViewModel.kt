package com.echoenglish.app.ui

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
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
import com.echoenglish.app.model.SubtitlePlaybackScope
import com.echoenglish.app.playback.PlaybackBus
import com.echoenglish.app.playback.PlaybackCommandRecoveryDecision
import com.echoenglish.app.playback.PlaybackCommandRecoveryPolicy
import com.echoenglish.app.playback.PlaybackContract
import com.echoenglish.app.playback.PlaybackRestoreDecision
import com.echoenglish.app.playback.PlaybackRestoreLoadState
import com.echoenglish.app.playback.PlaybackRestorePolicy
import com.echoenglish.app.playback.PlaybackService
import com.echoenglish.app.playback.PlaybackServicePolicy
import com.echoenglish.app.playback.PlaybackStopReason
import com.echoenglish.app.playback.PlaylistNavigation
import com.echoenglish.app.playback.SegmentPlaybackPolicy
import com.echoenglish.app.util.Segmenter
import com.echoenglish.app.util.SrtParser
import com.echoenglish.app.util.SubtitleTiming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val mutableStartupRestoredTrackId = MutableStateFlow(0L)
    val startupRestoredTrackId = mutableStartupRestoredTrackId.asStateFlow()
    private var originalCues: List<SrtCue> = emptyList()
    private var currentCues: List<SrtCue> = emptyList()
    private var currentSubtitleOffsetMs = 0L
    private var currentSegments: List<Segment> = emptyList()
    private val subtitleOffsetWriteMutex = Mutex()
    private val playbackCommandMutex = Mutex()
    private var completionHandled = false
    private var sleepTimerStopHandled = false
    private var lastPlaybackError = ""

    init {
        viewModelScope.launch {
            var startupRestorePending = true
            app.settingsRepository.settings.collect { value ->
                mutableSettings.value = value
                if (startupRestorePending) {
                    startupRestorePending = false
                    restoreStartup(value)
                }
            }
        }
        viewModelScope.launch {
            playback.collect { state ->
                if (state.errorMessage.isNotBlank() && state.errorMessage != lastPlaybackError) {
                    lastPlaybackError = state.errorMessage
                    mutableMessage.value = state.errorMessage
                } else if (state.errorMessage.isBlank()) {
                    lastPlaybackError = ""
                }
                if (state.stopReason == PlaybackStopReason.SLEEP_TIMER) {
                    if (!sleepTimerStopHandled) {
                        sleepTimerStopHandled = true
                        completionHandled = false
                        persistProgress(false)
                        mutableMessage.value = "\u5b9a\u65f6\u7ed3\u675f\uff0c\u5df2\u5728\u5f53\u524d\u6bb5\u7ed3\u675f\u540e\u505c\u6b62"
                    }
                } else if (
                    PlaybackServicePolicy.shouldAdvancePlaylist(state.completed, state.stopReason) &&
                    !completionHandled
                ) {
                    sleepTimerStopHandled = false
                    completionHandled = true
                    persistProgress(true)
                    advancePlaylist()
                } else if (!state.completed) {
                    sleepTimerStopHandled = false
                    completionHandled = false
                }
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

    private fun sendService(intent: Intent): Boolean {
        if (PlaybackService.dispatchToActiveService(intent)) {
            Log.d("EchoPlayback", "command sent to active service: " + intent.action)
            return true
        }
        if (intent.action != PlaybackContract.ACTION_LOAD) {
            Log.i(
                "EchoPlayback",
                "command deferred because playback source is missing: " + intent.action
            )
            return false
        }
        try {
            if (PlaybackServicePolicy.requiresForegroundStart(
                    intent.action,
                    intent.getBooleanExtra(PlaybackContract.EXTRA_AUTO_PLAY, true)
                )
            ) {
                ContextCompat.startForegroundService(app, intent)
            } else {
                app.startService(intent)
            }
            return true
        } catch (error: RuntimeException) {
            val backgroundStartBlocked =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    error is ForegroundServiceStartNotAllowedException
            if (!backgroundStartBlocked) throw error

            Log.e(
                "EchoPlayback",
                "foreground service start blocked while app is backgrounded; action=" + intent.action,
                error
            )
            mutableMessage.value = "\u7cfb\u7edf\u6682\u4e0d\u5141\u8bb8\u4ece\u540e\u53f0\u542f\u52a8\u64ad\u653e\uff0c\u8bf7\u56de\u5230\u5e94\u7528\u540e\u91cd\u8bd5"
            return false
        }
    }

    fun openTrack(track: TrackEntity) = viewModelScope.launch {
        val activeSettings = mutableSettings.value
        if (!prepareTrack(track, activeSettings)) return@launch
        mutableCurrent.value = track
        app.settingsRepository.saveLastTrack(track.id)
        sendTrackLoad(
            track = track,
            activeSettings = activeSettings,
            loadState = PlaybackRestoreLoadState(
                positionMs = track.currentPositionMs,
                segmentIndex = track.currentSegment,
                repeatIndex = 1,
                autoPlay = true,
                restoreExactPosition = false
            )
        )
    }

    private suspend fun restoreStartup(activeSettings: PlaybackSettings) {
        val lastTrackId = app.settingsRepository.lastTrackId.first()
        when (val decision = PlaybackRestorePolicy.decide(
            activeMediaId = playback.value.mediaId,
            activeSegmentCount = playback.value.segmentCount,
            lastTrackId = lastTrackId,
            activeServiceAvailable = PlaybackService.hasActiveInstance()
        )) {
            is PlaybackRestoreDecision.AttachToActive -> {
                if (!attachToActiveTrack(decision.trackId, activeSettings) && lastTrackId > 0L) {
                    loadSavedTrackPaused(lastTrackId, activeSettings)
                }
            }
            is PlaybackRestoreDecision.LoadPaused ->
                loadSavedTrackPaused(decision.trackId, activeSettings)
            PlaybackRestoreDecision.None -> Unit
        }
    }

    private suspend fun attachToActiveTrack(
        trackId: Long,
        activeSettings: PlaybackSettings
    ): Boolean {
        val track = dao.getById(trackId)?.takeIf { it.available } ?: return false
        if (!prepareTrack(track, activeSettings)) return false
        mutableCurrent.value = track
        mutableStartupRestoredTrackId.value = track.id
        return true
    }

    private suspend fun loadSavedTrackPaused(
        trackId: Long,
        activeSettings: PlaybackSettings
    ) {
        val track = dao.getById(trackId)
        if (track == null || !track.available) {
            mutableMessage.value = "上次播放的音频已不可用，请从播放列表重新选择"
            return
        }
        if (!prepareTrack(track, activeSettings)) return
        val loadState = PlaybackRestorePolicy.loadState(
            trackId = track.id,
            databasePositionMs = track.currentPositionMs,
            databaseSegmentIndex = track.currentSegment,
            session = app.playbackSessionStore.read()
        )
        mutableCurrent.value = track.copy(
            currentPositionMs = loadState.positionMs,
            currentSegment = loadState.segmentIndex
        )
        sendTrackLoad(track, activeSettings, loadState)
        mutableStartupRestoredTrackId.value = track.id
    }

    private fun prepareTrack(track: TrackEntity, activeSettings: PlaybackSettings): Boolean {
        originalCues = readCues(track)
        currentSubtitleOffsetMs = SubtitleTiming.normalizedOffsetMs(track.subtitleOffsetMs)
        currentCues = SubtitleTiming.adjustCues(
            originalCues,
            currentSubtitleOffsetMs,
            track.durationMs
        )
        if (activeSettings.segmentMode == SegmentMode.SUBTITLE &&
            Segmenter.cueOnly(currentCues, track.durationMs).isEmpty()
        ) {
            mutableMessage.value = "当前音频没有有效字幕，本次临时使用固定时长分段"
        }
        currentSegments = buildSegments(activeSettings, track.durationMs)
        if (currentSegments.isEmpty()) {
            mutableMessage.value = "无法生成播放片段，请检查音频时长或字幕"
            return false
        }
        return true
    }

    private fun sendTrackLoad(
        track: TrackEntity,
        activeSettings: PlaybackSettings,
        loadState: PlaybackRestoreLoadState
    ) {
        val targetSegment = loadState.segmentIndex.coerceIn(0, currentSegments.lastIndex)
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
            putExtra(PlaybackContract.EXTRA_GAP_MS, activeSettings.segmentGapMs)
            putExtra(
                PlaybackContract.EXTRA_SKIP_SUBTITLE_GAPS,
                shouldSkipSubtitleGaps(activeSettings, track.durationMs)
            )
            putExtra(PlaybackContract.EXTRA_INDEX, targetSegment)
            putExtra(PlaybackContract.EXTRA_POSITION, loadState.positionMs)
            putExtra(PlaybackContract.EXTRA_REPEAT_INDEX, loadState.repeatIndex)
            putExtra(PlaybackContract.EXTRA_AUTO_PLAY, loadState.autoPlay)
            putExtra(
                PlaybackContract.EXTRA_RESTORE_EXACT_POSITION,
                loadState.restoreExactPosition
            )
            putExtra(PlaybackContract.EXTRA_SPEED, activeSettings.speed)
        })
    }

    fun command(action: String, position: Long? = null) {
        viewModelScope.launch {
            playbackCommandMutex.withLock {
                deliverPlaybackCommand(action, position = position)
            }
        }
    }

    fun seekAbsolute(positionMs: Long) = command(PlaybackContract.ACTION_SEEK_ABSOLUTE, positionMs)

    fun seekToSegment(index: Int) {
        viewModelScope.launch {
            playbackCommandMutex.withLock {
                deliverPlaybackCommand(
                    PlaybackContract.ACTION_SEEK_SEGMENT,
                    segmentIndex = index
                )
            }
        }
    }

    fun onAppForegrounded() {
        viewModelScope.launch {
            playbackCommandMutex.withLock {
                val track = mutableCurrent.value ?: return@withLock
                if (PlaybackService.hasLoadedSource(track.id.toString())) return@withLock
                deliverPlaybackCommand(PlaybackCommandRecoveryPolicy.ACTION_FOREGROUND_RESUME)
            }
        }
    }

    private suspend fun deliverPlaybackCommand(
        action: String,
        position: Long? = null,
        segmentIndex: Int? = null
    ) {
        val track = mutableCurrent.value
        val sourceReady = track?.let {
            PlaybackService.hasLoadedSource(it.id.toString())
        } ?: false
        if (sourceReady) {
            if (action == PlaybackCommandRecoveryPolicy.ACTION_FOREGROUND_RESUME) return
            if (sendService(commandIntent(action, position, segmentIndex))) return
        }

        when (val decision = PlaybackCommandRecoveryPolicy.decide(
            action = action,
            sourceReady = false,
            hasSelectedTrack = track != null
        )) {
            PlaybackCommandRecoveryDecision.Dispatch -> Unit
            PlaybackCommandRecoveryDecision.Ignore -> Unit
            is PlaybackCommandRecoveryDecision.Recover -> recoverSelectedTrack(
                track = track ?: return,
                action = action,
                position = position,
                requestedSegmentIndex = segmentIndex,
                autoPlay = decision.autoPlay
            )
        }
    }

    private fun commandIntent(
        action: String,
        position: Long?,
        segmentIndex: Int?
    ): Intent = Intent(app, PlaybackService::class.java).apply {
        this.action = action
        position?.let { putExtra(PlaybackContract.EXTRA_POSITION, it) }
        segmentIndex?.let { putExtra(PlaybackContract.EXTRA_INDEX, it) }
    }

    private suspend fun recoverSelectedTrack(
        track: TrackEntity,
        action: String,
        position: Long?,
        requestedSegmentIndex: Int?,
        autoPlay: Boolean
    ) {
        val activeSettings = mutableSettings.value
        if (!prepareTrack(track, activeSettings)) return
        val savedState = PlaybackRestorePolicy.loadState(
            trackId = track.id,
            databasePositionMs = track.currentPositionMs,
            databaseSegmentIndex = track.currentSegment,
            session = app.playbackSessionStore.read()
        )
        val safeIndex = savedState.segmentIndex.coerceIn(0, currentSegments.lastIndex)
        val safePosition = savedState.positionMs.coerceIn(0L, track.durationMs)
        val loadState = when (action) {
            PlaybackCommandRecoveryPolicy.ACTION_FOREGROUND_RESUME,
            PlaybackContract.ACTION_TOGGLE -> savedState.copy(
                positionMs = safePosition,
                segmentIndex = safeIndex,
                autoPlay = autoPlay
            )
            PlaybackContract.ACTION_SEEK_ABSOLUTE -> savedState.copy(
                positionMs = (position ?: safePosition).coerceIn(0L, track.durationMs),
                segmentIndex = safeIndex,
                repeatIndex = 1,
                autoPlay = false
            )
            PlaybackContract.ACTION_SEEK_SEGMENT -> {
                val targetIndex = (requestedSegmentIndex ?: safeIndex)
                    .coerceIn(0, currentSegments.lastIndex)
                savedState.copy(
                    positionMs = currentSegments[targetIndex].startMs,
                    segmentIndex = targetIndex,
                    repeatIndex = 1,
                    autoPlay = false
                )
            }
            PlaybackContract.ACTION_NEXT -> {
                val targetIndex = (safeIndex + 1).coerceAtMost(currentSegments.lastIndex)
                savedState.copy(
                    positionMs = currentSegments[targetIndex].startMs,
                    segmentIndex = targetIndex,
                    repeatIndex = 1,
                    autoPlay = false
                )
            }
            PlaybackContract.ACTION_PREVIOUS -> {
                val positionInSegment = (safePosition - currentSegments[safeIndex].startMs)
                    .coerceAtLeast(0L)
                val targetIndex = SegmentPlaybackPolicy.previousTargetIndex(
                    safeIndex,
                    positionInSegment
                )
                savedState.copy(
                    positionMs = currentSegments[targetIndex].startMs,
                    segmentIndex = targetIndex,
                    repeatIndex = 1,
                    autoPlay = false
                )
            }
            PlaybackContract.ACTION_RESTART -> savedState.copy(
                positionMs = currentSegments[safeIndex].startMs,
                segmentIndex = safeIndex,
                repeatIndex = 1,
                autoPlay = false
            )
            PlaybackContract.ACTION_SEEK -> savedState.copy(
                positionMs = (currentSegments[safeIndex].startMs + (position ?: 0L))
                    .coerceIn(
                        currentSegments[safeIndex].startMs,
                        currentSegments[safeIndex].endMs
                    ),
                segmentIndex = safeIndex,
                repeatIndex = 1,
                autoPlay = false
            )
            else -> return
        }
        Log.i(
            "EchoPlayback",
            "recovering missing source action=$action trackId=${track.id} " +
                "position=${loadState.positionMs} segment=${loadState.segmentIndex} " +
                "repeat=${loadState.repeatIndex} autoPlay=${loadState.autoPlay}"
        )
        sendTrackLoad(track, activeSettings, loadState)
    }

    fun updateSettings(value: PlaybackSettings) {
        val currentTrack = mutableCurrent.value
        if (value.segmentMode == SegmentMode.SUBTITLE && currentTrack != null &&
            Segmenter.cueOnly(currentCues, currentTrack.durationMs).isEmpty()
        ) {
            mutableMessage.value = if (value.subtitlePlaybackScope == SubtitlePlaybackScope.CUES_ONLY) {
                "当前音频没有匹配的有效字幕，无法只播放字幕片段"
            } else {
                "当前音频没有匹配的有效字幕，无法切换为按字幕分段"
            }
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
        val paddingChanged = (previous.leadInMs != value.leadInMs ||
            previous.leadOutMs != value.leadOutMs) &&
            value.segmentMode == SegmentMode.SUBTITLE &&
            value.subtitlePlaybackScope == SubtitlePlaybackScope.CUES_ONLY
        if (mutableCurrent.value != null && (
                previous.segmentSeconds != value.segmentSeconds ||
                    previous.segmentMode != value.segmentMode ||
                    previous.subtitlePlaybackScope != value.subtitlePlaybackScope ||
                    paddingChanged
                )
        ) {
            rebuildActiveSegments(value, alignToSegmentStart = paddingChanged)
        }
        if (previous.repeatCount != value.repeatCount) {
            sendService(Intent(app, PlaybackService::class.java).apply {
                action = PlaybackContract.ACTION_UPDATE_REPEATS
                putExtra(PlaybackContract.EXTRA_REPEATS, value.repeatCount)
            })
        }
        if (previous.segmentGapMs != value.segmentGapMs) {
            sendService(Intent(app, PlaybackService::class.java).apply {
                action = PlaybackContract.ACTION_UPDATE_GAP
                putExtra(PlaybackContract.EXTRA_GAP_MS, value.segmentGapMs)
            })
        }
        if (previous.speed != value.speed) {
            sendService(Intent(app, PlaybackService::class.java).apply {
                action = PlaybackContract.ACTION_UPDATE_SPEED
                putExtra(PlaybackContract.EXTRA_SPEED, value.speed)
            })
        }
    }

    private fun rebuildActiveSegments(
        value: PlaybackSettings,
        alignToSegmentStart: Boolean = false
    ) {
        val track = mutableCurrent.value ?: return
        if (value.segmentMode == SegmentMode.SUBTITLE &&
            Segmenter.cueOnly(currentCues, track.durationMs).isEmpty()
        ) {
            val fallback = value.copy(segmentMode = SegmentMode.FIXED)
            mutableSettings.value = fallback
            mutableMessage.value = "当前音频没有匹配的有效字幕，已保持固定时长分段"
            viewModelScope.launch { app.settingsRepository.save(fallback) }
            return
        }
        currentSegments = buildSegments(value, track.durationMs)
        if (currentSegments.isEmpty()) return
        val currentPosition = playback.value.positionMs
        val targetPosition = if (alignToSegmentStart) {
            val index = currentSegments.indexOfLast { currentPosition >= it.startMs }
                .coerceIn(0, currentSegments.lastIndex)
            currentSegments[index].startMs
        } else {
            currentPosition
        }
        sendService(Intent(app, PlaybackService::class.java).apply {
            action = PlaybackContract.ACTION_UPDATE_SEGMENTS
            putExtra(PlaybackContract.EXTRA_STARTS, currentSegments.map { it.startMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_ENDS, currentSegments.map { it.endMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_TEXTS, currentSegments.map { it.text }.toTypedArray())
            putExtra(PlaybackContract.EXTRA_CUE_STARTS, currentCues.map { it.startMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_CUE_ENDS, currentCues.map { it.endMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_CUE_TEXTS, currentCues.map { it.text }.toTypedArray())
            putExtra(
                PlaybackContract.EXTRA_SKIP_SUBTITLE_GAPS,
                shouldSkipSubtitleGaps(value, track.durationMs)
            )
            putExtra(PlaybackContract.EXTRA_POSITION, targetPosition)
        })
    }

    private fun buildSegments(value: PlaybackSettings, durationMs: Long): List<Segment> {
        if (value.segmentMode == SegmentMode.SUBTITLE && currentCues.isNotEmpty()) {
            return if (value.subtitlePlaybackScope == SubtitlePlaybackScope.CUES_ONLY) {
                Segmenter.cueOnly(
                    currentCues,
                    durationMs,
                    value.leadInMs,
                    value.leadOutMs
                ).also { finalSegments ->
                    Log.i(
                        "EchoSegments",
                        "mode=SUBTITLE scope=CUES_ONLY subtitleOffsetMs=$currentSubtitleOffsetMs " +
                            "leadInMs=${value.leadInMs} leadOutMs=${value.leadOutMs} " +
                            "originalCues=${originalCues.size} adjustedCues=${currentCues.size} " +
                            "finalSegments=${finalSegments.size} firstOriginal=${originalCues.firstOrNull()} " +
                            "firstFinal=${finalSegments.firstOrNull()}"
                    )
                }
            } else {
                Segmenter.fromCues(currentCues, durationMs)
            }
        }
        return Segmenter.fixed(durationMs, value.segmentSeconds * 1000L).map { segment ->
            val text = currentCues.filter { it.startMs < segment.endMs && it.endMs > segment.startMs }.joinToString(" ") { it.text.replace('\n', ' ') }
            segment.copy(text = text)
        }
    }

    private fun shouldSkipSubtitleGaps(value: PlaybackSettings, durationMs: Long): Boolean =
        value.segmentMode == SegmentMode.SUBTITLE &&
            value.subtitlePlaybackScope == SubtitlePlaybackScope.CUES_ONLY &&
            Segmenter.cueOnly(currentCues, durationMs).isNotEmpty()

    private fun readCues(track: TrackEntity): List<SrtCue> = track.subtitleUri?.let { uri ->
        runCatching { app.contentResolver.openInputStream(Uri.parse(uri))?.use(SrtParser::parse) }.getOrNull()
    }.orEmpty()

    fun updateSubtitleOffset(requestedOffsetMs: Long) {
        val track = mutableCurrent.value ?: run {
            mutableMessage.value = "请先从播放列表打开一个音频"
            return
        }
        if (track.subtitleUri == null) {
            mutableMessage.value = "当前音频没有匹配字幕，无法调整字幕时间"
            return
        }
        val normalizedOffsetMs = SubtitleTiming.normalizedOffsetMs(requestedOffsetMs)
        if (normalizedOffsetMs == currentSubtitleOffsetMs) return

        val state = playback.value
        val previousCues = currentCues
        val previousSegments = currentSegments
        val activeSegment = previousSegments.getOrNull(state.segmentIndex)
        val activeCueIndex = activeSegment?.let { segment ->
            previousCues.firstOrNull { cue ->
                cue.startMs < segment.endMs && cue.endMs > segment.startMs
            }?.index
        } ?: previousCues.lastOrNull { it.startMs <= state.positionMs }?.index

        currentSubtitleOffsetMs = normalizedOffsetMs
        currentCues = SubtitleTiming.adjustCues(
            originalCues,
            currentSubtitleOffsetMs,
            track.durationMs
        )
        val updatedTrack = track.copy(subtitleOffsetMs = currentSubtitleOffsetMs)
        mutableCurrent.value = updatedTrack
        viewModelScope.launch {
            subtitleOffsetWriteMutex.withLock {
                dao.updateSubtitleOffset(updatedTrack.id, updatedTrack.subtitleOffsetMs)
            }
        }

        val activeSettings = mutableSettings.value
        currentSegments = buildSegments(activeSettings, track.durationMs)
        if (currentSegments.isEmpty()) {
            mutableMessage.value = "字幕校准后无法生成有效播放片段"
            return
        }

        val targetPosition = if (activeSettings.segmentMode == SegmentMode.SUBTITLE) {
            val adjustedCue = currentCues.firstOrNull { it.index == activeCueIndex }
            val adjustedSegment = adjustedCue?.let { cue ->
                currentSegments.firstOrNull { segment ->
                    cue.startMs < segment.endMs && cue.endMs > segment.startMs
                }
            }
            adjustedSegment?.startMs
                ?: currentSegments.getOrNull(state.segmentIndex)?.startMs
                ?: state.positionMs
        } else {
            state.positionMs
        }

        sendService(Intent(app, PlaybackService::class.java).apply {
            action = PlaybackContract.ACTION_UPDATE_SEGMENTS
            putExtra(PlaybackContract.EXTRA_STARTS, currentSegments.map { it.startMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_ENDS, currentSegments.map { it.endMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_TEXTS, currentSegments.map { it.text }.toTypedArray())
            putExtra(PlaybackContract.EXTRA_CUE_STARTS, currentCues.map { it.startMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_CUE_ENDS, currentCues.map { it.endMs }.toLongArray())
            putExtra(PlaybackContract.EXTRA_CUE_TEXTS, currentCues.map { it.text }.toTypedArray())
            putExtra(
                PlaybackContract.EXTRA_SKIP_SUBTITLE_GAPS,
                shouldSkipSubtitleGaps(activeSettings, track.durationMs)
            )
            putExtra(PlaybackContract.EXTRA_POSITION, targetPosition)
            putExtra(
                PlaybackContract.EXTRA_PRESERVE_POSITION,
                activeSettings.segmentMode == SegmentMode.FIXED
            )
        })

        Log.i(
            "EchoSubtitleTiming",
            "trackId=${track.id} subtitleOffsetMs=$currentSubtitleOffsetMs " +
                "originalCueCount=${originalCues.size} adjustedCueCount=${currentCues.size} " +
                "originalFirst=${originalCues.firstOrNull()} adjustedFirst=${currentCues.firstOrNull()} " +
                "segmentMode=${activeSettings.segmentMode} " +
                "subtitlePlaybackScope=${activeSettings.subtitlePlaybackScope} " +
                "leadInMs=${activeSettings.leadInMs} leadOutMs=${activeSettings.leadOutMs} " +
                "finalSegment=${currentSegments.getOrNull(state.segmentIndex)} targetPosition=$targetPosition"
        )
        val absoluteOffset = kotlin.math.abs(currentSubtitleOffsetMs)
        val offsetLabel = if (absoluteOffset % 1_000L == 0L) {
            "${absoluteOffset / 1_000L}秒"
        } else {
            "${absoluteOffset / 1_000.0}秒"
        }
        mutableMessage.value = when {
            currentSubtitleOffsetMs < 0 -> "字幕已提前 $offsetLabel"
            currentSubtitleOffsetMs > 0 -> "字幕已延后 $offsetLabel"
            else -> "字幕时间已恢复为不调整"
        }
    }

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
        PlaylistNavigation.nextIndex(mutableSettings.value.playlistMode, index, list.size)
            ?.let { nextIndex -> list.getOrNull(nextIndex)?.let { openTrack(it) } }
    }
}


