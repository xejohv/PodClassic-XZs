package io.github.xejohv.podclassicxzs.widget

import io.github.xejohv.podclassicxzs.values.Colors

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * 无专辑封面时的占位图案：
 * 深褐渐变底 + 一枚清晰、居中、大小适中的白色播放三角。
 * 全部用 Canvas 现场绘制，不依赖位图，因此任意尺寸下边缘都是锐的。
 */
class CoverPlaceholderDrawable(
    private val density: Float,
    private val nominalW: Int = 0,
    private val nominalH: Int = 0
) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Colors.selected_text
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Colors.main
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()

        // 底色跟随机身配色：浅色主题走白底 + 极浅灰（和原版 iPod 白屏一致），
        // 暗黑主题反过来用深色，避免整块惨白
        val dark = Colors.isDark
        val c1 = if (dark) Colors.background_light else Colors.background
        val c2 = if (dark) Colors.background else 0xFFF1F1ED.toInt()
        bgPaint.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.left.toFloat(), b.bottom.toFloat(),
            c1, c2, Shader.TileMode.CLAMP
        )
        canvas.drawRect(b, bgPaint)
        bgPaint.shader = null
        // 音符在浅色底上要用深墨色，否则白音符在白底上看不见
        notePaint.color = if (dark) Colors.selected_text else Colors.text

        // 不再画金线内框：封面图会被铺满放大并裁切，框线会露在屏幕边缘像一条竖线


        // 金色八分音符：整体高取短边的 34%，几何居中
        val s = Math.min(w, h)
        val cx = b.left + w / 2f
        val cy = b.top.toFloat() + h / 2f
        val nh = s * 0.34f
        val left = cx - s * 0.145f
        val top = cy - nh * 0.5f
        val headR = s * 0.055f

        // 音符头
        canvas.drawOval(
            android.graphics.RectF(
                left, top + nh * 0.70f,
                left + headR * 2f, top + nh * 0.70f + headR * 1.55f
            ),
            notePaint
        )

        // 竖杆
        val stemX = left + headR * 1.58f
        val stemW = s * 0.018f
        canvas.drawRect(stemX, top, stemX + stemW, top + nh * 0.80f, notePaint)

        // 旗
        path.reset()
        path.moveTo(stemX + stemW, top)
        path.quadTo(
            stemX + stemW + s * 0.105f, top + nh * 0.14f,
            stemX + stemW + s * 0.078f, top + nh * 0.40f
        )
        path.lineTo(stemX + stemW, top + nh * 0.30f)
        path.close()
        canvas.drawPath(path, notePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    // 名义尺寸，让按 intrinsic 尺寸铺满容器的代码正常工作
    // 名义尺寸：传了容器尺寸就用容器尺寸，这样铺满逻辑算出的缩放比恰好是 1，
    // 占位图不会被放大也不会漂移，三角始终完整居中
    override fun getIntrinsicWidth(): Int = if (nominalW > 0) nominalW else (160f * density).toInt()
    override fun getIntrinsicHeight(): Int = if (nominalH > 0) nominalH else (160f * density).toInt()
}
