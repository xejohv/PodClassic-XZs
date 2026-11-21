package io.github.xejohv.podclassicxzs.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 扫雷（三档难度）：
 * - 底部触控板拖动手掌光标，右侧「开」按钮翻开、「旗」按钮插旗，全部是鼠标式操作，
 *   手指不会压在格子上，也不会因为长按插旗误触暴雷
 * - 第一下翻开永远安全，也不再一进游戏就替玩家翻开一片
 * - 折叠态左右「‹ ›」换难度
 */
class MinesweeperGame(context: Context) : GameHost(context, "扫雷") {

    companion object {
        // 列, 行, 雷数：竖屏下按比例排，格子还是方方正正的
        private val LEVEL_COLS = intArrayOf(9, 13, 16)
        private val LEVEL_ROWS = intArrayOf(9, 13, 16)
        private val LEVEL_MINES = intArrayOf(10, 30, 48)
        private val LEVEL_NAMES = arrayOf("初级", "中级", "高级")
        private const val PAD_GAIN = 1.25f   // 触控板灵敏度：手指滑一格，光标走 1.25 格
    }

    private var levelIdx = 0
    private var cols = LEVEL_COLS[0]
    private var rows = LEVEL_ROWS[0]
    private var mines = LEVEL_MINES[0]

    private var mine = BooleanArray(cols * rows)
    private var open = BooleanArray(cols * rows)
    private var flag = BooleanArray(cols * rows)
    private var placed = false
    private var sel = 0
    private var curCX = 0f     // 光华标位置（格坐标，浮点，拖动时连续变化）
    private var curCY = 0f
    private var flagsLeft = mines
    private var boomAt = -1
    private var won = false

    // 触控板拖动锚点
    private var padDrag = false
    private var padDownX = 0f
    private var padDownY = 0f
    private var padStartCX = 0f
    private var padStartCY = 0f

    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flagPath = Path()
    private val ptrPath = Path()

    override fun barButtons(): List<BarBtn> = emptyList()

    /** 棋盘 + 触控板自己画，不需要底部操作条 */
    override fun barStyle(): Int = BAR_NONE

    override fun startHint(): String = "触控板移动 · 按钮 开/旗"

    /** 结束卡片标题：区分通关与踩雷 */
    override fun gameOverTitle(): String = if (won) "全部排除" else "踩雷了"

    // ---------------- 难度 ----------------

    override fun hasLevelSwitch(): Boolean = true

    override fun cycleLevel(dir: Int) {
        levelIdx = (levelIdx + if (dir > 0) 1 else LEVEL_NAMES.size - 1) % LEVEL_NAMES.size
        applyLevel()
        clearSave()
        resetGame()
    }

    private fun applyLevel() {
        cols = LEVEL_COLS[levelIdx]
        rows = LEVEL_ROWS[levelIdx]
        mines = LEVEL_MINES[levelIdx]
        val total = cols * rows
        mine = BooleanArray(total)
        open = BooleanArray(total)
        flag = BooleanArray(total)
    }

    // ---------------- 存档 ----------------

    override fun supportsSave(): Boolean = true

    override fun onSaveState(): String {
        val sb = StringBuilder()
        sb.append(levelIdx).append('|')
        sb.append(cols).append('|').append(rows).append('|').append(mines).append('|')
        sb.append(sel).append('|')
        sb.append(flagsLeft).append('|')
        sb.append(if (placed) 1 else 0).append('|')
        sb.append(score).append('|')
        sb.append(boomAt).append('|')
        sb.append(if (won) 1 else 0).append('|')
        for (i in mine.indices) sb.append(if (mine[i]) '1' else '0')
        sb.append('|')
        for (i in open.indices) sb.append(if (open[i]) '1' else '0')
        sb.append('|')
        for (i in flag.indices) sb.append(if (flag[i]) '1' else '0')
        return sb.toString()
    }

    override fun onRestoreState(data: String) {
        val p = data.split('|')
        if (p.size < 13) throw IllegalArgumentException("bad mines save")
        levelIdx = p[0].toInt().coerceIn(0, LEVEL_NAMES.size - 1)
        applyLevel()
        sel = p[4].toInt().coerceIn(0, mine.size - 1)
        flagsLeft = p[5].toInt()
        placed = p[6] == "1"
        score = p[7].toInt()
        boomAt = p[8].toInt()
        won = p[9] == "1"
        for (i in mine.indices) {
            mine[i] = p[10].getOrNull(i) == '1'
            open[i] = p[11].getOrNull(i) == '1'
            flag[i] = p[12].getOrNull(i) == '1'
        }
        curCX = (sel % cols).toFloat()
        curCY = (sel / cols).toFloat()
        gameOver = false
    }

    override fun resetGame() {
        val total = cols * rows
        if (mine.size != total) {
            applyLevel()
        }
        mine.fill(false)
        open.fill(false)
        flag.fill(false)
        placed = false
        curCX = (cols - 1) / 2f
        curCY = (rows - 1) / 2f
        sel = curCY.roundToInt() * cols + curCX.roundToInt()
        flagsLeft = mines
        boomAt = -1
        won = false
        score = 0
        gameOver = false
    }

    /** 开局什么都不替玩家做（原来一进全屏就从中心翻开一大片，很跳） */
    override fun onStart(): Boolean = true

    // ---------------- 逻辑 ----------------

    private fun neighbors(idx: Int): IntArray {
        val r = idx / cols
        val c = idx % cols
        val out = ArrayList<Int>(8)
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) {
                    continue
                }
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols) {
                    out.add(nr * cols + nc)
                }
            }
        }
        return out.toIntArray()
    }

    private fun countAround(idx: Int): Int {
        var n = 0
        for (k in neighbors(idx)) {
            if (mine[k]) {
                n++
            }
        }
        return n
    }

    private fun placeMines(safeIdx: Int) {
        val safe = HashSet<Int>()
        safe.add(safeIdx)
        for (k in neighbors(safeIdx)) {
            safe.add(k)
        }
        var placedCount = 0
        while (placedCount < mines) {
            val idx = Random.nextInt(mine.size)
            if (safe.contains(idx) || mine[idx]) {
                continue
            }
            mine[idx] = true
            placedCount++
        }
        placed = true
    }

    private fun reveal(idx: Int) {
        if (idx !in mine.indices || open[idx] || flag[idx]) {
            return
        }
        if (!placed) {
            placeMines(idx)
        }
        if (mine[idx]) {
            boomAt = idx
            open[idx] = true
            gameOver = true
            return
        }
        val queue = ArrayList<Int>()
        queue.add(idx)
        while (queue.isNotEmpty()) {
            val cur = queue.removeAt(queue.size - 1)
            if (open[cur] || flag[cur] || mine[cur]) {
                continue
            }
            open[cur] = true
            if (countAround(cur) == 0) {
                for (k in neighbors(cur)) {
                    if (!open[k] && !flag[k]) {
                        queue.add(k)
                    }
                }
            }
        }
        checkWin()
    }

    private fun checkWin() {
        var opened = 0
        for (i in open.indices) {
            if (open[i]) {
                opened++
            }
        }
        if (opened >= mine.size - mines) {
            won = true
            addScore(200 + levelIdx * 100)
            gameOver = true
        }
    }

    /** 光标格周围已插的旗数 */
    private fun flagsAround(idx: Int): Int {
        var n = 0
        for (k in neighbors(idx)) {
            if (flag[k]) {
                n++
            }
        }
        return n
    }

    /**
     * 「开」：光标是暗格就翻开；光标是已翻开、且周围旗数刚好等于数字的格子时，
     * 把周围没插旗的格子一起翻开（电脑版右键的「扫清周围」）。
     */
    private fun doOpen() {
        if (gameOver) {
            return
        }
        val i = sel
        if (open[i]) {
            val n = countAround(i)
            if (n > 0 && flagsAround(i) >= n) {
                for (k in neighbors(i)) {
                    if (!open[k] && !flag[k]) {
                        reveal(k)
                    }
                }
            }
            return
        }
        if (flag[i]) {
            return
        }
        reveal(i)
    }

    /** 「旗」：插旗 / 取消插旗 */
    private fun doFlag() {
        if (gameOver) {
            return
        }
        if (open[sel]) {
            return
        }
        if (flag[sel]) {
            flag[sel] = false
            flagsLeft++
        } else if (flagsLeft > 0) {
            flag[sel] = true
            flagsLeft--
        }
    }

    override fun onSlide(slideVal: Int): Boolean {
        moveCursor(if (slideVal > 0) 1 else -1, 0)
        return true
    }

    override fun onPrimary(): Boolean {
        doOpen()
        return true
    }

    override fun onSecondary(): Boolean {
        doFlag()
        return true
    }

    private fun moveCursor(dx: Int, dy: Int) {
        val c = (curCX.roundToInt() + dx).coerceIn(0, cols - 1)
        val r = (curCY.roundToInt() + dy).coerceIn(0, rows - 1)
        curCX = c.toFloat()
        curCY = r.toFloat()
        sel = r * cols + c
    }

    // ---------------- 触摸：触控板拖动 + 两个按钮 ----------------

    override fun onContentDown(x: Float, y: Float) {
        val p = padRect(playRect())
        padDrag = stripVisible() && p.contains(x, y)
        padDownX = x
        padDownY = y
        padStartCX = curCX
        padStartCY = curCY
    }

    override fun onDragContent(x: Float, y: Float): Boolean {
        if (!expanded || gameOver) {
            return false
        }
        if (!padDrag) {
            // 在棋盘上乱划不移动光标，也不会被当成圆盘左右滑
            return true
        }
        val cellPx = boardRect().width() / cols.toFloat()
        if (cellPx <= 0f) {
            return true
        }
        val rawX = padStartCX + (x - padDownX) / cellPx * PAD_GAIN
        val rawY = padStartCY + (y - padDownY) / cellPx * PAD_GAIN
        val tx = rawX.roundToInt().coerceIn(0, cols - 1)
        val ty = rawY.roundToInt().coerceIn(0, rows - 1)
        var sx = sel % cols
        var sy = sel / cols
        // 磁吸：手指要拖出 0.62 格才切到下一格，轻微抖动不会让光标乱跳
        if (tx != sx && abs(rawX - sx) >= 0.62f) {
            sx = tx
        }
        if (ty != sy && abs(rawY - sy) >= 0.62f) {
            sy = ty
        }
        curCX = sx.toFloat()
        curCY = sy.toFloat()
        sel = sy * cols + sx
        return true
    }

    override fun onTapContent(x: Float, y: Float): Boolean {
        if (!expanded) {
            return false
        }
        if (stripVisible() && padRect(playRect()).contains(x, y)) {
            return true
        }
        val base = playRect()
        if (topButtons() && hitCircle(openBtnCx(base), padRect(base).centerY(), dp(30f), x, y)) {
            doOpen()
            return true
        }
        if (topButtons() && hitCircle(flagBtnCx(base), padRect(base).centerY(), dp(30f), x, y)) {
            doFlag()
            return true
        }
        // 棋盘上点一下 = 把光标移过去（只定位，不翻开：避免手指一压就踩雷）
        val rect = boardRect(base)
        if (rect.contains(x.toInt(), y.toInt())) {
            val cellPx = rect.width() / cols.toFloat()
            val c = ((x - rect.left) / cellPx).toInt().coerceIn(0, cols - 1)
            val r = ((y - rect.top) / cellPx).toInt().coerceIn(0, rows - 1)
            curCX = c.toFloat()
            curCY = r.toFloat()
            sel = r * cols + c
            return true
        }
        return false
    }

    private fun hitCircle(cx: Float, cy: Float, r: Float, x: Float, y: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= r * r
    }

    // ---------------- 布局 ----------------

    /** 触控板只在全屏且已开局时出现；折叠预览里把它让开，棋盘才看得清 */
    private fun stripVisible(): Boolean = expanded && started

    private fun topButtons(): Boolean = stripVisible()

    private fun stripRect(base: Rect): RectF {
        return RectF(
            base.left + dp(10f), base.bottom - dp(6f) - dp(112f),
            base.right - dp(10f), base.bottom - dp(6f)
        )
    }

    /** 触控板边长（正方形，比控制带更高，往上长一点，手指更好拖） */
    private fun padSide(): Float = dp(162f)

    /** 触控板做成正方形：底边与控制带齐，往上延伸；右侧留给两个圆键 */
    private fun padRect(base: Rect): RectF {
        val bottom = base.bottom - dp(6f)
        val left = base.left + dp(15f)
        return RectF(left, bottom - padSide(), left + padSide(), bottom)
    }

    private fun openBtnCx(base: Rect): Float = base.right - dp(42f)

    private fun flagBtnCx(base: Rect): Float = base.right - dp(105f)

    /** 折叠态棋盘上方要留一条写「难度 XX」，全屏不需要 */
    private fun labelBand(): Int = 0

    private fun boardRect(base: Rect): Rect {
        val bottom = when {
            stripVisible() -> padRect(base).top.toInt() - dp(12f).toInt()
            !expanded -> base.bottom - dp(10f).toInt()
            else -> base.bottom
        }
        val top0 = base.top + labelBand()
        val areaH = bottom - top0
        val cell = min((base.width().toFloat() - dp(20f)) / cols, areaH.toFloat() / rows)
            .toInt().coerceAtLeast(8)
        val bw = cell * cols
        val bh = cell * rows
        val left = base.left + (base.width() - bw) / 2
        val top = top0 + (areaH - bh) / 2
        return Rect(left, top, left + bw, top + bh)
    }

    private fun boardRect(): Rect = boardRect(playRect())

    private fun refreshHud() {
        extraLine = "难度 " + LEVEL_NAMES[levelIdx] + " · 剩余旗 " + flagsLeft
    }

    override fun update(dt: Float) {
        refreshHud()
    }

    // ---------------- 绘制 ----------------

    override fun drawGame(canvas: Canvas, content: Rect) {
        refreshHud()
        val rect = boardRect(content)
        val cell = rect.width() / cols.toFloat()

        // 棋盘外框
        linePaint.color = Color.argb(46, 31, 26, 14)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(
            RectF(rect.left - dp(5f), rect.top - dp(5f), rect.right + dp(5f), rect.bottom + dp(5f)),
            dp(10f), dp(10f), linePaint
        )
        linePaint.style = Paint.Style.FILL

        fillPaint.color = 0x101D1D1D
        canvas.drawRect(rect, fillPaint)

        for (i in 0 until mine.size) {
            val r = i / cols
            val c = i % cols
            val l = rect.left + c * cell
            val t = rect.top + r * cell
            val rr = min(dp(3f), cell * 0.14f)
            // 格子缝随格子尺寸缩放，高级难度缩在折叠预览里也不会变成一串小圆点
            val ins = min(dp(1.6f), cell * 0.055f)
            if (open[i]) {
                fillPaint.color = 0x1A1D1D1D
                val oi = min(dp(0.8f), cell * 0.03f)
                canvas.drawRect(l + oi, t + oi, l + cell - oi, t + cell - oi, fillPaint)
            } else {
                fillPaint.color = 0xFF2E2514.toInt()
                canvas.drawRoundRect(
                    l + ins, t + ins, l + cell - ins, t + cell - ins,
                    rr, rr, fillPaint
                )
            }
        }

        // 数字与雷
        numPaint.textAlign = Paint.Align.CENTER
        for (i in 0 until mine.size) {
            val r = i / cols
            val c = i % cols
            val cx = rect.left + c * cell + cell / 2f
            val cy = rect.top + r * cell + cell * 0.76f
            if (mine[i] && (gameOver || open[i])) {
                fillPaint.color = 0xFFFF6A4A.toInt()
                canvas.drawCircle(cx, cy - cell * 0.24f, cell * 0.2f, fillPaint)
                continue
            }
            if (open[i]) {
                val n = countAround(i)
                if (n > 0) {
                    numPaint.textSize = cell * 0.6f
                    numPaint.color = numberColor(n)
                    canvas.drawText(n.toString(), cx, cy, numPaint)
                }
            } else if (flag[i]) {
                drawFlagGlyph(canvas, cx, rect.top + r * cell + cell * 0.52f, cell * 0.34f, 0xFFD32F16.toInt())
            }
        }
        numPaint.textAlign = Paint.Align.LEFT

        // 光标：一圈金色细框 + 极淡起底，比"整格填色"更准也更不挡数字
        val scx = rect.left + (curCX + 0.5f) * cell
        val scy = rect.top + (curCY + 0.5f) * cell
        fillPaint.color = Color.argb(52, 232, 214, 156)
        canvas.drawRoundRect(
            RectF(scx - cell / 2, scy - cell / 2, scx + cell / 2, scy + cell / 2),
            dp(3f), dp(3f), fillPaint
        )
        linePaint.color = 0xFF9A6B00.toInt()
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = maxOf(dp(2.2f), cell * 0.045f)
        canvas.drawRoundRect(
            RectF(scx - cell / 2 + linePaint.strokeWidth / 2, scy - cell / 2 + linePaint.strokeWidth / 2,
                scx + cell / 2 - linePaint.strokeWidth / 2, scy + cell / 2 - linePaint.strokeWidth / 2),
            dp(3f), dp(3f), linePaint
        )
        linePaint.style = Paint.Style.FILL

        // 结束文案改由结束卡片标题显示：原来画在棋盘上方那一行会被内容区裁掉上半截

        if (stripVisible()) {
            drawControlStrip(canvas, content)
        }
    }

    private fun drawControlStrip(canvas: Canvas, base: Rect) {
        val s = stripRect(base)

        // 触控板
        val pad = padRect(base)
        fillPaint.color = Color.argb(18, 31, 26, 14)
        canvas.drawRoundRect(pad, dp(13f), dp(13f), fillPaint)
        linePaint.color = Color.argb(40, 31, 26, 14)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(pad, dp(13f), dp(13f), linePaint)
        linePaint.style = Paint.Style.FILL

        // 触控板提示：鼠标箭头 + 两侧很淡的左右箭头
        val pcx = pad.centerX()
        val pcy = pad.centerY()

        // 触控板表面：细点阵，让"这是一块可拖动的板"一眼看出来
        fillPaint.color = Color.argb(20, 31, 26, 14)
        val stepX = pad.width() / 7f
        val stepY = pad.height() / 7f
        for (ci in 1..6) {
            for (rj in 1..6) {
                canvas.drawCircle(
                    pad.left + stepX * ci,
                    pad.top + stepY * rj,
                    dp(1.2f), fillPaint
                )
            }
        }

        // 两侧方向箭头贴到板边
        linePaint.color = Color.argb(72, 31, 26, 14)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = dp(2f)
        val ax = dp(18f)
        val ah = dp(6.5f)
        canvas.drawLine(pad.left + ax + dp(5f), pcy - ah, pad.left + ax, pcy, linePaint)
        canvas.drawLine(pad.left + ax, pcy, pad.left + ax + dp(5f), pcy + ah, linePaint)
        canvas.drawLine(pad.right - ax - dp(5f), pcy - ah, pad.right - ax, pcy, linePaint)
        canvas.drawLine(pad.right - ax, pcy, pad.right - ax - dp(5f), pcy + ah, linePaint)
        linePaint.style = Paint.Style.FILL

        drawPointer(canvas, pcx, pcy, if (padDrag) 150 else 110, 1.5f)

        // 两个按键
        drawActionBtn(canvas, flagBtnCx(base), pad.centerY(), false)
        drawActionBtn(canvas, openBtnCx(base), pad.centerY(), true)
    }

    /** 鼠标箭头（提示"这里是触控板"） */
    private fun drawPointer(canvas: Canvas, cx: Float, cy: Float, alpha: Int, scale: Float = 1f) {
        ptrPath.reset()
        ptrPath.moveTo(0f, 0f)
        ptrPath.lineTo(0f, dp(12f))
        ptrPath.lineTo(dp(3.2f), dp(9f))
        ptrPath.lineTo(dp(5.2f), dp(13.4f))
        ptrPath.lineTo(dp(7.5f), dp(12.4f))
        ptrPath.lineTo(dp(5.6f), dp(8.1f))
        ptrPath.lineTo(dp(9.6f), dp(8.1f))
        ptrPath.close()

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        canvas.translate(dp(-3.6f), dp(-6.6f))
        fillPaint.color = Color.argb(alpha, 31, 26, 14)
        canvas.drawPath(ptrPath, fillPaint)
        canvas.restore()
    }

    /** 圆形按键：isOpen=true 是「开」（方框+点），否则是「旗」 */
    private fun drawActionBtn(canvas: Canvas, cx: Float, cy: Float, isOpen: Boolean) {
        val r = dp(27f)
        val a = if (gameOver) 96 else 255
        fillPaint.color = Color.argb(a, 31, 26, 14)
        canvas.drawCircle(cx, cy, r, fillPaint)

        val ink = Color.argb(a, 240, 226, 180)
        if (isOpen) {
            linePaint.color = ink
            linePaint.style = Paint.Style.STROKE
            linePaint.strokeWidth = dp(2f)
            canvas.drawRoundRect(
                RectF(cx - dp(8.6f), cy - dp(8.6f), cx + dp(8.6f), cy + dp(8.6f)),
                dp(2.4f), dp(2.4f), linePaint
            )
            linePaint.style = Paint.Style.FILL
            fillPaint.color = ink
            canvas.drawCircle(cx, cy, dp(2.8f), fillPaint)
        } else {
            drawFlagGlyph(canvas, cx, cy, dp(9f), ink)
        }
    }

    /** 旗子：短杆 + 小三角旗，整体落在格子里；size 是旗高的四分之一 */
    private fun drawFlagGlyph(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        fillPaint.color = color
        flagPath.reset()
        flagPath.moveTo(cx - size * 0.16f, cy - size)
        flagPath.lineTo(cx + size * 0.80f, cy - size * 0.64f)
        flagPath.lineTo(cx - size * 0.16f, cy - size * 0.28f)
        flagPath.close()
        canvas.drawPath(flagPath, fillPaint)
        canvas.drawRect(cx - size * 0.32f, cy - size, cx - size * 0.16f, cy + size * 0.92f, fillPaint)
    }

    private fun numberColor(n: Int): Int = when (n) {
        1 -> 0xFF2E2514.toInt()
        2 -> 0xFF6E5E2C.toInt()
        3 -> 0xFFC0392B.toInt()
        4 -> 0xFF1F1A0E.toInt()
        else -> 0xFF7A5F16.toInt()
    }
}
