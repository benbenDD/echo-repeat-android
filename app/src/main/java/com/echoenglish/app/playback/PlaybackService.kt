package com.echoenglish.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.echoenglish.app.EchoEnglishApp
import com.echoenglish.app.MainActivity
import com.echoenglish.app.model.Segment
import com.echoenglish.app.util.Segmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@UnstableApi
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var sessionPlayer: Player
    private lateinit var session: MediaSession
    private val handler = Handler(Looper.getMainLooper())
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceMutex = Mutex()
    private lateinit var app: EchoEnglishApp
    private lateinit var playbackSessionStore: PlaybackSessionStore
    private lateinit var diagnostics: PlaybackDiagnostics
    private var lastSessionSavedAtMs = 0L
    private var lastDatabaseSavedAtMs = 0L

    private var starts = longArrayOf()
    private var ends = longArrayOf()
    private var texts = emptyArray<String>()
    private var cueStarts = longArrayOf()
    private var cueEnds = longArrayOf()
    private var cueTexts = emptyArray<String>()
    private var cueIds = intArrayOf()
    private var bookmarkedCueIds = emptySet<Int>()
    private var segmentCache = emptyList<SegmentSnapshot>()
    private var subtitleCache = emptyList<SubtitleSnapshot>()
    private var segmentIndex = 0
    private var repeatIndex = 1
    private var repeatCount = 1
    private var segmentGapMs = 0L
    private var followAlongEnabled = false
    private var skipSubtitleGaps = false
    private var currentTitle = ""
    private var knownDurationMs = 0L
    private var sourceUri: Uri? = null
    private var sourceMediaId = ""
    private var playbackWindowStartMs = 0L
    private var playbackWindowEndMs = 0L
    private var isPipelineClipped = false
    private var transitionInProgress = false
    private var queuedAdjacentSegmentIndex: Int? = null
    private var queuedAdjacentMediaItemIndex: Int? = null
    private var repeatBoundaryDetectedElapsedMs = 0L
    private var repeatRestartRequestedElapsedMs = 0L
    private var repeatReusedPreparedWindow = false
    private var repeatUsedAddMediaItem = false
    private var repeatCalledPrepare = false
    private var boundaryHandledGeneration = -1L
    private var armedBoundaryToken: BoundaryToken? = null
    private lateinit var gapWakeLock: PowerManager.WakeLock
    private var sleepDeadline = 0L
    private var stopAtSegmentEnd = true
    private var pendingSleepStop = false
    private var completed = false
    private var stopReason = PlaybackStopReason.NONE
    private var playbackError = ""
    private var playbackTaskActive = false
    private var playlistHandoffDeadlineElapsedMs = 0L
    private val playlistHandoffExpiryRunnable = Runnable { expirePlaylistHandoff() }

    private var scheduleGeneration = 0L
    private var boundaryMessage: PlayerMessage? = null
    private var gapRunnable: Runnable? = null
    private var isInSegmentGap = false
    private var isSegmentGapPaused = false
    private var isFollowAlongGap = false
    private var segmentGapRemainingMs = 0L
    private var gapDeadlineElapsedMs = 0L
    private var pendingGapAction: SegmentBoundaryAction? = null

    private data class BoundaryToken(
        val generation: Long,
        val segmentIndex: Int,
        val repeatIndex: Int,
        val startMs: Long,
        val endMs: Long
    )

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PlaybackService created")
        app = application as EchoEnglishApp
        playbackSessionStore = app.playbackSessionStore
        diagnostics = PlaybackDiagnostics(this, persistenceScope)
        diagnostics.record("service_created")
        gapWakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:segment-gap")
            .apply { setReferenceCounted(false) }
        val extractorsFactory = DefaultExtractorsFactory()
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractorsFactory))
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                setSeekParameters(SeekParameters.EXACT)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.i(TAG, "isPlaying=$isPlaying state=$playbackState phase=${phaseName()}")
                        diagnostics.record(
                            "is_playing_changed value=$isPlaying state=$playbackState " +
                                "playWhenReady=${player.playWhenReady} phase=${phaseName()} " +
                                "segment=$segmentIndex repeat=$repeatIndex"
                        )
                        if (isPlaying) {
                            logCompletedRepeatTransition()
                            armBoundary()
                        }
                        publish()
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        Log.i(TAG, "playWhenReady=$playWhenReady reason=$reason phase=${phaseName()}")
                        diagnostics.record(
                            "play_when_ready value=$playWhenReady reason=$reason phase=${phaseName()} " +
                                "timerDeadline=$sleepDeadline"
                        )
                        publish()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val queuedSegmentIndex = queuedAdjacentSegmentIndex
                        val queuedMediaItemIndex = queuedAdjacentMediaItemIndex
                        val currentMediaItemIndex = this@PlaybackService.player.currentMediaItemIndex
                        if (
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                            queuedSegmentIndex != null &&
                            queuedMediaItemIndex != null &&
                            currentMediaItemIndex == queuedMediaItemIndex
                        ) {
                            transitionInProgress = true
                            cancelBoundary()
                            segmentIndex = queuedSegmentIndex
                            repeatIndex = 1
                            playbackWindowStartMs = starts[queuedSegmentIndex]
                            playbackWindowEndMs = ends[queuedSegmentIndex]
                            queuedAdjacentSegmentIndex = null
                            queuedAdjacentMediaItemIndex = null
                            if (currentMediaItemIndex > 0) {
                                this@PlaybackService.player.removeMediaItems(0, currentMediaItemIndex)
                            }
                            transitionInProgress = false
                            Log.i(
                                TAG,
                                "queued transition segment=$segmentIndex repeat=1 " +
                                    "window=$playbackWindowStartMs..$playbackWindowEndMs " +
                                    "consumedMediaItemIndex=$currentMediaItemIndex " +
                                    "mediaItemCount=${this@PlaybackService.player.mediaItemCount}"
                            )
                            armBoundary()
                            publish()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.i(TAG, "playbackState=$playbackState")
                        if (
                            playbackState == Player.STATE_ENDED &&
                            PlaybackServicePolicy.mayAutomateBoundary(stopReason) &&
                            !completed &&
                            !transitionInProgress &&
                            starts.isNotEmpty()
                        ) {
                            handlePlaybackWindowEnded()
                        }
                        publish()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        cancelAutomatedWork(clearGap = true)
                        cancelPlaylistHandoff()
                        playbackTaskActive = false
                        playbackError = "播放中断：${error.errorCodeName}"
                        Log.e(TAG, playbackError, error)
                        diagnostics.record("player_error code=${error.errorCodeName} message=${error.message}")
                        publish()
                    }
                })
            }

        sessionPlayer = SegmentControlPlayer(player)
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = PlaybackContract.ACTION_OPEN_PLAYER
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val sessionActivity = PendingIntent.getActivity(this, 1001, launchIntent, pendingIntentFlags)
        session = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(sessionActivity)
            .build()
        addSession(session)
        Log.i(TAG, "MediaSession registered added=${isSessionAdded(session)}")

        val prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE)
        // Keep an expired persisted deadline so a recreated service handles it on its first tick.
        sleepDeadline = prefs.getLong("deadline", 0L)
        stopAtSegmentEnd = prefs.getBoolean("stop_at_end", true)
        handler.post(ticker)
        activeInstance = this
        activeSourceMediaId = null
        Log.i(TAG, "active service command dispatcher registered")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        Log.i(TAG, "onGetSession package=${controllerInfo.packageName} added=${isSessionAdded(session)}")
        return session
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val playlistHandoffActive = isPlaylistHandoffActive()
        val runInForeground =
            PlaybackServicePolicy.shouldRunInForeground(
                startInForegroundRequired = startInForegroundRequired,
                playbackTaskActive = playbackTaskActive,
                completed = completed,
                hasSource = sourceUri != null,
                playlistHandoffActive = playlistHandoffActive
            )
        Log.i(
            TAG,
            "onUpdateNotification foregroundRequired=$startInForegroundRequired " +
                "effectivePlaying=${effectiveIsPlaying()} phase=${phaseName()} foreground=$runInForeground " +
                "handoff=$playlistHandoffActive"
        )
        diagnostics.record(
            "notification requested=$startInForegroundRequired foreground=$runInForeground " +
                "taskActive=$playbackTaskActive phase=${phaseName()} hasSource=${sourceUri != null} " +
                "handoff=$playlistHandoffActive"
        )
        // Passing true actively promotes the service again if a transient player state caused
        // Media3 or an OEM power manager to downgrade it during a repeat/segment transition.
        super.onUpdateNotification(session, runInForeground)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action ?: "null"} startId=$startId")
        intent?.let(::handleCommand)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleCommand(intent: Intent) {
        Log.i(TAG, "handleCommand action=${intent.action ?: "null"}")
        diagnostics.record("command action=${intent.action ?: "null"} phase=${phaseName()}")
        when (intent.action) {
            PlaybackContract.ACTION_LOAD -> load(intent)
            PlaybackContract.ACTION_TOGGLE -> togglePlayback()
            PlaybackContract.ACTION_NEXT -> nextSegment()
            PlaybackContract.ACTION_PREVIOUS -> previousSegment()
            PlaybackContract.ACTION_RESTART -> moveTo(segmentIndex)
            PlaybackContract.ACTION_SEEK -> seekWithin(intent.getLongExtra(PlaybackContract.EXTRA_POSITION, 0))
            PlaybackContract.ACTION_SEEK_ABSOLUTE -> seekAbsolute(intent.getLongExtra(PlaybackContract.EXTRA_POSITION, 0))
            PlaybackContract.ACTION_SEEK_SEGMENT -> moveTo(intent.getIntExtra(PlaybackContract.EXTRA_INDEX, segmentIndex))
            PlaybackContract.ACTION_UPDATE_SEGMENTS -> updateSegments(intent)
            PlaybackContract.ACTION_UPDATE_BOOKMARKS -> updateBookmarks(intent)
            PlaybackContract.ACTION_UPDATE_REPEATS -> updateRepeats(intent.getIntExtra(PlaybackContract.EXTRA_REPEATS, repeatCount))
            PlaybackContract.ACTION_UPDATE_GAP -> updateGap(intent.getLongExtra(PlaybackContract.EXTRA_GAP_MS, segmentGapMs))
            PlaybackContract.ACTION_UPDATE_FOLLOW_ALONG -> updateFollowAlong(
                intent.getBooleanExtra(PlaybackContract.EXTRA_FOLLOW_ALONG, followAlongEnabled)
            )
            PlaybackContract.ACTION_UPDATE_SPEED -> updateSpeed(intent.getFloatExtra(PlaybackContract.EXTRA_SPEED, 1f))
            PlaybackContract.ACTION_TIMER -> setTimer(intent)
            PlaybackContract.ACTION_CANCEL_TIMER -> clearTimer()
        }
    }

    private inner class SegmentControlPlayer(delegate: Player) : ForwardingPlayer(delegate) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .addAll(
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                )
                .build()

        override fun isCommandAvailable(command: Int): Boolean =
            when (command) {
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> starts.isNotEmpty()
                else -> super.isCommandAvailable(command)
            }

        override fun hasPrevious(): Boolean = starts.isNotEmpty()
        override fun hasPreviousWindow(): Boolean = starts.isNotEmpty()
        override fun hasPreviousMediaItem(): Boolean = starts.isNotEmpty()
        override fun previous() = previousSegment()
        override fun seekToPrevious() = previousSegment()
        override fun seekToPreviousWindow() = previousSegment()
        override fun seekToPreviousMediaItem() = previousSegment()

        override fun hasNext(): Boolean = starts.isNotEmpty()
        override fun hasNextWindow(): Boolean = starts.isNotEmpty()
        override fun hasNextMediaItem(): Boolean = starts.isNotEmpty()
        override fun next() = nextSegment()
        override fun seekToNext() = nextSegment()
        override fun seekToNextWindow() = nextSegment()
        override fun seekToNextMediaItem() = nextSegment()

        override fun play() = handlePlayRequest()
        override fun pause() = handlePauseRequest()
        override fun isPlaying(): Boolean = effectiveIsPlaying()
        override fun getPlayWhenReady(): Boolean = effectiveIsPlaying()
        override fun getCurrentPosition(): Long = absolutePositionMs()
        override fun getContentPosition(): Long = absolutePositionMs()
        override fun getDuration(): Long = durationMs()
        override fun getBufferedPosition(): Long =
            if (isPipelineClipped) {
                (playbackWindowStartMs + super.getBufferedPosition())
                    .coerceAtMost(playbackWindowEndMs)
            } else {
                super.getBufferedPosition().coerceAtMost(durationMs())
            }

        override fun seekTo(positionMs: Long) = seekAbsolute(positionMs)
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) = seekAbsolute(positionMs)
    }

    private fun load(intent: Intent) {
        if (activeInstance === this) activeSourceMediaId = null
        val uri = intent.getStringExtra(PlaybackContract.EXTRA_URI)?.toUri() ?: run {
            playbackError = "无法播放：音频地址为空"
            Log.e(TAG, playbackError)
            publish()
            return
        }
        cancelAutomatedWork(clearGap = true)
        cancelPlaylistHandoff()
        playbackError = ""
        sourceUri = uri
        knownDurationMs = intent.getLongExtra(
            PlaybackContract.EXTRA_DURATION,
            0L
        ).coerceAtLeast(0)
        val rawStarts = intent.getLongArrayExtra(PlaybackContract.EXTRA_STARTS) ?: longArrayOf(0)
        val rawEnds = intent.getLongArrayExtra(PlaybackContract.EXTRA_ENDS)
            ?: longArrayOf(knownDurationMs)
        val rawTexts = intent.getStringArrayExtra(PlaybackContract.EXTRA_TEXTS) ?: emptyArray()
        if (!applyNormalizedSegments(rawStarts, rawEnds, rawTexts)) {
            playbackError = "无法播放：没有有效且不重叠的分段"
            Log.e(TAG, playbackError)
            publish()
            return
        }
        cueStarts = intent.getLongArrayExtra(PlaybackContract.EXTRA_CUE_STARTS) ?: longArrayOf()
        cueEnds = intent.getLongArrayExtra(PlaybackContract.EXTRA_CUE_ENDS) ?: longArrayOf()
        cueTexts = intent.getStringArrayExtra(PlaybackContract.EXTRA_CUE_TEXTS) ?: emptyArray()
        cueIds = intent.getIntArrayExtra(PlaybackContract.EXTRA_CUE_IDS) ?: IntArray(cueStarts.size) { it }
        bookmarkedCueIds = intent.getIntArrayExtra(PlaybackContract.EXTRA_BOOKMARKED_CUE_IDS)
            ?.toSet()
            .orEmpty()
        rebuildCaches()
        repeatCount = intent.getIntExtra(PlaybackContract.EXTRA_REPEATS, 1).coerceAtLeast(0)
        segmentGapMs = SegmentPlaybackPolicy.normalizedGapMs(
            intent.getLongExtra(PlaybackContract.EXTRA_GAP_MS, 0L)
        )
        followAlongEnabled = intent.getBooleanExtra(
            PlaybackContract.EXTRA_FOLLOW_ALONG,
            false
        )
        skipSubtitleGaps = intent.getBooleanExtra(
            PlaybackContract.EXTRA_SKIP_SUBTITLE_GAPS,
            false
        )
        val requestedPosition = intent.getLongExtra(PlaybackContract.EXTRA_POSITION, -1)
        val resolvedPosition = if (requestedPosition >= 0) {
            PlaybackMath.snapToPlayablePosition(
                starts,
                ends,
                requestedPosition,
                knownDurationMs,
                skipSubtitleGaps
            )
        } else {
            -1L
        }
        segmentIndex = if (resolvedPosition >= 0) {
            PlaybackMath.segmentIndexAt(starts, ends, resolvedPosition)
        } else {
            intent.getIntExtra(PlaybackContract.EXTRA_INDEX, 0)
                .coerceIn(0, starts.lastIndex)
        }
        repeatIndex = SegmentPlaybackPolicy.normalizedRepeatIndex(
            repeatCount,
            intent.getIntExtra(PlaybackContract.EXTRA_REPEAT_INDEX, 1)
        )
        currentTitle = intent.getStringExtra(PlaybackContract.EXTRA_TITLE).orEmpty()
        sourceMediaId = intent.getStringExtra(PlaybackContract.EXTRA_MEDIA_ID) ?: uri.toString()
        completed = false
        pendingSleepStop = false
        stopReason = PlaybackStopReason.NONE
        val requestedStartPosition = if (resolvedPosition >= 0) {
            resolvedPosition
        } else {
            starts[segmentIndex]
        }
        val startPosition = SegmentPlaybackPolicy.initialPosition(
            requestedPositionMs = requestedStartPosition,
            segmentStartMs = starts[segmentIndex],
            repeatCount = repeatCount,
            restoreExactPosition = intent.getBooleanExtra(
                PlaybackContract.EXTRA_RESTORE_EXACT_POSITION,
                false
            )
        )
        player.setPlaybackSpeed(intent.getFloatExtra(PlaybackContract.EXTRA_SPEED, 1f))
        startPlaybackAt(
            startPosition,
            shouldPlay = intent.getBooleanExtra(PlaybackContract.EXTRA_AUTO_PLAY, true)
        )
        if (activeInstance === this) activeSourceMediaId = sourceMediaId
        publish()
    }

    private fun applyNormalizedSegments(
        rawStarts: LongArray,
        rawEnds: LongArray,
        rawTexts: Array<String>
    ): Boolean {
        if (rawStarts.isEmpty() || rawStarts.size != rawEnds.size || knownDurationMs <= 0) {
            return false
        }
        val normalized = Segmenter.normalize(
            rawStarts.indices.map { index ->
                Segment(
                    startMs = rawStarts[index],
                    endMs = rawEnds[index],
                    text = rawTexts.getOrElse(index) { "" }
                )
            },
            durationMs = knownDurationMs,
            mergeOverlaps = true
        )
        if (normalized.isEmpty()) return false
        starts = normalized.map(Segment::startMs).toLongArray()
        ends = normalized.map(Segment::endMs).toLongArray()
        texts = normalized.map(Segment::text).toTypedArray()
        Log.i(
            TAG,
            "segments normalized input=${rawStarts.size} output=${starts.size} " +
                "overlapFree=${starts.indices.drop(1).all { ends[it - 1] <= starts[it] }}"
        )
        return true
    }

    private fun buildMediaItem(windowStartMs: Long, windowEndMs: Long): MediaItem {
        val uri = sourceUri ?: error("Source URI is not loaded")
        val builder = MediaItem.Builder()
            .setMediaId(sourceMediaId)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(currentTitle)
                    .setArtist("回声复读 · 分段听读")
                    .build()
            )
        if (windowStartMs > 0 || windowEndMs < knownDurationMs) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(windowStartMs)
                    .setEndPositionMs(windowEndMs)
                    .build()
            )
        }
        return builder.build()
    }

    private fun requiresPipelineClipping(): Boolean =
        repeatCount != 1 || skipSubtitleGaps || followAlongEnabled

    private fun isAdjacent(currentIndex: Int, nextIndex: Int): Boolean =
        nextIndex in starts.indices &&
            kotlin.math.abs(ends[currentIndex] - starts[nextIndex]) <= ADJACENT_TOLERANCE_MS

    private fun adjacentNextForQueue(): Int? {
        if (followAlongEnabled) return null
        val nextIndex = segmentIndex + 1
        return nextIndex.takeIf {
            SegmentPlaybackPolicy.canContinueIntoAdjacentNext(
                repeatCount = repeatCount,
                repeatIndex = repeatIndex,
                hasNextSegment = nextIndex in starts.indices,
                isAdjacent = nextIndex in starts.indices && isAdjacent(segmentIndex, nextIndex)
            )
        }
    }

    private fun desiredWindowEndMs(): Long = ends[segmentIndex]

    private fun discardFutureMediaItems() {
        val currentMediaItemIndex = player.currentMediaItemIndex
        val firstFutureIndex = currentMediaItemIndex + 1
        if (currentMediaItemIndex >= 0 && firstFutureIndex < player.mediaItemCount) {
            player.removeMediaItems(firstFutureIndex, player.mediaItemCount)
        }
        queuedAdjacentSegmentIndex = null
        queuedAdjacentMediaItemIndex = null
    }

    private fun prepareAdjacentSegmentForFinalRepeat(): Boolean {
        val nextSegmentIndex = adjacentNextForQueue() ?: return false
        val currentMediaItemIndex = player.currentMediaItemIndex
        if (currentMediaItemIndex < 0) return false
        val targetMediaItemIndex = currentMediaItemIndex + 1
        player.addMediaItem(
            targetMediaItemIndex,
            buildMediaItem(starts[nextSegmentIndex], ends[nextSegmentIndex])
        )
        queuedAdjacentSegmentIndex = nextSegmentIndex
        queuedAdjacentMediaItemIndex = targetMediaItemIndex
        Log.i(
            TAG,
            "adjacent item appended segment=$segmentIndex repeat=$repeatIndex " +
                "queuedSegment=$nextSegmentIndex queuedMediaItem=$targetMediaItemIndex " +
                "mediaItemCount=${player.mediaItemCount} usedAddMediaItem=true calledPrepare=false"
        )
        return true
    }

    private fun startPlaybackAt(absolutePositionMs: Long, shouldPlay: Boolean) {
        if (sourceUri == null || starts.isEmpty()) return
        transitionInProgress = true
        cancelBoundary()
        completed = false
        val useClip = requiresPipelineClipping()
        val queuedNext = if (useClip) adjacentNextForQueue() else null
        queuedAdjacentSegmentIndex = queuedNext
        queuedAdjacentMediaItemIndex = if (queuedNext != null) 1 else null
        playbackWindowStartMs = if (useClip) starts[segmentIndex] else 0L
        playbackWindowEndMs = if (useClip) desiredWindowEndMs() else knownDurationMs
        isPipelineClipped = useClip
        val targetAbsolute = absolutePositionMs.coerceIn(
            playbackWindowStartMs,
            playbackWindowEndMs
        )
        val itemPosition = if (isPipelineClipped) {
            targetAbsolute - playbackWindowStartMs
        } else {
            targetAbsolute
        }
        Log.i(
            TAG,
            "window start=$playbackWindowStartMs end=$playbackWindowEndMs " +
                "target=$targetAbsolute clipped=$isPipelineClipped segment=$segmentIndex " +
                "repeat=$repeatIndex queuedNext=${queuedNext ?: -1} usedSetMediaItems=true"
        )
        val currentItem = buildMediaItem(playbackWindowStartMs, playbackWindowEndMs)
        if (queuedNext != null) {
            player.setMediaItems(
                listOf(
                    currentItem,
                    buildMediaItem(starts[queuedNext], ends[queuedNext])
                ),
                0,
                itemPosition
            )
        } else {
            player.setMediaItem(currentItem, itemPosition)
        }
        if (repeatRestartRequestedElapsedMs > 0L) repeatCalledPrepare = true
        player.prepare()
        if (isPipelineClipped) {
            player.seekTo(0, itemPosition)
        }
        playbackTaskActive = shouldPlay
        if (shouldPlay) player.play() else player.pause()
        transitionInProgress = false
    }

    private fun restartCurrentSegment(shouldPlay: Boolean) {
        if (sourceUri == null || starts.isEmpty()) return
        val useClip = requiresPipelineClipping()
        val segmentStart = starts[segmentIndex]
        val desiredEnd = if (useClip) desiredWindowEndMs() else knownDurationMs
        val canReusePreparedWindow = SegmentPlaybackPolicy.canReusePreparedWindow(
            hasCurrentMediaItem = player.currentMediaItem != null,
            currentPipelineClipped = isPipelineClipped,
            requiredPipelineClipped = useClip,
            currentWindowStartMs = playbackWindowStartMs,
            requiredWindowStartMs = segmentStart,
            currentWindowEndMs = playbackWindowEndMs,
            requiredWindowEndMs = desiredEnd
        )
        repeatReusedPreparedWindow = canReusePreparedWindow
        if (!canReusePreparedWindow) {
            startPlaybackAt(starts[segmentIndex], shouldPlay)
            return
        }

        transitionInProgress = true
        cancelBoundary()
        completed = false
        val itemPositionMs = if (isPipelineClipped) 0L else segmentStart
        val currentMediaItemIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        discardFutureMediaItems()
        player.seekTo(currentMediaItemIndex, itemPositionMs)
        repeatUsedAddMediaItem = useClip && prepareAdjacentSegmentForFinalRepeat()
        Log.i(
            TAG,
            "window reused start=$playbackWindowStartMs end=$playbackWindowEndMs " +
                "target=${starts[segmentIndex]} segment=$segmentIndex repeat=$repeatIndex " +
                "reusedPreparedWindow=true usedSetMediaItems=false " +
                "usedAddMediaItem=$repeatUsedAddMediaItem calledPrepare=false " +
                "mediaItemCount=${player.mediaItemCount}"
        )
        playbackTaskActive = shouldPlay
        if (shouldPlay) player.play() else player.pause()
        transitionInProgress = false
    }

    private fun updateSegments(intent: Intent) {
        val rawStarts = intent.getLongArrayExtra(PlaybackContract.EXTRA_STARTS) ?: return
        val rawEnds = intent.getLongArrayExtra(PlaybackContract.EXTRA_ENDS) ?: return
        val continuePlaying = effectiveIsPlaying()
        val absolute = intent.getLongExtra(
            PlaybackContract.EXTRA_POSITION,
            absolutePositionMs()
        ).coerceAtLeast(0)
        cancelAutomatedWork(clearGap = true)
        if (!applyNormalizedSegments(
                rawStarts,
                rawEnds,
                intent.getStringArrayExtra(PlaybackContract.EXTRA_TEXTS) ?: emptyArray()
            )
        ) {
            playbackError = "更新分段失败：分段范围无效"
            Log.e(TAG, playbackError)
            publish()
            return
        }
        skipSubtitleGaps = intent.getBooleanExtra(
            PlaybackContract.EXTRA_SKIP_SUBTITLE_GAPS,
            false
        )
        intent.getLongArrayExtra(PlaybackContract.EXTRA_CUE_STARTS)?.let { cueStarts = it }
        intent.getLongArrayExtra(PlaybackContract.EXTRA_CUE_ENDS)?.let { cueEnds = it }
        intent.getStringArrayExtra(PlaybackContract.EXTRA_CUE_TEXTS)?.let { cueTexts = it }
        intent.getIntArrayExtra(PlaybackContract.EXTRA_CUE_IDS)?.let { cueIds = it }
        intent.getIntArrayExtra(PlaybackContract.EXTRA_BOOKMARKED_CUE_IDS)?.let {
            bookmarkedCueIds = it.toSet()
        }
        val target = PlaybackMath.snapToPlayablePosition(
            starts,
            ends,
            absolute,
            knownDurationMs,
            skipSubtitleGaps
        )
        segmentIndex = PlaybackMath.segmentIndexAt(starts, ends, target)
        repeatIndex = 1
        completed = false
        rebuildCaches()
        val preservePosition = intent.getBooleanExtra(
            PlaybackContract.EXTRA_PRESERVE_POSITION,
            false
        )
        val alignedTarget = if (preservePosition) {
            target
        } else {
            SegmentPlaybackPolicy.alignedInitialPosition(
                requestedPositionMs = target,
                segmentStartMs = starts[segmentIndex],
                repeatCount = repeatCount
            )
        }
        startPlaybackAt(alignedTarget, continuePlaying)
        publish()
    }

    private fun updateBookmarks(intent: Intent) {
        bookmarkedCueIds = intent.getIntArrayExtra(PlaybackContract.EXTRA_BOOKMARKED_CUE_IDS)
            ?.toSet()
            .orEmpty()
        rebuildCaches()
        publish()
    }

    private fun updateRepeats(value: Int) {
        val continuePlaying = effectiveIsPlaying()
        val wasInGap = isInSegmentGap
        val absolute = if (wasInGap) starts.getOrElse(segmentIndex) { 0L } else absolutePositionMs()
        cancelAutomatedWork(clearGap = true)
        repeatCount = value.coerceAtLeast(0)
        repeatIndex = 1
        completed = false
        val alignedPosition = SegmentPlaybackPolicy.alignedInitialPosition(
            requestedPositionMs = absolute,
            segmentStartMs = starts[segmentIndex],
            repeatCount = repeatCount
        )
        startPlaybackAt(alignedPosition, continuePlaying)
        publish()
    }

    private fun updateGap(value: Long) {
        val newGap = SegmentPlaybackPolicy.normalizedGapMs(value)
        if (!isInSegmentGap || isFollowAlongGap) {
            segmentGapMs = newGap
            publish()
            return
        }
        val shouldContinue = !isSegmentGapPaused
        cancelAutomatedWork(clearGap = true)
        segmentGapMs = newGap
        if (newGap == 0L) {
            executeBoundaryAction(SegmentBoundaryAction.REPEAT_CURRENT)
        } else {
            beginGap(
                SegmentBoundaryAction.REPEAT_CURRENT,
                durationMs = newGap,
                paused = !shouldContinue,
                followAlongGap = false
            )
        }
        publish()
    }

    private fun updateFollowAlong(enabled: Boolean) {
        if (followAlongEnabled == enabled) return
        val wasInGap = isInSegmentGap
        val continuePlaying = effectiveIsPlaying()
        val absolute = absolutePositionMs()
        followAlongEnabled = enabled
        if (wasInGap) {
            val action = pendingGapAction ?: SegmentBoundaryAction.REPEAT_CURRENT
            val shouldContinue = !isSegmentGapPaused
            cancelAutomatedWork(clearGap = true)
            val duration = boundaryPauseDurationMs(action)
            if (duration > 0L) {
                beginGap(
                    action,
                    durationMs = duration,
                    paused = !shouldContinue,
                    followAlongGap = followAlongEnabled
                )
            } else {
                executeBoundaryAction(action)
            }
        } else if (starts.isNotEmpty()) {
            cancelAutomatedWork(clearGap = true)
            startPlaybackAt(absolute, continuePlaying)
        }
        publish()
    }

    private fun updateSpeed(value: Float) {
        player.setPlaybackSpeed(value.coerceIn(0.25f, 3f))
        publish()
    }
    private fun tick() {
        handleSleepTimer()
        if (isInSegmentGap && !isSegmentGapPaused) {
            segmentGapRemainingMs =
                (gapDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        }
        if (player.isPlaying && !isInSegmentGap && ends.isNotEmpty()) {
            val position = absolutePositionMs()
            if (!isPipelineClipped) {
                val naturalIndex = PlaybackMath.segmentIndexAt(starts, ends, position)
                if (naturalIndex != segmentIndex) {
                    segmentIndex = naturalIndex
                    repeatIndex = 1
                }
            } else {
                val token = armedBoundaryToken
                if (
                    token != null &&
                    boundaryMessage == null &&
                    boundaryHandledGeneration != token.generation &&
                    position >= token.endMs
                ) {
                    onSegmentBoundary(token)
                }
            }
        }
        publish()
        persistPlaybackSession()
    }

    private fun handleSleepTimer() {
        if (sleepDeadline <= 0 || System.currentTimeMillis() < sleepDeadline) return
        if (SleepTimerExpiryPolicy.action(stopAtSegmentEnd, isInSegmentGap) == SleepTimerExpiryAction.STOP_NOW) {
            stopForSleepTimer()
            return
        }
        if (!pendingSleepStop) {
            pendingSleepStop = true
            armBoundary()
        }
    }

    private fun stopForSleepTimer() {
        cancelPlaylistHandoff()
        playbackTaskActive = false
        pendingSleepStop = false
        completed = false
        stopReason = PlaybackStopReason.SLEEP_TIMER
        cancelAutomatedWork(clearGap = true)
        player.pause()
        Log.i(TAG, "sleep timer stopped playback without completing track")
        diagnostics.record("sleep_timer_stop deadline=$sleepDeadline stopAtSegmentEnd=$stopAtSegmentEnd")
        clearTimer(preserveStopReason = true)
    }

    private fun beginPlaylistHandoff() {
        playlistHandoffDeadlineElapsedMs =
            SystemClock.elapsedRealtime() + PLAYLIST_HANDOFF_GRACE_MS
        handler.removeCallbacks(playlistHandoffExpiryRunnable)
        handler.postDelayed(playlistHandoffExpiryRunnable, PLAYLIST_HANDOFF_GRACE_MS)
        diagnostics.record("playlist_handoff_started graceMs=$PLAYLIST_HANDOFF_GRACE_MS")
    }

    private fun cancelPlaylistHandoff() {
        if (playlistHandoffDeadlineElapsedMs == 0L) return
        playlistHandoffDeadlineElapsedMs = 0L
        handler.removeCallbacks(playlistHandoffExpiryRunnable)
        diagnostics.record("playlist_handoff_cancelled phase=${phaseName()}")
    }

    private fun isPlaylistHandoffActive(): Boolean =
        playlistHandoffDeadlineElapsedMs > SystemClock.elapsedRealtime() &&
            completed &&
            stopReason == PlaybackStopReason.TRACK_COMPLETED

    private fun expirePlaylistHandoff() {
        if (playlistHandoffDeadlineElapsedMs == 0L) return
        if (SystemClock.elapsedRealtime() < playlistHandoffDeadlineElapsedMs) return
        playlistHandoffDeadlineElapsedMs = 0L
        diagnostics.record("playlist_handoff_expired phase=${phaseName()} taskActive=$playbackTaskActive")
        if (completed && !playbackTaskActive && ::session.isInitialized) {
            super.onUpdateNotification(session, false)
        }
    }

    private fun cancelBoundary() {
        scheduleGeneration++
        boundaryMessage?.cancel()
        boundaryMessage = null
        armedBoundaryToken = null
    }

    private fun armBoundary() {
        if (
            !player.isPlaying ||
            transitionInProgress ||
            isInSegmentGap ||
            starts.isEmpty() ||
            ends.isEmpty()
        ) {
            return
        }
        val endMs = ends[segmentIndex].coerceAtMost(knownDurationMs)
        val crossesIntoFollowingSegment =
            isPipelineClipped && playbackWindowEndMs > endMs + ADJACENT_TOLERANCE_MS
        if (!crossesIntoFollowingSegment && !pendingSleepStop) return

        val existing = armedBoundaryToken
        if (
            existing != null &&
            existing.segmentIndex == segmentIndex &&
            existing.repeatIndex == repeatIndex &&
            existing.startMs == starts[segmentIndex] &&
            existing.endMs == endMs &&
            boundaryHandledGeneration != existing.generation
        ) {
            return
        }

        cancelBoundary()
        val token = BoundaryToken(
            generation = scheduleGeneration,
            segmentIndex = segmentIndex,
            repeatIndex = repeatIndex,
            startMs = starts[segmentIndex],
            endMs = endMs
        )
        armedBoundaryToken = token
        val itemPositionMs = if (isPipelineClipped) {
            (endMs - playbackWindowStartMs).coerceAtLeast(0)
        } else {
            endMs
        }
        if (player.currentPosition >= itemPositionMs) {
            handler.post { onSegmentBoundary(token) }
            return
        }
        boundaryMessage = player.createMessage { _, payload ->
            onSegmentBoundary(payload as BoundaryToken)
        }
            .setLooper(Looper.getMainLooper())
            .setPayload(token)
            .setPosition(itemPositionMs)
            .setDeleteAfterDelivery(true)
            .send()
        Log.d(
            TAG,
            "boundary armed generation=${token.generation} segment=${token.segmentIndex} " +
                "repeat=${token.repeatIndex} start=${token.startMs} end=${token.endMs}"
        )
    }

    private fun onSegmentBoundary(token: BoundaryToken) {
        val currentToken = armedBoundaryToken
        if (
            currentToken != token ||
            token.generation != scheduleGeneration ||
            boundaryHandledGeneration == token.generation ||
            token.segmentIndex != segmentIndex ||
            token.repeatIndex != repeatIndex
        ) {
            Log.d(
                TAG,
                "stale boundary ignored token=$token current=$currentToken " +
                    "generation=$scheduleGeneration handled=$boundaryHandledGeneration"
            )
            return
        }
        boundaryMessage = null
        armedBoundaryToken = null
        boundaryHandledGeneration = token.generation
        val actual = absolutePositionMs()
        Log.i(
            TAG,
            "boundary handled generation=${token.generation} expectedStart=${token.startMs} " +
                "expectedEnd=${token.endMs} actual=$actual lateness=${actual - token.endMs}"
        )
        if (pendingSleepStop) {
            stopForSleepTimer()
            return
        }

        val action = SegmentPlaybackPolicy.boundaryAction(
            repeatCount,
            repeatIndex,
            segmentIndex == starts.lastIndex
        )
        if (action != SegmentBoundaryAction.NEXT_SEGMENT) {
            Log.w(TAG, "unexpected in-window boundary action=$action")
            return
        }
        segmentIndex++
        repeatIndex = 1
        completed = false
        armBoundary()
        publish()
    }

    private fun handlePlaybackWindowEnded() {
        if (!PlaybackServicePolicy.mayAutomateBoundary(stopReason)) {
            Log.i(TAG, "window end ignored after stop reason=$stopReason")
            return
        }
        if (transitionInProgress || completed || starts.isEmpty()) return
        cancelBoundary()

        while (
            segmentIndex < starts.lastIndex &&
            ends[segmentIndex] < playbackWindowEndMs - ADJACENT_TOLERANCE_MS &&
            isAdjacent(segmentIndex, segmentIndex + 1)
        ) {
            segmentIndex++
            repeatIndex = 1
        }

        Log.i(
            TAG,
            "window ended start=$playbackWindowStartMs end=$playbackWindowEndMs " +
                "segment=$segmentIndex repeat=$repeatIndex absolute=${absolutePositionMs()}"
        )
        if (pendingSleepStop) {
            stopForSleepTimer()
            return
        }

        val action = SegmentPlaybackPolicy.boundaryAction(
            repeatCount,
            repeatIndex,
            segmentIndex == starts.lastIndex
        )
        if (action == SegmentBoundaryAction.REPEAT_CURRENT) {
            repeatBoundaryDetectedElapsedMs = SystemClock.elapsedRealtime()
        } else {
            clearRepeatTransitionTiming()
        }
        val pauseDurationMs = boundaryPauseDurationMs(action)
        if (pauseDurationMs > 0L) {
            beginGap(
                action,
                durationMs = pauseDurationMs,
                followAlongGap = followAlongEnabled
            )
        } else {
            executeBoundaryAction(action)
        }
    }

    private fun boundaryPauseDurationMs(action: SegmentBoundaryAction): Long =
        SegmentPlaybackPolicy.boundaryPauseDurationMs(
            action = action,
            segmentDurationMs = (
                ends.getOrElse(segmentIndex) { 0L } - starts.getOrElse(segmentIndex) { 0L }
                ).coerceAtLeast(0L),
            playbackSpeed = player.playbackParameters.speed,
            configuredGapMs = segmentGapMs,
            followAlongEnabled = followAlongEnabled
        )

    private fun beginGap(
        action: SegmentBoundaryAction,
        durationMs: Long,
        paused: Boolean = false,
        followAlongGap: Boolean
    ) {
        cancelBoundary()
        gapRunnable?.let(handler::removeCallbacks)
        gapRunnable = null
        isInSegmentGap = true
        isSegmentGapPaused = paused
        isFollowAlongGap = followAlongGap
        playbackTaskActive = !paused
        pendingGapAction = action
        segmentGapRemainingMs = durationMs.coerceAtLeast(0L)
        gapDeadlineElapsedMs = SystemClock.elapsedRealtime() + segmentGapRemainingMs
        if (!paused) {
            acquireGapWakeLock()
            scheduleGapCompletion(scheduleGeneration)
        }
        Log.d(
            TAG,
            "gap started generation=$scheduleGeneration duration=$segmentGapRemainingMs " +
                "action=$action followAlong=$followAlongGap paused=$paused wake=${gapWakeLock.isHeld}"
        )
        publish()
    }

    private fun acquireGapWakeLock() {
        if (!gapWakeLock.isHeld) {
            gapWakeLock.acquire(MAX_GAP_WAKE_LOCK_MS)
        }
    }

    private fun releaseGapWakeLock() {
        if (gapWakeLock.isHeld) gapWakeLock.release()
    }

    private fun scheduleGapCompletion(token: Long) {
        gapDeadlineElapsedMs = SystemClock.elapsedRealtime() + segmentGapRemainingMs
        val runnable = Runnable { finishGap(token) }
        gapRunnable = runnable
        handler.postDelayed(runnable, segmentGapRemainingMs)
    }

    private fun finishGap(token: Long) {
        if (token != scheduleGeneration || !isInSegmentGap || isSegmentGapPaused) {
            Log.d(TAG, "stale gap ignored token=$token currentGeneration=$scheduleGeneration")
            return
        }
        gapRunnable = null
        val action = pendingGapAction ?: return clearGapState()
        clearGapState()
        executeBoundaryAction(action)
    }

    private fun executeBoundaryAction(action: SegmentBoundaryAction) {
        clearGapState()
        when (action) {
            SegmentBoundaryAction.REPEAT_CURRENT -> {
                if (repeatBoundaryDetectedElapsedMs == 0L) {
                    repeatBoundaryDetectedElapsedMs = SystemClock.elapsedRealtime()
                }
                repeatRestartRequestedElapsedMs = SystemClock.elapsedRealtime()
                repeatReusedPreparedWindow = false
                repeatUsedAddMediaItem = false
                repeatCalledPrepare = false
                repeatIndex++
                completed = false
                restartCurrentSegment(shouldPlay = true)
            }
            SegmentBoundaryAction.NEXT_SEGMENT -> {
                segmentIndex = (segmentIndex + 1).coerceAtMost(starts.lastIndex)
                repeatIndex = 1
                completed = false
                startPlaybackAt(starts[segmentIndex], shouldPlay = true)
            }
            SegmentBoundaryAction.COMPLETE -> completeTrack()
        }
        publish()
    }

    private fun completeTrack() {
        beginPlaylistHandoff()
        playbackTaskActive = false
        cancelAutomatedWork(clearGap = true)
        completed = true
        stopReason = PlaybackStopReason.TRACK_COMPLETED
        player.pause()
        publish()
        persistPlaybackSession(force = true)
    }
    private fun previousSegment() {
        if (starts.isEmpty()) return
        val positionInSegment = if (isInSegmentGap) {
            (ends[segmentIndex] - starts[segmentIndex]).coerceAtLeast(0)
        } else {
            (absolutePositionMs() - starts[segmentIndex]).coerceAtLeast(0)
        }
        val target = SegmentPlaybackPolicy.previousTargetIndex(segmentIndex, positionInSegment)
        moveTo(target)
    }

    private fun nextSegment() {
        if (starts.isEmpty()) return
        if (segmentIndex < starts.lastIndex) moveTo(segmentIndex + 1) else completeTrack()
    }

    private fun moveTo(index: Int) {
        if (starts.isEmpty()) return
        val continuePlaying = effectiveIsPlaying()
        cancelAutomatedWork(clearGap = true)
        segmentIndex = index.coerceIn(0, starts.lastIndex)
        repeatIndex = 1
        completed = false
        startPlaybackAt(starts[segmentIndex], continuePlaying)
        publish()
    }

    private fun seekWithin(relativeMs: Long) {
        if (starts.isEmpty()) return
        val continuePlaying = effectiveIsPlaying()
        cancelAutomatedWork(clearGap = true)
        repeatIndex = 1
        completed = false
        val target = (starts[segmentIndex] + relativeMs)
            .coerceIn(starts[segmentIndex], ends[segmentIndex])
        startPlaybackAt(target, continuePlaying)
        publish()
    }

    private fun seekAbsolute(positionMs: Long) {
        if (starts.isEmpty()) return
        val continuePlaying = effectiveIsPlaying()
        cancelAutomatedWork(clearGap = true)
        val target = PlaybackMath.snapToPlayablePosition(
            starts,
            ends,
            positionMs,
            knownDurationMs,
            skipSubtitleGaps
        )
        segmentIndex = PlaybackMath.segmentIndexAt(starts, ends, target)
        repeatIndex = 1
        completed = false
        startPlaybackAt(target, continuePlaying)
        publish()
    }

    private fun togglePlayback() {
        if (isInSegmentGap) {
            if (isSegmentGapPaused) resumeGap() else pauseGap()
        } else if (player.isPlaying) {
            playbackTaskActive = false
            player.pause()
        } else if (completed || player.playbackState == Player.STATE_ENDED) {
            cancelPlaylistHandoff()
            repeatIndex = 1
            completed = false
            stopReason = PlaybackStopReason.NONE
            startPlaybackAt(starts.getOrElse(segmentIndex) { 0L }, shouldPlay = true)
        } else {
            playbackTaskActive = true
            stopReason = PlaybackStopReason.NONE
            player.play()
            armBoundary()
        }
        publish()
        if (!effectiveIsPlaying()) persistPlaybackSession(force = true)
    }

    private fun handlePlayRequest() {
        cancelPlaylistHandoff()
        playbackTaskActive = true
        stopReason = PlaybackStopReason.NONE
        if (isInSegmentGap) {
            if (isSegmentGapPaused) resumeGap()
        } else if (completed || player.playbackState == Player.STATE_ENDED) {
            repeatIndex = 1
            completed = false
            startPlaybackAt(starts.getOrElse(segmentIndex) { 0L }, shouldPlay = true)
        } else {
            player.play()
            armBoundary()
        }
        publish()
    }

    private fun handlePauseRequest() {
        cancelPlaylistHandoff()
        playbackTaskActive = false
        if (isInSegmentGap) pauseGap() else player.pause()
        publish()
        persistPlaybackSession(force = true)
    }

    private fun pauseGap() {
        if (!isInSegmentGap || isSegmentGapPaused) return
        segmentGapRemainingMs =
            (gapDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        gapRunnable?.let(handler::removeCallbacks)
        gapRunnable = null
        isSegmentGapPaused = true
        playbackTaskActive = false
        scheduleGeneration++
        releaseGapWakeLock()
        Log.d(TAG, "gap paused remaining=$segmentGapRemainingMs")
    }

    private fun resumeGap() {
        if (!isInSegmentGap || !isSegmentGapPaused) return
        isSegmentGapPaused = false
        playbackTaskActive = true
        val token = ++scheduleGeneration
        acquireGapWakeLock()
        scheduleGapCompletion(token)
        Log.d(
            TAG,
            "gap resumed generation=$token remaining=$segmentGapRemainingMs wake=${gapWakeLock.isHeld}"
        )
    }

    private fun effectiveIsPlaying(): Boolean =
        player.isPlaying || (isInSegmentGap && !isSegmentGapPaused)

    private fun logCompletedRepeatTransition() {
        if (repeatRestartRequestedElapsedMs <= 0L) return
        val playbackResumedElapsedMs = SystemClock.elapsedRealtime()
        val boundaryElapsedMs = repeatBoundaryDetectedElapsedMs
            .takeIf { it > 0L }
            ?: repeatRestartRequestedElapsedMs
        Log.i(
            TAG,
            "repeat transition segment=$segmentIndex repeat=$repeatIndex/$repeatCount " +
                "segmentStartMs=${starts.getOrElse(segmentIndex) { 0L }} " +
                "segmentEndMs=${ends.getOrElse(segmentIndex) { 0L }} " +
                "segmentGapMs=$segmentGapMs boundaryDetectedElapsedMs=$boundaryElapsedMs " +
                "restartRequestedElapsedMs=$repeatRestartRequestedElapsedMs " +
                "playbackResumedElapsedMs=$playbackResumedElapsedMs " +
                "actualTransitionDurationMs=${playbackResumedElapsedMs - boundaryElapsedMs} " +
                "restartLatencyMs=${playbackResumedElapsedMs - repeatRestartRequestedElapsedMs} " +
                "reusedPreparedWindow=$repeatReusedPreparedWindow " +
                "mediaItemCount=${player.mediaItemCount} " +
                "queuedAdjacentSegmentIndex=${queuedAdjacentSegmentIndex ?: -1} " +
                "usedSetMediaItems=${!repeatReusedPreparedWindow} " +
                "usedAddMediaItem=$repeatUsedAddMediaItem calledPrepare=$repeatCalledPrepare"
        )
        clearRepeatTransitionTiming()
    }

    private fun clearRepeatTransitionTiming() {
        repeatBoundaryDetectedElapsedMs = 0L
        repeatRestartRequestedElapsedMs = 0L
        repeatReusedPreparedWindow = false
        repeatUsedAddMediaItem = false
        repeatCalledPrepare = false
    }

    private fun cancelAutomatedWork(clearGap: Boolean) {
        cancelBoundary()
        clearRepeatTransitionTiming()
        gapRunnable?.let(handler::removeCallbacks)
        gapRunnable = null
        transitionInProgress = false
        if (clearGap) clearGapState()
    }

    private fun clearGapState() {
        releaseGapWakeLock()
        isInSegmentGap = false
        isSegmentGapPaused = false
        isFollowAlongGap = false
        segmentGapRemainingMs = 0L
        gapDeadlineElapsedMs = 0L
        pendingGapAction = null
    }

    private fun absolutePositionMs(): Long {
        val itemPosition = player.currentPosition.coerceAtLeast(0)
        return if (isPipelineClipped) {
            (playbackWindowStartMs + itemPosition).coerceAtMost(playbackWindowEndMs)
        } else {
            itemPosition.coerceAtMost(knownDurationMs)
        }
    }
    private fun rebuildCaches() {
        segmentCache = starts.indices.map {
            SegmentSnapshot(
                starts[it],
                ends.getOrElse(it) { starts[it] },
                texts.getOrElse(it) { "" }
            )
        }
        subtitleCache = cueStarts.indices.mapNotNull { index ->
            cueTexts.getOrNull(index)?.let {
                SubtitleSnapshot(
                    cueIds.getOrElse(index) { index },
                    cueStarts[index],
                    cueEnds.getOrElse(index) { cueStarts[index] },
                    it,
                    cueIds.getOrElse(index) { index } in bookmarkedCueIds
                )
            }
        }
    }

    private fun durationMs(): Long = knownDurationMs.coerceAtLeast(0)

    private fun setTimer(intent: Intent) {
        val minutes = intent.getIntExtra(PlaybackContract.EXTRA_TIMER_MINUTES, 0)
        stopAtSegmentEnd = intent.getBooleanExtra(PlaybackContract.EXTRA_STOP_AT_END, true)
        pendingSleepStop = false
        stopReason = PlaybackStopReason.NONE
        sleepDeadline = if (minutes > 0) {
            System.currentTimeMillis() + minutes * 60_000L
        } else {
            0
        }
        getSharedPreferences("sleep_timer", MODE_PRIVATE).edit()
            .putLong("deadline", sleepDeadline)
            .putBoolean("stop_at_end", stopAtSegmentEnd)
            .apply()
        publish()
    }

    private fun clearTimer(preserveStopReason: Boolean = false) {
        sleepDeadline = 0
        if (!preserveStopReason) stopReason = PlaybackStopReason.NONE
        getSharedPreferences("sleep_timer", MODE_PRIVATE).edit().clear().apply()
        publish()
    }

    private fun publish() {
        val start = starts.getOrElse(segmentIndex) { 0 }
        val end = ends.getOrElse(segmentIndex) { durationMs() }
        val position = absolutePositionMs()
        val subtitlePosition = if (isInSegmentGap) {
            (end - 1).coerceAtLeast(start)
        } else {
            position
        }
        val cueIndex = PlaybackMath.subtitleIndexForPlayback(
            starts = cueStarts,
            ends = cueEnds,
            positionMs = subtitlePosition,
            segmentStartMs = start,
            segmentEndMs = end,
            preferSegmentCueDuringLeadIn = skipSubtitleGaps
        )
        val currentText = cueTexts.getOrElse(cueIndex) {
            texts.getOrElse(segmentIndex) { "" }
        }
        val nextText = cueTexts.getOrElse(cueIndex + 1) {
            texts.getOrElse(segmentIndex + 1) { "" }
        }
        PlaybackBus.update(
            PlaybackSnapshot(
                mediaId = sourceMediaId,
                title = currentTitle,
                isPlaying = effectiveIsPlaying(),
                positionMs = position,
                durationMs = durationMs(),
                segmentIndex = segmentIndex,
                segmentCount = starts.size,
                repeatIndex = repeatIndex,
                repeatCount = repeatCount,
                segmentStartMs = start,
                segmentEndMs = end,
                playbackSpeed = player.playbackParameters.speed,
                subtitle = currentText,
                nextSubtitle = nextText,
                subtitleIndex = cueIndex,
                segments = segmentCache,
                subtitles = subtitleCache,
                sleepDeadlineMs = sleepDeadline,
                isInSegmentGap = isInSegmentGap,
                isSegmentGapPaused = isSegmentGapPaused,
                isFollowAlongGap = isFollowAlongGap,
                segmentGapRemainingMs = segmentGapRemainingMs,
                completed = completed,
                stopReason = stopReason,
                errorMessage = playbackError
            )
        )
    }

    private fun persistPlaybackSession(
        force: Boolean = false,
        persistDatabase: Boolean = true
    ) {
        val trackId = sourceMediaId.toLongOrNull()?.takeIf { it > 0L } ?: return
        if (starts.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && !PlaybackSessionPolicy.isSaveDue(now, lastSessionSavedAtMs)) return
        val value = PersistedPlaybackSession(
            trackId = trackId,
            positionMs = absolutePositionMs(),
            segmentIndex = segmentIndex,
            repeatIndex = repeatIndex,
            wasPlaying = effectiveIsPlaying(),
            savedAtMs = now
        )
        val wasCompleted = completed
        playbackSessionStore.save(value, synchronous = force)
        lastSessionSavedAtMs = now
        if (!persistDatabase) return
        persistenceScope.launch {
            persistenceMutex.withLock {
                if (value.savedAtMs < lastDatabaseSavedAtMs) return@withLock
                app.database.trackDao.updateProgress(
                    value.trackId,
                    value.positionMs,
                    value.segmentIndex,
                    value.savedAtMs,
                    wasCompleted
                )
                lastDatabaseSavedAtMs = value.savedAtMs
            }
        }
    }

    private fun phaseName(): String = when {
        completed -> "COMPLETED"
        stopReason == PlaybackStopReason.SLEEP_TIMER -> "SLEEP_TIMER_STOPPED"
        isInSegmentGap && isSegmentGapPaused -> "GAP_PAUSED"
        isInSegmentGap -> "WAITING_GAP"
        player.isPlaying -> "PLAYING_SEGMENT"
        player.mediaItemCount > 0 -> "PAUSED"
        else -> "IDLE"
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistPlaybackSession(force = true)
        val keepService = PlaybackServicePolicy.shouldKeepOnTaskRemoved(
            effectiveIsPlaying(),
            player.mediaItemCount
        )
        Log.i(
            TAG,
            "onTaskRemoved effectivePlaying=${effectiveIsPlaying()} mediaItems=${player.mediaItemCount} keep=$keepService"
        )
        diagnostics.record(
            "task_removed effectivePlaying=${effectiveIsPlaying()} mediaItems=${player.mediaItemCount} keep=$keepService"
        )
        if (!keepService) stopSelf()
    }

    override fun onDestroy() {
        Log.i(
            TAG,
            "PlaybackService destroyed effectivePlaying=${effectiveIsPlaying()} " +
                "state=${player.playbackState} gapWakeHeld=${gapWakeLock.isHeld}"
        )
        if (::diagnostics.isInitialized) {
            diagnostics.record(
                "service_destroyed effectivePlaying=${effectiveIsPlaying()} state=${player.playbackState} " +
                    "phase=${phaseName()} taskActive=$playbackTaskActive"
            )
        }
        persistPlaybackSession(force = true, persistDatabase = false)
        handler.removeCallbacksAndMessages(null)
        if (activeInstance === this) {
            activeInstance = null
            activeSourceMediaId = null
            Log.i(TAG, "active service command dispatcher cleared")
        }
        cancelBoundary()
        releaseGapWakeLock()
        session.release()
        player.release()
        persistenceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EchoPlayback"
        @Volatile
        private var activeInstance: PlaybackService? = null
        @Volatile
        private var activeSourceMediaId: String? = null

        fun dispatchToActiveService(intent: Intent): Boolean {
            val service = activeInstance ?: return false
            if (
                intent.action != PlaybackContract.ACTION_LOAD &&
                activeSourceMediaId == null
            ) {
                return false
            }
            val command = Intent(intent)
            service.handler.post {
                if (activeInstance === service) {
                    service.handleCommand(command)
                }
            }
            return true
        }

        fun hasActiveInstance(): Boolean = activeInstance != null

        fun hasLoadedSource(mediaId: String): Boolean =
            activeInstance != null && activeSourceMediaId == mediaId

        private const val TICK_MS = 80L
        private const val PLAYLIST_HANDOFF_GRACE_MS = 15_000L
        private const val ADJACENT_TOLERANCE_MS = 2L
        private const val MAX_GAP_WAKE_LOCK_MS = 10_000L
    }
}
