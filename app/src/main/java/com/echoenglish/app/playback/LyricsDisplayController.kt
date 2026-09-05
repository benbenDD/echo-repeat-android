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
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.echoenglish.app.MainActivity
import com.echoenglish.app.R
import kotlin.math.roundToInt

/** Owns the optional floating subtitle and the separate silent subtitle notification. */
class LyricsDisplayController(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val positionPrefs = context.getSharedPreferences("floating_lyrics", Context.MODE_PRIVATE)
    private var overlayView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var floatingEnabled = false
    private var locked = false
    private var notificationEnabled = false
    private var lastTitle = ""
    private var lastText = ""

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    fun configure(floating: Boolean, isLocked: Boolean, notification: Boolean) {
        floatingEnabled = floating
        locked = isLocked
        notificationEnabled = notification
        if (!floatingEnabled) removeOverlay() else updateOverlay(lastText)
        if (!notificationEnabled) notificationManager.cancel(NOTIFICATION_ID)
        else updateNotification(lastTitle, lastText)
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
        removeOverlay()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun updateOverlay(text: String) {
        if (!floatingEnabled || text.isBlank() || !Settings.canDrawOverlays(context)) {
            removeOverlay()
            return
        }
        val view = overlayView ?: createOverlay().also { overlayView = it }
        view.text = text
        view.isClickable = !locked
        val params = overlayParams ?: return
        val desiredFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            if (locked) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
        if (params.flags != desiredFlags) {
            params.flags = desiredFlags
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun createOverlay(): TextView {
        val density = context.resources.displayMetrics.density
        val view = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding((18 * density).roundToInt(), (10 * density).roundToInt(), (18 * density).roundToInt(), (10 * density).roundToInt())
            maxLines = 3
            background = GradientDrawable().apply {
                setColor(Color.argb(210, 48, 42, 67))
                cornerRadius = 18 * density
                setStroke((1 * density).roundToInt(), Color.argb(180, 255, 255, 255))
            }
        }
        val params = WindowManager.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.86f).roundToInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = positionPrefs.getInt("x", (context.resources.displayMetrics.widthPixels * .07f).roundToInt())
            y = positionPrefs.getInt("y", (context.resources.displayMetrics.heightPixels * .72f).roundToInt())
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        view.setOnTouchListener { _, event ->
            if (locked) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).roundToInt()
                    params.y = startY + (event.rawY - downY).roundToInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    positionPrefs.edit().putInt("x", params.x).putInt("y", params.y).apply(); true
                }
                else -> false
            }
        }
        overlayParams = params
        windowManager.addView(view, params)
        return view
    }

    private fun removeOverlay() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
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
        // Notification permission can be denied independently of the saved display preference.
        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val CHANNEL_ID = "subtitle_display"
        private const val NOTIFICATION_ID = 2042
    }
}
