package io.github.xejohv.podclassicxzs.game

import io.github.xejohv.podclassicxzs.values.Colors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.widget.FrameLayout
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.storage.SPManager
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
        protected val COLOR_INK_SOFT: Int get() = Colors.gameInkSoft
        @JvmStatic
        protected val COLOR_ACCENT: Int get() = Colors.gameAccent
        @JvmStatic
        protected val COLOR_DEEP: Int get() = Colors.gameDeep
        @JvmStatic
        protected val COLOR_PAPER: Int get() = Colors.gamePaper
        @JvmStatic
        protected val COLOR_LINE: Int get() = Colors.gameLine

        /** 游戏面板上的深墨键帽（浅底上用深色键，字反过来是白的） */
        private val KEY_FACE = 0xFF26261F.toInt()
        private val KEY_EDGE = 0xFF44443C.toInt()
        private val KEY_INK = 0xFFF7F6EF.toInt()
        /** 提示胶囊：深墨底 + 米白字，放在浅色游戏画面上永远读得清 */
        private val TIP_BG = 0xF21B1B17.toInt()
        private val TIP_INK = 0xFFF7F4E8.toInt()
        private val TIP_SUB = 0xC0ECE7D6.toInt()
    }

    /** 操作条上的一个按钮：标签 + 点击动作 + 长按动作（可选）+ 按住重复（可选，连发用） */
    protected class BarBtn(
        val label: String,
        val onClick: () -> Unit,
        val onLongClick: (() -> Unit)?,
        /** 自绘图标：0=文字，1=炸弹，2=暂停，3=方向键（方向由 label 决定），5=直落 */
        val icon: Int = 0,
        /**
         * 按住不放时持续触发的动作：做俄罗斯方块的"软降"这类连发用。
         * 按下先走一次 onClick，停顿 260ms 后每 80ms 调一次这里，松手即停。
         */
        val onHold: (() -> Unit)? = null
    ) {
        /** 只有点击动作时，允许 BarBtn("◀") { ... } 这种写法 */
        constructor(label: String, onClick: () -> Unit) : this(label, onClick, null, 0)
        constructor(label: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) :
                this(label, onClick, onLongClick, 0)
        constructor(label: String, onClick: () -> Unit, icon: Int) : this(label, onClick, null, icon)
        /** 按住连发 + 主键图标 */
        constructor(label: String, onClick: () -> Unit, onHold: (() -> Unit)?, icon: Int) :
                this(label, onClick, null, icon, onHold)
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

    // ---------------- 存档：误触退出回来不让这一局白打 ----------------

    /** 上一局还没打完、这一局是被"中途退出"出来的 */
    protected var hasResume = false
        private set

    /** 子类是否支持存档：实现了 onSaveState / onRestoreState 的覆写成 true */
    protected open fun supportsSave(): Boolean = false

    /** 把当前局面序列化成一行字符串（自己定义分隔符，别用竖线以外容易撞的字符） */
    protected open fun onSaveState(): String? = null

    /** 从存档恢复局面 */
    protected open fun onRestoreState(data: String) {}

    private fun saveSlot(): String = "game_save_$titleText"

    /**
     * 只有"已经开局、还没结束"才落盘。
     * 不再要求 expanded：玩家常常先上滑收起控制盘（等于暂停），再按返回退出，
     * 这时如果只认全屏态，存档就丢了。
     */
    protected fun persistSave() {
        if (!supportsSave()) return
        if (!started || gameOver) return
        val data = onSaveState() ?: return
        try {
            SPManager.setString(saveSlot(), data)
        } catch (e: Throwable) {
            // 存档失败不该影响游戏本身
        }
    }

    protected fun clearSave() {
        if (!supportsSave()) return
        try {
            SPManager.setString(saveSlot(), null)
        } catch (e: Throwable) {
        }
    }

    private fun readSave(): String? {
        if (!supportsSave()) return null
        val raw = try {
            SPManager.getString(saveSlot())
        } catch (e: Throwable) {
            null
        }
        // SPManager.getString 的默认值是空串（不是 null），不判空会被当成
        // "有一份内容为空"的存档，反序列化时抛异常又清掉，等于存档白存。
        return if (raw.isNullOrEmpty()) null else raw
    }

    /** 进入时把存档读回来（局面还原但停在折叠态，等玩家选继续还是新局） */
    private fun loadSaveIfAny() {
        hasResume = false
        val data = readSave() ?: return
        try {
            onRestoreState(data)
            hasResume = true
        } catch (e: Throwable) {
            hasResume = false
            clearSave()
        }
    }

    /** 点「继续」：直接进全屏，接着上次的局面打 */
    private fun resumeGame() {
        hasResume = false
        started = true
        everStarted = true
        gameOver = false
        lastTick = 0L
        expanded = true
        Core.setGameExpanded(true)
        surface.postDelayed({
            if (expanded) {
                Core.setGameExpanded(true)
            }
        }, 260)
        surface.invalidate()
    }

    /** 点「新局」：丢掉存档，回到"点一下开始"的干净状态 */
    private fun restartFromPrompt() {
        hasResume = false
        clearSave()
        started = false
        everStarted = false
        gameOver = false
        resetGame()
        surface.invalidate()
    }

    /** 折叠态底部「继续 / 新局」两个按钮的命中区，drawResumeBar 里算一次 */
    private val resumeBtn = RectF()
    private val freshBtn = RectF()

    private fun drawResumeBar(canvas: Canvas, areaTop: Float, areaBottom: Float) {
        val w = width.toFloat()
        val h = (areaBottom - areaTop).coerceAtLeast(dp(44f))
        val btnH = min(h - dp(8f), dp(36f))
        val cy = (areaTop + areaBottom) / 2f
        val gap = dp(12f)
        val bw1 = dp(112f)
        val bw2 = dp(96f)
        val total = bw1 + gap + bw2
        val l = (w - total) / 2f
        val t = cy - btnH / 2f
        val b = cy + btnH / 2f
        resumeBtn.set(l, t, l + bw1, b)
        freshBtn.set(l + bw1 + gap, t, l + bw1 + gap + bw2, b)

        // 同帧前面的 dimOverlay 会把 fillPaint 的 alpha 压到 54/70，
        // 这里不重置，"继续上局"的深墨实底就会被稀释成一片灰
        fillPaint.alpha = 255
        textPaint.alpha = 255
        fillPaint.style = Paint.Style.FILL

        // 继续：实心深墨胶囊 + 米白字（主操作）
        fillPaint.shader = LinearGradient(
            0f, t, 0f, b,
            intArrayOf(0xFF2C2C24.toInt(), 0xFF1B1B17.toInt()),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(resumeBtn, btnH / 2f, btnH / 2f, fillPaint)
        fillPaint.shader = null
        textPaint.color = TIP_INK
        textPaint.textSize = dp(14f)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            "继续上局", resumeBtn.centerX(), cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint
        )

        // 新局：描边胶囊 + 深字（次要操作，位置分开避免误触）
        fillPaint.color = Color.argb(200, 255, 255, 254)
        canvas.drawRoundRect(freshBtn, btnH / 2f, btnH / 2f, fillPaint)
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(1.2f)
        fillPaint.color = Color.argb(110, 31, 31, 31)
        canvas.drawRoundRect(freshBtn, btnH / 2f, btnH / 2f, fillPaint)
        fillPaint.style = Paint.Style.FILL
        textPaint.color = COLOR_INK
        canvas.drawText(
            "新游戏", freshBtn.centerX(), cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint
        )
        textPaint.textAlign = Paint.Align.LEFT
    }

    /** 全屏态下往下甩一把：俄罗斯方块接成"直落"，默认什么都不做 */
    protected open fun onFlickDown(): Boolean = false

    // ---------------- 按住连发 ----------------

    private var holdIndex = -1
    private var holdRunnable: Runnable? = null

    private fun stopHold() {
        holdRunnable?.let { handler.removeCallbacks(it) }
        holdRunnable = null
        holdIndex = -1
    }

    /** 长按发出后 190ms 开始，每 60ms 一次；用来做俄罗斯方块的软降连发 */
    private fun startHold(idx: Int, btn: BarBtn) {
        val repeat = btn.onHold ?: return
        holdIndex = idx
        btn.onClick.invoke()
        val r = object : Runnable {
            override fun run() {
                if (!running || !expanded || gameOver || holdIndex != idx) {
                    return
                }
                repeat.invoke()
                handler.postDelayed(this, 60)
            }
        }
        holdRunnable = r
        handler.postDelayed(r, 260)
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
        // 进来先看有没有上一局的存档：有就还原局面、停在折叠态，等玩家点「继续 / 新局」
        hasResume = false
        loadSaveIfAny()
        syncMusic()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        surface.invalidate()
    }

    override fun onViewRemove() {
        // 中途退出：这一局的状态先落盘，回来还能接着打
        persistSave()
        stopHold()
        running = false
        handler.removeCallbacks(ticker)
        Core.setGameExpanded(false)
        expanded = false
    }

    override fun onViewDelete() {
        persistSave()
        stopHold()
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
        if (gameOver) {
            return true
        }
        if (!started) {
            // 还没开局时，左右滑先交给子类（例如贪吃蛇在这里切难度），别直接开局
            if (slideBeforeStart(slideVal)) {
                return true
            }
            // 折叠态（控制盘还展着）只负责选难度，不能顺手开局
            if (!expanded) {
                return true
            }
            markStarted()
        }
        if (!expanded) {
            return true
        }
        return onSlide(slideVal)
    }

    /** 未开局时左右滑的钩子：返回 true 表示已消费（贪吃蛇用来切换难度） */
    protected open fun slideBeforeStart(slideVal: Int): Boolean = false

    /** 折叠态是否显示「‹ › 难度」小键 */
    protected open fun hasLevelSwitch(): Boolean = false

    /** 点「‹ › 难度」小键时回调，dir=-1 上一档、+1 下一档 */
    protected open fun cycleLevel(dir: Int) {}

    // ---------------- 触摸：下滑折叠 / 上滑恢复 / 点击操作 ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragBaseX = event.x
                downTime = System.currentTimeMillis()
                longPressConsumed = false
                onContentDown(event.x, event.y)
                // 按住连发：操作条上的软降键按下去就一路降，松手即停
                if (expanded && started && !gameOver) {
                    val idx = hitBar(event.x, event.y)
                    val btn = if (idx >= 0) barButtons().getOrNull(idx) else null
                    if (btn != null && btn.onHold != null) {
                        startHold(idx, btn)
                        return true
                    }
                }
                longPressRunnable?.let { handler.removeCallbacks(it) }
                // 长按画面：给需要「短按=翻开、长按=插旗」之类的游戏用
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

            MotionEvent.ACTION_CANCEL -> {
                stopHold()
                longPressRunnable?.let { handler.removeCallbacks(it) }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (holdIndex >= 0 || holdRunnable != null) {
                    stopHold()
                    return true
                }
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
                        if (hasResume) {
                            // 有上一局存档：下滑就是"继续上局"，不再从零开始
                            resumeGame()
                            return true
                        }
                        expanded = true
                        Core.setGameExpanded(true)
                        // 再补一帧，防止刚加入的 View 还没测量完导致折叠没生效
                        surface.postDelayed({
                            if (expanded) {
                                Core.setGameExpanded(true)
                            }
                        }, 260)
                        if (gameOver) {
                            clearSave()
                            gameOver = false
                            started = false
                            resetGame()
                        }
                        // 「下滑全屏并开始」就要真的开始：别让玩家下滑完还停在静止画面
                        if (!started) {
                            markStarted()
                            onStart()
                        }
                        surface.invalidate()
                    } else {
                        // 已经在全屏：这一下往下的甩动交给游戏自己用。
                        // 俄罗斯方块在这里接成"直落"，跟手机版俄罗斯方块的手感一致，
                        // 不然全屏时往下甩一下就白滑了，玩家只会觉得手势不好用。
                        onFlickDown()
                    }
                    return true
                }
                if (-dy > dp(70f) && abs(dy) > abs(dx) * 1.4f) {
                    // 上滑不再折叠回控制盘：折起来露出的就是那块"音乐控制屏"，
                    // 全屏玩着的时候误触一下画面就被切断，太伤。退出统一走侧滑/返回键。
                    return true
                }

                // 轻触
                if (abs(dx) < dp(14f) && abs(dy) < dp(14f)) {
                    if (!expanded) {
                        if (hasResume) {
                            // 折叠态底部的「继续上局 / 新游戏」两个胶囊
                            if (resumeBtn.contains(event.x, event.y)) {
                                resumeGame()
                            } else if (freshBtn.contains(event.x, event.y)) {
                                restartFromPrompt()
                            }
                            return true
                        }
                        if (hasLevelSwitch()) {
                            if (levelLeftBtn.contains(event.x, event.y)) {
                                cycleLevel(-1)
                                surface.invalidate()
                                return true
                            }
                            if (levelRightBtn.contains(event.x, event.y)) {
                                cycleLevel(1)
                                surface.invalidate()
                                return true
                            }
                        }
                        return true
                    }
                    if (!started) {
                        // 开局交给各游戏自己的起手动作（不等于按下主操作键）
                        markStarted()
                        onStart()
                        return true
                    }
                    if (gameOver) {
                        clearSave()
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
                    val hit = hitBar(event.x, event.y)
                    if (hit >= 0) {
                        val buttons = barButtons()
                        val btn = buttons.getOrNull(hit) ?: return true
                        if (btn.onHold != null) {
                            // 连发键的短按走 onClick（DOWN 已经触发过一次，这里不再重复触发降格）
                            return true
                        }
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

    /** 画面（非操作条）按下时通知一次：给"相对位移拖动"记录锚点，不影响手势判定 */
    protected open fun onContentDown(x: Float, y: Float) {}

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
        // 这一局已经结束，存档作废：下次进来不该提示"继续上一局"
        clearSave()
    }

    /** 折叠后底部的自绘操作条高度：手柄式给足拇指空间，键盘式略矮 */
    private fun barHeight(): Float = if (barStyle() == BAR_PAD) dp(120f) else dp(84f)

    /** 当前是否真的要画一条操作条（BAR_NONE 或没有按钮时都不留白） */
    protected fun hasBar(): Boolean = barStyle() != BAR_NONE && barButtons().isNotEmpty()

    private fun barTop(): Float = if (hasBar()) height - barHeight() else height.toFloat()

    private fun barRect(): RectF = RectF(0f, barTop(), width.toFloat(), height.toFloat())

    // 手柄式布局的几何（只在 BAR_PAD 时使用）
    private fun padCrossCX(): Float = width * 0.23f
    private fun padCrossCY(): Float = barTop() + dp(9f) + (height - barTop() - dp(11f)) / 2f
    private fun padMainCX(): Float = width * 0.775f
    private fun padMainR(): Float = dp(48f)

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
            return idx.coerceIn(0, 3)
        }
        val slot = width.toFloat() / n
        val idx = (x / slot).toInt().coerceIn(0, n - 1)
        return idx
    }

    /** 内容区域（扣掉顶部 HUD 与底部操作条） */
    protected fun contentRect(): Rect {
        // HUD 占两行（分数 / 游戏状态），画面从它下面才开始，避免文字压在棋盘或敌机上
        val top = dp(54f).toInt()
        val bottom = when {
            // 控制盘展开时不绘制操作条，底边只留给提示条，画面占满其余空间
            !expanded -> (height - dp(52f)).toInt()
            // 全屏：画面直接顶到操作条上沿，不再留 12dp 空白，游戏区因此整体下移铺满
            hasBar() -> barTop().toInt()
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

        // 每帧开头把画笔状态拉回干净值：上一轮 dimOverlay 之类设过的 alpha / 线宽
        // 会残留在同一支 Paint 上，导致后面的实心胶囊、描边键被凭空稀释成灰
        fillPaint.alpha = 255
        textPaint.alpha = 255
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = null
        fillPaint.color = COLOR_BG
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        // 顶栏留一条极淡的机身色，让游戏屏跟外壳有一点呼应；不再铺"音乐封面 + 金蒙版"，
        // 那层东西在浅色 LCD 上只会把画面弄脏，也让暗色主题下的对比度彻底崩掉
        if (!expanded) {
            fillPaint.shader = LinearGradient(
                0f, 0f, 0f, h * 0.16f,
                intArrayOf(Colors.body_light, Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            fillPaint.alpha = 92
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.16f, fillPaint)
            fillPaint.alpha = 255
            fillPaint.shader = null
        }

        drawHud(canvas, w)

        // 全屏还没开局时，底部留出一条提示区：提示不压在棋盘/画面上，字也看得清
        hintReservePx = if (expanded && !started && startHint().isNotEmpty()) dp(92f) else 0f
        val content = playRect()
        val fullContent = contentRect()
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
            if (!everStarted && hasResume) {
                // 上一局存档在手：给「继续上局 / 新游戏」两个选择，别直接清零
                dimOverlay(canvas, content, 54)
                drawResumeBar(canvas, tipTop, contentBottom)
            } else if (!everStarted) {
                // 轻蒙版：预览保持看得清，不再把整块压成灰
                dimOverlay(canvas, content, 70)
                // 支持难度切换的游戏：胶囊收窄，左右各留一个「‹ ›」小键，
                // 触屏上比"左右滑圆盘"更好发现，也更不容易和切歌手势搞混
                tipMaxFrac = if (hasLevelSwitch()) 0.56f else 0.92f
                val tipRect = drawTipCapsule(canvas, tipTop, contentBottom, "下滑全屏并开始", "", true, true)
                tipMaxFrac = 0.92f
                if (hasLevelSwitch()) {
                    drawLevelKeys(canvas, tipRect, (tipTop + contentBottom) / 2f)
                }
            } else {
                // 中途暂停：画面 100% 保持原样，左上角角标 + 底部一条提示
                drawPauseBadge(canvas)
                drawTipCapsule(canvas, tipTop, contentBottom, "已暂停 · 侧滑退出", "", false, false)
            }
        } else if (hintReservePx > 0f) {
            // 全屏未开局：磨砂胶囊放在画面下方的专用提示区，不再直接压在棋盘上
            drawTipCapsule(
                canvas,
                content.bottom + dp(4f),
                fullContent.bottom - dp(2f),
                startHint(),
                startHintSub(),
                true,
                true
            )
        } else if (gameOver) {
            drawGameOverCard(canvas, content)
        }
    }

    /** 底部提示区预留高度，0 = 当前没有提示条 */
    private var hintReservePx = 0f

    /** 真正用来画游戏的区域（已经从 contentRect 里扣掉底部提示区） */
    protected fun playRect(): Rect {
        val c = contentRect()
        if (hintReservePx <= 0f) {
            return c
        }
        return Rect(c.left, c.top, c.right, max(c.top + 1, (c.bottom - hintReservePx).toInt()))
    }


    /** 结束卡片：深墨玻璃卡 + 金色内描边 + 三段信息，替代原来那条粗糙的深棕横条 */
    private fun drawGameOverCard(canvas: Canvas, content: Rect) {
        dimOverlay(canvas, content, 104)
        val cw = width * 0.84f
        val l = (width - cw) / 2f
        val r = l + cw
        val cy = content.top + content.height() * 0.46f
        val t = cy - dp(78f)
        val b = cy + dp(78f)
        val rad = dp(18f)

        // 落地阴影
        fillPaint.shader = null
        fillPaint.color = Color.argb(86, 0, 0, 0)
        canvas.drawRoundRect(RectF(l + dp(1f), t + dp(7f), r + dp(1f), b + dp(7f)), rad, rad, fillPaint)

        // 卡片本体：上浅下深的竖向渐变，像一块压在屏上的深色玻璃
        // 注意：阴影那一步把 paint 的 alpha 留在了 86，渐变会被整体乘上半透明，棋盘数字会透上来
        fillPaint.alpha = 255
        fillPaint.shader = LinearGradient(
            0f, t, 0f, b,
            intArrayOf(0xFF322B16.toInt(), 0xFF201B0D.toInt()),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(l, t, r, b), rad, rad, fillPaint)
        fillPaint.shader = null

        // 内描边：细金线，压在卡片内侧 1.5dp
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(1.2f)
        fillPaint.color = Color.argb(130, 240, 226, 180)
        canvas.drawRoundRect(
            RectF(l + dp(1.6f), t + dp(1.6f), r - dp(1.6f), b - dp(1.6f)),
            rad - dp(1.6f), rad - dp(1.6f), fillPaint
        )
        fillPaint.style = Paint.Style.FILL

        // 第一行：小号标签
        textPaint.color = Color.argb(165, 240, 226, 180)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(13f)
        canvas.drawText(
            if (gameOverTitle().isEmpty()) "游戏结束" else gameOverTitle(),
            width / 2f, t + dp(30f), textPaint
        )

        // 第二行：得分大数字
        textPaint.color = 0xFFF4E8BC.toInt()
        textPaint.textSize = dp(34f)
        canvas.drawText(score.toString(), width / 2f, t + dp(72f), textPaint)

        // 第三行：最高分
        smallPaint.color = Color.argb(150, 240, 226, 180)
        smallPaint.textAlign = Paint.Align.CENTER
        smallPaint.textSize = dp(12f)
        canvas.drawText("最高 $best", width / 2f, t + dp(94f), smallPaint)

        // 底部：细金线 + 操作提示
        fillPaint.color = Color.argb(90, 240, 226, 180)
        canvas.drawRect(l + dp(26f), b - dp(38f), r - dp(26f), b - dp(36.6f), fillPaint)
        smallPaint.color = Color.argb(175, 240, 226, 180)
        smallPaint.textSize = dp(12f)
        canvas.drawText(
            if (hasBar()) "点主键 再来一局 · 侧滑 退出画面" else "侧滑 退出画面",
            width / 2f, b - dp(22f), smallPaint
        )

        textPaint.textAlign = Paint.Align.LEFT
        smallPaint.textAlign = Paint.Align.LEFT
    }

    /** 结束卡片第一行文案，子类可覆写 */
    protected open fun gameOverTitle(): String = "游戏结束"

    /** 全屏未开局提示的第二行（可为空） */
    protected open fun startHintSub(): String = ""


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
        val baseline = top + h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText("已暂停", left + w / 2f, baseline, textPaint)
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
        fillPaint.color = Color.argb(alpha, 250, 250, 246)
        canvas.drawRect(0f, content.top.toFloat(), width.toFloat(), content.bottom.toFloat(), fillPaint)
    }

    private fun drawBar(canvas: Canvas, w: Int) {
        if (!hasBar()) {
            return
        }
        // 浅色面板上的操作条：从画面底色渐入一层更浅的雾白，上沿一条深色细线做分隔
        val rect = barRect()
        fillPaint.alpha = 255
        fillPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(150, 255, 255, 255), Color.argb(200, 255, 255, 255)),
            floatArrayOf(0f, 0.34f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, fillPaint)
        fillPaint.shader = null
        fillPaint.color = Color.argb(120, 31, 31, 31)
        canvas.drawRect(0f, rect.top, w.toFloat(), rect.top + dp(1.2f), fillPaint)

        val buttons = barButtons()
        val top = barTop()
        val cy = top + (height.toFloat() - top) / 2f

        if (barStyle() == BAR_PAD) {
            drawPad(canvas, buttons)
            return
        }

        // 键盘式操作条：每个键一个圆角键帽，按键区一眼能看出是"一排实体键"
        val slot = w.toFloat() / buttons.size
        val keyPad = dp(7f)
        val keyTop = top + keyPad
        val keyBottom = height - keyPad
        textPaint.textAlign = Paint.Align.CENTER
        for (i in buttons.indices) {
            val cx = slot * (i + 0.5f)
            val left = cx - slot / 2f + keyPad
            val right = cx + slot / 2f - keyPad

            // 深墨键帽：浅色操作条上用深键，字反转成米白，拇指区一眼分得出"键"和"底"
            fillPaint.alpha = 255
            fillPaint.shader = LinearGradient(
                0f, keyTop, 0f, keyBottom,
                intArrayOf(0xFF32322A.toInt(), KEY_FACE),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(left, keyTop, right, keyBottom), dp(10f), dp(10f), fillPaint)
            fillPaint.shader = null
            fillPaint.style = Paint.Style.STROKE
            fillPaint.strokeWidth = dp(1.2f)
            fillPaint.color = Color.argb(90, 255, 255, 255)
            canvas.drawRoundRect(RectF(left, keyTop, right, keyBottom), dp(10f), dp(10f), fillPaint)
            fillPaint.style = Paint.Style.FILL

            val btn = buttons[i]
            textPaint.color = KEY_INK
            if (btn.icon != 0) {
                drawBarIcon(canvas, btn.icon, cx, cy, dp(19f), KEY_INK)
            } else {
                val label = btn.label
                textPaint.textSize = dp(if (label.length <= 1) 30f else 16f)
                val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText(label, cx, baseline, textPaint)
            }
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    /** 手柄式操作条：左侧十字方向 + 右侧圆形主键（尺寸按拇指可及放大） */
    private fun drawPad(canvas: Canvas, buttons: List<BarBtn>) {
        val cx = padCrossCX()
        val cy = padCrossCY()
        // 十字底板原来顶到操作条最上沿，分隔线几乎贴在键上；整体下移 9dp，
        // 键块也按剩余高度收紧，让"线—键"之间留出一条看得见的分隔
        val r = dp(22.5f)
        val panelTop = barTop() + dp(9f)
        val panelBottom = height - dp(2f)
        val half = (panelBottom - panelTop) / 2f
        val gap = half - r - dp(5f)
        val panelPad = dp(5f)
        iconInk = KEY_INK

        // 十字底板：一圈极淡的墨色衬底，提示这是一整块方向区
        fillPaint.color = Color.argb(28, 31, 31, 31)
        canvas.drawRoundRect(
            RectF(cx - gap - r - panelPad, panelTop, cx + gap + r + panelPad, panelBottom),
            dp(12f), dp(12f), fillPaint
        )

        val dirs = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0) // 上/下/左/右
        for (i in 0 until min(4, buttons.size)) {
            val bx = cx + dirs[i].first * gap
            val by = cy + dirs[i].second * gap
            // 深墨键块 + 一点上亮下暗，拇指按下去有实感
            fillPaint.shader = LinearGradient(
                0f, by - r, 0f, by + r,
                intArrayOf(0xFF35352C.toInt(), KEY_FACE),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(bx - r, by - r, bx + r, by + r), dp(8f), dp(8f), fillPaint)
            fillPaint.shader = null
            drawArrow(canvas, bx, by, dp(14f), dirs[i].first, dirs[i].second)
        }

        // 右侧主键
        val mx = padMainCX()
        val mr = padMainR()
        fillPaint.shader = LinearGradient(
            0f, cy - mr, 0f, cy + mr,
            intArrayOf(0xFF35352C.toInt(), KEY_FACE),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(mx, cy, mr, fillPaint)
        fillPaint.shader = null
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(1.2f)
        fillPaint.color = Color.argb(70, 255, 255, 255)
        canvas.drawCircle(mx, cy, mr, fillPaint)
        fillPaint.style = Paint.Style.FILL

        val mainBtn = buttons.getOrNull(4)
        when (mainBtn?.icon) {
            1 -> drawBomb(canvas, mx, cy, mr * 0.56f)
            2 -> drawPause(canvas, mx, cy, mr * 0.52f)
            3 -> drawRotate(canvas, mx, cy, mr * 0.52f)
            4 -> drawSwap(canvas, mx, cy, mr * 0.50f)
            5 -> drawHardDrop(canvas, mx, cy, mr * 0.52f)
            else -> {
                textPaint.color = KEY_INK
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = dp(26f)
                val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText(mainBtn?.label ?: "●", mx, baseline, textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
        }
    }

    /** 旋转图标：两段粗圆弧 + 端头三角，读起来就是「转一下方向」 */
    private fun drawRotate(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val r = size * 0.84f
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        fillPaint.color = iconInk
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(3.6f)
        fillPaint.strokeCap = Paint.Cap.BUTT
        // 两段各 132° 的弧，留出两个缺口放箭头，形成「环形回转」的图形语言
        canvas.drawArc(oval, -42f, 132f, false, fillPaint)
        canvas.drawArc(oval, 138f, 132f, false, fillPaint)
        fillPaint.style = Paint.Style.FILL
        fillPaint.strokeCap = Paint.Cap.BUTT
        rotateHead(canvas, cx, cy, r, 90f)
        rotateHead(canvas, cx, cy, r, 270f)
    }

    /** 环上的箭头端：尖端顺着弧的前进方向漂出去 */
    private fun rotateHead(canvas: Canvas, cx: Float, cy: Float, r: Float, deg: Float) {
        val a = Math.toRadians(deg.toDouble())
        val px = cx + (r * Math.cos(a)).toFloat()
        val py = cy + (r * Math.sin(a)).toFloat()
        // 屏幕坐标 Y 向下，角度增大即顺时针：切向 = (-sin, cos)
        val tx = (-Math.sin(a)).toFloat()
        val ty = Math.cos(a).toFloat()
        val nx = Math.cos(a).toFloat()
        val ny = Math.sin(a).toFloat()
        val len = dp(9.5f)
        val half = dp(5.6f)
        val head = android.graphics.Path()
        head.moveTo(px + tx * len, py + ty * len)
        head.lineTo(px + nx * half, py + ny * half)
        head.lineTo(px - nx * half, py - ny * half)
        head.close()
        canvas.drawPath(head, fillPaint)
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
        fillPaint.color = iconInk
        canvas.drawPath(p, fillPaint)
    }

    /** 暂停图标：两条竖条 */
    private fun drawPause(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        fillPaint.color = iconInk
        val bw = size * 0.34f
        val bh = size * 1.5f
        canvas.drawRect(cx - size * 0.55f, cy - bh / 2f, cx - size * 0.55f + bw, cy + bh / 2f, fillPaint)
        canvas.drawRect(cx + size * 0.21f, cy - bh / 2f, cx + size * 0.21f + bw, cy + bh / 2f, fillPaint)
    }

    /** 炸弹图标：圆身 + 引线 + 火花，解决「中键是什么」看不懂的问题 */
    private fun drawBomb(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        fillPaint.color = iconInk
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

    /** 交换图标：上下两条相反方向的箭头，不写汉字也能一眼看出「换一下」 */
    private fun drawSwap(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        fillPaint.color = iconInk
        fillPaint.style = Paint.Style.FILL
        val halfW = size * 1.35f
        val dy = size * 0.62f
        val th = size * 0.22f
        val headW = size * 0.5f
        val headH = size * 0.5f

        // 上线（→）：杆 + 右端三角
        canvas.drawRect(cx - halfW, cy - dy - th / 2f, cx + halfW - headW, cy - dy + th / 2f, fillPaint)
        val p1 = android.graphics.Path()
        p1.moveTo(cx + halfW - headW, cy - dy - headH)
        p1.lineTo(cx + halfW, cy - dy)
        p1.lineTo(cx + halfW - headW, cy - dy + headH)
        p1.close()
        canvas.drawPath(p1, fillPaint)

        // 下线（←）：杆 + 左端三角
        canvas.drawRect(cx - halfW + headW, cy + dy - th / 2f, cx + halfW, cy + dy + th / 2f, fillPaint)
        val p2 = android.graphics.Path()
        p2.moveTo(cx - halfW + headW, cy + dy - headH)
        p2.lineTo(cx - halfW, cy + dy)
        p2.lineTo(cx - halfW + headW, cy + dy + headH)
        p2.close()
        canvas.drawPath(p2, fillPaint)
    }

    /** 通用小图标绘制（供 GRID 布局里的功能键使用） */
    private fun drawBarIcon(canvas: Canvas, icon: Int, cx: Float, cy: Float, size: Float, ink: Int) {
        iconInk = ink
        when (icon) {
            1 -> drawBomb(canvas, cx, cy, size)
            2 -> drawPause(canvas, cx, cy, size * 0.8f)
            3 -> drawRotate(canvas, cx, cy, size * 0.9f)
            4 -> drawSwap(canvas, cx, cy, size * 0.8f)
            5 -> drawHardDrop(canvas, cx, cy, size)
        }
    }

    /** 当前图标使用的墨色（深键帽上给白，浅底上给深墨） */
    private var iconInk: Int = KEY_INK

    /** 直落图标：两枚向下的箭头 + 底下一道横杠，一眼就是「一落到底」 */
    private fun drawHardDrop(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        fillPaint.color = iconInk
        for (i in 0..1) {
            val top = cy - size * 0.95f + i * size * 0.72f
            val p = android.graphics.Path()
            p.moveTo(cx - size * 0.78f, top)
            p.lineTo(cx + size * 0.78f, top)
            p.lineTo(cx, top + size * 0.62f)
            p.close()
            canvas.drawPath(p, fillPaint)
        }
        val barY = cy + size * 0.86f
        canvas.drawRect(cx - size * 0.8f, barY, cx + size * 0.8f, barY + size * 0.2f, fillPaint)
    }

    /** 折叠后「还没开局」时的提示，子类可覆写成自己的操作说明 */
    protected open fun startHint(): String = "点击操作条中键 开始游戏"

    /** 游戏内要纵向跟手（飞机上下飞）时覆写成 true：上滑折叠只在顶部标题条区域生效 */
    protected open fun collapseSwipeFromTopOnly(): Boolean = false

    private fun drawHud(canvas: Canvas, w: Int) {
        // 游戏画面里不放任何音乐元素，也不放游戏名：
        // 控制盘展开时顶部标题栏已经有标题，全屏时画面越干净越好，只在右侧保留分数。
        textPaint.color = COLOR_INK
        textPaint.textSize = dp(15f)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("分数 $score  最高 $best", w - dp(24f), dp(26f), textPaint)

        // 第二行只放游戏自己的状态（消行 / 长度 / 生命），整行都在 HUD 里，绝不压到画面
        if (extraLine.isNotEmpty()) {
            // 和分数同一行、同一字号：左边状态、右边分数，一眼扫过去是一整行信息
            smallPaint.color = COLOR_INK
            smallPaint.textSize = dp(15f)
            smallPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(fitText(extraLine, w * 0.5f, smallPaint), dp(24f), dp(26f), smallPaint)
        }

        // HUD 与画面之间一条极细的分隔线：浅色面板上靠线分层，不靠"压一块深色"
        fillPaint.color = Color.argb(70, 31, 31, 31)
        canvas.drawRect(dp(20f), dp(38f), w.toFloat() - dp(20f), dp(38f) + dp(0.9f), fillPaint)
    }

    /**
     * 场景提示：深墨胶囊 + 米白字，整条内容严格居中。
     *
     * 改版原因：原来是一条"白色磨砂胶囊 + 左侧圆形箭头 + 右对齐起排的两行文字"，
     * 在浅色游戏面板上几乎看不出边界，长文案又会把文字挤成左重右轻，
     * 看上去就是"没居中"。现在换成实心深墨胶囊，文字按胶囊宽度整体居中，
     * 有没有图标都不再影响对齐；四种外壳下都是同一个高对比样式。
     */
    /** 胶囊最大占比（折叠态要放难度小键时会收窄） */
    private var tipMaxFrac = 0.92f

    /** 折叠态左右两个「难度」小键的命中区 */
    private val levelLeftBtn = RectF()
    private val levelRightBtn = RectF()

    private fun drawTipCapsule(
        canvas: Canvas,
        areaTop: Float,
        areaBottom: Float,
        title: String,
        sub: String,
        arrowDown: Boolean,
        strong: Boolean
    ): RectF {
        val w = width.toFloat()
        val areaH = areaBottom - areaTop
        val wantH = if (sub.isEmpty()) dp(56f) else dp(78f)
        val capH = max(dp(46f), min(wantH, areaH - dp(10f)))
        val cy = (areaTop + areaBottom) / 2f
        val k = min(1f, max(0.86f, capH / wantH))

        // 先量文字，宽度跟着内容走：内容短就短胶囊，内容长才放宽，不会出现"胶囊很宽、字偏一头"
        textPaint.textAlign = Paint.Align.LEFT
        var titleSize = dp(16f) * k
        var subSize = dp(11.5f) * k
        textPaint.textSize = titleSize
        smallPaint.textSize = subSize
        var tw = textPaint.measureText(title)
        if (sub.isNotEmpty()) {
            tw = max(tw, smallPaint.measureText(sub))
        }
        val padX = dp(26f) * k
        val capW = min(w * tipMaxFrac, tw + padX * 2f)
        val l = (w - capW) / 2f
        val r = l + capW
        val t = cy - capH / 2f
        val b = t + capH
        val rad = capH / 2f

        // 柔和投影
        fillPaint.shader = null
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.argb(if (strong) 46 else 26, 20, 20, 16)
        canvas.drawRoundRect(RectF(l, t + dp(3f), r, b + dp(3.5f)), rad, rad, fillPaint)

        // 实底：深墨，轻微竖向渐变（上稍亮、下稍沉）
        // 注意：用 shader 时颜色由 Paint.alpha 乘算，前面 dimOverlay / 各种 argb 底会把
        // alpha 残留成 70 左右，导致深墨胶囊被稀释成中灰、白字糊成一片 —— 这里必须复位
        fillPaint.alpha = 255
        fillPaint.shader = LinearGradient(
            0f, t, 0f, b,
            intArrayOf(0xFF2A2A24.toInt(), TIP_BG),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(l, t, r, b), rad, rad, fillPaint)
        fillPaint.shader = null

        // 一圈极细的亮边，边界干净
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(0.8f)
        fillPaint.color = Color.argb(48, 255, 255, 255)
        canvas.drawRoundRect(RectF(l, t, r, b), rad, rad, fillPaint)
        fillPaint.style = Paint.Style.FILL

        // 收字：内容仍然过宽时缩字号，缩到 11dp 为止
        textPaint.textSize = titleSize
        while (titleSize > dp(11f) && textPaint.measureText(title) > capW - padX * 1.4f) {
            titleSize -= dp(0.8f)
            textPaint.textSize = titleSize
        }
        smallPaint.textSize = subSize
        while (sub.isNotEmpty() && subSize > dp(9f) && smallPaint.measureText(sub) > capW - padX * 1.4f) {
            subSize -= dp(0.6f)
            smallPaint.textSize = subSize
        }

        // 文字整体居中：单行垂直居中；两行以胶囊中线为轴上下对称
        val cx = w / 2f
        textPaint.color = TIP_INK
        textPaint.textAlign = Paint.Align.CENTER
        if (sub.isEmpty()) {
            canvas.drawText(title, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        } else {
            canvas.drawText(
                title, cx,
                cy - dp(7f) * k - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint
            )
            smallPaint.color = TIP_SUB
            smallPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                sub, cx,
                cy + dp(12f) * k - (smallPaint.ascent() + smallPaint.descent()) / 2f, smallPaint
            )
        }
        textPaint.textAlign = Paint.Align.CENTER
        smallPaint.textAlign = Paint.Align.LEFT
        return RectF(l, t, r, b)
    }

    /** 折叠态：胶囊左右各一颗「‹ ›」小键，点一下换难度（贪吃蛇三档难度） */
    private fun drawLevelKeys(canvas: Canvas, tip: RectF, cy: Float) {
        val kb = dp(46f)
        val gap = dp(10f)
        val r = kb / 2f
        val lx = max(dp(8f) + r, tip.left - gap - r)
        val rx = min(width.toFloat() - dp(8f) - r, tip.right + gap + r)
        levelLeftBtn.set(lx - r, cy - r, lx + r, cy + r)
        levelRightBtn.set(rx - r, cy - r, rx + r, cy + r)
        for (i in 0..1) {
            val c = if (i == 0) levelLeftBtn else levelRightBtn
            fillPaint.alpha = 255
            fillPaint.shader = LinearGradient(
                0f, c.top, 0f, c.bottom,
                intArrayOf(0xFF32322A.toInt(), KEY_FACE.toInt()),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle((c.left + c.right) / 2f, cy, r, fillPaint)
            fillPaint.shader = null
            // 三角箭头：左边那颗朝左，右边那颗朝右
            val dir = if (i == 0) -1f else 1f
            val tri = android.graphics.Path()
            val ax = (c.left + c.right) / 2f
            val s = dp(11f)
            tri.moveTo(ax - dir * s * 0.9f, cy - s)
            tri.lineTo(ax + dir * s * 1.1f, cy)
            tri.lineTo(ax - dir * s * 0.9f, cy + s)
            tri.close()
            fillPaint.color = KEY_INK
            canvas.drawPath(tri, fillPaint)
        }
    }

    /** 人字形箭头：arrowDown=true 朝下 */
    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, size: Float, arrowDown: Boolean, color: Int) {
        val half = size / 2f
        val sign = if (arrowDown) 1f else -1f
        val path = android.graphics.Path()
        path.moveTo(cx - half, cy - sign * half * 0.58f)
        path.lineTo(cx, cy + sign * half * 0.58f)
        path.lineTo(cx + half, cy - sign * half * 0.58f)
        fillPaint.color = color
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(2.2f)
        fillPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, fillPaint)
        fillPaint.style = Paint.Style.FILL
        fillPaint.strokeCap = Paint.Cap.BUTT
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
        // cy 是「这一行的垂直中心」，减去 (ascent+descent)/2 才能让字形在框里真正上下居中
        val baseline = cy - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, width / 2f, baseline, paint)
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
