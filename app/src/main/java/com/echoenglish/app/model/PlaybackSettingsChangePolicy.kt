package com.echoenglish.app.model

object PlaybackSettingsChangePolicy {
    fun requiresCurrentTrackValidation(previous: PlaybackSettings, next: PlaybackSettings): Boolean =
        previous.segmentMode != next.segmentMode ||
            previous.subtitlePlaybackScope != next.subtitlePlaybackScope
}
