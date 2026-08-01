package com.echoenglish.app.playback

import android.app.PendingIntent
import android.content.Intent
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.echoenglish.app.MainActivity

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var sessionPlayer: Player
    private lateinit var session: MediaSession
    private val handler = Handler(Looper.getMainLooper())

    private var starts = longArrayOf()
    private var ends = longArrayOf()
    private var texts = emptyArray<String>()
    private var cueStarts = longArrayOf()
    private var cueEnds = longArrayOf()
    private var cueTexts = emptyArray<String>()
    private var segmentCache = emptyList<SegmentSnapshot>()
    private var subtitleCache = emptyList<SubtitleSnapshot>()
    private var segmentIndex = 0
    private var repeatIndex = 1
    private var repeatCount = 1
    private var segmentGapMs = 0L
    private var skipSubtitleGaps = false
    private var currentTitle = ""
    private var knownDurationMs = 0L
    private var sleepDeadline = 0L
    private var stopAtSegmentEnd = true
    private var pendingSleepStop = false
    private var completed = false
    private var playbackError = ""

    private var scheduleGeneration = 0L
    private var boundaryMessage: PlayerMessage? = null
    private var gapRunnable: Runnable? = null
    private var isInSegmentGap = false
    private var isSegmentGapPaused = false
    private var segmentGapRemainingMs = 0L
    private var gapDeadlineElapsedMs = 0L
    private var pendingGapAction: SegmentBoundaryAction? = null

    private data class BoundaryToken(
        val generation: Long,
        val segmentIndex: Int,
        val repeatIndex: Int,
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
        player = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
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
                        if (isPlaying) armBoundary()
                        publish()
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        Log.i(TAG, "playWhenReady=$playWhenReady reason=$reason phase=${phaseName()}")
                        publish()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.i(TAG, "playbackState=$playbackState")
                        if (playbackState == Player.STATE_READY && player.isPlaying) armBoundary()
                        if (playbackState == Player.STATE_ENDED && !completed && starts.isNotEmpty()) {
                            completeTrack()
                        }
                        publish()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        cancelAutomatedWork(clearGap = true)
                        playbackError = "播放中断：${error.errorCodeName}"
                        Log.e(TAG, playbackError, error)
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
        sleepDeadline = prefs.getLong("deadline", 0L).takeIf { it > System.currentTimeMillis() } ?: 0L
        stopAtSegmentEnd = prefs.getBoolean("stop_at_end", true)
        handler.post(ticker)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        Log.i(TAG, "onGetSession package=${controllerInfo.packageName} added=${isSessionAdded(session)}")
        return session
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        Log.i(
            TAG,
            "onUpdateNotification foregroundRequired=$startInForegroundRequired effectivePlaying=${effectiveIsPlaying()} phase=${phaseName()}"
        )
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action ?: "null"} startId=$startId")
        when (intent?.action) {
            PlaybackContract.ACTION_LOAD -> load(intent)
            PlaybackContract.ACTION_TOGGLE -> togglePlayback()
            PlaybackContract.ACTION_NEXT -> nextSegment()
            PlaybackContract.ACTION_PREVIOUS -> previousSegment()
            PlaybackContract.ACTION_RESTART -> moveTo(segmentIndex)
            PlaybackContract.ACTION_SEEK -> seekWithin(intent.getLongExtra(PlaybackContract.EXTRA_POSITION, 0))
            PlaybackContract.ACTION_SEEK_ABSOLUTE -> seekAbsolute(intent.getLongExtra(PlaybackContract.EXTRA_POSITION, 0))
            PlaybackContract.ACTION_SEEK_SEGMENT -> moveTo(intent.getIntExtra(PlaybackContract.EXTRA_INDEX, segmentIndex))
            PlaybackContract.ACTION_UPDATE_SEGMENTS -> updateSegments(intent)
            PlaybackContract.ACTION_UPDATE_REPEATS -> updateRepeats(intent.getIntExtra(PlaybackContract.EXTRA_REPEATS, repeatCount))
            PlaybackContract.ACTION_UPDATE_GAP -> updateGap(intent.getLongExtra(PlaybackContract.EXTRA_GAP_MS, segmentGapMs))
            PlaybackContract.ACTION_UPDATE_SPEED -> updateSpeed(intent.getFloatExtra(PlaybackContract.EXTRA_SPEED, 1f))
            PlaybackContract.ACTION_TIMER -> setTimer(intent)
            PlaybackContract.ACTION_CANCEL_TIMER -> clearTimer()
        }
        return super.onStartCommand(intent, flags, startId)
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
    }

    private fun load(intent: Intent) {
        val uri = intent.getStringExtra(PlaybackContract.EXTRA_URI)?.toUri() ?: run {
            playbackError = "无法播放：音频地址为空"
            Log.e(TAG, playbackError)
            publish()
            return
        }
        cancelAutomatedWork(clearGap = true)
        playbackError = ""
        starts = intent.getLongArrayExtra(PlaybackContract.EXTRA_STARTS) ?: longArrayOf(0)
        ends = intent.getLongArrayExtra(PlaybackContract.EXTRA_ENDS) ?: longArrayOf(Long.MAX_VALUE)
        texts = intent.getStringArrayExtra(PlaybackContract.EXTRA_TEXTS) ?: emptyArray()
        cueStarts = intent.getLongArrayExtra(PlaybackContract.EXTRA_CUE_STARTS) ?: longArrayOf()
        cueEnds = intent.getLongArrayExtra(PlaybackContract.EXTRA_CUE_ENDS) ?: longArrayOf()
        cueTexts = intent.getStringArrayExtra(PlaybackContract.EXTRA_CUE_TEXTS) ?: emptyArray()
        rebuildCaches()
        knownDurationMs = intent.getLongExtra(
            PlaybackContract.EXTRA_DURATION,
            ends.lastOrNull() ?: 0
        ).coerceAtLeast(0)
        repeatCount = intent.getIntExtra(PlaybackContract.EXTRA_REPEATS, 1).coerceAtLeast(0)
        segmentGapMs = SegmentPlaybackPolicy.normalizedGapMs(
            intent.getLongExtra(PlaybackContract.EXTRA_GAP_MS, 0L)
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
                .coerceIn(0, (starts.size - 1).coerceAtLeast(0))
        }
        repeatIndex = 1
        currentTitle = intent.getStringExtra(PlaybackContract.EXTRA_TITLE).orEmpty()
        completed = false
        pendingSleepStop = false
        val startPosition = if (resolvedPosition >= 0) {
            resolvedPosition
        } else {
            starts.getOrElse(segmentIndex) { 0 }
        }
        val mediaId = intent.getStringExtra(PlaybackContract.EXTRA_MEDIA_ID) ?: uri.toString()
        val item = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(currentTitle)
                    .setArtist("回声英语 · 分段复读")
                    .build()
            )
            .build()
        player.setMediaItem(item, startPosition)
        player.setPlaybackSpeed(intent.getFloatExtra(PlaybackContract.EXTRA_SPEED, 1f))
        player.prepare()
        player.play()
        publish()
    }

    private fun updateSegments(intent: Intent) {
        val newStarts = intent.getLongArrayExtra(PlaybackContract.EXTRA_STARTS) ?: return
        val newEnds = intent.getLongArrayExtra(PlaybackContract.EXTRA_ENDS) ?: return
        if (newStarts.isEmpty() || newStarts.size != newEnds.size) return
        val continuePlaying = effectiveIsPlaying()
        val absolute = intent.getLongExtra(
            PlaybackContract.EXTRA_POSITION,
            player.currentPosition
        ).coerceAtLeast(0)
        cancelAutomatedWork(clearGap = true)
        starts = newStarts
        ends = newEnds
        texts = intent.getStringArrayExtra(PlaybackContract.EXTRA_TEXTS)
            ?: Array(starts.size) { "" }
        skipSubtitleGaps = intent.getBooleanExtra(
            PlaybackContract.EXTRA_SKIP_SUBTITLE_GAPS,
            false
        )
        val target = PlaybackMath.snapToPlayablePosition(
            starts,
            ends,
            absolute,
            durationMs(),
            skipSubtitleGaps
        )
        segmentIndex = PlaybackMath.segmentIndexAt(starts, ends, target)
        repeatIndex = 1
        completed = false
        rebuildCaches()
        player.seekTo(target)
        if (continuePlaying) player.play() else player.pause()
        if (continuePlaying) armBoundary()
        publish()
    }

    private fun updateRepeats(value: Int) {
        val continuePlaying = effectiveIsPlaying()
        val wasInGap = isInSegmentGap
        cancelAutomatedWork(clearGap = true)
        repeatCount = value.coerceAtLeast(0)
        repeatIndex = 1
        completed = false
        if (wasInGap && starts.isNotEmpty()) player.seekTo(starts[segmentIndex])
        if (continuePlaying) player.play() else player.pause()
        if (continuePlaying) armBoundary()
        publish()
    }

    private fun updateGap(value: Long) {
        val newGap = SegmentPlaybackPolicy.normalizedGapMs(value)
        if (!isInSegmentGap) {
            segmentGapMs = newGap
            armBoundary()
            publish()
            return
        }
        val action = pendingGapAction
        val shouldContinue = !isSegmentGapPaused
        cancelAutomatedWork(clearGap = true)
        segmentGapMs = newGap
        if (action != null) {
            if (newGap == 0L) {
                executeBoundaryAction(action)
            } else {
                beginGap(action, paused = !shouldContinue)
            }
        }
        publish()
    }

    private fun updateSpeed(value: Float) {
        boundaryMessage?.cancel()
        boundaryMessage = null
        scheduleGeneration++
        player.setPlaybackSpeed(value.coerceIn(0.25f, 3f))
        if (player.isPlaying) armBoundary()
        publish()
    }

    private fun tick() {
        handleSleepTimer()
        if (isInSegmentGap && !isSegmentGapPaused) {
            segmentGapRemainingMs =
                (gapDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        }
        if (player.isPlaying && !isInSegmentGap && ends.isNotEmpty()) {
            val position = player.currentPosition
            val exactBoundary = SegmentPlaybackPolicy.requiresExactBoundary(
                repeatCount,
                segmentGapMs,
                segmentIndex == starts.lastIndex,
                pendingSleepStop,
                skipSubtitleGaps
            )
            if (!exactBoundary) {
                val naturalIndex = PlaybackMath.segmentIndexAt(starts, ends, position)
                if (naturalIndex != segmentIndex) {
                    segmentIndex = naturalIndex
                    repeatIndex = 1
                    armBoundary()
                }
            } else if (position >= ends[segmentIndex] && boundaryMessage == null) {
                val token = BoundaryToken(
                    scheduleGeneration,
                    segmentIndex,
                    repeatIndex,
                    ends[segmentIndex]
                )
                onSegmentBoundary(token)
            }
        }
        publish()
    }

    private fun handleSleepTimer() {
        if (sleepDeadline <= 0 || System.currentTimeMillis() < sleepDeadline) return
        if (!stopAtSegmentEnd || isInSegmentGap) {
            stopForSleepTimer()
            return
        }
        if (!pendingSleepStop) {
            pendingSleepStop = true
            armBoundary()
        }
    }

    private fun stopForSleepTimer() {
        cancelAutomatedWork(clearGap = true)
        player.pause()
        pendingSleepStop = false
        clearTimer()
    }

    private fun armBoundary() {
        boundaryMessage?.cancel()
        boundaryMessage = null
        if (!player.isPlaying || isInSegmentGap || starts.isEmpty() || ends.isEmpty()) return
        val needsBoundary = SegmentPlaybackPolicy.requiresExactBoundary(
            repeatCount,
            segmentGapMs,
            segmentIndex == starts.lastIndex,
            pendingSleepStop,
            skipSubtitleGaps
        )
        if (!needsBoundary) return

        val knownEnd = durationMs().takeIf { it > 0 } ?: ends[segmentIndex]
        val endMs = ends[segmentIndex].coerceAtMost(knownEnd)
        val token = BoundaryToken(++scheduleGeneration, segmentIndex, repeatIndex, endMs)
        if (player.currentPosition >= endMs) {
            handler.post { onSegmentBoundary(token) }
            return
        }
        boundaryMessage = player.createMessage { _, payload ->
            onSegmentBoundary(payload as BoundaryToken)
        }
            .setLooper(Looper.getMainLooper())
            .setPayload(token)
            .setPosition(endMs)
            .setDeleteAfterDelivery(true)
            .send()
        Log.d(
            TAG,
            "boundary armed token=${token.generation} segment=${token.segmentIndex} repeat=${token.repeatIndex} end=$endMs"
        )
    }

    private fun onSegmentBoundary(token: BoundaryToken) {
        if (
            token.generation != scheduleGeneration ||
            token.segmentIndex != segmentIndex ||
            token.repeatIndex != repeatIndex ||
            token.endMs != ends.getOrElse(segmentIndex) { -1L }
                .coerceAtMost(durationMs().takeIf { it > 0 } ?: Long.MAX_VALUE)
        ) {
            Log.d(
                TAG,
                "stale boundary ignored token=$token currentGeneration=$scheduleGeneration segment=$segmentIndex repeat=$repeatIndex"
            )
            return
        }
        boundaryMessage = null
        if (pendingSleepStop) {
            player.pause()
            player.seekTo(token.endMs)
            pendingSleepStop = false
            clearTimer()
            publish()
            return
        }

        val action = SegmentPlaybackPolicy.boundaryAction(
            repeatCount,
            repeatIndex,
            segmentIndex == starts.lastIndex
        )
        if (action != SegmentBoundaryAction.COMPLETE && segmentGapMs > 0) {
            // Enter the gap before pausing the delegate so MediaSession still reports
            // an active playback operation and keeps its media notification foreground.
            beginGap(action)
            player.pause()
            player.seekTo(token.endMs)
        } else {
            player.pause()
            player.seekTo(token.endMs)
            executeBoundaryAction(action)
        }
    }

    private fun beginGap(action: SegmentBoundaryAction, paused: Boolean = false) {
        boundaryMessage?.cancel()
        boundaryMessage = null
        gapRunnable?.let(handler::removeCallbacks)
        gapRunnable = null
        scheduleGeneration++
        isInSegmentGap = true
        isSegmentGapPaused = paused
        pendingGapAction = action
        segmentGapRemainingMs = segmentGapMs
        gapDeadlineElapsedMs = SystemClock.elapsedRealtime() + segmentGapRemainingMs
        if (!paused) scheduleGapCompletion(scheduleGeneration)
        Log.d(
            TAG,
            "gap started generation=$scheduleGeneration duration=$segmentGapMs action=$action paused=$paused"
        )
        publish()
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
        val action = pendingGapAction ?: return
        gapRunnable = null
        clearGapState()
        executeBoundaryAction(action)
    }

    private fun executeBoundaryAction(action: SegmentBoundaryAction) {
        clearGapState()
        when (action) {
            SegmentBoundaryAction.REPEAT_CURRENT -> {
                repeatIndex++
                completed = false
                player.seekTo(starts[segmentIndex])
                player.play()
                armBoundary()
            }
            SegmentBoundaryAction.NEXT_SEGMENT -> {
                segmentIndex = (segmentIndex + 1).coerceAtMost(starts.lastIndex)
                repeatIndex = 1
                completed = false
                player.seekTo(starts[segmentIndex])
                player.play()
                armBoundary()
            }
            SegmentBoundaryAction.COMPLETE -> completeTrack()
        }
        publish()
    }

    private fun completeTrack() {
        cancelAutomatedWork(clearGap = true)
        completed = true
        player.pause()
        if (ends.isNotEmpty()) {
            player.seekTo(ends[segmentIndex].coerceAtMost(durationMs()))
        }
        publish()
    }

    private fun previousSegment() {
        if (starts.isEmpty()) return
        val positionInSegment = if (isInSegmentGap) {
            (ends[segmentIndex] - starts[segmentIndex]).coerceAtLeast(0)
        } else {
            (player.currentPosition - starts[segmentIndex]).coerceAtLeast(0)
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
        player.seekTo(starts[segmentIndex])
        if (continuePlaying) player.play() else player.pause()
        if (continuePlaying) armBoundary()
        publish()
    }

    private fun seekWithin(relativeMs: Long) {
        if (starts.isEmpty()) return
        val continuePlaying = effectiveIsPlaying()
        cancelAutomatedWork(clearGap = true)
        repeatIndex = 1
        completed = false
        player.seekTo(
            (starts[segmentIndex] + relativeMs)
                .coerceIn(starts[segmentIndex], ends[segmentIndex])
        )
        if (continuePlaying) player.play() else player.pause()
        if (continuePlaying) armBoundary()
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
            durationMs(),
            skipSubtitleGaps
        )
        segmentIndex = PlaybackMath.segmentIndexAt(starts, ends, target)
        repeatIndex = 1
        completed = false
        player.seekTo(target)
        if (continuePlaying) player.play() else player.pause()
        if (continuePlaying) armBoundary()
        publish()
    }

    private fun togglePlayback() {
        if (isInSegmentGap) {
            if (isSegmentGapPaused) resumeGap() else pauseGap()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
            armBoundary()
        }
        publish()
    }

    private fun handlePlayRequest() {
        if (isInSegmentGap) {
            if (isSegmentGapPaused) resumeGap()
        } else {
            player.play()
            armBoundary()
        }
        publish()
    }

    private fun handlePauseRequest() {
        if (isInSegmentGap) pauseGap() else player.pause()
        publish()
    }

    private fun pauseGap() {
        if (!isInSegmentGap || isSegmentGapPaused) return
        segmentGapRemainingMs =
            (gapDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        gapRunnable?.let(handler::removeCallbacks)
        gapRunnable = null
        isSegmentGapPaused = true
        scheduleGeneration++
        Log.d(TAG, "gap paused remaining=$segmentGapRemainingMs")
    }

    private fun resumeGap() {
        if (!isInSegmentGap || !isSegmentGapPaused) return
        isSegmentGapPaused = false
        val token = ++scheduleGeneration
        scheduleGapCompletion(token)
        Log.d(TAG, "gap resumed generation=$token remaining=$segmentGapRemainingMs")
    }

    private fun effectiveIsPlaying(): Boolean =
        player.isPlaying || (isInSegmentGap && !isSegmentGapPaused)

    private fun cancelAutomatedWork(clearGap: Boolean) {
        scheduleGeneration++
        boundaryMessage?.cancel()
        boundaryMessage = null
        gapRunnable?.let(handler::removeCallbacks)
        gapRunnable = null
        if (clearGap) clearGapState()
    }

    private fun clearGapState() {
        isInSegmentGap = false
        isSegmentGapPaused = false
        segmentGapRemainingMs = 0L
        gapDeadlineElapsedMs = 0L
        pendingGapAction = null
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
                    cueStarts[index],
                    cueEnds.getOrElse(index) { cueStarts[index] },
                    it
                )
            }
        }
    }

    private fun durationMs(): Long =
        player.duration.takeIf { it > 0 } ?: knownDurationMs.coerceAtLeast(0)

    private fun setTimer(intent: Intent) {
        val minutes = intent.getIntExtra(PlaybackContract.EXTRA_TIMER_MINUTES, 0)
        stopAtSegmentEnd = intent.getBooleanExtra(PlaybackContract.EXTRA_STOP_AT_END, true)
        pendingSleepStop = false
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

    private fun clearTimer() {
        sleepDeadline = 0
        getSharedPreferences("sleep_timer", MODE_PRIVATE).edit().clear().apply()
        publish()
    }

    private fun publish() {
        val start = starts.getOrElse(segmentIndex) { 0 }
        val end = ends.getOrElse(segmentIndex) { durationMs() }
        val position = player.currentPosition.coerceAtLeast(0)
        val subtitlePosition = if (isInSegmentGap) {
            (end - 1).coerceAtLeast(start)
        } else {
            position
        }
        val cueIndex = PlaybackMath.subtitleIndexAt(cueStarts, subtitlePosition)
        val currentText = cueTexts.getOrElse(cueIndex) {
            texts.getOrElse(segmentIndex) { "" }
        }
        val nextText = cueTexts.getOrElse(cueIndex + 1) {
            texts.getOrElse(segmentIndex + 1) { "" }
        }
        PlaybackBus.update(
            PlaybackSnapshot(
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
                segmentGapRemainingMs = segmentGapRemainingMs,
                completed = completed,
                errorMessage = playbackError
            )
        )
    }

    private fun phaseName(): String = when {
        completed -> "COMPLETED"
        isInSegmentGap && isSegmentGapPaused -> "GAP_PAUSED"
        isInSegmentGap -> "WAITING_GAP"
        player.isPlaying -> "PLAYING_SEGMENT"
        player.mediaItemCount > 0 -> "PAUSED"
        else -> "IDLE"
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val keepService = PlaybackServicePolicy.shouldKeepOnTaskRemoved(
            effectiveIsPlaying(),
            player.mediaItemCount
        )
        Log.i(
            TAG,
            "onTaskRemoved effectivePlaying=${effectiveIsPlaying()} mediaItems=${player.mediaItemCount} keep=$keepService"
        )
        if (!keepService) stopSelf()
    }

    override fun onDestroy() {
        Log.i(
            TAG,
            "PlaybackService destroyed effectivePlaying=${effectiveIsPlaying()} state=${player.playbackState}"
        )
        handler.removeCallbacksAndMessages(null)
        boundaryMessage?.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EchoPlayback"
        private const val TICK_MS = 80L
    }
}
