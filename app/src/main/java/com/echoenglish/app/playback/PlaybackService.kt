package com.echoenglish.app.playback

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.*
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
    private var segmentIndex = 0
    private var repeatIndex = 1
    private var repeatCount = 1
    private var currentTitle = ""
    private var sleepDeadline = 0L
    private var stopAtSegmentEnd = true
    private var pendingSleepStop = false
    private var completed = false

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(), true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
                override fun onPlaybackStateChanged(playbackState: Int) = publish()
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
            PlaybackContract.ACTION_NEXT -> moveTo(segmentIndex + 1, true)
            PlaybackContract.ACTION_PREVIOUS -> moveTo(segmentIndex - 1, true)
            PlaybackContract.ACTION_RESTART -> moveTo(segmentIndex, true)
            PlaybackContract.ACTION_SEEK -> seekWithin(intent.getLongExtra(PlaybackContract.EXTRA_POSITION, 0))
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
        repeatCount = intent.getIntExtra(PlaybackContract.EXTRA_REPEATS, 1).coerceAtLeast(0)
        segmentIndex = intent.getIntExtra(PlaybackContract.EXTRA_INDEX, 0).coerceIn(0, (starts.size - 1).coerceAtLeast(0))
        repeatIndex = 1
        currentTitle = intent.getStringExtra(PlaybackContract.EXTRA_TITLE).orEmpty()
        completed = false
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(currentTitle).setArtist("回声英语 · 分段复读").build())
            .build()
        player.setMediaItem(item, starts.getOrElse(segmentIndex) { 0 })
        player.setPlaybackSpeed(intent.getFloatExtra(PlaybackContract.EXTRA_SPEED, 1f))
        player.prepare()
        player.play()
        publish()
    }

    private fun tick() {
        if (sleepDeadline > 0 && System.currentTimeMillis() >= sleepDeadline) {
            if (stopAtSegmentEnd) pendingSleepStop = true else {
                player.pause(); clearTimer()
            }
        }
        if (player.isPlaying && ends.isNotEmpty() && player.currentPosition >= ends[segmentIndex] - 35) {
            if (pendingSleepStop) {
                player.pause(); clearTimer(); pendingSleepStop = false
            } else if (repeatCount == 0 || repeatIndex < repeatCount) {
                repeatIndex++
                player.seekTo(starts[segmentIndex])
            } else if (segmentIndex < starts.lastIndex) {
                segmentIndex++
                repeatIndex = 1
                player.seekTo(starts[segmentIndex])
            } else {
                completed = true
                player.pause()
                player.seekTo(ends[segmentIndex])
            }
        }
        publish()
    }

    private fun moveTo(index: Int, play: Boolean) {
        if (starts.isEmpty()) return
        segmentIndex = index.coerceIn(0, starts.lastIndex)
        repeatIndex = 1
        completed = false
        player.seekTo(starts[segmentIndex])
        if (play) player.play()
        publish()
    }

    private fun seekWithin(relativeMs: Long) {
        if (starts.isEmpty()) return
        player.seekTo((starts[segmentIndex] + relativeMs).coerceIn(starts[segmentIndex], ends[segmentIndex]))
    }

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
        val end = ends.getOrElse(segmentIndex) { player.duration.coerceAtLeast(0) }
        PlaybackBus.update(PlaybackSnapshot(
            title = currentTitle,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            segmentIndex = segmentIndex,
            segmentCount = starts.size,
            repeatIndex = repeatIndex,
            repeatCount = repeatCount,
            segmentStartMs = start,
            segmentEndMs = end,
            subtitle = texts.getOrElse(segmentIndex) { "" },
            nextSubtitle = texts.getOrElse(segmentIndex + 1) { "" },
            sleepDeadlineMs = sleepDeadline,
            completed = completed
        ))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        session.release()
        player.release()
        super.onDestroy()
    }
}
