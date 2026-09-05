package com.echoenglish.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.echoenglish.app.MainActivity
import com.echoenglish.app.R
import com.echoenglish.app.model.FloatingLyricsColor
import kotlin.math.abs
import kotlin.math.roundToInt

/** Owns the optional floating subtitle and the separate silent subtitle notification. */
class LyricsDisplayController(
    private val context: Context,
    private val onCloseFloating: () -> Unit,
    private val onLockFloating: (Boolean) -> Unit
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val positionPrefs = context.getSharedPreferences("floating_lyrics", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var overlayText: TextView? = null
    private var controlsView: View? = null
    private var lockButton: ImageButton? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var floatingEnabled = false
    private var locked = false
    private var lyricsColor = FloatingLyricsColor.ORANGE
    private var notificationEnabled = false
    private var lastTitle = ""
    private var lastText = ""
    private val hideControls = Runnable { setInteractionVisible(false) }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel("subtitle_display_visible")
            notificationManager.deleteNotificationChannel("subtitle_display_prominent_v2")
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "台词显示",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "在通知栏和锁屏显示当前台词"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }

    fun configure(
        floating: Boolean,
        isLocked: Boolean,
        color: FloatingLyricsColor,
        notification: Boolean
    ) {
        floatingEnabled = floating
        locked = isLocked
        lyricsColor = color
        notificationEnabled = notification
        if (!floatingEnabled) removeOverlay() else updateOverlay(lastText)
        if (!notificationEnabled) {
            notificationManager.cancel(NOTIFICATION_ID)
        } else {
            updateNotification(lastTitle, lastText)
        }
    }

    fun update(title: String, text: String) {
        val unchanged = title == lastTitle && text == lastText
        lastTitle = title
        lastText = text
        if (unchanged) return
        updateOverlay(text)
        updateNotification(title, text)
    }

    fun release() {
        handler.removeCallbacks(hideControls)
        removeOverlay()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun updateOverlay(text: String) {
        if (!floatingEnabled || text.isBlank() || !Settings.canDrawOverlays(context)) {
            removeOverlay()
            return
        }
        if (overlayView == null) overlayView = createOverlay()
        overlayText?.apply {
            this.text = text
            setTextColor(lyricsTextColor())
        }
        updateLockIcon()
    }

    private fun createOverlay(): View {
        val density = context.resources.displayMetrics.density
        val normalHorizontalPadding = (14 * density).roundToInt()
        val textView = TextView(context).apply {
            setTextColor(lyricsTextColor())
            textSize = 19f
            gravity = Gravity.CENTER
            setPadding(normalHorizontalPadding, (10 * density).roundToInt(), normalHorizontalPadding, (10 * density).roundToInt())
            maxLines = 3
            background = null
        }
        overlayText = textView

        fun iconButton(icon: Int, description: String) = ImageButton(context).apply {
            setImageResource(icon)
            contentDescription = description
            setColorFilter(Color.WHITE)
            setPadding((7 * density).roundToInt(), (7 * density).roundToInt(), (7 * density).roundToInt(), (7 * density).roundToInt())
            background = GradientDrawable().apply {
                setColor(Color.argb(205, 48, 42, 67))
                shape = GradientDrawable.OVAL
            }
        }

        val lock = iconButton(lockIcon(), if (locked) "解锁悬浮台词" else "锁定悬浮台词").apply {
            setOnClickListener {
                locked = !locked
                updateLockIcon()
                onLockFloating(locked)
                showInteractionTemporarily()
            }
        }
        lockButton = lock
        val close = iconButton(R.drawable.ic_lyrics_close, "关闭悬浮台词").apply {
            setOnClickListener {
                floatingEnabled = false
                removeOverlay()
                onCloseFloating()
            }
        }
        val buttonSize = (32 * density).roundToInt()
        val controls = FrameLayout(context).apply {
            visibility = View.GONE
            addView(lock, FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.CENTER_VERTICAL or Gravity.START))
            addView(close, FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.CENTER_VERTICAL or Gravity.END))
        }
        controlsView = controls

        val root = FrameLayout(context).apply {
            addView(textView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            addView(controls, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, buttonSize, Gravity.TOP).apply {
                topMargin = (4 * density).roundToInt()
                marginStart = (5 * density).roundToInt()
                marginEnd = (5 * density).roundToInt()
            })
        }
        val params = WindowManager.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.90f).roundToInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = positionPrefs.getInt("x", (context.resources.displayMetrics.widthPixels * .05f).roundToInt())
            y = positionPrefs.getInt("y", (context.resources.displayMetrics.heightPixels * .72f).roundToInt())
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        textView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    setInteractionVisible(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!locked) {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (abs(dx) > 6 * density || abs(dy) > 6 * density) dragged = true
                        if (dragged) {
                            params.x = startX + dx.roundToInt()
                            params.y = startY + dy.roundToInt()
                            windowManager.updateViewLayout(root, params)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragged) {
                        positionPrefs.edit().putInt("x", params.x).putInt("y", params.y).apply()
                    }
                    showInteractionTemporarily()
                    true
                }
                else -> false
            }
        }
        overlayParams = params
        windowManager.addView(root, params)
        return root
    }

    private fun setInteractionVisible(visible: Boolean) {
        controlsView?.visibility = if (visible) View.VISIBLE else View.GONE
        overlayText?.apply {
            background = if (visible) interactionBackground() else null
            val density = resources.displayMetrics.density
            val horizontal = (14 * density).roundToInt()
            setPadding(
                if (visible) (48 * density).roundToInt() else horizontal,
                (10 * density).roundToInt(),
                if (visible) (48 * density).roundToInt() else horizontal,
                (10 * density).roundToInt()
            )
        }
    }

    private fun showInteractionTemporarily() {
        handler.removeCallbacks(hideControls)
        setInteractionVisible(true)
        handler.postDelayed(hideControls, CONTROLS_VISIBLE_MS)
    }

    private fun interactionBackground() = GradientDrawable().apply {
        val density = context.resources.displayMetrics.density
        setColor(Color.argb(145, 48, 42, 67))
        cornerRadius = 16 * density
    }

    private fun lyricsTextColor(): Int = when (lyricsColor) {
        FloatingLyricsColor.ORANGE -> Color.rgb(255, 167, 38)
        FloatingLyricsColor.GREEN -> Color.rgb(102, 220, 120)
        FloatingLyricsColor.WHITE -> Color.WHITE
    }

    private fun lockIcon(): Int = if (locked) R.drawable.ic_lyrics_unlock else R.drawable.ic_lyrics_lock

    private fun updateLockIcon() {
        lockButton?.apply {
            setImageResource(lockIcon())
            contentDescription = if (locked) "解锁悬浮台词" else "锁定悬浮台词"
        }
    }

    private fun removeOverlay() {
        handler.removeCallbacks(hideControls)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        overlayText = null
        controlsView = null
        lockButton = null
        overlayParams = null
    }

    private fun updateNotification(title: String, text: String) {
        if (!notificationEnabled || text.isBlank()) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = PlaybackContract.ACTION_OPEN_PLAYER
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(context, 1002, intent, flags)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title.ifBlank { "当前台词" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val CHANNEL_ID = "subtitle_display"
        private const val NOTIFICATION_ID = 2042
        private const val CONTROLS_VISIBLE_MS = 3_000L
    }
}
