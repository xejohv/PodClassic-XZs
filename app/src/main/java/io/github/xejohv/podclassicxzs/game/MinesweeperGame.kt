package io.github.xejohv.podclassicxzs.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import kotlin.math.min
import kotlin.random.Random

/**
 * 扫雷（9×9、10 颗雷）：
 * - 第一下点开永远安全
 * - ◀ ▶ 移动光标，● 翻开，长按 ● 插旗；也可以直接点格子
 */
class MinesweeperGame(context: Context) : GameHost(context, "扫雷") {

    companion object {
        private const val SIZE = 9
        private const val TOTAL = SIZE * SIZE
        private const val MINES = 10
    }

    private val mine = BooleanArray(TOTAL)
    private val open = BooleanArray(TOTAL)
    private val flag = BooleanArray(TOTAL)
    private var placed = false
    private var sel = 40
    private var flagsLeft = MINES
    private var boomAt = -1
    private var won = false

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flagPath = Path()

    private val bar = listOf(
        BarBtn("◀") { if (expanded && started && !gameOver) onSlide(-1) },
        BarBtn("●") { if (expanded && started && !gameOver) onPrimary() },
        BarBtn("▶") { if (expanded && started && !gameOver) onSlide(1) }
    )

    override fun barButtons(): List<BarBtn> = bar

    /** 棋盘本身就够点，去掉底部操作条，棋盘直接占满屏底 */
    override fun barStyle(): Int = BAR_NONE

    override fun startHint(): String = "短按 翻开 · 长按 插旗"

    override fun resetGame() {
        mine.fill(false)
        open.fill(false)
        flag.fill(false)
        placed = false
        sel = 40
        flagsLeft = MINES
        boomAt = -1
        won = false
        score = 0
        gameOver = false
    }

    private fun neighbors(idx: Int): IntArray {
        val r = idx / SIZE
        val c = idx % SIZE
        val out = ArrayList<Int>(8)
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) {
                    continue
                }
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until SIZE && nc in 0 until SIZE) {
                    out.add(nr * SIZE + nc)
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
        while (placedCount < MINES) {
            val idx = Random.nextInt(TOTAL)
            if (safe.contains(idx) || mine[idx]) {
                continue
            }
            mine[idx] = true
            placedCount++
        }
        placed = true
    }

    private fun reveal(idx: Int) {
        if (open[idx] || flag[idx]) {
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
        // 泛洪展开
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
        for (i in 0 until TOTAL) {
            if (open[i]) {
                opened++
            }
        }
        if (opened >= TOTAL - MINES) {
            won = true
            addScore(200)
            gameOver = true
        }
    }

    override fun onSlide(slideVal: Int): Boolean {
        sel = if (slideVal > 0) (sel + 1) % TOTAL else (sel + TOTAL - 1) % TOTAL
        return true
    }

    override fun onPrimary(): Boolean {
        if (flag[sel]) {
            flag[sel] = false
            flagsLeft++
            return true
        }
        reveal(sel)
        return true
    }

    override fun onSecondary(): Boolean {
        if (open[sel]) {
            return true
        }
        if (flag[sel]) {
            flag[sel] = false
            flagsLeft++
        } else if (flagsLeft > 0) {
            flag[sel] = true
            flagsLeft--
        }
        return true
    }

    override fun onTapContent(x: Float, y: Float): Boolean {
        val rect = boardRect()
        if (!rect.contains(x.toInt(), y.toInt())) {
            return false
        }
        val cell = rect.width() / SIZE.toFloat()
        val c = ((x - rect.left) / cell).toInt().coerceIn(0, SIZE - 1)
        val r = ((y - rect.top) / cell).toInt().coerceIn(0, SIZE - 1)
        sel = r * SIZE + c
        if (!flag[sel]) {
            reveal(sel)
        }
        return true
    }

    /** 长按格子：插旗 / 取消插旗（绝不翻开，避免手滑炸雷） */
    override fun onLongPressContent(x: Float, y: Float): Boolean {
        val rect = boardRect()
        if (!rect.contains(x.toInt(), y.toInt())) {
            return false
        }
        val cell = rect.width() / SIZE.toFloat()
        val c = ((x - rect.left) / cell).toInt().coerceIn(0, SIZE - 1)
        val r = ((y - rect.top) / cell).toInt().coerceIn(0, SIZE - 1)
        val idx = r * SIZE + c
        sel = idx
        if (open[idx]) {
            return true
        }
        if (flag[idx]) {
            flag[idx] = false
            flagsLeft++
        } else if (flagsLeft > 0) {
            flag[idx] = true
            flagsLeft--
        }
        return true
    }

    private fun boardRect(): Rect {
        val content = contentRect()
        val top0 = content.top + dp(56f)
        val availH = (content.bottom - dp(14f)) - top0
        val size = min(content.width().toFloat() - dp(16f), availH).toInt()
        val left = content.left + (content.width() - size) / 2
        val top = (top0 + (availH - size) / 2f).toInt()
        return Rect(left, top, left + size, top + size)
    }

    override fun update(dt: Float) {
        extraLine = "剩余旗 $flagsLeft"
    }

    override fun drawGame(canvas: Canvas, content: Rect) {
        val rect = boardRect()
        val cell = rect.width() / SIZE.toFloat()

        fillPaint.color = 0x22FFFFFF
        canvas.drawRect(rect, fillPaint)

        // 选中的格子框
        fillPaint.color = 0x332E2514
        val sr = sel / SIZE
        val sc = sel % SIZE
        canvas.drawRect(
            rect.left + sc * cell, rect.top + sr * cell,
            rect.left + (sc + 1) * cell, rect.top + (sr + 1) * cell, fillPaint
        )

        for (i in 0 until TOTAL) {
            val r = i / SIZE
            val c = i % SIZE
            val left = rect.left + c * cell
            val top = rect.top + r * cell
            if (open[i]) {
                fillPaint.color = 0x3AE8D69C
                canvas.drawRect(left + dp(1f), top + dp(1f), left + cell - dp(1f), top + cell - dp(1f), fillPaint)
            } else {
                fillPaint.color = 0xFF2E2514.toInt()
                canvas.drawRoundRect(
                    left + dp(2f), top + dp(2f), left + cell - dp(2f), top + cell - dp(2f),
                    dp(3f), dp(3f), fillPaint
                )
            }
        }

        // 数字与雷旗
        numPaint.textAlign = Paint.Align.CENTER
        for (i in 0 until TOTAL) {
            val r = i / SIZE
            val c = i % SIZE
            val cx = rect.left + c * cell + cell / 2f
            val cy = rect.top + r * cell + cell * 0.76f

            if (mine[i] && (gameOver || open[i])) {
                fillPaint.color = 0xFFFF6A4A.toInt()
                canvas.drawCircle(cx, cy - cell * 0.24f, cell * 0.22f, fillPaint)
                continue
            }
            if (open[i]) {
                val n = countAround(i)
                if (n > 0) {
                    numPaint.textSize = cell * 0.62f
                    numPaint.color = numberColor(n)
                    canvas.drawText(n.toString(), cx, cy, numPaint)
                }
            } else if (flag[i]) {
                fillPaint.color = 0xFFE8D69C.toInt()
                flagPath.reset()
                flagPath.moveTo(cx - cell * 0.18f, cy - cell * 0.52f)
                flagPath.lineTo(cx + cell * 0.24f, cy - cell * 0.36f)
                flagPath.lineTo(cx - cell * 0.18f, cy - cell * 0.20f)
                flagPath.close()
                canvas.drawPath(flagPath, fillPaint)
                canvas.drawRect(cx - cell * 0.20f, cy - cell * 0.52f, cx - cell * 0.14f, cy, fillPaint)
            }
        }
        numPaint.textAlign = Paint.Align.LEFT

        if (gameOver) {
            textPaint.color = COLOR_DEEP
            textPaint.textSize = dp(20f)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                if (won) "全部排除！" else "踩雷了",
                rect.centerX().toFloat(),
                rect.top - dp(2f),
                textPaint
            )
            textPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun numberColor(n: Int): Int = when (n) {
        1 -> 0xFF2E2514.toInt()
        2 -> 0xFF6E5E2C.toInt()
        3 -> 0xFFC0392B.toInt()
        4 -> 0xFF1F1A0E.toInt()
        else -> 0xFF8C7A3B.toInt()
    }
}
