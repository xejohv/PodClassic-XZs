package io.github.xejohv.podclassicxzs.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * 飞机大战（重做版）：
 * - 全自动开火，玩家只管走位；◀ ▶ 移动，● 清屏炸弹
 * - 敌机有大小两级、会反击开火，被击中/爆炸都有粒子反馈
 * - 子弹画成发光的白色曳光弹，保证一眼就能看清
 */
class ShooterGame(context: Context) : GameHost(context, "飞机大战") {

    private class Bullet(var x: Float, var y: Float)

    private class Enemy(var x: Float, var y: Float, var vy: Float, var vx: Float, var hp: Int, var r: Float)

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var size: Float)

    private var playerX = 0.5f

    /** 纵向位置（0 = 画面顶，1 = 战况带上沿）：飞机可以上下飞，不再只能左右平移 */
    private var playerY = 0.82f
    private var bullets = ArrayList<Bullet>()
    private var eBullets = ArrayList<Bullet>()
    private var enemies = ArrayList<Enemy>()
    private var particles = ArrayList<Particle>()
    private var fireTimer = 0f
    private var spawnTimer = 0f
    private var enemyFireTimer = 0f
    private var lives = 3
    private var bombs = 2
    private var hitFlash = 0f
    private var invincible = 0f
    private var elapsed = 0f

    private val path = Path()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 不放底部操作条：画面吃满到底，炸弹按钮画在左下角（拇指原位可达） */
    override fun barButtons(): List<BarBtn> = emptyList()

    override fun barStyle(): Int = BAR_NONE

    /** 飞机会跟着手指上下飞，所以上滑折叠只在顶部标题区生效，免得一往上飞就被折叠 */
    override fun collapseSwipeFromTopOnly(): Boolean = true

    /** 左下角炸弹按钮的圆心与半径（绘制与命中共用） */
    private fun bombCX(content: Rect): Float = content.left + dp(56f)
    private fun bombCY(content: Rect): Float = content.bottom - dp(28f)
    private fun bombR(): Float = dp(20f)

    override fun resetGame() {
        playerX = 0.5f
        playerY = 0.82f
        bullets.clear()
        eBullets.clear()
        enemies.clear()
        particles.clear()
        fireTimer = 0f
        spawnTimer = 0f
        enemyFireTimer = 0f
        lives = 3
        bombs = 2
        score = 0
        gameOver = false
        hitFlash = 0f
        invincible = 0f
        elapsed = 0f
    }

    private fun spawnEnemy() {
        val big = Random.nextFloat() < 0.28f
        enemies.add(
            Enemy(
                0.12f + Random.nextFloat() * 0.76f,
                -0.08f,
                if (big) 0.075f else 0.10f + Random.nextFloat() * 0.08f,
                (Random.nextFloat() - 0.5f) * 0.06f,
                if (big) 3 else 1,
                if (big) 0.062f else 0.042f
            )
        )
    }

    private fun boom(x: Float, y: Float, n: Int) {
        repeat(n) {
            particles.add(
                Particle(
                    x, y,
                    (Random.nextFloat() - 0.5f) * 0.35f,
                    (Random.nextFloat() - 0.5f) * 0.35f,
                    320f + Random.nextFloat() * 260f,
                    0.008f + Random.nextFloat() * 0.012f
                )
            )
        }
    }

    override fun update(dt: Float) {
        val sec = dt / 1000f
        elapsed += dt
        extraLine = ""

        fireTimer += dt
        if (fireTimer >= 380f) {
            fireTimer = 0f
            bullets.add(Bullet(playerX, playerY - 0.03f))
        }

        spawnTimer += dt
        val spawnGap = max(340f, 900f - elapsed / 1000f * 34f)
        if (spawnTimer >= spawnGap) {
            spawnTimer = 0f
            spawnEnemy()
        }

        enemyFireTimer += dt
        if (enemyFireTimer >= 1100f && enemies.isNotEmpty()) {
            enemyFireTimer = 0f
            val e = enemies[Random.nextInt(enemies.size)]
            if (e.y > 0.02f && e.y < 0.8f) {
                eBullets.add(Bullet(e.x, e.y + 0.03f))
            }
        }

        for (b in bullets) {
            b.y -= 0.95f * sec
        }
        bullets.removeAll { it.y < 0.02f }

        for (b in eBullets) {
            b.y += 0.24f * sec
        }
        eBullets.removeAll { it.y > 1.0f }

        for (e in enemies) {
            e.y += e.vy * sec
            e.x += e.vx * sec
            if (e.x < 0.06f) {
                e.x = 0.06f
                e.vx = -e.vx
            }
            if (e.x > 0.94f) {
                e.x = 0.94f
                e.vx = -e.vx
            }
        }

        // 子弹命中敌机
        val deadBullets = ArrayList<Bullet>()
        val deadEnemies = ArrayList<Enemy>()
        for (b in bullets) {
            for (e in enemies) {
                if (deadEnemies.contains(e) || deadBullets.contains(b)) {
                    continue
                }
                if (abs(b.x - e.x) < e.r + 0.012f && abs(b.y - e.y) < e.r + 0.02f) {
                    deadBullets.add(b)
                    e.hp--
                    if (e.hp <= 0) {
                        deadEnemies.add(e)
                        addScore(if (e.r > 0.05f) 40 else 18)
                        boom(e.x, e.y, if (e.r > 0.05f) 14 else 8)
                    } else {
                        boom(e.x, e.y, 3)
                    }
                }
            }
        }
        bullets.removeAll { deadBullets.contains(it) }
        enemies.removeAll { deadEnemies.contains(it) }

        // 敌机飞出底部
        val escaped = enemies.filter { it.y > 0.98f }
        if (escaped.isNotEmpty()) {
            enemies.removeAll { it.y > 0.98f }
            lives -= escaped.size
            if (lives <= 0) {
                lives = 0
                finishGame()
            }
        }

        // 敌机撞我方
        if (invincible <= 0f) {
            val rammed = enemies.firstOrNull {
                abs(it.x - playerX) < it.r + 0.05f && abs(it.y - playerY) < it.r + 0.06f
            }
            if (rammed != null) {
                enemies.remove(rammed)
                boom(rammed.x, rammed.y, 12)
                lives--
                invincible = 900f
                hitFlash = 240f
                if (lives <= 0) {
                    lives = 0
                    finishGame()
                }
            }
        }

        // 敌弹打到我方
        if (invincible <= 0f) {
            val hit = eBullets.firstOrNull { abs(it.x - playerX) < 0.05f && abs(it.y - playerY) < 0.055f }
            if (hit != null) {
                eBullets.remove(hit)
                boom(hit.x, hit.y, 8)
                lives--
                invincible = 900f
                hitFlash = 240f
                if (lives <= 0) {
                    lives = 0
                    finishGame()
                }
            }
        }

        if (hitFlash > 0f) {
            hitFlash -= dt
        }
        if (invincible > 0f) {
            invincible -= dt
        }

        // 粒子
        for (p in particles) {
            p.x += p.vx * sec
            p.y += p.vy * sec
            p.life -= dt
        }
        particles.removeAll { it.life <= 0f }
    }

    /** 手指拖动：飞机直接跟到手指的 x/y（纵向也能飞，不再是只能左右平移） */
    override fun onDragContent(x: Float, y: Float): Boolean {
        val c = contentRect()
        if (c.width() <= 0 || c.height() <= 0) {
            return false
        }
        playerX = ((x - c.left) / c.width().toFloat()).coerceIn(0.08f, 0.92f)
        val hudTop = c.bottom - dp(56f)
        val oy = c.top + dp(10f)
        val mapH = (hudTop - oy).coerceAtLeast(dp(120f))
        playerY = ((y - oy) / mapH).coerceIn(0.26f, 0.94f)
        return true
    }

    override fun onSlide(slideVal: Int): Boolean {
        // 碟片左右键仍可作为备用微调
        playerX += if (slideVal > 0) 0.09f else -0.09f
        playerX = playerX.coerceIn(0.08f, 0.92f)
        return true
    }

    override fun onStart(): Boolean = true

    override fun onPrimary(): Boolean {
        if (bombs <= 0) {
            return true
        }
        bombs--
        addScore(enemies.size * 12)
        for (e in enemies) {
            boom(e.x, e.y, 10)
        }
        enemies.clear()
        eBullets.clear()
        hitFlash = 160f
        return true
    }

    override fun onSecondary(): Boolean {
        return onPrimary()
    }

    /** 画面内左下角炸弹按钮的点击 */
    override fun onTapContent(x: Float, y: Float): Boolean {
        if (!expanded || !started || gameOver) {
            return false
        }
        val c = contentRect()
        val cx = bombCX(c)
        val cy = bombCY(c)
        val rr = bombR() + dp(10f)
        if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= rr * rr) {
            return onPrimary()
        }
        return false
    }

    override fun startHint(): String = ""

    // ---------------- 绘制 ----------------

    override fun drawGame(canvas: Canvas, content: Rect) {
        val w = content.width().toFloat()
        val h = content.height().toFloat()
        val ox = content.left.toFloat()
        val oy = content.top.toFloat()
        // 底部 HUD 单独占一条，游戏内容只映射到它以上的游戏区，避免飞机与炸弹/生命重叠
        val hudTop = content.bottom - dp(56f)
        // 全屏与半屏都用「HUD 以上」的整块高度：敌人和战机都不会落到炸弹/生命那一行，
        // 全屏时画面因此整体下移、铺满操作条以上区域
        val mapH = (hudTop - oy).coerceAtLeast(dp(120f))

        // 背景星点（缓慢下移，给画面一点纵深）
        fillPaint.color = 0x1F1D1D1D
        val stars = 26
        for (i in 0 until stars) {
            val sx = ((i * 37) % 100) / 100f
            val sy = (((i * 61) % 100) / 100f + (elapsed / 1000f) * 0.06f) % 1f
            canvas.drawCircle(ox + sx * w, oy + sy * mapH, dp(1.2f), fillPaint)
        }

        // 受击闪白
        if (hitFlash > 0f) {
            fillPaint.color = 0x241D1D1D
            canvas.drawRect(content, fillPaint)
        }

        // 敌机
        for (e in enemies) {
            drawEnemy(canvas, ox + e.x * w, oy + e.y * mapH, e.r * w, e.r > 0.05f)
        }

        // 敌弹（暗色弹体 + 亮边，和白色曳光弹区分开）
        for (b in eBullets) {
            val cx = ox + b.x * w
            val cy = oy + b.y * mapH
            fillPaint.color = 0xFF3A2E16.toInt()
            canvas.drawRoundRect(cx - w * 0.011f, cy - dp(7f), cx + w * 0.011f, cy + dp(7f), dp(4f), dp(4f), fillPaint)
            fillPaint.color = 0xFFFF6A4A.toInt()
            canvas.drawCircle(cx, cy, dp(3.2f), fillPaint)
        }

        // 我方子弹：外层光晕 + 白色曳光
        for (b in bullets) {
            val cx = ox + b.x * w
            val cy = oy + b.y * mapH
            glowPaint.color = 0x33B8860B.toInt()
            canvas.drawRoundRect(cx - w * 0.014f, cy - dp(15f), cx + w * 0.014f, cy + dp(9f), dp(6f), dp(6f), glowPaint)
            fillPaint.color = 0xFF1D1D1D.toInt()
            canvas.drawRoundRect(cx - w * 0.006f, cy - dp(13f), cx + w * 0.006f, cy + dp(7f), dp(4f), dp(4f), fillPaint)
        }

        // 爆炸粒子
        for (p in particles) {
            val alpha = (p.life / 500f).coerceIn(0f, 1f)
            glowPaint.color = 0xFFF2B33C.toInt()
            glowPaint.alpha = (255 * alpha).toInt()
            canvas.drawCircle(ox + p.x * w, oy + p.y * mapH, p.size * w, glowPaint)
        }

        // 底部状态区：不再用虚线"划一刀"，改成一条与画面同色系的浅圆角信息条，
        // 让炸弹按钮和战况数字有明确的落脚处（上下都居中在条内）
        if (started) {
            fillPaint.color = Color.argb(22, 31, 26, 14)
            canvas.drawRoundRect(
                RectF(content.left + dp(10f), hudTop + dp(4f),
                    content.right - dp(10f), content.bottom - dp(4f)),
                dp(12f), dp(12f), fillPaint
            )
            glowPaint.color = Color.argb(46, 31, 26, 14)
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = dp(1f)
            canvas.drawRoundRect(
                RectF(content.left + dp(10f), hudTop + dp(4f),
                    content.right - dp(10f), content.bottom - dp(4f)),
                dp(12f), dp(12f), glowPaint
            )
            glowPaint.style = Paint.Style.FILL
        }

        // 炸弹按钮与生命值只在开局后出现：预览态还没有战况，画出来只会挤在飞机尾部
        if (started) {
            // 左下角：炸弹按钮（图标小一号，拇指原位可达）
            val bcx = bombCX(content)
            val bcy = bombCY(content)
            val br = bombR()
            fillPaint.color = 0xFF2E2514.toInt()
            canvas.drawCircle(bcx, bcy, br, fillPaint)
            glowPaint.color = 0xFF9A6B00.toInt()
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = dp(2f)
            canvas.drawCircle(bcx, bcy, br, glowPaint)
            glowPaint.style = Paint.Style.FILL

            // 按钮里画一枚真正的炸弹（圆弹体 + 引信火花），一眼看得出是"清屏炸弹"
            drawBombGlyph(canvas, bcx, bcy + dp(3f), dp(13f), 0xFFFFFDF2.toInt(), 0xFFFF6A4A.toInt())

            // 右侧战况：×颗数 · 生命 ×n，和按钮同一条水平中线
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = dp(15f)
            textPaint.color = 0xFF1F1A0E.toInt()
            val tx = bcx + br + dp(14f)
            val ty = bcy - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText("×$bombs    生命 ×$lives", tx, ty, textPaint)
        }

        // 我方战机（闪烁表示无敌时间）：纵向跟着手指走，尾焰不探进底部战况条
        val blink = invincible > 0f && ((invincible.toInt() / 120) % 2 == 0)
        if (!blink) {
            val py = oy + mapH * playerY.coerceIn(0.26f, 0.94f)
            drawPlayer(canvas, ox + playerX * w, py, w * 0.085f)
        }
    }

    /**
     * 炸弹图标：航空炸弹剪影——泪滴弹体（鼻尖朝下）+ 两侧尾翼 + 顶端引信火花。
     * 小尺寸下剪影比"圆球 + ✹"更容易一眼认出是炸弹。
     */
    private fun drawBombGlyph(canvas: Canvas, cx: Float, cy: Float, s: Float, body: Int, spark: Int) {
        // 弹体
        path.reset()
        path.moveTo(cx - s * 0.32f, cy - s * 0.24f)
        path.quadTo(cx - s * 0.56f, cy + s * 0.32f, cx, cy + s * 0.94f)
        path.quadTo(cx + s * 0.56f, cy + s * 0.32f, cx + s * 0.32f, cy - s * 0.24f)
        path.close()
        fillPaint.color = body
        canvas.drawPath(path, fillPaint)
        // 尾翼（左右各一片，斜向上张开）
        path.reset()
        path.moveTo(cx - s * 0.32f, cy - s * 0.24f)
        path.lineTo(cx - s * 0.60f, cy - s * 0.62f)
        path.lineTo(cx - s * 0.36f, cy - s * 0.62f)
        path.lineTo(cx - s * 0.20f, cy - s * 0.34f)
        path.close()
        canvas.drawPath(path, fillPaint)
        path.reset()
        path.moveTo(cx + s * 0.32f, cy - s * 0.24f)
        path.lineTo(cx + s * 0.60f, cy - s * 0.62f)
        path.lineTo(cx + s * 0.36f, cy - s * 0.62f)
        path.lineTo(cx + s * 0.20f, cy - s * 0.34f)
        path.close()
        canvas.drawPath(path, fillPaint)
        // 引信火花：顶端三道短辐射
        glowPaint.color = spark
        glowPaint.style = Paint.Style.STROKE
        glowPaint.strokeWidth = dp(1.6f)
        for (i in -1..1) {
            val a = (-90f + i * 34f) * 0.017453292f
            canvas.drawLine(
                cx + (s * 0.14f) * kotlin.math.cos(a), cy - s * 0.50f + (s * 0.10f) * kotlin.math.sin(a),
                cx + (s * 0.34f) * kotlin.math.cos(a), cy - s * 0.50f + (s * 0.34f) * kotlin.math.sin(a),
                glowPaint
            )
        }
        glowPaint.style = Paint.Style.FILL
    }

    private fun drawPlayer(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        // 尾焰
        glowPaint.color = 0x66C08A16.toInt()
        canvas.drawCircle(cx, cy + size * 0.72f, size * 0.20f, glowPaint)
        glowPaint.color = 0x33C08A16.toInt()
        canvas.drawCircle(cx, cy + size * 0.86f, size * 0.30f, glowPaint)

        // 机身：深色实心 + 亮金描边，保证在金色背景上清楚
        path.reset()
        path.moveTo(cx, cy - size)
        path.lineTo(cx + size * 0.28f, cy - size * 0.10f)
        path.lineTo(cx + size * 0.95f, cy + size * 0.34f)
        path.lineTo(cx + size * 0.34f, cy + size * 0.40f)
        path.lineTo(cx + size * 0.20f, cy + size * 0.86f)
        path.lineTo(cx - size * 0.20f, cy + size * 0.86f)
        path.lineTo(cx - size * 0.34f, cy + size * 0.40f)
        path.lineTo(cx - size * 0.95f, cy + size * 0.34f)
        path.lineTo(cx - size * 0.28f, cy - size * 0.10f)
        path.close()

        fillPaint.color = 0xFF1F1A0E.toInt()
        canvas.drawPath(path, fillPaint)

        glowPaint.color = 0xFF9A6B00.toInt()
        glowPaint.style = Paint.Style.STROKE
        glowPaint.strokeWidth = dp(2.4f)
        canvas.drawPath(path, glowPaint)
        glowPaint.style = Paint.Style.FILL

        // 座舱
        fillPaint.color = 0xFFFFFDF2.toInt()
        canvas.drawRoundRect(
            cx - size * 0.13f, cy - size * 0.52f, cx + size * 0.13f, cy - size * 0.06f,
            size * 0.14f, size * 0.14f, fillPaint
        )
    }

    private fun drawEnemy(canvas: Canvas, cx: Float, cy: Float, size: Float, big: Boolean) {
        path.reset()
        path.moveTo(cx, cy + size * 1.05f)
        path.lineTo(cx + size * 0.30f, cy + size * 0.10f)
        path.lineTo(cx + size * 0.92f, cy - size * 0.22f)
        path.lineTo(cx + size * 0.30f, cy - size * 0.34f)
        path.lineTo(cx, cy - size * 1.0f)
        path.lineTo(cx - size * 0.30f, cy - size * 0.34f)
        path.lineTo(cx - size * 0.92f, cy - size * 0.22f)
        path.lineTo(cx - size * 0.30f, cy + size * 0.10f)
        path.close()

        fillPaint.color = if (big) 0xFF4A3A16.toInt() else 0xFF5A4C22.toInt()
        canvas.drawPath(path, fillPaint)

        glowPaint.color = if (big) 0xFFB0572B.toInt() else 0xFF8A6A1A.toInt()
        glowPaint.style = Paint.Style.STROKE
        glowPaint.strokeWidth = dp(2f)
        canvas.drawPath(path, glowPaint)
        glowPaint.style = Paint.Style.FILL

        // 机腹小灯
        fillPaint.color = 0xFFFF6A4A.toInt()
        canvas.drawCircle(cx, cy - size * 0.15f, size * 0.16f, fillPaint)
    }
}
