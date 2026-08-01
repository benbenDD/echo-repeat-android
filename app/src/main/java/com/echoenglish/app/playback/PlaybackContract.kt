package com.echoenglish.app.playback

object PlaybackContract {
    const val ACTION_LOAD = "com.echoenglish.LOAD"
    const val ACTION_TOGGLE = "com.echoenglish.TOGGLE"
    const val ACTION_NEXT = "com.echoenglish.NEXT_SEGMENT"
    const val ACTION_PREVIOUS = "com.echoenglish.PREVIOUS_SEGMENT"
    const val ACTION_RESTART = "com.echoenglish.RESTART_SEGMENT"
    const val ACTION_SEEK = "com.echoenglish.SEEK"
    const val ACTION_TIMER = "com.echoenglish.TIMER"
    const val ACTION_CANCEL_TIMER = "com.echoenglish.CANCEL_TIMER"
    const val EXTRA_URI = "uri"
    const val EXTRA_TITLE = "title"
    const val EXTRA_STARTS = "starts"
    const val EXTRA_ENDS = "ends"
    const val EXTRA_TEXTS = "texts"
    const val EXTRA_REPEATS = "repeats"
    const val EXTRA_INDEX = "index"
    const val EXTRA_SPEED = "speed"
    const val EXTRA_POSITION = "position"
    const val EXTRA_TIMER_MINUTES = "timer_minutes"
    const val EXTRA_STOP_AT_END = "stop_at_end"
}
