package io.github.xejohv.podclassicxzs.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import io.github.xejohv.podclassicxzs.base.ScreenView
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class TetrisGame(context: Context) : GameHost(context, "俄罗斯方块"), ScreenView {

    companion object {
        private const val COLS = 10
        /** 经典掌机液晶是 10×20 的窄长屏，比 10×16 更像那块砖头机 */
        private const val ROWS = 20

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

        // 单色液晶：七种形状都用同一支墨绿"墨水"，靠形状本身区分，不再上金边
        private val COLORS = intArrayOf(
            0xFF2C3A24.toInt(),
            0xFF2C3A24.toInt(),
            0xFF2C3A24.toInt(),
            0xFF2C3A24.toInt(),
            0xFF2C3A24.toInt(),
            0xFF2C3A24.toInt(),
            0xFF2C3A24.toInt()
        )

        /** 液晶底色 / 边框 / 墨色 */
        private const val LCD_BG_TOP = 0xFFD4DEB8.toInt()
        private const val LCD_BG_BOTTOM = 0xFFC4D0A1.toInt()
        private const val LCD_FRAME = 0xFF3A452D.toInt()
        private const val LCD_INK = 0xFF2C3A24.toInt()
        private const val LCD_INK_SOFT = 0x882C3A24.toInt()
        private const val LCD_GRID = 0x2E3A452D.toInt()
    }

    private val board = IntArray(COLS * ROWS)
    private var pieceShape = 0
    private var pieceRot = 0
    private var pieceX = 3
    private var pieceY = 0
    private var nextShape = 0
    private var dropTimer = 0f
    private var lines = 0

    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 手柄式操作条：左十字（上=旋转 / 下=软降，按住连发 / 左右=移动），右侧大圆键=直落
    private val bar = listOf(
        BarBtn("↑") { if (expanded && started && !gameOver) onPrimary() },
        BarBtn("↓", { if (expanded && started && !gameOver) softDrop() },
            { if (expanded && started && !gameOver) softDrop() },
            0),
        BarBtn("←") { if (expanded && started && !gameOver) onSlide(-1) },
        BarBtn("→") { if (expanded && started && !gameOver) onSlide(1) },
        BarBtn("直落", { if (expanded && started && !gameOver) hardDrop() }, 5)
    )

    override fun barButtons(): List<BarBtn> = bar

    override fun barStyle(): Int = BAR_PAD

    override fun startHint(): String = "↑旋转 · ↓软降（按住连发）· 画面下甩 直落"

    /** 起手就是开新局：清盘 + 出第一块，别带着上一局的残留 */
    override fun onStart(): Boolean {
        resetGame()
        return true
    }

    private fun softDrop() {
        if (!collides(pieceShape, pieceRot, pieceX, pieceY + 1)) {
            pieceY++
            dropTimer = 0f
        } else {
            lockPiece()
        }
    }

    override fun resetGame() {
        board.fill(0)
        score = 0
        lines = 0
        gameOver = false
        dropTimer = 0f
        nextShape = Random.nextInt(SHAPES.size)
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
        pieceShape = nextShape
        nextShape = Random.nextInt(SHAPES.size)
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

    /** 全屏态往下甩一把 = 直落，比找那颗大圆键快 */
    override fun onFlickDown(): Boolean {
        if (!expanded || !started || gameOver) return false
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
        // 落到底给一下轻震，手上能确认"这一块已经砸下去了"
        try {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (e: Throwable) {
        }
        lockPiece()
    }

    // ---------------- 存档 ----------------

    override fun supportsSave(): Boolean = true

    /** 棋盘 200 格压成 200 个 0/1 字符；当前方块与下一个方块单独记 */
    override fun onSaveState(): String {
        val sb = StringBuilder()
        sb.append(lines).append('|')
        sb.append(score).append('|')
        sb.append(pieceShape).append(',').append(pieceRot).append(',')
            .append(pieceX).append(',').append(pieceY).append('|')
        sb.append(nextShape).append('|')
        for (i in board.indices) {
            sb.append(if (board[i] == 0) '0' else '1')
        }
        return sb.toString()
    }

    override fun onRestoreState(data: String) {
        val parts = data.split('|')
        if (parts.size < 5) {
            throw IllegalArgumentException("bad tetris save")
        }
        lines = parts[0].toInt()
        score = parts[1].toInt()
        val p = parts[2].split(',')
        pieceShape = p[0].toInt().coerceIn(0, SHAPES.size - 1)
        pieceRot = p[1].toInt().coerceIn(0, 3)
        pieceX = p[2].toInt()
        pieceY = p[3].toInt()
        nextShape = parts[3].toInt().coerceIn(0, SHAPES.size - 1)
        board.fill(0)
        val cells = parts[4]
        for (i in board.indices) {
            if (i < cells.length && cells[i] == '1') {
                board[i] = COLORS[0]
            }
        }
        dropTimer = 0f
        gameOver = false
        extraLine = "消行 $lines"
    }

    override fun drawGame(canvas: Canvas, content: Rect) {
        // 整块屏就是一块老掌机液晶：淡黄绿底、深墨绿细框，棋盘和右栏都长在屏上
        val sidePad = dp(9f)       // 液晶框到屏幕左右边界的边距：左右必须一样宽
        val framePad = dp(7f)      // 框内留白（棋盘/分隔线离框线的那一圈）
        val contentH = content.height()
        val compactMode = contentH < dp(360f)
        // 顶部至少留 7dp：框顶线画在 top-framePad，留少了会被 canvas 裁掉，框线就"丢失"了
        val topPad = dp(9f)
        val bottomPad = if (compactMode) dp(13f) else dp(21f)
        val availH = contentH - topPad - bottomPad

        // 格子取整数像素：小数格宽会让最后一条格线和框边差半像素，
        // 圈出来的就是"格子跟外框对不上、边上留一条没格子的空带"。
        // 棋盘也直接贴着分隔线，中间不再留空档。
        var scrL = content.left + sidePad
        var scrR = content.right - sidePad
        val cellPx: Int
        if (compactMode) {
            cellPx = floor(availH / ROWS).toInt().coerceAtLeast(6)
            val panelW = max(dp(64f), cellPx.toFloat() * COLS * 0.85f)
            val compactW = cellPx.toFloat() * COLS + framePad * 2f + panelW
            scrL = content.left + (content.width() - compactW) / 2f
            scrR = scrL + compactW
        } else {
            val innerW = scrR - scrL - framePad * 2f
            val boardAvailW = innerW - innerW * 0.26f
            cellPx = min(floor(boardAvailW / COLS), floor(availH / ROWS)).toInt().coerceAtLeast(6)
        }
        val cell = cellPx.toFloat()
        val boardW = cell * COLS
        val boardH = cell * ROWS
        val left = scrL + framePad          // 第一列贴着框内沿
        val top = content.top + topPad

        val scrT = top - framePad
        val scrB = top + boardH + framePad

        // 液晶底板（竖向极轻渐变，别做成死板的纯色）
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = LinearGradient(
            scrL, scrT, scrL, scrB,
            intArrayOf(LCD_BG_TOP, LCD_BG_BOTTOM),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(scrL, scrT, scrR, scrB), dp(5f), dp(5f), fillPaint)
        fillPaint.shader = null

        // 屏框
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(2f)
        fillPaint.color = LCD_FRAME
        canvas.drawRoundRect(RectF(scrL, scrT, scrR, scrB), dp(5f), dp(5f), fillPaint)
        fillPaint.style = Paint.Style.FILL

        // 棋盘点阵格：只有交叉处的淡墨线，像液晶的像素栅格
        gridPaint.color = LCD_GRID
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

        // 左右分栏：棋盘 / 信息栏之间一条竖线，像掌机屏上印的分区线
        val splitX = left + boardW
        fillPaint.color = LCD_FRAME
        fillPaint.alpha = 210
        canvas.drawRect(splitX, scrT + dp(3f), splitX + dp(1.3f), scrB - dp(3f), fillPaint)
        fillPaint.alpha = 255

        // 右栏：直接在液晶上用墨色写，不再铺白底卡片
        drawPanel(
            canvas,
            splitX + dp(7f),
            scrR - dp(10f),
            top,
            boardH,
            compactMode
        )
    }

    /** 右侧竖栏：得分 / 进度 / 等级 / 下一个 / 当前；直接写在液晶上，画面矮时收成紧凑版 */
    private fun drawPanel(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        availH: Float,
        compact: Boolean
    ) {
        val w = right - left
        if (w <= dp(40f)) {
            return
        }
        val padX = dp(6f)
        val x = left + padX
        val textW = w - padX * 2f
        var y = top + dp(4f)

        textPaint.textAlign = Paint.Align.LEFT

        // 得分
        textPaint.color = LCD_INK_SOFT
        textPaint.textSize = dp(11f)
        canvas.drawText("得分", x, y + dp(11f), textPaint)
        textPaint.color = LCD_INK
        textPaint.textSize = dp(19f)
        canvas.drawText(score.toString(), x, y + dp(33f), textPaint)
        y += dp(40f)

        if (compact) {
            // 折叠预览态：只留得分 + 等级，别把下面几栏挤到画面外
            textPaint.color = LCD_INK_SOFT
            textPaint.textSize = dp(11f)
            canvas.drawText("等级", x, y + dp(10f), textPaint)
            textPaint.color = LCD_INK
            textPaint.textSize = dp(16f)
            canvas.drawText((1 + lines / 10).toString(), x, y + dp(27f), textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            return
        }

        // 进度（每 10 行一级，条内按 0~9 行填充）
        textPaint.color = LCD_INK_SOFT
        textPaint.textSize = dp(11f)
        canvas.drawText("进度", x, y + dp(10f), textPaint)
        y += dp(14f)
        val barH = dp(6f)
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(1f)
        fillPaint.color = LCD_INK_SOFT
        canvas.drawRoundRect(RectF(x, y, x + textW, y + barH), barH / 2f, barH / 2f, fillPaint)
        fillPaint.style = Paint.Style.FILL
        val prog = (lines % 10) / 10f
        if (prog > 0f) {
            fillPaint.color = LCD_INK
            canvas.drawRoundRect(RectF(x, y, x + textW * prog, y + barH), barH / 2f, barH / 2f, fillPaint)
        }
        y += barH + dp(8f)

        // 等级
        textPaint.color = LCD_INK_SOFT
        textPaint.textSize = dp(11f)
        canvas.drawText("等级", x, y + dp(10f), textPaint)
        textPaint.color = LCD_INK
        textPaint.textSize = dp(16f)
        canvas.drawText((1 + lines / 10).toString(), x, y + dp(27f), textPaint)
        y += dp(34f)

        // 下一个
        textPaint.color = LCD_INK_SOFT
        textPaint.textSize = dp(11f)
        canvas.drawText("下一个", x, y + dp(10f), textPaint)
        y += dp(14f)
        val miniH = dp(42f)
        fillPaint.color = 0x1F2C3A24
        canvas.drawRoundRect(RectF(x, y, x + textW, y + miniH), dp(4f), dp(4f), fillPaint)
        drawMini(canvas, nextShape, 0, x, y, textW, miniH)
        y += miniH + dp(8f)

        // 当前
        textPaint.color = LCD_INK_SOFT
        textPaint.textSize = dp(11f)
        canvas.drawText("当前", x, y + dp(10f), textPaint)
        y += dp(14f)
        fillPaint.color = 0x1F2C3A24
        canvas.drawRoundRect(RectF(x, y, x + textW, y + miniH), dp(4f), dp(4f), fillPaint)
        drawMini(canvas, pieceShape, pieceRot, x, y, textW, miniH)

        textPaint.textAlign = Paint.Align.LEFT
    }

    /** 迷你形状预览：按实际占用的行列居中，不按 4x4 硬撑 */
    private fun drawMini(canvas: Canvas, shape: Int, rot: Int, left: Float, top: Float, w: Float, h: Float) {
        val cells = cellsOf(shape, rot)
        var minC = 4
        var maxC = -1
        var minR = 4
        var maxR = -1
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (cells[r * 4 + c]) {
                    if (c < minC) minC = c
                    if (c > maxC) maxC = c
                    if (r < minR) minR = r
                    if (r > maxR) maxR = r
                }
            }
        }
        if (maxC < 0) {
            return
        }
        val cols = maxC - minC + 1
        val rows = maxR - minR + 1
        val mc = min(w / cols, h / rows) * 0.84f
        val ox = left + (w - mc * cols) / 2f
        val oy = top + (h - mc * rows) / 2f
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (cells[r * 4 + c]) {
                    drawBlock(canvas, ox + (c - minC) * mc, oy + (r - minR) * mc, mc, COLORS[shape])
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

    /** 落点预览：只描边不填色，像液晶上的一块淡淡轮廓 */
    private fun drawGhost(canvas: Canvas, left: Float, top: Float, cell: Float) {
        val gy = ghostY()
        if (gy <= pieceY) {
            return
        }
        val cells = cellsOf(pieceShape, pieceRot)
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(1.6f)
        fillPaint.color = 0x552C3A24
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (cells[r * 4 + c]) {
                    val x = left + (pieceX + c) * cell
                    val y = top + (gy + r) * cell
                    canvas.drawRect(
                        x + cell * 0.16f, y + cell * 0.16f,
                        x + cell * 0.84f, y + cell * 0.84f, fillPaint
                    )
                }
            }
        }
        fillPaint.style = Paint.Style.FILL
    }

    /** 液晶像素块：实心墨色、直角、块间留一点缝，凑近看就是一块块点阵 */
    private fun drawBlock(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        fillPaint.color = color
        canvas.drawRect(
            x + size * 0.08f, y + size * 0.08f,
            x + size * 0.92f, y + size * 0.92f, fillPaint
        )
    }
}
