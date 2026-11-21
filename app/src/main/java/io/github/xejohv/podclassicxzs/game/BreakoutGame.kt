package io.github.xejohv.podclassicxzs.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sin

/**
 * 打砖块：
 * - 下方控制带里左右拖，挡板跟着走（手指不在挡板上，挡板全程看得见）；轻点画面发球
 * - 球落底扣一条命，清空所有砖块进下一关
 */
class BreakoutGame(context: Context) : GameHost(context, "打砖块") {

    companion object {
        private const val COLS = 7
        private const val ROWS = 6
        private const val PADDLE_Y = 0.94f   // 挡板中心线（归一化纵向坐标，相对游戏区）
        private const val BALL_R = 0.012f    // 球半径（归一化）
        private const val DRAG_GAIN = 1.1f   // 拖动灵敏度：略大于 1，手指不用滑满整屏
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

    // 相对拖动：记住手指按下时的位置和挡板位置，之后只看位移量
    private var dragging = false
    private var lastDragMs = 0L
    private var dragStartX = 0f
    private var dragStartPaddle = 0.5f

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)


    // 打砖块不需要底部操作条：控制全在下方的控制带里
    override fun barButtons(): List<BarBtn> = emptyList()

    override fun startHint(): String = "下方拖动 · 轻点发球"

    // ---------------- 布局：游戏区 + 控制带 ----------------

    /** 控制带高度（手指在这里左右滑） */
    private fun ctrlHeight(): Float = dp(46f)

    /** 控制带只在全屏时出现：折叠预览是一张"海报"，多一条操作带反而别扭 */
    private fun ctrlVisible(): Boolean = expanded

    /** 游戏区：砖块、球和挡板都在框里；底边让开控制带，挡板不会被手指压住 */
    private fun fieldRect(c: Rect): RectF {
        val bottom = if (ctrlVisible()) c.bottom - ctrlHeight() - dp(16f) else c.bottom.toFloat()
        return RectF(
            c.left.toFloat(), c.top.toFloat(), c.right.toFloat(),
            bottom
        )
    }

    private fun ctrlRect(c: Rect): RectF {
        return RectF(
            c.left + dp(14f), c.bottom - ctrlHeight() - dp(5f),
            c.right - dp(14f), c.bottom - dp(5f)
        )
    }

    // ---------------- 存档 ----------------

    override fun supportsSave(): Boolean = true

    override fun onSaveState(): String {
        val sb = StringBuilder()
        sb.append(score).append('|')
        sb.append(lives).append('|')
        sb.append(level).append('|')
        sb.append(remain).append('|')
        sb.append(if (launched) 1 else 0).append('|')
        sb.append(paddleX).append('|')
        sb.append(ballX).append(',').append(ballY).append(',')
            .append(vx).append(',').append(vy).append('|')
        for (b in bricks) sb.append(if (b == 0) '0' else '1')
        return sb.toString()
    }

    override fun onRestoreState(data: String) {
        val p = data.split('|')
        if (p.size < 8) throw IllegalArgumentException("bad breakout save")
        score = p[0].toInt()
        lives = p[1].toInt()
        level = p[2].toInt()
        remain = p[3].toInt()
        launched = p[4] == "1"
        paddleX = p[5].toFloat()
        val ball = p[6].split(',')
        ballX = ball[0].toFloat()
        ballY = ball[1].toFloat()
        vx = ball[2].toFloat()
        vy = ball[3].toFloat()
        for (i in bricks.indices) {
            bricks[i] = if (p[7].getOrNull(i) == '1') 1 else 0
        }
        hitFlash = 0f
        gameOver = false
    }

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


    /** 待发/接球后球心位置：贴在挡板上沿，球体不侵入挡板 */
    private fun restBallY(): Float {
        val c = contentRect()
        val hh = fieldRect(c).height()
        if (hh <= 0f) return PADDLE_Y - BALL_R
        return PADDLE_Y - dp(15f) / hh
    }

    private fun resetBall() {
        launched = false
        ballX = paddleX
        ballY = restBallY()
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
            ballY = restBallY()
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
            ballY = restBallY()
            vy = -abs(vy)
            vx = (ballX - paddleX) * 5.4f
            if (abs(vx) < 0.10f) {
                vx = if (vx >= 0f) 0.10f else -0.10f
            }
            hitFlash = 120f
        }

        // 落底（游戏区下沿就是丢球线，不会掉到控制带上）
        if (ballY > 1.0f) {
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
        val brickTop = 0.06f
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

    /** 按下：记锚点（手指落哪都行，不需要压在挡板上） */
    override fun onContentDown(x: Float, y: Float) {
        dragging = true
        lastDragMs = System.currentTimeMillis()
        dragStartX = x
        dragStartPaddle = paddleX
    }

    /** 拖动：按手指走的位移带动挡板，不加绝对对齐，所以挡板不会"跳"到手指下 */
    override fun onDragContent(x: Float, y: Float): Boolean {
        val c = contentRect()
        if (!dragging || c.width() <= 0) {
            return false
        }
        lastDragMs = System.currentTimeMillis()
        val dx = (x - dragStartX) / c.width().toFloat() * DRAG_GAIN
        paddleX = (dragStartPaddle + dx).coerceIn(0.11f, 0.89f)
        if (!launched) {
            ballX = paddleX
        }
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
        val f = fieldRect(content)
        val w = f.width()
        val h = f.height()
        val ox = f.left
        val oy = f.top

        if (hitFlash > 0f) {
            fillPaint.color = 0x1A1D1D1D
            canvas.drawRect(content, fillPaint)
        }

        // 游戏区外框：把"球在跑的这块"和下方控制带分开，边界一眼可见
        framePaint.color = Color.argb(56, 31, 26, 14)
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(f, dp(12f), dp(12f), framePaint)
        framePaint.style = Paint.Style.FILL

        // 砖块
        val brickW = w / COLS
        val brickH = h * 0.05f
        val top = oy + h * 0.06f
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (bricks[r * COLS + c] == 0) {
                    continue
                }
                val left = ox + c * brickW + dp(3.5f)
                val right = ox + (c + 1) * brickW - dp(3.5f)
                val bt = top + r * brickH + dp(2.5f)
                val bb = top + (r + 1) * brickH - dp(2.5f)
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
        fillPaint.color = 0xFFE8D69C.toInt()
        canvas.drawRoundRect(px - pw / 2, py - dp(7f), px + pw / 2, py - dp(4.6f), dp(4f), dp(4f), fillPaint)

        // 球
        val bx = ox + ballX * w
        val by = oy + ballY * h
        glowPaint.color = 0x33B8860B.toInt()
        canvas.drawCircle(bx, by, dp(13f), glowPaint)
        fillPaint.color = 0xFF1D1D1D.toInt()
        canvas.drawCircle(bx, by, dp(7f), fillPaint)

        if (ctrlVisible()) {
            drawCtrlStrip(canvas, content)
        }
    }

    // ---------------- 控制带 ----------------

    private fun drawCtrlStrip(canvas: Canvas, content: Rect) {
        val cr = ctrlRect(content)

        // 极淡的圆角底：说明"这一条可以操作"，但不是按钮，不抢画面
        fillPaint.color = Color.argb(20, 31, 26, 14)
        canvas.drawRoundRect(cr, dp(13f), dp(13f), fillPaint)
        framePaint.color = Color.argb(46, 31, 26, 14)
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(cr, dp(13f), dp(13f), framePaint)
        framePaint.style = Paint.Style.FILL

        drawHandHint(canvas, cr.centerX(), cr.centerY())
    }

    /**
     * 提示图：一只张开的手掌 + 两侧"‹ ›"箭头，整体很淡；
     * 手掌做极轻的左右呼吸，暗示"在这条带上左右滑"，不用写一堆字。
     */
    private fun drawHandHint(canvas: Canvas, cx: Float, cy: Float) {
        val phase = (System.currentTimeMillis() % 2600L) / 2600f * 6.2832f
        val breath = sin(phase) * dp(5f)
        val active = System.currentTimeMillis() - lastDragMs < 420L
        val inkAlpha = if (active) 118 else 86
        val ink = Color.argb(inkAlpha, 31, 26, 14)

        // 两侧箭头
        framePaint.color = Color.argb(58, 31, 26, 14)
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = dp(1.7f)
        val ay = cy
        val ax1 = cx - dp(25f)
        val ax2 = cx + dp(25f)
        canvas.drawLine(ax1 + dp(4.2f), ay - dp(6f), ax1 - dp(2.4f), ay, framePaint)
        canvas.drawLine(ax1 - dp(2.4f), ay, ax1 + dp(4.2f), ay + dp(6f), framePaint)
        canvas.drawLine(ax2 - dp(4.2f), ay - dp(6f), ax2 + dp(2.4f), ay, framePaint)
        canvas.drawLine(ax2 + dp(2.4f), ay, ax2 - dp(4.2f), ay + dp(6f), framePaint)
        framePaint.style = Paint.Style.FILL

        // 手掌
        canvas.save()
        canvas.translate(cx + breath, cy + dp(1.5f))
        canvas.scale(1.14f, 1.14f)
        fillPaint.color = ink

        // 掌：比原来窄一点，不要一团肉
        canvas.drawRoundRect(
            RectF(-dp(6.2f), -dp(1.4f), dp(6.2f), dp(7.2f)), dp(3.2f), dp(3.2f), fillPaint
        )

        // 四指：细长、高度错开，才看得出手不是一排柱子
        val fw = dp(2.5f)
        val step = dp(2.85f)
        val tops = floatArrayOf(dp(9.5f), dp(12f), dp(11f), dp(8f))
        for (i in 0 until 4) {
            val fx = -dp(5.55f) + i * step
            val ft = -dp(1.4f) - tops[i]
            canvas.drawRoundRect(
                RectF(fx, ft, fx + fw, dp(0.2f)), dp(1.25f), dp(1.25f), fillPaint
            )
        }

        // 拇指：从掌侧斜着伸出去，端点跟掌有重叠，看着才是"连着的"
        canvas.save()
        canvas.rotate(-34f, -dp(5.9f), dp(3.9f))
        canvas.drawRoundRect(
            RectF(-dp(9.8f), dp(1.4f), -dp(6.4f), dp(6.6f)), dp(1.3f), dp(1.3f), fillPaint
        )
        canvas.restore()

        canvas.restore()
    }
}
