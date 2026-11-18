package io.github.xejohv.podclassicxzs.values

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import io.github.xejohv.podclassicxzs.R
import io.github.xejohv.podclassicxzs.base.BaseApplication.Companion.context


@SuppressLint("UseCompatLoadingForDrawables")
object Icons {

    val MENU by lazy { Icon(context.getDrawable(R.drawable.ic_menu_white_24dp)!!) }
    val PREV by lazy { Icon(context.getDrawable(R.drawable.ic_skip_previous_white_24dp)!!) }
    val NEXT by lazy { Icon(context.getDrawable(R.drawable.ic_skip_next_white_24dp)!!) }
    val PAUSE by lazy { Icon(context.getDrawable(R.drawable.ic_pause_white_24dp)!!) }
    val ARROW_WHITE by lazy { Icon(context.getDrawable(R.drawable.ic_keyboard_arrow_right_white_24dp)!!) }
    val ARROW_BLACK by lazy { Icon(context.getDrawable(R.drawable.ic_keyboard_arrow_right_grey_600_24dp)!!) }
    val PLAY_BLUE by lazy { Icon(context.getDrawable(R.drawable.ic_play_arrow_blue_500_18dp)!!) }
    val PAUSE_BLUE by lazy { Icon(context.getDrawable(R.drawable.ic_pause_blue_500_18dp)!!) }
    val PLAY_MODE_SHUFFLE by lazy { Icon(context.getDrawable(R.drawable.ic_shuffle_grey_600_18dp)!!) }
    val PLAY_MODE_SINGLE by lazy { Icon(context.getDrawable(R.drawable.ic_repeat_one_grey_600_18dp)!!) }
    val PLAY_MODE_REPEAT by lazy { Icon(context.getDrawable(R.drawable.ic_repeat_grey_600_18dp)!!) }
    val PLAYING_WHITE by lazy { Icon(context.getDrawable(R.drawable.ic_volume_up_white_18dp)!!) }
    val PLAYING_BLACK by lazy { Icon(context.getDrawable(R.drawable.ic_volume_up_grey_600_18dp)!!) }
    val VOLUME_DOWN by lazy { Icon(context.getDrawable(R.drawable.ic_volume_down_grey_600_18dp)!!) }
    val VOLUME_UP by lazy { Icon(context.getDrawable(R.drawable.ic_volume_up_grey_600_18dp)!!) }

    //val STOP_TIME by lazy { Icon(context.getDrawable(R.drawable.ic_access_time_grey_600_18dp)!!) }
    val DEFAULT by lazy { Icon(context.getDrawable(R.drawable.ic_default)!!) }

    /**
     * 无封面时的占位图案：一枚实心深褐八分音符。
     * 原来是一块冷白色三角，在金色底上对比度只有 1.45:1，看着糊；
     * 深褐 #2E2514 与金底 #E8D69C 的对比度约 10.45:1，清楚又不抢眼。
     */
    val EMPTY: Bitmap by lazy {
        val size = Values.IMAGE_WIDTH
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF2E2514.toInt()
        val s = size.toFloat()
        // 音符头（椭圆）
        canvas.drawOval(
            android.graphics.RectF(s * 0.255f, s * 0.545f, s * 0.465f, s * 0.695f),
            p
        )
        // 竖杆
        canvas.drawRect(s * 0.415f, s * 0.285f, s * 0.455f, s * 0.635f, p)
        // 旗
        val path = android.graphics.Path()
        path.moveTo(s * 0.455f, s * 0.285f)
        path.lineTo(s * 0.685f, s * 0.395f)
        path.lineTo(s * 0.665f, s * 0.475f)
        path.lineTo(s * 0.455f, s * 0.405f)
        path.close()
        canvas.drawPath(path, p)
        bmp
    }

    fun getBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            if (drawable.
                opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        )
        val canvas = Canvas(bitmap)
        //drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        return bitmap
    }

    class Icon(val drawable: Drawable) {
        init {
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        }

        val bitmap by lazy { getBitmap(drawable) }
        val height = bitmap.height
        val width = bitmap.width
    }
}