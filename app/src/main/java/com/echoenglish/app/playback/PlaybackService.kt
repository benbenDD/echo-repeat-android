package com.echoenglish.app.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
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
    private var currentTitle = ""
    private var knownDurationMs = 0L
    private var sleepDeadline = 0L
    private var stopAtSegmentEnd = true
    private var pendingSleepStop = false
    private var completed = false

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 80)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(), true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) completed = true
                    publish()
                }
            })
        }
        session = MediaSession.Builder(this, player).build()
        val prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE)
        sleepDeadline = prefs.getLong("deadline", 0L).takeIf { it > System.currentTimeMillis() } ?: 0L
        stopAtSegmentEnd = prefs.getBoolean("stop_at_end", true)
        handler.post(ticker)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PlaybackContract.ACTION_LOAD -> load(intent)
            PlaybackContract.ACTION_TOGGLE -> if (player.isPlaying) player.pause() else player.play()
            PlaybackContract.ACTION_NEXT -> moveTo(segmentIndex + 1)
            PlaybackContract.ACTION_PREVIOUS -> moveTo(segmentIndex - 1)
            PlaybackContract.ACTION_RESTART -> moveTo(segmentIndex)
            PlaybackContract.ACTION_SEEK -> seekWithin(intent.getLongExtra(PlaybackContract.EXTRA_POSITION, 0))
            PlaybackContract.ACTION_SEEK_ABSOLUTE -> seekAbsolute(intent.getLongExtra(PlaybackContract.EXTRA_POSITION, 0))
            PlaybackContract.ACTION_SEEK_SEGMENT -> moveTo(intent.getIntExtra(PlaybackContract.EXTRA_INDEX, segmentIndex))
            PlaybackContract.ACTION_UPDATE_SEGMENTS -> updateSegments(intent)
            PlaybackContract.ACTION_UPDATE_REPEATS -> updateRepeats(intent.getIntExtra(PlaybackContract.EXTRA_REPEATS, repeatCount))
            PlaybackContract.ACTION_UPDATE_SPEED -> updateSpeed(intent.getFloatExtra(PlaybackContract.EXTRA_SPEED, 1f))
            PlaybackContract.ACTION_TIMER -> setTimer(intent)
            PlaybackContract.ACTION_CANCEL_TIMER -> clearTimer()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun load(intent: Intent) {
        val uri = intent.getStringExtra(PlaybackContract.EXTRA_URI)?.toUri() ?: return
        starts = intent.getLongArrayExtra(PlaybackContract.EXTRA_STARTS) ?: longArrayOf(0)
        ends = intent.getLongArrayExtra(PlaybackContract.EXTRA_ENDS) ?: longArrayOf(Long.MAX_VALUE)
        texts = intent.getStringArrayExtra(PlaybackContract.EXTRA_TEXTS) ?: emptyArray()
        cueStarts = intent.getLongArrayExtra(PlaybackContract.EXTRA_CUE_STARTS) ?: longArrayOf()
        cueEnds = intent.getLongArrayExtra(PlaybackContract.EXTRA_CUE_ENDS) ?: longArrayOf()
        cueTexts = intent.getStringArrayExtra(PlaybackContract.EXTRA_CUE_TEXTS) ?: emptyArray()
        rebuildCaches()
        knownDurationMs = intent.getLongExtra(PlaybackContract.EXTRA_DURATION, ends.lastOrNull() ?: 0).coerceAtLeast(0)
        repeatCount = intent.getIntExtra(PlaybackContract.EXTRA_REPEATS, 1).coerceAtLeast(0)
        val requestedPosition = intent.getLongExtra(PlaybackContract.EXTRA_POSITION, -1)
        segmentIndex = if (requestedPosition >= 0) PlaybackMath.segmentIndexAt(starts, ends, requestedPosition)
            else intent.getIntExtra(PlaybackContract.EXTRA_INDEX, 0).coerceIn(0, (starts.size - 1).coerceAtLeast(0))
        repeatIndex = 1
        currentTitle = intent.getStringExtra(PlaybackContract.EXTRA_TITLE).orEmpty()
        completed = false
        val startPosition = if (requestedPosition >= 0) requestedPosition.coerceIn(0, knownDurationMs.coerceAtLeast(0)) else starts.getOrElse(segmentIndex) { 0 }
        val item = MediaItem.Builder().setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(currentTitle).setArtist("回声英语 · 分段复读").build())
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
        val wasPlaying = player.isPlaying
        val absolute = intent.getLongExtra(PlaybackContract.EXTRA_POSITION, player.currentPosition).coerceAtLeast(0)
        starts = newStarts
        ends = newEnds
        texts = intent.getStringArrayExtra(PlaybackContract.EXTRA_TEXTS) ?: Array(starts.size) { "" }
        segmentIndex = PlaybackMath.segmentIndexAt(starts, ends, absolute)
        repeatIndex = 1
        completed = false
        rebuildCaches()
        player.seekTo(absolute.coerceAtMost(durationMs()))
        if (wasPlaying) player.play() else player.pause()
        publish()
    }

    private fun updateRepeats(value: Int) {
        repeatCount = value.coerceAtLeast(0)
        repeatIndex = 1
        publish()
    }

    private fun updateSpeed(value: Float) {
        player.setPlaybackSpeed(value.coerceIn(0.25f, 3f))
        publish()
    }

    private fun tick() {
        if (sleepDeadline > 0 && System.currentTimeMillis() >= sleepDeadline) {
            if (stopAtSegmentEnd) pendingSleepStop = true else { player.pause(); clearTimer() }
        }
        if (player.isPlaying && ends.isNotEmpty()) {
            val position = player.currentPosition
            val segmentEnd = ends[segmentIndex]
            if (pendingSleepStop && position >= segmentEnd) {
                player.pause()
                clearTimer()
                pendingSleepStop = false
            } else if (repeatCount == 1) {
                // Keep a single pass on ExoPlayer's continuous timeline. Seeking at every
                // adjacent boundary creates an audible discontinuity.
                val naturalIndex = PlaybackMath.segmentIndexAt(starts, ends, position)
                if (naturalIndex != segmentIndex) {
                    segmentIndex = naturalIndex
                    repeatIndex = 1
                }
                if (segmentIndex == starts.lastIndex && position >= ends[segmentIndex]) {
                    completed = true
                    player.pause()
                    player.seekTo(ends[segmentIndex])
                }
            } else if (position >= segmentEnd) {
                if (PlaybackMath.shouldRepeatSegment(repeatCount, repeatIndex)) {
                    repeatIndex++
                    player.seekTo(starts[segmentIndex])
                } else if (segmentIndex < starts.lastIndex) {
                    // The final repetition is already moving forward on the same media
                    // timeline, so entering the next segment must not seek again.
                    segmentIndex++
                    repeatIndex = 1
                } else {
                    completed = true
                    player.pause()
                    player.seekTo(ends[segmentIndex])
                }
            }
        }
        publish()
    }

    private fun moveTo(index: Int) {
        if (starts.isEmpty()) return
        val wasPlaying = player.isPlaying
        segmentIndex = index.coerceIn(0, starts.lastIndex)
        repeatIndex = 1
        completed = false
        player.seekTo(starts[segmentIndex])
        if (wasPlaying) player.play() else player.pause()
        publish()
    }

    private fun seekWithin(relativeMs: Long) {
        if (starts.isEmpty()) return
        repeatIndex = 1
        completed = false
        player.seekTo((starts[segmentIndex] + relativeMs).coerceIn(starts[segmentIndex], ends[segmentIndex]))
        publish()
    }

    private fun seekAbsolute(positionMs: Long) {
        if (starts.isEmpty()) return
        val target = positionMs.coerceIn(0, durationMs())
        segmentIndex = PlaybackMath.segmentIndexAt(starts, ends, target)
        repeatIndex = 1
        completed = false
        player.seekTo(target)
        publish()
    }

    private fun rebuildCaches() {
        segmentCache = starts.indices.map { SegmentSnapshot(starts[it], ends.getOrElse(it) { starts[it] }, texts.getOrElse(it) { "" }) }
        subtitleCache = cueStarts.indices.mapNotNull { index ->
            cueTexts.getOrNull(index)?.let { SubtitleSnapshot(cueStarts[index], cueEnds.getOrElse(index) { cueStarts[index] }, it) }
        }
    }

    private fun durationMs(): Long = player.duration.takeIf { it > 0 } ?: knownDurationMs.coerceAtLeast(0)

    private fun setTimer(intent: Intent) {
        val minutes = intent.getIntExtra(PlaybackContract.EXTRA_TIMER_MINUTES, 0)
        stopAtSegmentEnd = intent.getBooleanExtra(PlaybackContract.EXTRA_STOP_AT_END, true)
        sleepDeadline = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0
        getSharedPreferences("sleep_timer", MODE_PRIVATE).edit().putLong("deadline", sleepDeadline).putBoolean("stop_at_end", stopAtSegmentEnd).apply()
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
        val cueIndex = PlaybackMath.subtitleIndexAt(cueStarts, position)
        val currentText = cueTexts.getOrElse(cueIndex) { texts.getOrElse(segmentIndex) { "" } }
        val nextText = cueTexts.getOrElse(cueIndex + 1) { texts.getOrElse(segmentIndex + 1) { "" } }
        PlaybackBus.update(PlaybackSnapshot(
            title = currentTitle,
            isPlaying = player.isPlaying,
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
            completed = completed
        ))
    }

    override fun onTaskRemoved(rootIntent: Intent?) { if (!player.isPlaying) stopSelf() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        session.release()
        player.release()
        super.onDestroy()
    }
}
