package com.kwhysee.pocketmix

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

/** pocketMix 영상만 다른 앱 위의 독립 창으로 표시한다. 시스템 PiP는 사용하지 않는다. */
class FloatingVideoService : Service() {
    companion object {
        const val ACTION_START = "com.kwhysee.pocketmix.FLOATING_START"
        const val ACTION_STOP = "com.kwhysee.pocketmix.FLOATING_STOP"
        const val EXTRA_VIDEO_IDS = "video_ids"

        private const val CHANNEL_ID = "floating_videos"
        private const val NOTIFICATION_ID = 1002
        private const val SAVE_INTERVAL_MS = 15_000L

        private var active: FloatingVideoService? = null

        fun hasActiveSession(): Boolean = active != null

        /** 앱으로 돌아올 때 먼저 창과 플레이어를 정리하고 DB에 저장한다. */
        fun finishActiveSession() {
            active?.finishSession()
        }
    }

    private data class FloatingWindow(
        val videoId: String,
        val root: View,
        val playerView: PlayerView,
        val player: ExoPlayer,
        val params: WindowManager.LayoutParams,
        var playbackState: PlaybackState,
    )

    private lateinit var database: PocketMixDatabase
    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest

    private val handler = Handler(Looper.getMainLooper())
    private val windows = linkedMapOf<String, FloatingWindow>()
    private var savedPockets = emptyList<PocketState>()
    private var savedPlaybackStates = emptyMap<String, PlaybackState>()
    private var hasAudioFocus = false
    private var finishing = false

    private val saveRunnable = Runnable { saveNow() }
    private val positionTicker = object : Runnable {
        override fun run() {
            if (finishing) return
            if (!Settings.canDrawOverlays(this@FloatingVideoService)) {
                finishSession()
                return
            }
            windows.values.forEach(::rememberPosition)
            saveNow()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = PocketMixDatabase(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change <= 0) {
                    hasAudioFocus = false
                    windows.values.forEach { it.player.pause() }
                } else {
                    hasAudioFocus = true
                }
            }
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "떠 있는 영상",
            NotificationManager.IMPORTANCE_LOW,
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
        active = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishSession()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("pocketMix 떠 있는 영상")
            .setContentText("창을 눌러 조작하거나 알림에서 모두 닫으세요.")
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(
                R.mipmap.ic_launcher,
                "모두 닫기",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, FloatingVideoService::class.java).apply {
                        action = ACTION_STOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.e("PocketMixFloating", "다른 앱 위에 표시 권한이 없습니다.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (windows.isNotEmpty()) return START_NOT_STICKY

        try {
            val saved = database.loadWorkspace()
            savedPockets = saved.pockets
            savedPlaybackStates = saved.playbackStates
            val requestedIds = intent?.getStringArrayListExtra(EXTRA_VIDEO_IDS)
                .orEmpty().distinct()
            val playableVideos = requestedIds.mapNotNull { id ->
                saved.videos.findUsableVideo(id)
            }

            playableVideos.forEachIndexed { index, video ->
                addFloatingWindow(video, index)
            }

            if (windows.isEmpty()) {
                stopSelf()
            } else {
                hasAudioFocus = audioManager.requestAudioFocus(focusRequest) ==
                        AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                windows.values.forEach { it.player.playWhenReady = hasAudioFocus }
                handler.postDelayed(positionTicker, SAVE_INTERVAL_MS)
            }
        } catch (error: Exception) {
            Log.e("PocketMixFloating", "떠 있는 창 시작 실패", error)
            finishSession()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun addFloatingWindow(video: Video, index: Int) {
        val state = savedPlaybackStates[video.videoId]
            ?: PlaybackState(videoId = video.videoId)
        val player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(video.fileLocation))
            seekTo(state.positionMs.coerceAtLeast(0L))
            volume = if (state.muted) 0f else state.volume.coerceIn(0, 100) / 100f
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val width = dp(280).coerceAtMost((screenWidth * 0.9f).roundToInt())
        val height = dp(230).coerceAtMost((screenHeight * 0.45f).roundToInt())
        val initialX = dp(16) + (index % 2) * dp(20)
        val initialY = dp(70) + index * (height + dp(12))
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
            y = initialY.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            elevation = dp(8).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.DKGRAY)
        }
        val title = TextView(this).apply {
            text = video.title
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(8), 0, dp(8), 0)
        }
        header.addView(
            title,
            LinearLayout.LayoutParams(0, dp(40), 1f),
        )
        val retryButton = Button(this).apply {
            text = "재시도"
            textSize = 10f
            visibility = View.GONE
            contentDescription = "${video.title} 재생 다시 시도"
            setOnClickListener {
                visibility = View.GONE
                player.prepare()
                player.play()
            }
        }
        header.addView(retryButton, LinearLayout.LayoutParams(dp(68), dp(40)))
        val closeButton = Button(this).apply {
            text = "×"
            contentDescription = "${video.title} 떠 있는 창 닫기"
            setOnClickListener { closeWindow(video.videoId) }
        }
        header.addView(closeButton, LinearLayout.LayoutParams(dp(48), dp(40)))
        root.addView(header)

        val playerView = PlayerView(this).apply {
            this.player = player
            useController = true
        }
        root.addView(
            playerView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.DKGRAY)
        }
        val volume = SeekBar(this).apply {
            max = 100
            progress = state.volume
            contentDescription = "${video.title} 음량"
        }
        footer.addView(volume, LinearLayout.LayoutParams(0, dp(48), 1f))
        val muteButton = ImageButton(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        updateMuteButton(muteButton, video.title, state.muted)
        footer.addView(muteButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        val resizeHandle = TextView(this).apply {
            text = "↘"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "${video.title} 창 크기 조절"
        }
        footer.addView(resizeHandle, LinearLayout.LayoutParams(dp(38), dp(48)))
        root.addView(footer)

        val floatingWindow = FloatingWindow(
            videoId = video.videoId,
            root = root,
            playerView = playerView,
            player = player,
            params = params,
            playbackState = state,
        )

        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                floatingWindow.playbackState = floatingWindow.playbackState.copy(
                    volume = progress,
                    muted = false,
                )
                updateMuteButton(muteButton, video.title, muted = false)
                applyVolume(floatingWindow)
                scheduleSave()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        muteButton.setOnClickListener {
            floatingWindow.playbackState = floatingWindow.playbackState.toggleMute()
            updateMuteButton(
                muteButton,
                video.title,
                floatingWindow.playbackState.muted,
            )
            applyVolume(floatingWindow)
            scheduleSave()
        }

        addDragHandler(header, floatingWindow, resize = false)
        addDragHandler(resizeHandle, floatingWindow, resize = true)

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && !hasAudioFocus) {
                    hasAudioFocus = audioManager.requestAudioFocus(focusRequest) ==
                            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                    if (!hasAudioFocus) player.pause()
                }
                if (!isPlaying) {
                    rememberPosition(floatingWindow)
                    scheduleSave()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                rememberPosition(floatingWindow)
                scheduleSave()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PocketMixFloating", "영상=${video.videoId} 재생 오류", error)
                retryButton.visibility = View.VISIBLE
            }
        })

        try {
            windowManager.addView(root, params)
            windows[video.videoId] = floatingWindow
            player.prepare()
        } catch (error: Exception) {
            playerView.player = null
            player.release()
            throw error
        }
    }

    private fun addDragHandler(
        handle: View,
        floatingWindow: FloatingWindow,
        resize: Boolean,
    ) {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        var startWidth = 0
        var startHeight = 0

        handle.setOnTouchListener { _, event ->
            val params = floatingWindow.params
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    startWidth = params.width
                    startHeight = params.height
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - startRawX).roundToInt()
                    val deltaY = (event.rawY - startRawY).roundToInt()
                    if (resize) {
                        params.width = (startWidth + deltaX).coerceIn(
                            dp(210),
                            (screenWidth - params.x).coerceAtLeast(dp(210)),
                        )
                        params.height = (startHeight + deltaY).coerceIn(
                            dp(170),
                            (screenHeight - params.y).coerceAtLeast(dp(170)),
                        )
                    } else {
                        params.x = (startX + deltaX).coerceIn(
                            0,
                            (screenWidth - params.width).coerceAtLeast(0),
                        )
                        params.y = (startY + deltaY).coerceIn(
                            0,
                            (screenHeight - params.height).coerceAtLeast(0),
                        )
                    }
                    windowManager.updateViewLayout(floatingWindow.root, params)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    rememberGeometry(floatingWindow)
                    scheduleSave()
                    true
                }

                else -> false
            }
        }
    }

    private fun applyVolume(window: FloatingWindow) {
        val state = window.playbackState
        window.player.volume = if (state.muted) 0f else state.volume / 100f
        rememberPosition(window)
    }

    private fun updateMuteButton(
        button: ImageButton,
        videoTitle: String,
        muted: Boolean,
    ) {
        button.setImageResource(
            if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        )
        button.contentDescription = if (muted) {
            "$videoTitle 음소거 해제"
        } else {
            "$videoTitle 음소거"
        }
    }

    private fun rememberPosition(window: FloatingWindow) {
        window.playbackState = window.playbackState.copy(
            positionMs = window.player.currentPosition.coerceAtLeast(0L),
        )
        savedPlaybackStates = savedPlaybackStates +
                (window.videoId to window.playbackState)
    }

    private fun rememberGeometry(window: FloatingWindow) {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenHeight = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        savedPockets = savedPockets.map { pocket ->
            if (pocket.videoId != window.videoId) {
                pocket
            } else {
                pocket.copy(
                    x = window.params.x / screenWidth,
                    y = window.params.y / screenHeight,
                    width = window.params.width / screenWidth,
                    height = window.params.height / screenHeight,
                ).normalized()
            }
        }
    }

    private fun scheduleSave() {
        if (finishing) return
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 500L)
    }

    private fun saveNow() {
        if (savedPockets.isNotEmpty()) {
            database.saveState(savedPockets, savedPlaybackStates)
        }
    }

    private fun closeWindow(videoId: String) {
        val window = windows.remove(videoId) ?: return
        rememberPosition(window)
        rememberGeometry(window)
        window.player.pause()
        window.playerView.player = null
        window.player.release()
        runCatching { windowManager.removeView(window.root) }
        scheduleSave()
        if (windows.isEmpty()) finishSession()
    }

    private fun finishSession() {
        if (finishing) return
        finishing = true
        handler.removeCallbacks(saveRunnable)
        handler.removeCallbacks(positionTicker)
        windows.values.forEach {
            rememberPosition(it)
            rememberGeometry(it)
            it.player.pause()
            it.playerView.player = null
            it.player.release()
            runCatching { windowManager.removeView(it.root) }
        }
        windows.clear()
        saveNow()
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasAudioFocus = false
        active = null
        stopSelf()
    }

    override fun onDestroy() {
        finishSession()
        database.close()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}