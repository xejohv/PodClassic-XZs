package io.github.xejohv.podclassicxzs.view

import android.annotation.SuppressLint
import android.content.Context
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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.media.AudioFocusManager
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.values.Colors
import java.io.File
import kotlin.math.abs

/**
 * 视频页。
 *
 * 与相机页同一套沉浸逻辑：
 *  - 进入即全屏（控制盘收起、标题栏隐藏），画面尽量大；
 *  - 单指上滑展回控制盘（退出沉浸），下滑收回（重新全屏）—— 与游戏页手势一致；
 *  - 左上「‹ 返回」点一下退回列表，长按 0.6 秒直接回主菜单；
 *  - 三指上滑回主菜单（App 全局手势）；
 *  - 画面点按 = 播放/暂停。
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

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            overlay.invalidate()
            if (overlay.isAttachedToWindow) handler.postDelayed(this, 500)
        }
    }

    private val immerseAgain = Runnable {
        if (overlay.isAttachedToWindow) Core.setGameExpanded(true)
    }

    init {
        addView(
            videoView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })

        setBackgroundColor(Colors.text)

        requestAudioFocus()
        videoView.apply {
            setVideoPath(file.toString())
            setOnErrorListener { _, _, _ -> return@setOnErrorListener true }
            start()
        }

        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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

    override fun onViewAdd() {
        requestAudioFocus()
        // 视频页进入即沉浸：收起控制盘、隐藏标题栏
        Core.setGameExpanded(true)
        overlay.postDelayed(immerseAgain, 260)
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 500)
        overlay.resetHint()
    }

    override fun onViewRemove() {
        overlay.removeCallbacks(immerseAgain)
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
        overlay.removeCallbacks(immerseAgain)
        handler.removeCallbacks(ticker)
        videoView.apply {
            stopPlayback()
            suspend()
            setOnErrorListener(null)
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

    /** 浮在画面上的控制层：返回、播放指示、进度、手势提示 */
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

        fun resetHint() {
            hintUntil = System.currentTimeMillis() + 2500
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            // 顶部渐隐 + 底部渐隐，保证文字在亮画面上可读
            p.style = Paint.Style.FILL
            p.color = Color.argb(96, 0, 0, 0)
            canvas.drawRect(0f, 0f, w, dp(64f), p)
            canvas.drawRect(0f, h - dp(56f), w, h, p)

            drawBack(canvas)
            drawHint(canvas, w)
            drawProgress(canvas, w, h)
            drawPlayIcon(canvas, w, h)
        }

        private fun drawBack(canvas: Canvas) {
            val cx = dp(34f)
            val cy = dp(34f)
            p.style = Paint.Style.FILL
            p.color = Color.argb(120, 0, 0, 0)
            canvas.drawCircle(cx, cy, dp(24f), p)
            p.color = Color.WHITE
            path.reset()
            path.moveTo(cx - dp(6f), cy)
            path.lineTo(cx + dp(6f), cy - dp(10f))
            path.lineTo(cx + dp(6f), cy + dp(10f))
            path.close()
            canvas.drawPath(path, p)
        }

        private fun drawHint(canvas: Canvas, w: Float) {
            if (System.currentTimeMillis() > hintUntil) return
            p.style = Paint.Style.FILL
            p.color = Color.argb(150, 255, 255, 255)
            p.textAlign = Paint.Align.CENTER
            p.textSize = dp(12f)
            canvas.drawText("点画面暂停/继续 · 上滑展回控制盘 · 左上长按回主菜单", w / 2f, dp(86f), p)
        }

        private fun drawProgress(canvas: Canvas, w: Float, h: Float) {
            val duration = try {
                videoView.duration
            } catch (e: Exception) {
                0
            }
            if (duration <= 0) return
            val pos = try {
                videoView.currentPosition
            } catch (e: Exception) {
                0
            }
            val l = dp(20f)
            val r = w - dp(20f)
            val y = h - dp(30f)
            p.style = Paint.Style.FILL
            p.color = Color.argb(110, 255, 255, 255)
            canvas.drawRoundRect(RectF(l, y, r, y + dp(3f)), dp(2f), dp(2f), p)
            val ratio = pos.toFloat() / duration.toFloat()
            p.color = Color.WHITE
            canvas.drawRoundRect(RectF(l, y, l + (r - l) * ratio, y + dp(3f)), dp(2f), dp(2f), p)

            p.textSize = dp(12f)
            p.color = Color.argb(220, 255, 255, 255)
            p.textAlign = Paint.Align.LEFT
            canvas.drawText(timeText(pos), l, y - dp(6f), p)
            p.textAlign = Paint.Align.RIGHT
            canvas.drawText(timeText(duration), r, y - dp(6f), p)
        }

        private fun drawPlayIcon(canvas: Canvas, w: Float, h: Float) {
            val playing = try {
                videoView.isPlaying
            } catch (e: Exception) {
                false
            }
            if (playing) return
            val cx = w / 2f
            val cy = h / 2f
            p.style = Paint.Style.FILL
            p.color = Color.argb(190, 255, 255, 255)
            path.reset()
            path.moveTo(cx - dp(20f), cy - dp(34f))
            path.lineTo(cx + dp(34f), cy)
            path.lineTo(cx - dp(20f), cy + dp(34f))
            path.close()
            canvas.drawPath(path, p)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = System.currentTimeMillis()
                    longPressed = false
                    if (event.x < dp(80f) && event.y < dp(80f)) {
                        postDelayed(longPressRunnable, 600)
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!longPressed && abs(event.y - downY) > dp(28f)) {
                        removeCallbacks(longPressRunnable)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPressRunnable)
                    if (longPressed) {
                        longPressed = false
                        return true
                    }
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val dt = System.currentTimeMillis() - downTime
                    // 纵向滑动：上滑展回控制盘，下滑收回（与游戏一致）
                    if (dt > 120 && abs(dy) > dp(70f) && abs(dy) > abs(dx) * 1.4f) {
                        if (dy < 0) {
                            Core.setGameExpanded(false)
                        } else {
                            Core.setGameExpanded(true)
                        }
                        return true
                    }
                    if (event.x < dp(80f) && event.y < dp(80f)) {
                        Core.removeView()
                        return true
                    }
                    togglePlay()
                    return true
                }
                else -> return true
            }
        }
    }
}
