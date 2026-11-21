package io.github.xejohv.podclassicxzs.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import io.github.xejohv.podclassicxzs.values.Colors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PodClassic 自己的锁屏页（盖在主界面上的一整层）。
 *
 *  顶部：时间 + 日期
 *  中部：挂锁
 *  底部：一条"滑动解锁"轨道，圆形把手从左拖到右端就解锁
 *  最底：一条倒计时细线，5 秒内没解锁就自动熄屏
 *
 * 不用文字，靠"轨道 + 把手 + 右向箭头"表达可滑动；把手在拖动时有 1:1 跟随，手感上的确定性靠这个。
 */
@SuppressLint("ViewConstructor")
class LockView(context: Context) : View(context) {

    companion object {
        /** 亮屏后多久没解锁就熄屏 */
        const val IDLE_TIMEOUT_MS = 5000L
        /** 把手走到这个比例就算解锁 */
        private const val UNLOCK_RATIO = 0.78f
        /** 倒计时刷新间隔 */
        private const val TICK_MS = 50L
    }

    /** 解锁回调 */
    var onUnlock: (() -> Unit)? = null
    /** 5 秒到点回调（去熄屏） */
    var onTimeout: (() -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val dateFmt = SimpleDateFormat("M月d日 EEEE", Locale.CHINA)
    private val letterSpacing = 0f

    private var handleT = 0f
    private var dragging = false
    private var downX = 0f
    private var downT = 0f
    private var deadline = 0L
    private var finished = false

    /** 轨道几何，onSizeChanged 里算一次 */
    private var trackL = 0f
    private var trackR = 0f
    private var trackY = 0f
    private var trackH = 0f

    private val ticker = object : Runnable {
        override fun run() {
            if (finished) {
                return
            }
            val now = SystemClock.uptimeMillis()
            if (now >= deadline) {
                finished = true
                invalidate()
                onTimeout?.invoke()
                return
            }
            invalidate()
            handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        setBackgroundColor(Colors.color_primary)
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trackH = dp * 17f
        val tw = Math.min(w * 0.68f, dp * 252f)
        trackL = (w - tw) / 2f
        trackR = trackL + tw
        trackY = h * 0.805f
    }

    /** 主动开始 5 秒倒计时（每次重新亮屏、每次上手都重置） */
    fun armTimeout() {
        finished = false
        deadline = SystemClock.uptimeMillis() + IDLE_TIMEOUT_MS
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, TICK_MS)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        armTimeout()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val ink = Colors.ink_on_body
        val accent = Colors.main

        // ---------- 时间 / 日期 ----------
        val now = Date()
        paint.setStyle(Paint.Style.FILL)
        paint.setTextAlign(Paint.Align.CENTER)
        paint.color = ink
        paint.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        paint.textSize = dp * 66f
        paint.letterSpacing = 0.02f
        canvas.drawText(timeFmt.format(now), w / 2f, h * 0.325f, paint)

        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = dp * 15f
        paint.color = Color.argb(190, Color.red(ink), Color.green(ink), Color.blue(ink))
        paint.letterSpacing = 0.06f
        canvas.drawText(dateFmt.format(now), w / 2f, h * 0.325f + dp * 30f, paint)
        paint.letterSpacing = letterSpacing

        // ---------- 挂锁 ----------
        drawPadlock(canvas, w / 2f, h * 0.512f, ink)

        // ---------- 滑动解锁轨道 ----------
        drawSlider(canvas)

        // ---------- 5 秒倒计时：底部一条细线，随时间缩短 ----------
        val remain = if (finished) 0f else Math.max(0L, deadline - SystemClock.uptimeMillis()).toFloat() / IDLE_TIMEOUT_MS
        val barH = dp * 4.5f
        val barY = h - barH
        paint.color = Color.argb(34, Color.red(ink), Color.green(ink), Color.blue(ink))
        canvas.drawRect(0f, barY, w, barY + barH, paint)
        paint.color = Color.argb(225, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRect(0f, barY, w * remain, barY + barH, paint)
    }

    /**
     * 解锁滑块（重做版）。
     *
     * 旧版是一条平涂的圆角条 + 一枚白饼 + 一个单箭头，边界散、没有层次，
     * 看上去像一块贴在屏幕上的灰色塑料。现在改成三层结构：
     *   1. 凹槽：竖向渐变（顶部深、底部浅）+ 一圈亮描边，像嵌进屏幕的一条玻璃缝；
     *   2. 进度：从左侧长到把手位置，主题色渐变，越拖越亮；
     *   3. 把手：金属圆片（径向高光 + 顶部弧线 + 主题色圆芯），拖动时外圈起一层柔光。
     * 右端是三重人字箭头，随时间依次点亮，明确告诉你"往这边拖"。
     */
    private fun drawSlider(canvas: Canvas) {
        // 同一支 paint 在锁屏上要先画"时间/日期"的半透明字，alpha 会一路残留到这里，
        // 结果白玻璃把手被画成一片中灰（实测 141,141,141）。每次进滑块先拉回 255。
        paint.alpha = 255
        // 滑块要跟着"屏底色"的明暗走，而不是无脑跟随机身色：
        // 暗黑/深色主题下用白玻璃 + 亮金，不然一整条黑压黑，看不清也拖不动；
        // 浅色主题下继续用深墨凹槽。
        val bgColor = Colors.background
        val bgLum = (0.299f * Color.red(bgColor) + 0.587f * Color.green(bgColor) +
                0.114f * Color.blue(bgColor)) / 255f
        val darkBg = bgLum < 0.5f
        val ink = if (darkBg) 0xFFFFFFFF.toInt() else Colors.ink_on_body
        val accentBase = Colors.main
        val accentLum = (0.299f * Color.red(accentBase) + 0.587f * Color.green(accentBase) +
                0.114f * Color.blue(accentBase)) / 255f
        // 主题主色太暗（暗黑主题的深红）时，换成一段亮金，暗底上才读得出来
        val accent = if (darkBg && accentLum < 0.46f) 0xFFE7C463.toInt() else accentBase
        val cy = trackY + trackH / 2f
        val r = trackH / 2f
        val travel = trackR - trackL - trackH
        val hx = trackL + r + travel * handleT

        // 1) 凹槽
        rect.set(trackL, trackY, trackR, trackY + trackH)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(if (darkBg) 26 else 48, Color.red(ink), Color.green(ink), Color.blue(ink))
        canvas.drawRoundRect(rect, r, r, paint)
        paint.shader = LinearGradient(
            0f, trackY, 0f, trackY + trackH,
            intArrayOf(
                Color.argb(if (darkBg) 34 else 58, Color.red(ink), Color.green(ink), Color.blue(ink)),
                Color.argb(if (darkBg) 6 else 12, Color.red(ink), Color.green(ink), Color.blue(ink))
            ),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, r, r, paint)
        paint.shader = null
        // 顶部一条 1px 亮线 + 底部一条极淡暗线，做出"凹"的错觉
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp * 1f
        paint.color = Color.argb(if (darkBg) 110 else 70, 255, 255, 255)
        canvas.drawRoundRect(rect, r, r, paint)
        paint.style = Paint.Style.FILL

        // 2) 进度段
        if (handleT > 0.004f) {
            rect.set(trackL, trackY, (hx + r * 0.4f).coerceAtMost(trackR), trackY + trackH)
            paint.shader = LinearGradient(
                trackL, 0f, hx + r, 0f,
                intArrayOf(
                    Color.argb((44 + 96 * handleT).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.argb((96 + 130 * handleT).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
                ),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, r, r, paint)
            paint.shader = null
        }

        // 3) 右端三重人字箭头：跑马灯式依次点亮
        val now = SystemClock.uptimeMillis()
        val phase = (now % 1500L) / 1500f
        for (i in 0..2) {
            val x = trackR - dp * 20f - i * dp * 15f
            val lit = (((phase + i * 0.22f) % 1f) < 0.5f)
            drawChevron(
                canvas, x, cy, dp * (11f + i * 1.6f),
                Color.argb(
                    if (lit) 235 else (if (darkBg) 104 else 92),
                    Color.red(accent), Color.green(accent), Color.blue(accent)
                )
            )
        }

        // 4) 把手：投影 → 拖动柔光 → 金属圆片 → 顶部高光弧 → 主题色圆芯
        val hr = dp * 20f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(56, 0, 0, 0)
        canvas.drawCircle(hx, cy + dp * 3.4f, hr + dp * 1.2f, paint)

        if (handleT > 0.02f) {
            paint.color = Color.argb((52 * handleT).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(hx, cy, hr + dp * 7f + dp * 3f * handleT, paint)
        }

        // 上一行 Color.argb(56,...) 会把 paint.alpha 一并改成 56，
        // 白色渐变圆片会被顺着稀释成中灰（实测 141,141,141），所以这里必须拉满
        paint.alpha = 255
        paint.shader = RadialGradient(
            hx - hr * 0.28f, cy - hr * 0.42f, hr * 1.5f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFF8F6F0.toInt(), 0xFFE6E2D6.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(hx, cy, hr, paint)
        paint.shader = null
        paint.alpha = 255

        // 上缘高光弧：一小段白色弧线，读起来像一颗有弧度的实体
        rect.set(hx - hr * 0.74f, cy - hr * 0.74f, hx + hr * 0.74f, cy + hr * 0.74f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp * 1.6f
        paint.color = Color.argb(215, 255, 255, 255)
        canvas.drawArc(rect, 202f, 132f, false, paint)
        // 外圈细边，压住金属边
        paint.strokeWidth = dp * 1f
        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(hx, cy, hr, paint)
        paint.style = Paint.Style.FILL

        // 圆芯：唱片那一点，拖动时从暗到亮
        paint.color = Color.argb(
            (96 + 140 * handleT).toInt(),
            Color.red(accent), Color.green(accent), Color.blue(accent)
        )
        canvas.drawCircle(hx, cy, hr * 0.29f, paint)

        // 5) 轨道上方的"滑动解锁"：只在还没拖动时给一句话，
        //    拖起来就慢慢淡掉，不跟进度条抢注意力
        val fade = (1f - handleT / 0.35f).coerceIn(0f, 1f)
        if (fade > 0.02f) {
            paint.style = Paint.Style.FILL
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp * 14f
            paint.alpha = 255
            paint.color = Color.argb(
                (fade * (if (darkBg) 168f else 132f)).toInt(),
                Color.red(ink), Color.green(ink), Color.blue(ink)
            )
            canvas.drawText("滑动解锁", (trackL + trackR) / 2f, trackY - dp * 13f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }

    /** 人字形箭头（朝右） */
    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val half = size / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp * 2.2f
        paint.color = color
        path.reset()
        path.moveTo(cx - half, cy - half * 0.62f)
        path.lineTo(cx + half * 0.55f, cy)
        path.lineTo(cx - half, cy + half * 0.62f)
        canvas.drawPath(path, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    /** 一把挂锁：圆角锁身 + 半圆锁梁（弧两端各补一小段竖线，锁梁才接得住锁身） */
    private fun drawPadlock(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val bodyW = dp * 34f
        val bodyH = dp * 26f
        val bodyTop = cy + dp * 6f
        paint.color = Color.argb(190, Color.red(color), Color.green(color), Color.blue(color))
        rect.set(cx - bodyW / 2f, bodyTop, cx + bodyW / 2f, bodyTop + bodyH)
        canvas.drawRoundRect(rect, dp * 7f, dp * 7f, paint)
        // 锁梁：上半圆 + 两段竖直延伸到锁身
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp * 3.6f
        val sh = dp * 11f
        rect.set(cx - sh, bodyTop - dp * 18f, cx + sh, bodyTop + dp * 4f)
        canvas.drawArc(rect, 180f, 180f, false, paint)
        val legTop = bodyTop - dp * 7f
        canvas.drawLine(cx - sh, legTop, cx - sh, bodyTop, paint)
        canvas.drawLine(cx + sh, legTop, cx + sh, bodyTop, paint)
        paint.style = Paint.Style.FILL
    }

    /** 右向箭头（横杆 + 人字），和动态岛那种"往这边"的方向感一致 */
    private fun drawArrow(canvas: Canvas, x: Float, y: Float, color: Int, alpha: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp * 2.6f
        paint.color = Color.argb((140 * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        path.reset()
        val len = dp * 26f
        val arm = dp * 8f
        path.moveTo(x - len, y)
        path.lineTo(x, y)
        path.moveTo(x - arm, y - arm)
        path.lineTo(x, y)
        path.lineTo(x - arm, y + arm)
        canvas.drawPath(path, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // android.util.Log.i("PodLock", "touch a=${e.actionMasked} x=${e.x} y=${e.y} trackY=$trackY dragging=$dragging t=$handleT finished=$finished")
        if (finished) {
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val cy = trackY + trackH / 2f
                if (e.y > trackY - dp * 44f && e.y < cy + trackH + dp * 44f) {
                    dragging = true
                    downX = e.x
                    downT = handleT
                    armTimeout() // 一旦上手，倒计时重来
                    return true
                }
                return super.onTouchEvent(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    return super.onTouchEvent(e)
                }
                val travel = trackR - trackL - trackH
                if (travel <= 0f) {
                    return true
                }
                handleT = (downT + (e.x - downX) / travel).coerceIn(0f, 1f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    return super.onTouchEvent(e)
                }
                dragging = false
                if (e.actionMasked == MotionEvent.ACTION_UP && handleT >= UNLOCK_RATIO) {
                    finishUnlock()
                } else {
                    springBack()
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun springBack() {
        val from = handleT
        val start = SystemClock.uptimeMillis()
        val dur = 220L
        handler.post(object : Runnable {
            override fun run() {
                val t = Math.min(1f, (SystemClock.uptimeMillis() - start).toFloat() / dur)
                val eased = DecelerateInterpolator().getInterpolation(t)
                handleT = from * (1f - eased)
                invalidate()
                if (t < 1f) {
                    handler.postDelayed(this, 16)
                } else {
                    handleT = 0f
                    invalidate()
                }
            }
        })
        armTimeout()
    }

    private fun finishUnlock() {
        finished = true
        handler.removeCallbacks(ticker)
        handleT = 1f
        invalidate()
        postDelayed({ onUnlock?.invoke() }, 130)
    }
}
