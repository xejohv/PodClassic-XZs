package io.github.xejohv.podclassicxzs.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import io.github.xejohv.podclassicxzs.util.ThreadUtil
import io.github.xejohv.podclassicxzs.util.VolumeUtil
import io.github.xejohv.podclassicxzs.values.Icons.MENU
import io.github.xejohv.podclassicxzs.values.Icons.NEXT
import io.github.xejohv.podclassicxzs.values.Icons.PAUSE
import io.github.xejohv.podclassicxzs.values.Icons.PREV
import io.github.xejohv.podclassicxzs.values.Values
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt


class SlideController : View {

    companion object {
        private const val SLIDE_VAL = 30
        // 红盘半径占滚动区短边的比例（越大盘越大）
        private const val RADIUS_RATIO = 0.90f
        // 四个按键图标中心到圆心的距离占半径的比例（与参考图一致：图标落在红盘内部靠近盘缘）
        private const val ICON_RATIO = 0.83f
        private var centerX = 0f
        private var centerY = 0f

        private var minR = 0f
        private var maxR = 0f

        // 图标位图自带透明留白，直接按位图尺寸定位会让“视觉间距”歪掉，这里取可见内容的包围盒
        private fun contentBounds(bm: android.graphics.Bitmap): IntArray {
            val w = bm.width
            val h = bm.height
            if (w <= 0 || h <= 0) return intArrayOf(0, 0, w, h)
            val px = IntArray(w * h)
            bm.getPixels(px, 0, w, 0, 0, w, h)
            var l = w
            var t = h
            var r = -1
            var b = -1
            for (y in 0 until h) {
                val base = y * w
                for (x in 0 until w) {
                    if ((px[base + x] ushr 24) > 8) {
                        if (x < l) l = x
                        if (x > r) r = x
                        if (y < t) t = y
                        if (y > b) b = y
                    }
                }
            }
            return if (r < 0) intArrayOf(0, 0, w, h) else intArrayOf(l, t, r + 1, b + 1)
        }

        private val boxMenu by lazy { contentBounds(MENU.bitmap) }
        private val boxPrev by lazy { contentBounds(PREV.bitmap) }
        private val boxNext by lazy { contentBounds(NEXT.bitmap) }
        private val boxPause by lazy { contentBounds(PAUSE.bitmap) }

    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attributeSet: AttributeSet?) : super(context, attributeSet)

    private val paint = Paint()

    var enable = true
        set(value) {
            if (!value) {
                cancelTimer()
            }
            field = value
        }

    var colorController: Int = Color.RED
    var colorButton: Int = Color.BLACK

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val x = MeasureSpec.getSize(widthMeasureSpec)
        val y = MeasureSpec.getSize(heightMeasureSpec)
        centerX = x / 2f
        centerY = y / 2f

        val center = min(centerX, centerY)

        // 红盘放大到接近滚动区满宽（参考图红盘直径约占屏幕宽度 72%），只留一点上下边距
        maxR = center * RADIUS_RATIO
        val edgeMargin = 8f * resources.displayMetrics.density
        if (maxR > center - edgeMargin) {
            maxR = center - edgeMargin
        }

        //maxR = min(maxR, center - Values.screenWidth / 12)

        /*
        when {
            MainActivity.screenRatio <= 17f / 9f && MainActivity.screenRatio >= 16f / 9f -> {
                maxR = maxR / 8 * 7
            }
            MainActivity.screenRatio > 1f / 2f -> {
                maxR = maxR / 4 * 3
            }
            MainActivity.screenRatio <= 1f / 2f && MainActivity.screenRatio > 17f / 9f -> {
                maxR = maxR / 6 * 5
            }
        }

         */

        minR = maxR / 16 * 5

        setMeasuredDimension(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.isAntiAlias = true
        paint.colorFilter = null   // 每帧先清空滤镜，避免上一帧的图标滤镜把盘面染黑
        paint.color = colorController//Colors.controller
        canvas.drawCircle(centerX, centerY, maxR, paint)
        paint.color = colorButton//Colors.color_primary
        canvas.drawCircle(centerX, centerY, minR, paint)

        val r = maxR

        // 四个按键都落在红盘内部、靠近盘缘（与参考图一致）：按“可见内容”居中，保证左右/上下对称
        val dIcon = r * ICON_RATIO

        // 白碟用深色图标、黑碟用白色图标，保证始终看得清
        val discLight = (Color.red(colorController) * 299 + Color.green(colorController) * 587 + Color.blue(colorController) * 114) / 1000
        val iconColor = if (discLight > 150) Color.rgb(26, 22, 12) else Color.WHITE
        paint.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)

        val menuW = boxMenu[2] - boxMenu[0]
        val menuH = boxMenu[3] - boxMenu[1]
        val pauseW = boxPause[2] - boxPause[0]
        val pauseH = boxPause[3] - boxPause[1]
        val prevW = boxPrev[2] - boxPrev[0]
        val prevH = boxPrev[3] - boxPrev[1]
        val nextW = boxNext[2] - boxNext[0]
        val nextH = boxNext[3] - boxNext[1]

        canvas.drawBitmap(
            MENU.bitmap,
            centerX - menuW / 2f - boxMenu[0],
            centerY - dIcon - menuH / 2f - boxMenu[1],
            paint
        )
        canvas.drawBitmap(
            PAUSE.bitmap,
            centerX - pauseW / 2f - boxPause[0],
            centerY + dIcon - pauseH / 2f - boxPause[1],
            paint
        )
        canvas.drawBitmap(
            PREV.bitmap,
            centerX - dIcon - prevW / 2f - boxPrev[0],
            centerY - prevH / 2f - boxPrev[1],
            paint
        )
        canvas.drawBitmap(
            NEXT.bitmap,
            centerX + dIcon - nextW / 2f - boxNext[0],
            centerY - nextH / 2f - boxNext[1],
            paint
        )

    }

    private var startPoint: TouchPoint =
        TouchPoint.emptyTouchPoint()
    private var prevPoint: TouchPoint =
        TouchPoint.emptyTouchPoint()
    private var touchTimer: Timer? = null

    private fun cancelTimer() {
        touchTimer?.cancel()
        touchTimer = null
    }

    private fun setTimer(curPoint: TouchPoint) {
        if (!enable) {
            return
        }
        val timerTask = object : TimerTask() {
            override fun run() {
                ThreadUtil.runOnUiThread(Runnable {
                    onTouch()
                    if (startPoint.slided) {
                        // 已在滚轮滑动中，这个定时器没有意义，直接停掉，避免每 600ms 反复触发回调
                        cancelTimer()
                        return@Runnable
                    }
                    if (startPoint.inCenter && curPoint.inCenter && (prevPoint.isEmpty() || prevPoint.inCenter)) {
                        onEnterLongClick()
                        cancelTimer()
                    } else if (startPoint.inCircle && curPoint.inCircle && (prevPoint.isEmpty() || prevPoint.inCircle)) {
                        when (startPoint.deg) {
                            in 45..135 -> onMenuLongClick()
                            in 135..225 -> onPrevLongClick()
                            in 225..315 -> onPauseLongClick()
                            in 315..360 -> onNextLongClick()
                            in 0..45 -> onNextLongClick()
                        }
                    }
                })
            }
        }
        cancelTimer()
        touchTimer = Timer().apply {
            schedule(timerTask, 500, 600)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || !enable) {
            // android.util.Log.d("SlideController", "onTouchEvent called: event null or not enabled")
            return super.onTouchEvent(event)
        }

        // android.util.Log.d("SlideController", "onTouchEvent called: action=${event.action}, x=${event.x}, y=${event.y}")

        val curPoint = TouchPoint(
            event.x,
            event.y,
            System.currentTimeMillis()
        )

        // android.util.Log.d("SlideController", "touch point: r=${curPoint.r}, maxR=$maxR")

        if (curPoint.r > maxR) {
            // 如果触摸点不在圆盘区域内，不处理事件，让事件传递给下面的控件
            // android.util.Log.d("SlideController", "touch outside disc, passing event down")
            return false
        }
        onTouch()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startPoint = curPoint
                prevPoint.clear()
                setTimer(curPoint)
            }

            MotionEvent.ACTION_MOVE -> {
                val slideVal = if (prevPoint.isEmpty()) TouchPoint.calcSlideVal(
                    startPoint,
                    curPoint
                ) else TouchPoint.calcSlideVal(
                    prevPoint,
                    curPoint
                )
                if (startPoint.inCircle && curPoint.inCircle) {
                    if (!prevPoint.isEmpty() && slideVal != 0) {
                        //如果slideVal = 0 不会调用slide方法
                        onSlide(slideVal)
                        startPoint.slided = true
                        cancelTimer()
                    }
                }
                prevPoint = curPoint
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelTimer()
                if (startPoint.slided) {
                    return true
                }
                if ((curPoint.t - startPoint.t) <= 400) {
                    if (startPoint.inCenter && curPoint.inCenter) {
                        onEnterClick()
                    } else if (startPoint.inCircle && curPoint.inCircle) {
                        when (startPoint.deg) {
                            in 45..135 -> {
                                onMenuClick()
                            }
                            in 135..225 -> {
                                onPrevClick()
                            }
                            in 225..315 -> {
                                onPauseClick()
                            }
                            in 315..360 -> {
                                onNextClick()
                            }
                            in 0..45 -> {
                                onNextClick()
                            }
                        }
                    }
                }
                startPoint.clear()
                prevPoint.clear()
            }
        }
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || !enable) {
            // android.util.Log.d("SlideController", "dispatchTouchEvent called: event null or not enabled")
            return super.dispatchTouchEvent(event)
        }

        // android.util.Log.d("SlideController", "dispatchTouchEvent called: action=${event.action}, x=${event.x}, y=${event.y}")
        val handled = super.dispatchTouchEvent(event)
        // android.util.Log.d("SlideController", "dispatchTouchEvent: $handled")
        return handled
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            cancelTimer()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelTimer()
    }

    class TouchPoint constructor(x: Float, y: Float, t: Long) {

        companion object {

            fun emptyTouchPoint(): TouchPoint {
                return TouchPoint(
                    0f,
                    0f,
                    0L
                )
            }

            /**
             * 大于0：next，等于0：未移动，小于0：prev
             */
            private fun calcSlideVal(prevDeg: Int, curDeg: Int): Int {
                val minus = prevDeg - curDeg
                if (abs(minus) >= SLIDE_VAL * 4 || minus == 0) {
                    return 0
                }
                if ((prevDeg == 360 - SLIDE_VAL || prevDeg == 360 - SLIDE_VAL * 2 || prevDeg == 360 - SLIDE_VAL * 3) && curDeg == 0) {
                    return -1
                } else if (prevDeg == 0 && (curDeg == 360 - SLIDE_VAL || curDeg == 360 - SLIDE_VAL * 2 || curDeg == 360 - SLIDE_VAL * 3)) {
                    return 1
                }
                return if (minus > 0) 1 else -1
            }

            fun calcSlideVal(prevPoint: TouchPoint, curPoint: TouchPoint): Int {

                return calcSlideVal(
                    prevPoint.deg,
                    curPoint.deg
                )
            }
        }


        var x = 0f
        var y = 0f
        var t = 0L
        var r = 0f
        var deg = 0

        var inCenter = false
        var inCircle = false
        var slided = false

        init {
            this.x = x - centerX
            this.y = -(y - centerY)
            this.t = t
            this.r = getR(this.x, this.y)
            this.deg = getDeg(this.x, this.y)

            inCenter = r >= 0 && r < minR
            inCircle = r in minR..maxR
        }

        fun clear() {
            x = 0f
            y = 0f
            t = 0L
            r = 0f
            deg = 0
            inCenter = false
            inCircle = false
            slided = false
        }

        private fun getR(x: Float, y: Float): Float {
            return sqrt(x * x + y * y)
        }

        private fun getDeg(x: Float, y: Float): Int {
            var deg: Int = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toInt()
            if (deg < 0) {
                deg += 360
            }
            deg /= SLIDE_VAL
            deg *= SLIDE_VAL
            return deg
        }

        fun isEmpty(): Boolean {
            return t == 0L
        }

    }

    private fun onEnterClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onEnterClick() == true) {
        }
    }

    private fun onEnterLongClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onEnterLongClick() == true) {
        }
    }

    private fun onMenuClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onMenuClick() == true) {
        }
    }

    private fun onMenuLongClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onMenuLongClick() == true) {
        }
    }

    private fun onPrevClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onPrevClick() == true) {
        }
    }

    private fun onPrevLongClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onPrevLongClick() == true) {
        }
    }

    private fun onNextClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onNextClick() == true) {
        }
    }

    private fun onNextLongClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onNextLongClick() == true) {
        }
    }

    private fun onPauseClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onPauseClick() == true) {
        }
    }

    private fun onPauseLongClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onPauseLongClick() == true) {
        }
    }

    private fun onSlide(slideVal: Int) {
        if (!enable) {
            return
        }
        val bOnSlide = onTouchListener?.onSlide(slideVal)
        if (bOnSlide == true) {
        }
    }

    private fun onTouch() {
        if (!enable) {
            return
        }
        onTouchListener?.onTouch()
    }


    var onTouchListener: OnTouchListener? = null

    interface OnTouchListener {
        fun onSlide(slideVal: Int) = false
        fun onEnterClick() = false
        fun onEnterLongClick() = false
        fun onPrevClick() = false
        fun onPrevLongClick() = false
        fun onNextClick() = false
        fun onNextLongClick() = false
        fun onMenuClick() = false
        fun onMenuLongClick() = false
        fun onPauseClick() = false
        fun onPauseLongClick() = false
        fun onTouch()
    }
}