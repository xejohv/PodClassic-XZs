package io.github.xejohv.podclassicxzs.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class SnakeGame(context: Context) : GameHost(context, "贪吃蛇") {

    companion object {
        private const val COLS = 15
        private const val ROWS = 15
    }

    private class Cell(val x: Int, val y: Int)

    private val snake = ArrayList<Cell>()
    private var dir = 1 // 0 上 1 右 2 下 3 左
    private var food = Cell(5, 5)
    private var moveTimer = 0f
    private var growth = 0
    private var moving = false

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 手柄式操作条：左十字（上/下/左/右 绝对方向）+ 右圆键（暂停/继续）
    private val bar = listOf(
        BarBtn("↑") { if (expanded && started && !gameOver) turn(0) },
        BarBtn("↓") { if (expanded && started && !gameOver) turn(2) },
        BarBtn("←") { if (expanded && started && !gameOver) turn(3) },
        BarBtn("→") { if (expanded && started && !gameOver) turn(1) },
        BarBtn("II", { if (expanded && started && !gameOver) onPrimary() }, 2)
    )

    override fun barButtons(): List<BarBtn> = bar

    override fun barStyle(): Int = BAR_PAD

    /** 绝对方向转向：不允许 180° 掉头 */
    private fun turn(newDir: Int) {
        if ((newDir + 2) % 4 == dir) {
            return
        }
        dir = newDir
        moving = true
    }

    override fun resetGame() {
        snake.clear()
        snake.add(Cell(COLS / 2, ROWS - 4))
        snake.add(Cell(COLS / 2 - 1, ROWS - 4))
        snake.add(Cell(COLS / 2 - 2, ROWS - 4))
        dir = 0
        growth = 0
        moving = false
        score = 0
        gameOver = false
        moveTimer = 0f
        placeFood()
    }

    private fun placeFood() {
        while (true) {
            val c = Random.nextInt(COLS)
            val r = Random.nextInt(ROWS)
            if (snake.none { it.x == c && it.y == r }) {
                food = Cell(c, r)
                return
            }
        }
    }

    private fun stepInterval(): Float = max(130f, 245f - score * 1.4f)

    override fun update(dt: Float) {
        extraLine = "长度 " + snake.size
        if (!moving) {
            return
        }
        moveTimer += dt
        if (moveTimer < stepInterval()) {
            return
        }
        moveTimer = 0f

        val head = snake.first()
        val nx = when (dir) {
            1 -> head.x + 1
            3 -> head.x - 1
            else -> head.x
        }
        val ny = when (dir) {
            0 -> head.y - 1
            2 -> head.y + 1
            else -> head.y
        }
        if (nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS) {
            finishGame()
            return
        }
        if (snake.any { it.x == nx && it.y == ny }) {
            finishGame()
            return
        }
        snake.add(0, Cell(nx, ny))
        if (nx == food.x && ny == food.y) {
            addScore(10)
            growth += 1
            placeFood()
        }
        if (growth > 0) {
            growth--
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    override fun onSlide(slideVal: Int): Boolean {
        dir = if (slideVal > 0) (dir + 1) % 4 else (dir + 3) % 4
        moving = true
        return true
    }

    override fun onStart(): Boolean {
        moving = true
        return true
    }

    override fun onPrimary(): Boolean {
        // 中键：暂停 / 继续
        moving = !moving
        moveTimer = 0f
        return true
    }

    override fun onSecondary(): Boolean {
        // 长按主键：重新开始
        resetGame()
        markStarted()
        return true
    }

    override fun startHint(): String = "左十字 转向 · 右圆键 暂停"

    override fun drawGame(canvas: Canvas, content: Rect) {
        val cell = min(
            (content.width() - dp(20f)) / COLS.toFloat(),
            (content.height() - dp(10f)) / ROWS.toFloat()
        )
        val boardW = cell * COLS
        val boardH = cell * ROWS
        val left = content.left + (content.width() - boardW) / 2f
        val top = content.top + (content.height() - boardH) / 2f

        fillPaint.color = 0x30000000
        canvas.drawRect(left, top, left + boardW, top + boardH, fillPaint)

        gridPaint.color = 0x55FFFFFF
        gridPaint.strokeWidth = 1f
        for (c in 0..COLS) {
            canvas.drawLine(left + c * cell, top, left + c * cell, top + boardH, gridPaint)
        }
        for (r in 0..ROWS) {
            canvas.drawLine(left, top + r * cell, left + boardW, top + r * cell, gridPaint)
        }

        // 食物（金色小方块）
        fillPaint.color = 0xFFE4D48F.toInt()
        canvas.drawRoundRect(
            left + food.x * cell + cell * 0.18f,
            top + food.y * cell + cell * 0.18f,
            left + (food.x + 1) * cell - cell * 0.18f,
            top + (food.y + 1) * cell - cell * 0.18f,
            cell * 0.22f,
            cell * 0.22f,
            fillPaint
        )

        // 蛇身
        bodyPaint.color = 0xFF6E5E2C.toInt()
        headPaint.color = 0xFF2E2514.toInt()
        snake.forEachIndexed { i, s ->
            val p = if (i == 0) headPaint else bodyPaint
            canvas.drawRoundRect(
                left + s.x * cell + cell * 0.1f,
                top + s.y * cell + cell * 0.1f,
                left + (s.x + 1) * cell - cell * 0.1f,
                top + (s.y + 1) * cell - cell * 0.1f,
                cell * 0.24f,
                cell * 0.24f,
                p
            )
        }
    }
}
