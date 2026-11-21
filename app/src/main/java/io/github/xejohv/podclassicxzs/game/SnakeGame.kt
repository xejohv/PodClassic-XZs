package io.github.xejohv.podclassicxzs.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 贪吃蛇。
 *
 * 本轮改了三件事：
 *  1. 难度分级（悠闲 / 标准 / 挑战）——没开局时左右滑就能换，速度曲线和加速幅度跟着变；
 *  2. 视觉改成"浅色液晶底 + 深墨蛇 + 深琥珀食物"，暗黑机身下也看得清；
 *  3. 加入存档：误触退出回来还能接着打这一局（底部给「继续上局 / 新游戏」）。
 */
class SnakeGame(context: Context) : GameHost(context, "贪吃蛇") {

    companion object {
        private const val COLS = 15
        private const val ROWS = 15

        /** 难度：0 悠闲 / 1 标准 / 2 挑战 */
        private val LEVEL_NAMES = arrayOf("悠闲", "标准", "挑战")
        private val LEVEL_BASE = arrayOf(305f, 235f, 172f)
        private val LEVEL_FLOOR = arrayOf(160f, 120f, 88f)
        private val LEVEL_STEP = arrayOf(2.2f, 3.0f, 3.6f)

        private const val SNAKE_BODY = 0xFF55524A.toInt()
        private const val SNAKE_HEAD = 0xFF1D1D1D.toInt()
        private const val FOOD_GOLD = 0xFF9A6B00.toInt()
        private const val BOARD_FILL = 0x141D1D1D
        private const val BOARD_GRID = 0x261D1D1D
    }

    private class Cell(val x: Int, val y: Int)

    private val snake = ArrayList<Cell>()
    private var dir = 1 // 0 上 1 右 2 下 3 左
    private var food = Cell(5, 5)
    private var moveTimer = 0f
    private var growth = 0
    private var moving = false
    private var level = 1
    /** 吃到食物的脉冲：0..1，用来把食物画大一点，给一点"吃到了"的反馈 */
    private var eatPulse = 0f

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

    override fun supportsSave(): Boolean = true

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
        // 开局先向上游：行数最多的那个方向，玩家有时间反应；向右开局 4 格就撞墙
        dir = 0
        growth = 0
        moving = false
        score = 0
        gameOver = false
        moveTimer = 0f
        eatPulse = 0f
        placeFood()
        extraLine = "难度 ${LEVEL_NAMES[level]} · 长度 ${snake.size}"
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

    /** 每档难度一个基准间隔，随蛇身长度逐步加快，但都有一个下限，别快到无法反应 */
    private fun stepInterval(): Float {
        val i = level.coerceIn(0, 2)
        return max(LEVEL_FLOOR[i], LEVEL_BASE[i] - snake.size * LEVEL_STEP[i])
    }

    /** 没开局时左右滑换难度，不消耗开局 */
    override fun slideBeforeStart(slideVal: Int): Boolean {
        level = (level + if (slideVal > 0) 1 else 2) % 3
        resetGame()
        return true
    }

    /** 折叠态底部两侧的「‹ ›」小键，TC 触屏上更好点 */
    override fun hasLevelSwitch(): Boolean = true

    override fun cycleLevel(dir: Int) {
        level = (level + if (dir > 0) 1 else 2) % 3
        resetGame()
    }

    override fun update(dt: Float) {
        extraLine = "难度 ${LEVEL_NAMES[level]} · 长度 ${snake.size}"
        if (eatPulse > 0f) {
            eatPulse = max(0f, eatPulse - dt / 260f)
        }
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
            addScore(10 + level * 5)
            growth += 1
            eatPulse = 1f
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
        clearSave()
        resetGame()
        markStarted()
        return true
    }

    override fun startHint(): String = "左十字 转向 · 右圆键 暂停 · 左右滑 换难度"

    // ---------------- 存档 ----------------

    override fun onSaveState(): String {
        val sb = StringBuilder()
        sb.append(level).append('|')
        sb.append(dir).append('|')
        sb.append(score).append('|')
        sb.append(growth).append('|')
        sb.append(if (moving) 1 else 0).append('|')
        sb.append(food.x).append(',').append(food.y).append('|')
        for (i in snake.indices) {
            if (i > 0) sb.append(';')
            sb.append(snake[i].x).append(',').append(snake[i].y)
        }
        return sb.toString()
    }

    override fun onRestoreState(data: String) {
        val parts = data.split('|')
        if (parts.size < 7) {
            throw IllegalArgumentException("bad snake save")
        }
        level = parts[0].toInt().coerceIn(0, 2)
        dir = parts[1].toInt().coerceIn(0, 3)
        score = parts[2].toInt()
        growth = parts[3].toInt()
        moving = parts[4] == "1"
        val fx = parts[5].split(',')
        food = Cell(fx[0].toInt(), fx[1].toInt())
        snake.clear()
        for (seg in parts[6].split(';')) {
            if (seg.isEmpty()) continue
            val xy = seg.split(',')
            snake.add(Cell(xy[0].toInt(), xy[1].toInt()))
        }
        if (snake.isEmpty()) {
            throw IllegalArgumentException("empty snake")
        }
        moveTimer = 0f
        gameOver = false
        extraLine = "难度 ${LEVEL_NAMES[level]} · 长度 ${snake.size}"
    }

    override fun drawGame(canvas: Canvas, content: Rect) {
        val cell = min(
            (content.width() - dp(20f)) / COLS.toFloat(),
            (content.height() - dp(10f)) / ROWS.toFloat()
        )
        val boardW = cell * COLS
        val boardH = cell * ROWS
        val left = content.left + (content.width() - boardW) / 2f
        val top = content.top + (content.height() - boardH) / 2f

        // 棋盘：浅色面板上压一块极淡的墨色，再用细网格划分格子
        fillPaint.color = BOARD_FILL
        canvas.drawRect(left, top, left + boardW, top + boardH, fillPaint)

        gridPaint.color = BOARD_GRID
        gridPaint.strokeWidth = 1f
        for (c in 0..COLS) {
            canvas.drawLine(left + c * cell, top, left + c * cell, top + boardH, gridPaint)
        }
        for (r in 0..ROWS) {
            canvas.drawLine(left, top + r * cell, left + boardW, top + r * cell, gridPaint)
        }

        // 食物（深琥珀小方块，吃到时向外弹一下）
        val pulse = 1f + 0.30f * sin(eatPulse * Math.PI).toFloat() * eatPulse
        val inset = cell * 0.20f / pulse
        fillPaint.color = FOOD_GOLD
        canvas.drawRoundRect(
            left + food.x * cell + inset,
            top + food.y * cell + inset,
            left + (food.x + 1) * cell - inset,
            top + (food.y + 1) * cell - inset,
            cell * 0.24f,
            cell * 0.24f,
            fillPaint
        )

        // 蛇身 / 蛇头
        bodyPaint.color = SNAKE_BODY
        headPaint.color = SNAKE_HEAD
        snake.forEachIndexed { i, s ->
            val p = if (i == 0) headPaint else bodyPaint
            val f = if (i == 0) 0.10f else 0.12f
            canvas.drawRoundRect(
                left + s.x * cell + cell * f,
                top + s.y * cell + cell * f,
                left + (s.x + 1) * cell - cell * f,
                top + (s.y + 1) * cell - cell * f,
                cell * 0.24f,
                cell * 0.24f,
                p
            )
        }

        // 蛇头上一对眼睛，让蛇头有朝向感（浅底上白点压深头，最清楚）
        val head = snake.firstOrNull() ?: return
        eyePaint.color = 0xFFF7F6EF.toInt()
        val hx = left + head.x * cell + cell / 2f
        val hy = top + head.y * cell + cell / 2f
        val off = cell * 0.19f
        val r = cell * 0.10f
        when (dir) {
            0 -> {
                canvas.drawCircle(hx - off, hy - off, r, eyePaint)
                canvas.drawCircle(hx + off, hy - off, r, eyePaint)
            }
            2 -> {
                canvas.drawCircle(hx - off, hy + off, r, eyePaint)
                canvas.drawCircle(hx + off, hy + off, r, eyePaint)
            }
            1 -> {
                canvas.drawCircle(hx + off, hy - off, r, eyePaint)
                canvas.drawCircle(hx + off, hy + off, r, eyePaint)
            }
            else -> {
                canvas.drawCircle(hx - off, hy - off, r, eyePaint)
                canvas.drawCircle(hx - off, hy + off, r, eyePaint)
            }
        }
    }
}
