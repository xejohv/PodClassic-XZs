package io.github.xejohv.podclassicxzs.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import io.github.xejohv.podclassicxzs.base.ScreenView
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class TetrisGame(context: Context) : GameHost(context, "俄罗斯方块"), ScreenView {

    companion object {
        private const val COLS = 10
        private const val ROWS = 16

        // 四种旋转的 4x4 形状
        private val SHAPES = arrayOf(
            intArrayOf(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0), // I
            intArrayOf(0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0), // O
            intArrayOf(0, 1, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0), // T
            intArrayOf(0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), // S
            intArrayOf(1, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0), // Z
            intArrayOf(1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0), // J
            intArrayOf(0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0)  // L
        )

        // 7 种形状都用深浅不同的"黑金"块体，靠亮金描边在金色底上分清楚
        private val COLORS = intArrayOf(
            0xFF1F1A0E.toInt(),
            0xFF3A2E16.toInt(),
            0xFF2A2415.toInt(),
            0xFF4A3A16.toInt(),
            0xFF231E12.toInt(),
            0xFF33280F.toInt(),
            0xFF181408.toInt()
        )
    }

    private val board = IntArray(COLS * ROWS)
    private var pieceShape = 0
    private var pieceRot = 0
    private var pieceX = 3
    private var pieceY = 0
    private var dropTimer = 0f
    private var lines = 0

    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 俄罗斯方块自己的键：◀ ▶ 移动、● 旋转、▼ 直接落到底（不用长按）
    private val bar = listOf(
        BarBtn("◀") { if (expanded && started && !gameOver) onSlide(-1) },
        BarBtn("●") { if (expanded && started && !gameOver) onPrimary() },
        BarBtn("▼") { if (expanded && started && !gameOver) hardDrop() },
        BarBtn("▶") { if (expanded && started && !gameOver) onSlide(1) }
    )

    override fun barButtons(): List<BarBtn> = bar

    override fun startHint(): String = "点击 ● 旋转方块 · ▼ 直落"

    override fun resetGame() {
        board.fill(0)
        score = 0
        lines = 0
        gameOver = false
        dropTimer = 0f
        spawnPiece()
    }

    private fun dropInterval(): Float = max(160f, 620f - lines * 18f)

    private fun cellsOf(shape: Int, rot: Int): BooleanArray {
        val base = SHAPES[shape]
        var cells = BooleanArray(16)
        for (i in 0 until 16) {
            cells[i] = base[i] == 1
        }
        repeat(rot) {
            val next = BooleanArray(16)
            for (r in 0 until 4) {
                for (c in 0 until 4) {
                    next[r * 4 + c] = cells[(3 - c) * 4 + r]
                }
            }
            cells = next
        }
        return cells
    }

    private fun spawnPiece() {
        pieceShape = Random.nextInt(SHAPES.size)
        pieceRot = 0
        pieceX = 3
        pieceY = 0
        if (collides(pieceShape, pieceRot, pieceX, pieceY)) {
            finishGame()
        }
    }

    private fun collides(shape: Int, rot: Int, ox: Int, oy: Int): Boolean {
        val cells = cellsOf(shape, rot)
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (!cells[r * 4 + c]) {
                    continue
                }
                val x = ox + c
                val y = oy + r
                if (x < 0 || x >= COLS || y >= ROWS) {
                    return true
                }
                if (y >= 0 && board[y * COLS + x] != 0) {
                    return true
                }
            }
        }
        return false
    }

    private fun lockPiece() {
        val cells = cellsOf(pieceShape, pieceRot)
        val color = COLORS[pieceShape]
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (!cells[r * 4 + c]) {
                    continue
                }
                val x = pieceX + c
                val y = pieceY + r
                if (y in 0 until ROWS && x in 0 until COLS) {
                    board[y * COLS + x] = color
                }
            }
        }
        clearLines()
        spawnPiece()
    }

    private fun clearLines() {
        var removed = 0
        var row = ROWS - 1
        while (row >= 0) {
            var full = true
            for (c in 0 until COLS) {
                if (board[row * COLS + c] == 0) {
                    full = false
                    break
                }
            }
            if (full) {
                for (r in row downTo 1) {
                    for (c in 0 until COLS) {
                        board[r * COLS + c] = board[(r - 1) * COLS + c]
                    }
                }
                for (c in 0 until COLS) {
                    board[c] = 0
                }
                removed++
                // 同一行重新检查
                continue
            }
            row--
        }
        if (removed > 0) {
            lines += removed
            addScore(
                when (removed) {
                    1 -> 100
                    2 -> 300
                    3 -> 500
                    else -> 800
                } * (1 + lines / 10)
            )
        }
    }

    override fun update(dt: Float) {
        dropTimer += dt
        if (dropTimer >= dropInterval()) {
            dropTimer = 0f
            if (!collides(pieceShape, pieceRot, pieceX, pieceY + 1)) {
                pieceY++
            } else {
                lockPiece()
            }
        }
        extraLine = "消行 $lines"
    }

    override fun onSlide(slideVal: Int): Boolean {
        val dx = if (slideVal > 0) 1 else -1
        if (!collides(pieceShape, pieceRot, pieceX + dx, pieceY)) {
            pieceX += dx
            return true
        }
        return false
    }

    override fun onPrimary(): Boolean {
        val newRot = (pieceRot + 1) % 4
        if (!collides(pieceShape, newRot, pieceX, pieceY)) {
            pieceRot = newRot
        }
        return true
    }

    override fun onSecondary(): Boolean {
        hardDrop()
        return true
    }

    private fun hardDrop() {
        var moved = false
        while (!collides(pieceShape, pieceRot, pieceX, pieceY + 1)) {
            pieceY++
            moved = true
        }
        if (moved) {
            addScore(20)
        }
        lockPiece()
    }

    override fun drawGame(canvas: Canvas, content: Rect) {
        val cell = min(
            (content.width() - dp(24f)) / COLS.toFloat(),
            (content.height() - dp(12f)) / ROWS.toFloat()
        )
        val boardW = cell * COLS
        val boardH = cell * ROWS
        val left = content.left + (content.width() - boardW) / 2f
        val top = content.top + (content.height() - boardH) / 2f

        // 棋盘底
        fillPaint.color = 0x30000000
        canvas.drawRect(left, top, left + boardW, top + boardH, fillPaint)

        // 网格
        gridPaint.color = 0x55FFFFFF
        gridPaint.strokeWidth = 1f
        for (c in 0..COLS) {
            canvas.drawLine(left + c * cell, top, left + c * cell, top + boardH, gridPaint)
        }
        for (r in 0..ROWS) {
            canvas.drawLine(left, top + r * cell, left + boardW, top + r * cell, gridPaint)
        }

        // 已落下的方块
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val color = board[r * COLS + c]
                if (color != 0) {
                    drawBlock(canvas, left + c * cell, top + r * cell, cell, color)
                }
            }
        }

        // 落点预览（幽灵块）：只画描边，标出这个方块会落在哪
        if (!gameOver) {
            drawGhost(canvas, left, top, cell)
        }

        // 当前方块
        if (!gameOver) {
            val cells = cellsOf(pieceShape, pieceRot)
            val color = COLORS[pieceShape]
            for (r in 0 until 4) {
                for (c in 0 until 4) {
                    if (cells[r * 4 + c]) {
                        drawBlock(canvas, left + (pieceX + c) * cell, top + (pieceY + r) * cell, cell, color)
                    }
                }
            }
        }
    }

    /** 当前方块如果立刻直落，会停在哪一行 */
    private fun ghostY(): Int {
        var y = pieceY
        while (!collides(pieceShape, pieceRot, pieceX, y + 1)) {
            y++
        }
        return y
    }

    /** 落点预览：只描边不填色，不干扰已经堆好的块 */
    private fun drawGhost(canvas: Canvas, left: Float, top: Float, cell: Float) {
        val gy = ghostY()
        if (gy <= pieceY) {
            return
        }
        val cells = cellsOf(pieceShape, pieceRot)
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(1.8f)
        fillPaint.color = 0xCC1F1A0E.toInt()
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (cells[r * 4 + c]) {
                    val x = left + (pieceX + c) * cell
                    val y = top + (gy + r) * cell
                    canvas.drawRect(
                        x + cell * 0.14f, y + cell * 0.14f,
                        x + cell * 0.86f, y + cell * 0.86f, fillPaint
                    )
                }
            }
        }
        fillPaint.style = Paint.Style.FILL
    }

    private fun drawBlock(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        // 亮金外框：保证在金色背景上轮廓清楚
        fillPaint.color = 0xFFE8D69C.toInt()
        canvas.drawRoundRect(
            x + size * 0.05f, y + size * 0.05f,
            x + size * 0.95f, y + size * 0.95f,
            size * 0.18f, size * 0.18f, fillPaint
        )
        // 块体
        fillPaint.color = color
        canvas.drawRoundRect(
            x + size * 0.12f, y + size * 0.12f,
            x + size * 0.88f, y + size * 0.88f,
            size * 0.13f, size * 0.13f, fillPaint
        )
        // 顶部高光
        fillPaint.color = 0x33FFFFFF
        canvas.drawRect(x + size * 0.18f, y + size * 0.16f, x + size * 0.82f, y + size * 0.24f, fillPaint)
    }
}
