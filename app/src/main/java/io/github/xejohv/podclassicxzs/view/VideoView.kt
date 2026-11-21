package io.github.xejohv.podclassicxzs.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.VideoView
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.gestures.GestureService
import io.github.xejohv.podclassicxzs.media.AudioFocusManager
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.values.Colors
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * 视频页。
 *
 * 两层状态：
 *  - 半屏（默认）：控制盘展开，画面在上半部按比例居中，红盘在下面；
 *  - 全屏：整机真横屏（Activity 跟随旋转 90°），画面按原始比例铺满、多出来的地方留黑边，
 *    不拉伸变形。退出全屏靠右下角那个「收拢」图标、或者左右边缘向内滑（App 全局侧边手势直接回列表）。
 *  - 左上「‹ 返回」点一下退回列表，长按 0.6 秒直接回主菜单；
 *  - 点画面中央 = 播放/暂停。
 */
@SuppressLint("ViewConstructor")
class VideoView(context: Context, val file: File) : FrameLayout(context), ScreenView {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val audioFocusManager =
        AudioFocusManager(context, object : AudioFocusManager.OnAudioFocusChangeListener {
            override fun onAudioFocusGain() {}
            override fun onAudioFocusLoss(permanent: Boolean, pausedByDuck: Boolean) {
                if (videoView.isPlaying) {
                    videoView.pause()
                    overlay.invalidate()
                }
            }
        })

    private val videoView = VideoView(context)
    private val overlay = VideoOverlay(context)

    /** 视频原始尺寸，用来算 letterbox，避免全屏后被拉长变形 */
    private var videoW = 0
    private var videoH = 0

    /** true = 横屏全屏 */
    private var landscape = false

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            overlay.invalidate()
            if (overlay.isAttachedToWindow) handler.postDelayed(this, 500)
        }
    }

    init {
        addView(
            videoView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.CENTER })

        setBackgroundColor(Colors.text)

        requestAudioFocus()
        videoView.apply {
            setVideoPath(file.toString())
            setOnErrorListener { _, _, _ -> return@setOnErrorListener true }
            setOnPreparedListener { mp ->
                videoW = mp.videoWidth
                videoH = mp.videoHeight
                fitVideo()
            }
            start()
        }

        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** 按容器尺寸给视频做等比适配：宽或高顶到边界，另一边留黑边，绝不拉伸 */
    private fun fitVideo() {
        val boxW = width
        val boxH = height
        if (boxW <= 0 || boxH <= 0 || videoW <= 0 || videoH <= 0) return
        val scale = min(boxW.toFloat() / videoW, boxH.toFloat() / videoH)
        val w = (videoW * scale).toInt()
        val h = (videoH * scale).toInt()
        videoView.layoutParams = LayoutParams(w, h).apply { gravity = Gravity.CENTER }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitVideo()
    }

    private fun requestAudioFocus() {
        MediaPresenter.pause()
        audioFocusManager.requestAudioFocus()
    }

    private fun togglePlay() {
        if (videoView.isPlaying) {
            videoView.pause()
            audioFocusManager.abandonAudioFocus()
        } else {
            requestAudioFocus()
            videoView.start()
        }
        overlay.invalidate()
    }

    private fun activityOrientation(mode: Int) {
        try {
            (context as? Activity)?.requestedOrientation = mode
        } catch (e: Exception) {
            // 某些机型没有权限改方向时忽略，画面照样按剩余空间适配
        }
    }

    /** 进入全屏：真横屏 + 收起控制盘，并把底部悬浮退出条一起收掉（全屏看片不需要任何浮层） */
    fun enterFullscreen() {
        landscape = true
        Core.setGameExpanded(true)
        GestureService.hideExitBar()
        // 全屏看片时连系统那条底部细横线也一起收掉：它本来就不能点，留着只会让人以为坏了
        GestureService.setPillVisible(false)
        activityOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        overlay.hideControlsNow()
        postDelayed({ fitVideo() }, 320)
        overlay.invalidate()
    }

    /** 回到半屏：竖屏 + 控制盘展开 */
    fun exitFullscreen() {
        landscape = false
        Core.setGameExpanded(false)
        GestureService.setPillVisible(true)
        activityOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        overlay.hideControlsNow()
        postDelayed({ fitVideo() }, 320)
        overlay.invalidate()
    }

    override fun onViewAdd() {
        requestAudioFocus()
        // 进视频先给半屏：画面在上、红盘在下面，想看满屏再点右下角的全屏键
        Core.setGameExpanded(false)
        // 视频页不挂悬浮退出条（返回键在画面左上角，长按左上角也能直接退）
        GestureService.hideExitBar()
        GestureService.setPillVisible(true)
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 500)
        overlay.resetHint()
    }

    override fun onViewRemove() {
        handler.removeCallbacks(ticker)
        audioFocusManager.abandonAudioFocus()
        videoView.pause()
    }

    override fun enter(): Boolean {
        togglePlay()
        return true
    }

    override fun enterLongClick(): Boolean {
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        val sv = slideVal * 10 * 1000
        var progress = videoView.currentPosition
        val duration = videoView.duration
        if ((progress <= 0 && sv < 0) || (progress >= duration && sv > 0)) {
            return false
        }
        progress += sv
        if (progress < 0) {
            progress = 0
        }
        if (progress > duration) {
            progress = duration
        }
        videoView.seekTo(progress)
        overlay.invalidate()
        return true
    }

    override fun getTitle(): String {
        return file.name
    }

    override fun onViewDelete() {
        handler.removeCallbacks(ticker)
        // 视频页走了，把全局底部横线还回去
        GestureService.setPillVisible(true)
        // 退出视频必须把屏幕转回竖屏，否则整个 App 会横着留在桌面上
        if (landscape) {
            landscape = false
            activityOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }
        videoView.apply {
            stopPlayback()
            suspend()
            setOnErrorListener(null)
            setOnPreparedListener(null)
        }
        removeAllViews()
        audioFocusManager.abandonAudioFocus()
    }

    private fun timeText(ms: Int): String {
        if (ms <= 0) return "0:00"
        val total = ms / 1000
        val m = total / 60
        val s = total % 60
        return String.format("%d:%02d", m, s)
    }

    /** 浮在画面上的控制层：返回、播放指示、进度、手势提示、全屏键 */
    @SuppressLint("ViewConstructor")
    inner class VideoOverlay(context: Context) : View(context) {

        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var longPressed = false
        private val longPressRunnable = Runnable {
            longPressed = true
            Core.home()
        }

        private var hintUntil = 0L

        /**
         * 控制条显隐：跟手机视频播放器一致 ——
         * 进片和播放时**默认完全干净**（一个按钮都不画），轻触画面才浮出控件，
         * 浮出后 3.2 秒没动静自动再收起，再轻触又出来。
         */
        private var controlsVisible = false

        /**
         * 底部进度条拖动快进/快退：
         * 按下即暂停并进入 scrub，只更新画面上的进度与气泡，松手才真正 seekTo，
         * 这样在 XZs 上拖起来不会一顿一顿地卡。
         */
        private var scrubbing = false
        private var scrubPos = 0
        private var scrubDuration = 0
        private var resumeAfterScrub = false
        private val hideControls = Runnable {
            controlsVisible = false
            invalidate()
        }

        fun resetHint() {
            hintUntil = System.currentTimeMillis() + 2500
            invalidate()
        }

        /** 立刻收起全部控件（进片 / 切全屏时用，画面保持干净） */
        fun hideControlsNow() {
            handler.removeCallbacks(hideControls)
            controlsVisible = false
            invalidate()
        }

        private fun showControls(autoHide: Boolean) {
            controlsVisible = true
            invalidate()
            handler.removeCallbacks(hideControls)
            if (autoHide) {
                handler.postDelayed(hideControls, 3200)
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            if (controlsVisible) {
                // 顶部渐隐 + 底部渐隐，保证文字在亮画面上可读
                p.style = Paint.Style.FILL
                p.color = Color.argb(76, 0, 0, 0)
                canvas.drawRect(0f, 0f, w, dp(52f), p)
                canvas.drawRect(0f, h - dp(46f), w, h, p)

                // 全屏时不再画左上返回键：切换只留右下角那个全屏/收回键
                if (!landscape) drawBack(canvas)
                drawFullscreen(canvas, w, h)
                drawProgress(canvas, w, h)
                drawPlayIcon(canvas, w, h)
            }
            drawHint(canvas, w)
        }

        private fun drawBack(canvas: Canvas) {
            val cx = dp(28f)
            val cy = dp(28f)
            p.style = Paint.Style.FILL
            p.color = Color.argb(104, 0, 0, 0)
            canvas.drawCircle(cx, cy, dp(17f), p)
            p.color = Color.WHITE
            path.reset()
            path.moveTo(cx - dp(4.5f), cy)
            path.lineTo(cx + dp(4.5f), cy - dp(7.5f))
            path.lineTo(cx + dp(4.5f), cy + dp(7.5f))
            path.close()
            canvas.drawPath(path, p)
        }

        /** 右下角全屏键：四角向外 = 全屏；四角向内 = 收回半屏 */
        private fun drawFullscreen(canvas: Canvas, w: Float, h: Float) {
            val cx = w - dp(28f)
            val cy = h - dp(58f)
            p.style = Paint.Style.FILL
            p.color = Color.argb(104, 0, 0, 0)
            canvas.drawCircle(cx, cy, dp(17f), p)
            p.color = Color.WHITE
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(1.7f)
            val arm = dp(6.8f)
            val gap = dp(3.4f)
            val out = landscape
            for (i in 0 until 4) {
                val sx = if (i % 2 == 0) -1f else 1f
                val sy = if (i < 2) -1f else 1f
                val ax = cx + sx * gap
                val ay = cy + sy * gap
                val bx = cx + sx * (gap + arm)
                val by = cy + sy * (gap + arm)
                // 全屏时画"向外张开"的角，半屏时画"向内收拢"的角
                val innerX = if (out) ax else bx
                val innerY = if (out) ay else by
                val tipX = if (out) bx else ax
                val tipY = if (out) by else ay
                canvas.drawLine(innerX, innerY, tipX, innerY, p)
                canvas.drawLine(innerX, innerY, innerX, tipY, p)
            }
            p.style = Paint.Style.FILL
        }

        private fun barLeft() = dp(20f)

        private fun barRight() = width - dp(20f)

        private fun barY() = height - dp(30f)

        /** 进度条命中带：只在画面最下面一条，右下角全屏键那块让给全屏键 */
        private fun inScrubBand(x: Float, y: Float): Boolean {
            if (width <= 0 || height <= 0) return false
            if (y < height - dp(64f) || y > height - dp(6f)) return false
            if (x < dp(14f) || x > width - dp(64f)) return false
            return true
        }

        private fun beginScrub(): Boolean {
            scrubDuration = try {
                videoView.duration
            } catch (e: Exception) {
                0
            }
            if (scrubDuration <= 0) return false
            scrubbing = true
            resumeAfterScrub = try {
                videoView.isPlaying
            } catch (e: Exception) {
                false
            }
            if (resumeAfterScrub) {
                try {
                    videoView.pause()
                } catch (e: Exception) {
                }
            }
            handler.removeCallbacks(hideControls)
            controlsVisible = true
            invalidate()
            return true
        }

        private fun updateScrub(x: Float) {
            if (!scrubbing) return
            val l = barLeft()
            val r = barRight()
            val ratio = ((x - l) / (r - l)).coerceIn(0f, 1f)
            scrubPos = (ratio * scrubDuration).toInt()
            invalidate()
        }

        private fun finishScrub() {
            val target = scrubPos
            scrubbing = false
            try {
                videoView.seekTo(target)
            } catch (e: Exception) {
            }
            if (resumeAfterScrub) {
                try {
                    videoView.start()
                } catch (e: Exception) {
                }
            }
            resumeAfterScrub = false
            showControls(true)
        }

        private fun drawHint(canvas: Canvas, w: Float) {
            if (System.currentTimeMillis() > hintUntil) return
            p.style = Paint.Style.FILL
            p.color = Color.argb(150, 255, 255, 255)
            p.textAlign = Paint.Align.CENTER
            p.textSize = dp(12f)
            val text = if (landscape) {
                "轻触画面 显示控件 · 底部进度条左右拖动快进 · 右下角收回半屏"
            } else {
                "轻触画面 显示控件 · 底部进度条左右拖动快进 · 右下角 全屏"
            }
            canvas.drawText(text, w / 2f, dp(86f), p)
        }

        private fun drawProgress(canvas: Canvas, w: Float, h: Float) {
            val duration = if (scrubbing) scrubDuration else try {
                videoView.duration
            } catch (e: Exception) {
                0
            }
            if (duration <= 0) return
            val pos = if (scrubbing) scrubPos else try {
                videoView.currentPosition
            } catch (e: Exception) {
                0
            }
            val l = barLeft()
            val r = barRight()
            val y = barY()
            p.style = Paint.Style.FILL
            p.color = Color.argb(110, 255, 255, 255)
            canvas.drawRoundRect(RectF(l, y, r, y + dp(3f)), dp(2f), dp(2f), p)
            val ratio = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            val dotX = l + (r - l) * ratio
            p.color = Color.WHITE
            canvas.drawRoundRect(RectF(l, y, dotX, y + dp(3f)), dp(2f), dp(2f), p)

            // 进度条端头的圆点：明确告诉用户这条能拖
            if (scrubbing) {
                p.color = Color.argb(70, 0, 0, 0)
                canvas.drawCircle(dotX, y + dp(1.5f), dp(13f), p)
                p.color = Color.WHITE
                canvas.drawCircle(dotX, y + dp(1.5f), dp(6.5f), p)
            } else {
                p.color = Color.WHITE
                canvas.drawCircle(dotX, y + dp(1.5f), dp(6f), p)
            }

            p.textSize = dp(12f)
            p.color = Color.argb(220, 255, 255, 255)
            p.textAlign = Paint.Align.LEFT
            canvas.drawText(timeText(pos), l, y - dp(8f), p)
            p.textAlign = Paint.Align.RIGHT
            canvas.drawText(timeText(duration), r, y - dp(8f), p)

            // 拖动时在圆点上方浮一个时间气泡，手指压住进度条也看得见目标时间
            if (scrubbing) {
                val label = timeText(scrubPos)
                p.textSize = dp(13f)
                val tw = p.measureText(label)
                val bw = tw + dp(18f)
                val bh = dp(24f)
                val bx = (dotX - bw / 2f).coerceIn(dp(4f), w - dp(4f) - bw)
                val by = y - dp(32f)
                p.color = Color.argb(170, 0, 0, 0)
                canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), dp(6f), dp(6f), p)
                p.color = Color.WHITE
                p.textAlign = Paint.Align.CENTER
                canvas.drawText(label, bx + bw / 2f, by + bh * 0.68f, p)
                p.textAlign = Paint.Align.LEFT
            }
        }

        private fun drawPlayIcon(canvas: Canvas, w: Float, h: Float) {
            val playing = try {
                videoView.isPlaying
            } catch (e: Exception) {
                false
            }
            val cx = w / 2f
            val cy = h / 2f
            // 播放/暂停合成一个按钮：媒体播放器都是这个样子，轻触画面显隐这套控件
            p.style = Paint.Style.FILL
            p.color = Color.argb(92, 0, 0, 0)
            canvas.drawCircle(cx, cy, dp(25f), p)
            p.color = Color.argb(234, 255, 255, 255)
            if (playing) {
                val bw = dp(4f)
                val bh = dp(13.5f)
                val g = dp(4f)
                canvas.drawRect(cx - g - bw, cy - bh, cx - g, cy + bh, p)
                canvas.drawRect(cx + g, cy - bh, cx + g + bw, cy + bh, p)
            } else {
                path.reset()
                path.moveTo(cx - dp(8.5f), cy - dp(14f))
                path.lineTo(cx + dp(16f), cy)
                path.lineTo(cx - dp(8.5f), cy + dp(14f))
                path.close()
                canvas.drawPath(path, p)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = System.currentTimeMillis()
                    longPressed = false
                    // 底部进度条：按住就能拖，不必先轻触唤出控件
                    if (inScrubBand(event.x, event.y) && beginScrub()) {
                        updateScrub(event.x)
                        return true
                    }
                    if (!landscape && event.x < dp(80f) && event.y < dp(80f)) {
                        postDelayed(longPressRunnable, 600)
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scrubbing) {
                        updateScrub(event.x)
                        return true
                    }
                    if (!longPressed && abs(event.y - downY) > dp(28f)) {
                        removeCallbacks(longPressRunnable)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (scrubbing) {
                        finishScrub()
                        return true
                    }
                    removeCallbacks(longPressRunnable)
                    if (longPressed) {
                        longPressed = false
                        return true
                    }
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val dt = System.currentTimeMillis() - downTime

                    // 左右边缘向内滑（屏幕左右 60dp 起手）：直接退出全屏 / 回半屏
                    val fromEdge = downX < dp(60f) || downX > width - dp(60f)
                    if (landscape && fromEdge && dt > 120 && abs(dx) > dp(80f) && abs(dx) > abs(dy)) {
                        exitFullscreen()
                        return true
                    }

                    // 纵向滑动：半屏时下滑进全屏，全屏时上滑保留（不改方向）
                    if (!landscape && dt > 120 && abs(dy) > dp(70f) && abs(dy) > abs(dx) * 1.4f) {
                        if (dy > 0) {
                            enterFullscreen()
                            return true
                        }
                    }

                    // 右下角全屏键
                    val fx = width - dp(28f)
                    val fy = height - dp(58f)
                    if (controlsVisible && abs(event.x - fx) < dp(30f) && abs(event.y - fy) < dp(30f)) {
                        if (landscape) exitFullscreen() else enterFullscreen()
                        return true
                    }

                    // 全屏时不再留左上返回键的位置，只靠右下角全屏键收回半屏
                    if (!landscape && controlsVisible && event.x < dp(60f) && event.y < dp(60f)) {
                        Core.removeView()
                        return true
                    }

                    // 中央播放/暂停键（控件可见时才认）
                    val ccx = width / 2f
                    val ccy = height / 2f
                    if (controlsVisible && abs(event.x - ccx) < dp(34f) && abs(event.y - ccy) < dp(34f)) {
                        togglePlay()
                        showControls(true)
                        return true
                    }

                    // 其余地方：轻触一下收起控件，再轻触一下出现（和手机视频播放器一致）
                    if (controlsVisible) {
                        handler.removeCallbacks(hideControls)
                        controlsVisible = false
                        invalidate()
                    } else {
                        showControls(true)
                    }
                    return true
                }
                else -> return true
            }
        }
    }
}
