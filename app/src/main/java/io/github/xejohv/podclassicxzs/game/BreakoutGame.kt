package io.github.xejohv.podclassicxzs.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs

/**
 * 打砖块：
 * - ◀ ▶ 移动挡板，● 发球；球落底扣一条命，清空所有砖块进下一关
 */
class BreakoutGame(context: Context) : GameHost(context, "打砖块") {

    companion object {
        private const val COLS = 7
        private const val ROWS = 6
        private const val PADDLE_Y = 0.91f   // 挡板中心线（归一化纵向坐标）
        private const val BALL_R = 0.012f    // 球半径（归一化）
    }

    private val bricks = IntArray(COLS * ROWS)
    private var paddleX = 0.5f
    private var ballX = 0.5f
    private var ballY = PADDLE_Y - BALL_R
    private var vx = 0.22f
    private var vy = -0.34f
    private var launched = false
    private var lives = 3
    private var level = 1
    private var remain = 0
    private var hitFlash = 0f

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)


    // 打砖块不需要底部操作条：左右滑动移动挡板，轻点画面发球
    override fun barButtons(): List<BarBtn> = emptyList()

    override fun startHint(): String = "左右滑动移动挡板 · 轻点发球"

    // 轻点画面 = 发球（未发球时），发球后轻点不再干扰
    override fun onTapContent(x: Float, y: Float): Boolean {
        if (!expanded || gameOver) {
            return false
        }
        if (!launched) {
            launched = true
            return true
        }
        return false
    }

    override fun resetGame() {
        score = 0
        lives = 3
        level = 1
        gameOver = false
        paddleX = 0.5f
        buildLevel()
    }

    private fun buildLevel() {
        for (i in 0 until COLS * ROWS) {
            bricks[i] = if (i / COLS < ROWS - 2) 1 else 1
        }
        // 越上面越"硬"的感觉：上两排两层
        remain = COLS * ROWS
        launched = false
        resetBall()
    }

    private fun resetBall() {
        launched = false
        ballX = paddleX
        ballY = PADDLE_Y - BALL_R
        vx = if ((level + score) % 2 == 0) 0.20f else -0.20f
        vy = -0.32f - level * 0.02f
    }

    override fun update(dt: Float) {
        val sec = dt / 1000f
        extraLine = "生命 $lives   第 $level 关"
        if (hitFlash > 0f) {
            hitFlash -= dt
        }
        if (!launched) {
            ballX = paddleX
            ballY = PADDLE_Y - BALL_R
            return
        }

        ballX += vx * sec
        ballY += vy * sec

        if (ballX < 0.03f) {
            ballX = 0.03f
            vx = -vx
        }
        if (ballX > 0.97f) {
            ballX = 0.97f
            vx = -vx
        }
        if (ballY < 0.03f) {
            ballY = 0.03f
            vy = -vy
        }

        // 挡板反弹：只在球心接近挡板上表面时判定，反弹后球紧贴挡板
        if (ballY > PADDLE_Y - 0.035f && ballY < PADDLE_Y + 0.010f && vy > 0f &&
            abs(ballX - paddleX) < 0.11f
        ) {
            ballY = PADDLE_Y - BALL_R
            vy = -abs(vy)
            vx = (ballX - paddleX) * 5.4f
            if (abs(vx) < 0.10f) {
                vx = if (vx >= 0f) 0.10f else -0.10f
            }
            hitFlash = 120f
        }

        // 落底
        if (ballY > 1.02f) {
            lives--
            if (lives <= 0) {
                lives = 0
                finishGame()
            } else {
                resetBall()
            }
            return
        }

        // 砖块碰撞
        val brickW = 1f / COLS
        val brickH = 0.045f
        val brickTop = 0.04f
        val col = (ballX / brickW).toInt().coerceIn(0, COLS - 1)
        val row = ((ballY - brickTop) / brickH).toInt()
        if (row in 0 until ROWS && ballY > brickTop) {
            val idx = row * COLS + col
            if (bricks[idx] > 0) {
                bricks[idx] = 0
                remain--
                addScore(10 * level)
                vy = -vy
                hitFlash = 120f
                if (remain <= 0) {
                    level++
                    buildLevel()
                }
            }
        }
    }

    /** 手指横向拖动：挡板直接贴在手指对应的 x，1:1 跟手 */
    override fun onDragContent(x: Float, y: Float): Boolean {
        val c = contentRect()
        if (c.width() <= 0) {
            return false
        }
        paddleX = ((x - c.left) / c.width().toFloat()).coerceIn(0.11f, 0.89f)
        if (!launched) {
            ballX = paddleX
        }
        hitFlash = maxOf(0f, hitFlash)
        return true
    }

    override fun onSlide(slideVal: Int): Boolean {
        paddleX += if (slideVal > 0) 0.085f else -0.085f
        paddleX = paddleX.coerceIn(0.11f, 0.89f)
        if (!launched) {
            ballX = paddleX
        }
        return true
    }

    override fun onPrimary(): Boolean {
        if (!launched) {
            launched = true
        }
        return true
    }

    override fun onSecondary(): Boolean {
        return onPrimary()
    }

    override fun drawGame(canvas: Canvas, content: Rect) {
        val w = content.width().toFloat()
        val h = content.height().toFloat()
        val ox = content.left.toFloat()
        val oy = content.top.toFloat()

        if (hitFlash > 0f) {
            fillPaint.color = 0x22FFFFFF
            canvas.drawRect(content, fillPaint)
        }

        // 砖块
        val brickW = w / COLS
        val brickH = h * 0.05f
        val top = oy + h * 0.05f
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (bricks[r * COLS + c] == 0) {
                    continue
                }
                val left = ox + c * brickW + dp(3f)
                val right = ox + (c + 1) * brickW - dp(3f)
                val bt = top + r * brickH + dp(3f)
                val bb = top + (r + 1) * brickH - dp(3f)
                fillPaint.color = if (r % 2 == 0) 0xFF2E2514.toInt() else 0xFF4A3A16.toInt()
                canvas.drawRoundRect(left, bt, right, bb, dp(4f), dp(4f), fillPaint)
                fillPaint.color = 0xFFE8D69C.toInt()
                canvas.drawRect(left + dp(2f), bt + dp(1.5f), right - dp(2f), bt + dp(3f), fillPaint)
            }
        }

        // 挡板
        val pw = w * 0.22f
        val px = ox + paddleX * w
        val py = oy + h * PADDLE_Y
        fillPaint.color = 0xFF1F1A0E.toInt()
        canvas.drawRoundRect(px - pw / 2, py - dp(7f), px + pw / 2, py + dp(7f), dp(7f), dp(7f), fillPaint)
        fillPaint.color = 0xFFE8D69C.toInt();
        canvas.drawRoundRect(px - pw / 2, py - dp(7f), px + pw / 2, py - dp(4.6f), dp(4f), dp(4f), fillPaint)

        // 球
        val bx = ox + ballX * w
        val by = oy + ballY * h
        glowPaint.color = 0x66FFF4C8.toInt()
        canvas.drawCircle(bx, by, dp(13f), glowPaint)
        fillPaint.color = 0xFFFFFDF2.toInt()
        canvas.drawCircle(bx, by, dp(7f), fillPaint)
    }
}
