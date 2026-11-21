package io.github.xejohv.podclassicxzs.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min
import kotlin.random.Random

/**
 * 数独：
 * - 点格子选中，点下方数字行填入 1~9 或 ✕ 清空（长按格子也能清空）
 * - 数字行只在选中可填空格时浮出
 * - 填满且全部正确即过关，会重新生成一题
 */
class SudokuGame(context: Context) : GameHost(context, "数独") {

    private val solution = IntArray(81)
    private val puzzle = IntArray(81)
    private val value = IntArray(81)
    private var sel = -1
    private var solvedOnce = false

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bar = listOf(
        BarBtn("◀") { if (expanded && started && !gameOver) onSlide(-1) },
        BarBtn("●") { if (expanded && started && !gameOver) onPrimary() },
        BarBtn("▶") { if (expanded && started && !gameOver) onSlide(1) }
    )

    override fun barButtons(): List<BarBtn> = bar

    /** 不要底部操作条：数字改用画面内的数字行，选格用触摸 */
    override fun barStyle(): Int = BAR_NONE

    override fun startHint(): String = "点格子选中 · 点下方数字填入"

    override fun startHintSub(): String = "长按格子 清空"

    override fun gameOverTitle(): String = "全部填对"

    // ---------------- 存档 ----------------

    override fun supportsSave(): Boolean = true

    private fun pack(arr: IntArray): String {
        val sb = StringBuilder(arr.size)
        for (v in arr) sb.append(v.coerceIn(0, 9).toString())
        return sb.toString()
    }

    private fun unpack(s: String, out: IntArray) {
        for (i in out.indices) {
            out[i] = s.getOrNull(i)?.digitToIntOrNull() ?: 0
        }
    }

    override fun onSaveState(): String {
        return sel.toString() + "|" + score + "|" + (if (solvedOnce) 1 else 0) + "|" +
                pack(solution) + "|" + pack(puzzle) + "|" + pack(value)
    }

    override fun onRestoreState(data: String) {
        val p = data.split('|')
        if (p.size < 6) throw IllegalArgumentException("bad sudoku save")
        sel = p[0].toInt()
        score = p[1].toInt()
        solvedOnce = p[2] == "1"
        unpack(p[3], solution)
        unpack(p[4], puzzle)
        unpack(p[5], value)
        gameOver = false
    }

    /** 数字行：仅在全屏、已开局、且选中了一个可填写的空格时出现 */
    private fun digitsVisible(): Boolean =
        expanded && started && !gameOver && sel in 0 until 81 && puzzle[sel] == 0

    private fun padRect(): Rect {
        val content = contentRect()
        // 两排数字（1-5 / 6-9 + ✕），紧贴棋盘下沿，不再贴着屏幕最底边留出一个大空档
        val h = dp(112f).toInt()
        val side = dp(12f).toInt()
        val top = boardRect().bottom + dp(12f).toInt()
        return Rect(content.left + side, top, content.right - side, top + h)
    }


    override fun resetGame() {
        solvedOnce = false
        sel = -1
        score = 0
        gameOver = false
        generate()
    }

    // ---------------- 出题 ----------------

    private fun generate() {
        solution.fill(0)
        fillBoard(solution)
        // 挖空
        for (i in 0 until 81) {
            puzzle[i] = solution[i]
            value[i] = 0
        }
        var removed = 0
        while (removed < 50) {
            val idx = Random.nextInt(81)
            if (puzzle[idx] != 0) {
                puzzle[idx] = 0
                removed++
            }
        }
    }

    private fun fillBoard(board: IntArray): Boolean {
        for (i in 0 until 81) {
            if (board[i] != 0) {
                continue
            }
            val nums = IntArray(9) { it + 1 }
            // 简单打乱
            for (k in 8 downTo 1) {
                val j = Random.nextInt(k + 1)
                val tmp = nums[k]
                nums[k] = nums[j]
                nums[j] = tmp
            }
            for (n in nums) {
                if (ok(board, i, n)) {
                    board[i] = n
                    if (fillBoard(board)) {
                        return true
                    }
                    board[i] = 0
                }
            }
            return false
        }
        return true
    }

    private fun ok(board: IntArray, idx: Int, n: Int): Boolean {
        val r = idx / 9
        val c = idx % 9
        for (k in 0 until 9) {
            if (board[r * 9 + k] == n) {
                return false
            }
            if (board[k * 9 + c] == n) {
                return false
            }
        }
        val br = (r / 3) * 3
        val bc = (c / 3) * 3
        for (dr in 0 until 3) {
            for (dc in 0 until 3) {
                if (board[(br + dr) * 9 + bc + dc] == n) {
                    return false
                }
            }
        }
        return true
    }

    // ---------------- 交互 ----------------

    override fun update(dt: Float) {
        extraLine = when {
            solvedOnce -> "完成"
            sel in 0 until 81 -> "第${sel / 9 + 1}行 第${sel % 9 + 1}列"
            else -> "点格子选中"
        }
    }

    override fun onSlide(slideVal: Int): Boolean {
        if (sel !in 0 until 81) {
            sel = 40
            return true
        }
        sel = if (slideVal > 0) (sel + 1) % 81 else (sel + 80) % 81
        return true
    }

    override fun onStart(): Boolean = true

    override fun onPrimary(): Boolean {
        if (sel !in 0 until 81 || puzzle[sel] != 0) {
            return true
        }
        value[sel] = (value[sel] + 1) % 10
        checkWin()
        return true
    }

    override fun onSecondary(): Boolean {
        if (sel in 0 until 81 && puzzle[sel] == 0) {
            value[sel] = 0
        }
        return true
    }

    private fun checkWin() {
        var full = true
        var right = true
        for (i in 0 until 81) {
            val v = if (puzzle[i] != 0) puzzle[i] else value[i]
            if (v == 0) {
                full = false
                break
            }
            if (v != solution[i]) {
                right = false
                break
            }
        }
        if (full && right && !solvedOnce) {
            solvedOnce = true
            addScore(120)
            finishGame()
        }
    }

    override fun onTapContent(x: Float, y: Float): Boolean {
        // 先判数字行（1-9 + ✕ 清空）
        if (digitsVisible()) {
            val pad = padRect()
            if (pad.contains(x.toInt(), y.toInt())) {
                val slot = pad.width() / 5f
                val row = ((y - pad.top) / (pad.height() / 2f)).toInt().coerceIn(0, 1)
                val col = ((x - pad.left) / slot).toInt().coerceIn(0, 4)
                val idx = row * 5 + col
                value[sel] = if (idx == 9) 0 else idx + 1
                checkWin()
                return true
            }
        }
        val rect = boardRect()
        if (!rect.contains(x.toInt(), y.toInt())) {
            // 点棋盘外：取消选中，数字行收起
            sel = -1
            return true
        }
        val cell = rect.width() / 9f
        val c = ((x - rect.left) / cell).toInt().coerceIn(0, 8)
        val r = ((y - rect.top) / cell).toInt().coerceIn(0, 8)
        sel = r * 9 + c
        return true
    }

    /** 长按格子：清空该格（题目自带数字不动） */
    override fun onLongPressContent(x: Float, y: Float): Boolean {
        val rect = boardRect()
        if (!rect.contains(x.toInt(), y.toInt())) {
            return false
        }
        val cell = rect.width() / 9f
        val c = ((x - rect.left) / cell).toInt().coerceIn(0, 8)
        val r = ((y - rect.top) / cell).toInt().coerceIn(0, 8)
        val idx = r * 9 + c
        sel = idx
        if (puzzle[idx] == 0) {
            value[idx] = 0
        }
        return true
    }

    private fun boardRect(): Rect {
        val content = playRect()
        // 棋盘 + 数字行当成一整块：在内容区里垂直居中；数字行收起时棋盘自己吃满并居中
        val padH = if (digitsVisible()) dp(112f) + dp(12f) else 0f
        val availH = content.height() - padH
        val size = min(content.width().toFloat() - dp(16f), availH).toInt()
        val left = content.left + (content.width() - size) / 2
        // 在整块屏的高度上居中（不是只在内容区居中），上下留白看起来才对称
        val centered = (height - (size + padH)) / 2f
        val top = maxOf(content.top.toFloat(), centered).toInt()
        return Rect(left, top, left + size, top + size)
    }

    override fun drawGame(canvas: Canvas, content: Rect) {
        val rect = boardRect()
        val cell = rect.width() / 9f

        // 底
        fillPaint.color = 0x121D1D1D
        canvas.drawRect(rect, fillPaint)

        // 选中的格子
        if (sel in 0 until 81) {
            val sr = sel / 9
            val sc = sel % 9
            fillPaint.color = 0x552E2514
            canvas.drawRect(
                rect.left + sc * cell, rect.top + sr * cell,
                rect.left + (sc + 1) * cell, rect.top + (sr + 1) * cell, fillPaint
            )
        }

        // 细网格
        gridPaint.color = 0x331D1D1D
        gridPaint.strokeWidth = dp(1f)
        for (i in 0..9) {
            canvas.drawLine(rect.left + i * cell, rect.top.toFloat(), rect.left + i * cell, rect.bottom.toFloat(), gridPaint)
            canvas.drawLine(rect.left.toFloat(), rect.top + i * cell, rect.right.toFloat(), rect.top + i * cell, gridPaint)
        }
        // 3x3 粗线
        gridPaint.color = 0xAA2E2514.toInt()
        gridPaint.strokeWidth = dp(2.2f)
        for (i in 0..3) {
            canvas.drawLine(rect.left + i * 3 * cell, rect.top.toFloat(), rect.left + i * 3 * cell, rect.bottom.toFloat(), gridPaint)
            canvas.drawLine(rect.left.toFloat(), rect.top + i * 3 * cell, rect.right.toFloat(), rect.top + i * 3 * cell, gridPaint)
        }

        // 数字
        numPaint.textAlign = Paint.Align.CENTER
        numPaint.textSize = cell * 0.72f
        for (i in 0 until 81) {
            val r = i / 9
            val c = i % 9
            val given = puzzle[i]
            val v = if (given != 0) given else value[i]
            if (v == 0) {
                continue
            }
            val wrong = given == 0 && v != solution[i]
            numPaint.color = when {
                given != 0 -> 0xFF1F1A0E.toInt()
                wrong -> 0xFFC0392B.toInt()
                else -> 0xFF7A5F16.toInt()
            }
            val cx = rect.left + c * cell + cell / 2f
            val cy = rect.top + r * cell + cell * 0.76f
            canvas.drawText(v.toString(), cx, cy, numPaint)
        }
        numPaint.textAlign = Paint.Align.LEFT

        if (gameOver && solvedOnce) {
            // 过关提示交给基类的结束卡片，这里只把棋盘压暗一点，别叠两层文字
            fillPaint.color = 0x339A6B00.toInt()
            canvas.drawRect(rect, fillPaint)
        }

        // 数字行：只在选中可填空格时浮出，1-9 + ✕
        if (digitsVisible()) {
            val pad = padRect()
            val slot = pad.width() / 5f
            val rowH = pad.height() / 2f
            val cur = value[sel]
            for (i in 0 until 10) {
                val r0 = i / 5
                val c0 = i % 5
                val left = pad.left + c0 * slot
                val top = pad.top + r0 * rowH
                val isCur = (i < 9 && cur == i + 1) || (i == 9 && cur == 0)
                fillPaint.color = if (isCur) Color.argb(235, 46, 37, 20) else Color.argb(64, 46, 37, 20)
                canvas.drawRoundRect(
                    RectF(left + dp(7f), top + dp(9f), left + slot - dp(7f), top + rowH - dp(9f)),
                    dp(11f), dp(11f), fillPaint
                )
                numPaint.textAlign = Paint.Align.CENTER
                numPaint.textSize = dp(22f)
                numPaint.color = if (isCur) 0xFFE8D69C.toInt() else 0xFF1F1A0E.toInt()
                canvas.drawText(
                    if (i == 9) "✕" else (i + 1).toString(),
                    left + slot / 2f,
                    top + rowH / 2f + dp(7.5f),
                    numPaint
                )
            }
            numPaint.textAlign = Paint.Align.LEFT
        }
    }
}
