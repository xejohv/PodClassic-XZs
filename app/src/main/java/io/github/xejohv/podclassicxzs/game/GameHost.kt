package io.github.xejohv.podclassicxzs.game

import io.github.xejohv.podclassicxzs.values.Colors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.widget.FrameLayout
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.util.MediaUtil
import io.github.xejohv.podclassicxzs.widget.Screen
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 迷你游戏基类：
 * - 画面上铺一层「当前播放歌曲的封面（模糊感）+ 金色蒙版」，游戏和正在播放的音乐是一体的
 * - 控制盘展开时游戏只能暂停，提示「下滑 放大画面并开始」；
 *   在画面上向下滑 → 控制盘折叠、画面占满整块屏、游戏开始；
 *   向上滑 → 控制盘恢复、游戏暂停
 * - 折叠后下方自带一条操作条，按钮由各游戏自己定制（不是所有游戏都用同一套键）
 * - 正在播放的曲目显示在顶部标题栏，游戏画面里不再出现音乐文字
 */
abstract class GameHost(context: Context, private val titleText: String) : FrameLayout(context), ScreenView {

    companion object {
        /** 一行均分（默认） */
        const val BAR_GRID = 0
        /** 手柄式：左侧十字方向（前 4 个按钮按 上/下/左/右 顺序）+ 右侧主键（第 5 个） */
        const val BAR_PAD = 1
        /** 不画操作条，画面占满屏底（用画面内自绘的控件代替） */
        const val BAR_NONE = 2

        private val BEST = HashMap<String, Int>()
        @JvmStatic
        protected val COLOR_BG: Int get() = Colors.gameBg
        @JvmStatic
        protected val COLOR_INK: Int get() = Colors.gameInk
        @JvmStatic
        protected val COLOR_ACCENT: Int get() = Colors.gameAccent
        @JvmStatic
        protected val COLOR_DEEP: Int get() = Colors.gameDeep
    }

    /** 操作条上的一个按钮：标签 + 点击动作 + 长按动作（可选） */
    protected class BarBtn(
        val label: String,
        val onClick: () -> Unit,
        val onLongClick: (() -> Unit)?,
        /** 自绘图标：0=文字，1=炸弹，2=暂停，3=方向键（方向由 label 决定） */
        val icon: Int = 0
    ) {
        /** 只有点击动作时，允许 BarBtn("◀") { ... } 这种写法 */
        constructor(label: String, onClick: () -> Unit) : this(label, onClick, null, 0)
        constructor(label: String, onClick: () -> Unit, icon: Int) : this(label, onClick, null, icon)
    }

    protected open fun barStyle(): Int = BAR_GRID

    protected val density: Float = resources.displayMetrics.density
    protected fun dp(v: Float): Float = v * density

    protected val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_INK
        textSize = dp(17f)
    }
    protected val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_INK
        textSize = dp(13f)
    }

    protected var running = false
    protected var started = false
    protected var gameOver = false
    protected var score = 0
    protected var best = BEST[titleText] ?: 0
    protected var extraLine: String = ""

    /** true = 控制盘已折叠、画面占满整块屏、游戏可运行 */
    protected var expanded = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var lastTick = 0L
    private var lastTrackId: Long? = null
    protected var trackTitle = ""
    protected var trackPlaying = false
    private var coverBlur: Bitmap? = null
    private var lastTitleSent = ""

    // 触摸状态
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var dragBaseX = 0f
    private var longPressRunnable: Runnable? = null
    private var longPressConsumed = false

    /** 玩过至少一局之后为 true，用来区分「第一次进来」和「中途暂停」 */
    protected var everStarted = false
        private set

    protected fun markStarted() {
        started = true
        everStarted = true
    }

    private val surface = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            drawFrame(canvas)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) {
                return
            }
            val now = System.currentTimeMillis()
            val dt = if (lastTick == 0L) 16f else (now - lastTick).toFloat()
            lastTick = now
            syncMusic()
            if (started && !gameOver && expanded) {
                update(dt.coerceAtMost(60f))
            }
            surface.invalidate()
            handler.postDelayed(this, 30)
        }
    }

    init {
        isClickable = true
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        lastTitleSent = titleText
    }

    /**
     * 这个 App 的 ScreenLayout 只给最后一个子 View 做 layout、不做 measure，
     * 所以必须自己把绘制子 View 铺满，否则它会是 0 尺寸、一个字都画不出来。
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        surface.layout(0, 0, width, height)
    }

    // ---------------- ScreenView ----------------

    override fun getTitle(): String? = if (trackTitle.isNotEmpty()) "♪ $trackTitle" else titleText

    override fun onViewAdd() {
        running = true
        lastTick = 0L
        resetGame()
        started = false
        everStarted = false
        gameOver = false
        expanded = false
        Core.setGameExpanded(false)
        syncMusic()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        surface.invalidate()
    }

    override fun onViewRemove() {
        running = false
        handler.removeCallbacks(ticker)
        Core.setGameExpanded(false)
        expanded = false
    }

    override fun onViewDelete() {
        running = false
        handler.removeCallbacks(ticker)
        Core.setGameExpanded(false)
        expanded = false
    }

    // 控制盘展开时游戏一律不响应，只能先下滑折叠
    override fun enter(): Boolean {
        if (!expanded) {
            return true
        }
        if (!started) {
            markStarted()
            resetGame()
            return true
        }
        if (gameOver) {
            gameOver = false
            markStarted()
            resetGame()
            return true
        }
        return onPrimary()
    }

    override fun enterLongClick(): Boolean {
        if (!expanded) {
            return true
        }
        if (!started) {
            markStarted()
            resetGame()
            return true
        }
        return onSecondary()
    }

    override fun slide(slideVal: Int): Boolean {
        if (!expanded || gameOver) {
            return true
        }
        if (!started) {
            markStarted()
        }
        return onSlide(slideVal)
    }

    // ---------------- 触摸：下滑折叠 / 上滑恢复 / 点击操作 ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragBaseX = event.x
                downTime = System.currentTimeMillis()
                longPressConsumed = false
                // 长按画面：给需要「短按=翻开、长按=插旗」之类的游戏用
                longPressRunnable?.let { handler.removeCallbacks(it) }
                val lp = Runnable {
                    if (expanded && started && !gameOver) {
                        longPressConsumed = onLongPressContent(downX, downY)
                    }
                }
                longPressRunnable = lp
                handler.postDelayed(lp, 460)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > dp(14f) || abs(event.y - downY) > dp(14f)) {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                }
                if (expanded && !gameOver) {
                    // 优先让游戏自己接管拖动（例如打砖块挡板 1:1 跟随手指）
                    if (onDragContent(event.x, event.y)) {
                        return true
                    }
                    val dx = event.x - dragBaseX
                    if (abs(dx) > dp(24f)) {
                        onSlide(if (dx > 0) 1 else -1)
                        dragBaseX = event.x
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                if (longPressConsumed) {
                    longPressConsumed = false
                    return true
                }
                val dy = event.y - downY
                val dx = event.x - downX
                val dt = System.currentTimeMillis() - downTime

                if (dy > dp(70f) && abs(dy) > abs(dx) * 1.4f) {
                    // 下滑：折叠控制盘、画面放大
                    if (!expanded) {
                        expanded = true
                        Core.setGameExpanded(true)
                        // 再补一帧，防止刚加入的 View 还没测量完导致折叠没生效
                        surface.postDelayed({
                            if (expanded) {
                                Core.setGameExpanded(true)
                            }
                        }, 260)
                        if (gameOver) {
                            gameOver = false
                            started = false
                            resetGame()
                        }
                        surface.invalidate()
                    }
                    return true
                }
                if (-dy > dp(70f) && abs(dy) > abs(dx) * 1.4f) {
                    // 上滑：控制盘恢复，游戏暂停
                    if (expanded) {
                        expanded = false
                        Core.setGameExpanded(false)
                        surface.invalidate()
                    }
                    return true
                }

                // 轻触
                if (abs(dx) < dp(14f) && abs(dy) < dp(14f)) {
                    if (!expanded) {
                        return true
                    }
                    if (!started) {
                        // 开局交给各游戏自己的起手动作（不等于按下主操作键）
                        markStarted()
                        onStart()
                        return true
                    }
                    if (gameOver) {
                        markStarted()
                        gameOver = false
                        resetGame()
                        onStart()
                        return true
                    }
                    // 先给画面区（点选格子之类），再给底部操作条
                    if (onTapContent(event.x, event.y)) {
                        return true
                    }
                    val idx = hitBar(event.x, event.y)
                    if (idx >= 0) {
                        val buttons = barButtons()
                        val btn = buttons.getOrNull(idx) ?: return true
                        if (dt > 450L && btn.onLongClick != null) {
                            btn.onLongClick.invoke()
                        } else {
                            btn.onClick.invoke()
                        }
                    }
                }
                return true
            }
        }
        return true
    }

    // ---------------- 子类实现 ----------------

    protected abstract fun resetGame()

    protected abstract fun update(dt: Float)

    protected abstract fun drawGame(canvas: Canvas, content: Rect)

    /** 控制盘「中键」：各游戏自己的主操作 */
    protected abstract fun onPrimary(): Boolean

    /** 开局时的起手动作，默认等同主操作键；需要区分的游戏自行覆写 */
    protected open fun onStart(): Boolean = onPrimary()

    protected abstract fun onSecondary(): Boolean

    protected abstract fun onSlide(slideVal: Int): Boolean

    /** 画面上（非操作条）横向拖动的钩子：返回 true 表示已接管（如挡板直接跟随手指） */
    protected open fun onDragContent(x: Float, y: Float): Boolean = false

    /** 画面上（非操作条）轻触的钩子，返回 true 表示已消费 */
    protected open fun onTapContent(x: Float, y: Float): Boolean = false

    /** 画面上（非操作条）长按的钩子，返回 true 表示已消费 */
    protected open fun onLongPressContent(x: Float, y: Float): Boolean = false

    private var defaultBar: List<BarBtn>? = null

    /** 底部操作条按钮，各游戏自己定制 */
    protected open fun barButtons(): List<BarBtn> {
        val cached = defaultBar
        if (cached != null) {
            return cached
        }
        val built = listOf(
            BarBtn("◀") { if (expanded && started && !gameOver) onSlide(-1) },
            BarBtn("●") { if (expanded && started && !gameOver) onPrimary() },
            BarBtn("▶") { if (expanded && started && !gameOver) onSlide(1) }
        )
        defaultBar = built
        return built
    }

    protected fun addScore(v: Int) {
        score += v
        if (score > best) {
            best = score
            BEST[titleText] = score
        }
    }

    protected fun finishGame() {
        gameOver = true
        if (score > best) {
            best = score
            BEST[titleText] = score
        }
    }

    /** 折叠后底部的自绘操作条高度 */
    private fun barHeight(): Float = dp(74f)

    /** 当前是否真的要画一条操作条（BAR_NONE 或没有按钮时都不留白） */
    protected fun hasBar(): Boolean = barStyle() != BAR_NONE && barButtons().isNotEmpty()

    private fun barTop(): Float = if (hasBar()) height - barHeight() else height.toFloat()

    private fun barRect(): RectF = RectF(0f, barTop(), width.toFloat(), height.toFloat())

    // 手柄式布局的几何（只在 BAR_PAD 时使用）
    private fun padCrossCX(): Float = width * 0.23f
    private fun padCrossCY(): Float = barTop() + (height - barTop()) / 2f
    private fun padMainCX(): Float = width * 0.775f
    private fun padMainR(): Float = dp(32f)

    /** -1 = 不在条上，否则是按钮序号 */
    private fun hitBar(x: Float, y: Float): Int {
        if (y < barTop()) {
            return -1
        }
        val n = barButtons().size
        if (n <= 0) {
            return -1
        }
        if (barStyle() == BAR_PAD) {
            // 十字区（左）/ 主键区（右），整块区域命中，拇指不用精确对正
            if (x >= width * 0.5f) {
                return if (n > 4) 4 else 3
            }
            val dx = x - padCrossCX()
            val dy = y - padCrossCY()
            val idx = if (abs(dx) > abs(dy)) {
                if (dx > 0) 3 else 2          // 右 / 左
            } else {
                if (dy > 0) 1 else 0          // 下 / 上
            }
            return idx.coerceIn(0, n - 1)
        }
        val slot = width.toFloat() / n
        val idx = (x / slot).toInt().coerceIn(0, n - 1)
        return idx
    }

    /** 内容区域（扣掉顶部 HUD 与底部操作条） */
    protected fun contentRect(): Rect {
        val top = dp(42f).toInt()
        val bottom = when {
            // 控制盘展开时不绘制操作条，底边只留给提示条，画面占满其余空间
            !expanded -> (height - dp(40f)).toInt()
            hasBar() -> (barTop() - dp(12f)).toInt()
            else -> height
        }
        return Rect(0, top, width, max(top + 1, bottom))
    }

    // ---------------- 音乐联动 ----------------

    private fun syncMusic() {
        // 优先读 MediaPresenter 的实时数据（后台服务推送过来的），
        // getCurrent() 依赖 MediaController 绑定，游戏界面里常常拿不到，会退回显示游戏名
        val liveMusic = try {
            MediaPresenter.music.get()
        } catch (e: Throwable) {
            null
        }
        val music = liveMusic ?: try {
            MediaPresenter.getCurrent()
        } catch (e: Throwable) {
            null
        }
        trackPlaying = try {
            MediaPresenter.isPlaying()
        } catch (e: Throwable) {
            false
        }
        if (music != null && music.id != lastTrackId) {
            lastTrackId = music.id
            trackTitle = music.title ?: ""
            try {
                val bmp = MediaUtil.getMusicImage(music)
                coverBlur = if (bmp == null) null else makeBlur(bmp)
            } catch (e: Throwable) {
                coverBlur = null
            }
        }
        // 曲目/播放状态变了就把标题栏同步一次（标题栏只显示曲名，不再显示游戏名）
        val t = getTitle() ?: ""
        if (t != lastTitleSent) {
            lastTitleSent = t
            var p: ViewParent? = parent
            while (p != null) {
                if (p is Screen) {
                    p.setTitle()
                    break
                }
                p = p.parent
            }
        }
    }

    private fun makeBlur(src: Bitmap): Bitmap {
        val smallW = 36
        val smallH = max(1, (src.height.toFloat() / src.width * smallW).toInt())
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val outW = 216
        val outH = max(1, (smallH.toFloat() / smallW * outW).toInt())
        return Bitmap.createScaledBitmap(small, outW, outH, true)
    }

    // ---------------- 绘制 ----------------

    private fun drawFrame(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) {
            return
        }

        fillPaint.shader = null
        fillPaint.color = COLOR_BG
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val blur = coverBlur
        // 只在控制盘展开时用封面做模糊底；全屏游戏时不铺音乐背景，保证画面干净
        if (blur != null && !expanded) {
            val src = Rect(0, 0, blur.width, blur.height)
            val scale = max(w.toFloat() / blur.width, h.toFloat() / blur.height)
            val cw = (blur.width * scale).toInt()
            val ch = (blur.height * scale).toInt()
            val dst = Rect((w - cw) / 2, (h - ch) / 2, (w - cw) / 2 + cw, (h - ch) / 2 + ch)
            fillPaint.alpha = 235
            canvas.drawBitmap(blur, src, dst, fillPaint)
            fillPaint.alpha = 255
            fillPaint.color = Color.argb(190, 232, 214, 156)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)
        }

        drawHud(canvas, w)

        val content = contentRect()
        // 游戏只允许画在内容区里，避免精灵压到顶部的分数
        canvas.save()
        canvas.clipRect(content)
        drawGame(canvas, content)
        canvas.restore()

        if (expanded) {
            drawBar(canvas, w)
        }

        if (!expanded) {
            // 控制盘展开时：画面留给游戏预览，提示收进底部一条，虚线把它与画面分开
            val contentBottom = height.toFloat()
            val tipTop = content.bottom.toFloat()
            if (!everStarted) {
                // 轻蒙版：预览保持看得清，不再把整块压成灰
                dimOverlay(canvas, content, 70)
                hudDivider(canvas, tipTop)
                fillPaint.color = Color.argb(150, 232, 214, 156)
                canvas.drawRect(0f, tipTop, width.toFloat(), contentBottom, fillPaint)
                textPaint.color = COLOR_DEEP
                drawCenteredFit(canvas, "下滑 全屏并开始游戏", tipTop + dp(26f), 17f, textPaint)
            } else {
                // 中途暂停：画面 100% 保持原样，左上角角标 + 底部一条细提示
                drawPauseBadge(canvas)
                hudDivider(canvas, tipTop)
                fillPaint.color = Color.argb(150, 232, 214, 156)
                canvas.drawRect(0f, tipTop, width.toFloat(), contentBottom, fillPaint)
                textPaint.color = COLOR_DEEP
                drawCenteredFit(canvas, "上滑 恢复控制盘 · 已暂停", tipTop + dp(26f), 14f, textPaint)
            }
        } else if (!started && startHint().isNotEmpty()) {
            // 全屏但还没开局：操作条上方一条窄提示，虚线分隔
            val bottom = barTop()
            hudDivider(canvas, bottom - dp(38f))
            fillPaint.color = Color.argb(120, 232, 214, 156)
            canvas.drawRect(0f, bottom - dp(38f), width.toFloat(), bottom, fillPaint)
            textPaint.color = COLOR_DEEP
            drawCenteredFit(canvas, startHint(), bottom - dp(12f), 14f, textPaint)
        } else if (gameOver) {
            // 结束：只压一层很淡的遮罩，得分与提示都落在操作条上方，不盖棋盘中心
            dimOverlay(canvas, content, 90)
            textPaint.color = COLOR_DEEP
            val bottom = barTop()
            drawCenteredFit(canvas, "游戏结束 · 得分 $score", bottom - dp(48f), 20f, textPaint)
            drawCenteredFit(canvas, if (hasBar()) "点击主键 重新开始 · 上滑 恢复控制盘" else "上滑 恢复控制盘", bottom - dp(24f), 13f, textPaint)
        }
    }

    /** 暂停角标：左上角一小块深色圆角，不铺整屏，不挡游戏画面 */
    private fun drawPauseBadge(canvas: Canvas) {
        val h = dp(34f)
        val w = dp(96f)
        val left = dp(16f)
        val top = contentTopBadge()
        fillPaint.color = Color.argb(230, 36, 30, 16)
        canvas.drawRoundRect(RectF(left, top, left + w, top + h), dp(8f), dp(8f), fillPaint)
        textPaint.color = 0xFFF0E2B4.toInt()
        textPaint.textSize = dp(15f)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("已暂停", left + w / 2f, top + h * 0.66f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun contentTopBadge(): Float = dp(58f)

    /** 画面与提示条之间的虚线分隔，和打炮 HUD 的处理保持一致 */
    private fun hudDivider(canvas: Canvas, y: Float) {
        fillPaint.color = Color.argb(96, 46, 37, 20)
        val dashW = dp(12f)
        val gap = dp(8f)
        val rightEdge = width - dp(14f)
        var x = dp(14f)
        while (x < rightEdge) {
            canvas.drawRect(x, y, minOf(x + dashW, rightEdge), y + dp(1.4f), fillPaint)
            x += dashW + gap
        }
    }

    private fun dimOverlay(canvas: Canvas, content: Rect, alpha: Int) {
        fillPaint.color = Color.argb(alpha, 232, 214, 156)
        canvas.drawRect(0f, content.top.toFloat(), width.toFloat(), content.bottom.toFloat(), fillPaint)
    }

    private fun drawBar(canvas: Canvas, w: Int) {
        if (!hasBar()) {
            return
        }
        fillPaint.color = Color.argb(232, 0, 0, 0)
        canvas.drawRect(barRect(), fillPaint)

        val buttons = barButtons()
        val top = barTop()
        val cy = top + (height.toFloat() - top) / 2f

        if (barStyle() == BAR_PAD) {
            drawPad(canvas, buttons)
            return
        }

        val slot = w.toFloat() / buttons.size
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        for (i in buttons.indices) {
            val cx = slot * (i + 0.5f)
            val btn = buttons[i]
            if (btn.icon != 0) {
                drawBarIcon(canvas, btn.icon, cx, cy, dp(17f))
            } else {
                val label = btn.label
                textPaint.textSize = dp(if (label.length <= 1) 30f else 17f)
                canvas.drawText(label, cx, cy + textPaint.textSize * 0.36f, textPaint)
            }
            // 细竖分隔线，弱化处理，不抢画面
            if (i > 0) {
                fillPaint.color = Color.argb(90, 232, 214, 156)
                canvas.drawRect(cx - slot / 2f - dp(0.5f), cy - dp(18f), cx - slot / 2f + dp(0.5f), cy + dp(18f), fillPaint)
            }
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    /** 手柄式操作条：左侧十字方向 + 右侧圆形主键 */
    private fun drawPad(canvas: Canvas, buttons: List<BarBtn>) {
        val cx = padCrossCX()
        val cy = padCrossCY()
        val r = dp(17f)
        val gap = dp(20f)

        // 十字底板（弱化，提示这是一整块方向区）
        fillPaint.color = Color.argb(46, 232, 214, 156)
        canvas.drawRoundRect(
            RectF(cx - gap - r - dp(4f), cy - gap - r - dp(4f), cx + gap + r + dp(4f), cy + gap + r + dp(4f)),
            dp(10f), dp(10f), fillPaint
        )

        val dirs = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0) // 上/下/左/右
        for (i in 0 until min(4, buttons.size)) {
            val bx = cx + dirs[i].first * gap
            val by = cy + dirs[i].second * gap
            fillPaint.color = Color.argb(226, 232, 214, 156)
            canvas.drawRoundRect(RectF(bx - r, by - r, bx + r, by + r), dp(6f), dp(6f), fillPaint)
            drawArrow(canvas, bx, by, dp(11f), dirs[i].first, dirs[i].second)
        }

        // 右侧主键
        val mx = padMainCX()
        val mr = padMainR()
        fillPaint.color = Color.argb(230, 232, 214, 156)
        canvas.drawCircle(mx, cy, mr, fillPaint)
        val mainBtn = buttons.getOrNull(4)
        when (mainBtn?.icon) {
            1 -> drawBomb(canvas, mx, cy, mr * 0.56f)
            2 -> drawPause(canvas, mx, cy, mr * 0.52f)
            else -> {
                textPaint.color = COLOR_DEEP
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = dp(22f)
                canvas.drawText(mainBtn?.label ?: "●", mx, cy + dp(8f), textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
        }
    }

    /** 方向箭头（不依赖字体，避免 ◀▶ 缺字形） */
    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, size: Float, dx: Int, dy: Int) {
        val p = android.graphics.Path()
        if (dx != 0) {
            p.moveTo(cx + dx * size, cy)
            p.lineTo(cx - dx * size * 0.55f, cy - size)
            p.lineTo(cx - dx * size * 0.55f, cy + size)
        } else {
            p.moveTo(cx, cy + dy * size)
            p.lineTo(cx - size, cy - dy * size * 0.55f)
            p.lineTo(cx + size, cy - dy * size * 0.55f)
        }
        fillPaint.color = COLOR_DEEP
        canvas.drawPath(p, fillPaint)
    }

    /** 暂停图标：两条竖条 */
    private fun drawPause(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        fillPaint.color = COLOR_DEEP
        val bw = size * 0.34f
        val bh = size * 1.5f
        canvas.drawRect(cx - size * 0.55f, cy - bh / 2f, cx - size * 0.55f + bw, cy + bh / 2f, fillPaint)
        canvas.drawRect(cx + size * 0.21f, cy - bh / 2f, cx + size * 0.21f + bw, cy + bh / 2f, fillPaint)
    }

    /** 炸弹图标：圆身 + 引线 + 火花，解决「中键是什么」看不懂的问题 */
    private fun drawBomb(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        fillPaint.color = COLOR_DEEP
        canvas.drawCircle(cx, cy + r * 0.18f, r, fillPaint)
        // 引线
        canvas.drawLine(cx + r * 0.62f, cy - r * 0.42f, cx + r * 1.05f, cy - r * 1.05f, fillPaint)
        // 火花
        fillPaint.color = 0xFFE8D69C.toInt()
        canvas.drawCircle(cx + r * 1.08f, cy - r * 1.12f, r * 0.26f, fillPaint)
        // 高光
        fillPaint.color = Color.argb(70, 232, 214, 156)
        canvas.drawCircle(cx - r * 0.3f, cy - r * 0.1f, r * 0.33f, fillPaint)
    }

    /** 通用小图标绘制（供 GRID 布局里的功能键使用） */
    private fun drawBarIcon(canvas: Canvas, icon: Int, cx: Float, cy: Float, size: Float) {
        when (icon) {
            1 -> drawBomb(canvas, cx, cy, size)
            2 -> drawPause(canvas, cx, cy, size * 0.8f)
        }
    }

    /** 折叠后「还没开局」时的提示，子类可覆写成自己的操作说明 */
    protected open fun startHint(): String = "点击操作条中键 开始游戏"

    private fun drawHud(canvas: Canvas, w: Int) {
        // 游戏画面里不放任何音乐元素，也不放游戏名：
        // 控制盘展开时顶部标题栏已经有标题，全屏时画面越干净越好，只在右侧保留分数。
        textPaint.color = COLOR_INK
        textPaint.textSize = dp(15f)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("分数 $score  最高 $best", w - dp(24f), dp(26f), textPaint)

        // 第二行只放游戏自己的状态（消行 / 长度 / 生命）
        if (extraLine.isNotEmpty()) {
            smallPaint.color = COLOR_INK
            smallPaint.textSize = dp(13f)
            smallPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(fitText(extraLine, w - dp(48f), smallPaint), dp(24f), dp(46f), smallPaint)
        }
    }

    /** 居中绘制，并按可用宽度自动缩小字号，避免长提示被屏幕切掉 */
    private fun drawCenteredFit(canvas: Canvas, text: String, cy: Float, sizeSp: Float, paint: Paint) {
        var size = dp(sizeSp)
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        val maxWidth = width - dp(48f)
        while (size > dp(11f) && paint.measureText(text) > maxWidth) {
            size -= dp(1f)
            paint.textSize = size
        }
        canvas.drawText(text, width / 2f, cy, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /** 超长文字按宽度截断并补省略号，避免和右边的分数撞在一起 */
    protected fun fitText(text: String, maxWidth: Float, paint: Paint): String {
        if (text.isEmpty() || paint.measureText(text) <= maxWidth) {
            return text
        }
        val tail = "…"
        var len = text.length
        while (len > 1 && paint.measureText(text.substring(0, len) + tail) > maxWidth) {
            len--
        }
        return text.substring(0, len) + tail
    }
}
