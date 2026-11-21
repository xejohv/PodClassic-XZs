package io.github.xejohv.podclassicxzs.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.util.MediaStoreUtil
import io.github.xejohv.podclassicxzs.values.Colors
import io.github.xejohv.podclassicxzs.values.Strings
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/**
 * 照片墙（带缩略图）。
 *
 * 原来的照片页是一列纯文件名，跟"文件管理器"没区别，看不出拍的是什么。
 * 原版 iPod classic 的照片页其实是封面墙：3 列格子、每格一张缩略图、底下一条文件名。
 * 这里就照那个来：3×2 网格、圆角缩略图、选中格加白框并提亮，
 * 左右滑在格子里移动（跨页自动翻），中键进大图。
 *
 * 缩略图异步解码 + 内存缓存（LruCache），不解原图，320px 长边足够网格用。
 */
@SuppressLint("ViewConstructor")
class PhotoGridView(context: Context) : View(context), ScreenView {

    companion object {
        private const val COLS = 3
        private const val ROWS = 2
        private const val PAGE = COLS * ROWS
        /** 缩略图长边目标像素 */
        private const val THUMB_PX = 360
    }

    private val photos: ArrayList<File> = MediaStoreUtil.getPhotoList()
    private var sel = 0
    private var page = 0
    private var downX = 0f
    private var downY = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val src = Rect()

    /** 缩略图缓存：key = 绝对路径 */
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val pool = Executors.newFixedThreadPool(2)
    @Volatile
    private var alive = true

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    override fun getTitle(): String = Strings.PHOTO

    override fun enter(): Boolean {
        if (photos.isEmpty()) return false
        Core.addView(ImageView(context, photos, sel))
        return true
    }

    override fun enterLongClick(): Boolean = false

    override fun slide(slideVal: Int): Boolean {
        if (photos.isEmpty()) return false
        val next = sel + slideVal
        if (next < 0 || next >= photos.size) return false
        sel = next
        page = sel / PAGE
        loadPage()
        invalidate()
        return true
    }

    override fun onViewAdd() {
        alive = true
        loadPage()
    }

    override fun onViewDelete() {
        alive = false
        pool.shutdownNow()
        cache.evictAll()
    }

    override fun onViewRemove() {
        alive = true
    }

    // ---------------- 缩略图加载 ----------------

    private fun loadPage() {
        if (photos.isEmpty()) return
        val from = page * PAGE
        val to = min(from + PAGE, photos.size)
        for (i in from until to) {
            requestThumb(photos[i])
        }
    }

    private fun requestThumb(file: File) {
        val key = file.absolutePath
        if (cache.get(key) != null) return
        pool.execute {
            if (!alive) return@execute
            val bmp = decodeThumb(file)
            if (bmp == null) return@execute
            cache.put(key, bmp)
            post { invalidate() }
        }
    }

    private fun decodeThumb(file: File): Bitmap? {
        try {
            if (!file.exists()) return null
            val opt = BitmapFactory.Options()
            opt.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, opt)
            if (opt.outWidth <= 0 || opt.outHeight <= 0) return null
            var sample = 1
            var w = opt.outWidth
            var h = opt.outHeight
            while (w / 2 >= THUMB_PX || h / 2 >= THUMB_PX) {
                w /= 2
                h /= 2
                sample *= 2
            }
            val out = BitmapFactory.Options()
            out.inSampleSize = sample
            out.inPreferredConfig = Bitmap.Config.RGB_565
            return BitmapFactory.decodeFile(file.absolutePath, out)
        } catch (e: Throwable) {
            return null
        }
    }

    // ---------------- 布局 ----------------

    private class Cell(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun cells(w: Float, h: Float): ArrayList<Cell> {
        val out = ArrayList<Cell>(PAGE)
        val pad = dp(16f)
        val gap = dp(10f)
        val nameH = dp(24f)
        val cellW = (w - pad * 2f - gap * (COLS - 1)) / COLS
        val maxCellH = (h - pad * 2f - nameH - gap * (ROWS - 1)) / ROWS
        val cellH = min(cellW * 0.86f, maxCellH)
        val totalW = cellW * COLS + gap * (COLS - 1)
        val totalH = cellH * ROWS + gap * (ROWS - 1)
        val ox = (w - totalW) / 2f
        val oy = (h - totalH - nameH) / 2f
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val l = ox + c * (cellW + gap)
                val t = oy + r * (cellH + gap)
                out.add(Cell(l, t, l + cellW, t + cellH))
            }
        }
        return out
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawColor(Colors.background)

        if (photos.isEmpty()) {
            paint.style = Paint.Style.FILL
            paint.color = Colors.text
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(16f)
            canvas.drawText("没有找到照片", w / 2f, h / 2f, paint)
            return
        }

        val cs = cells(w, h)
        val from = page * PAGE
        for (i in 0 until PAGE) {
            val idx = from + i
            if (idx >= photos.size) break
            val cell = cs[i]
            val file = photos[idx]
            val bmp = cache.get(file.absolutePath)

            // 缩略图底槽（还没解码出来时铺一块极淡的灰，别留黑洞）
            rect.set(cell.left, cell.top, cell.right, cell.bottom)
            paint.style = Paint.Style.FILL
            paint.color = if (bmp != null) Color.WHITE else 0xFFF1F0EA.toInt()
            canvas.drawRoundRect(rect, dp(4f), dp(4f), paint)

            if (bmp != null) {
                // 居中裁切铺满（cover），保持缩略图不变形
                val cw0 = cell.right - cell.left
                val ch0 = cell.bottom - cell.top
                val scale = maxOf(cw0 / bmp.width, ch0 / bmp.height)
                val cw = (bmp.width * scale).toInt()
                val ch = (bmp.height * scale).toInt()
                val cx = ((cell.left + cell.right) / 2f - cw / 2f).toInt()
                val cy = ((cell.top + cell.bottom) / 2f - ch / 2f).toInt()
                src.set(0, 0, bmp.width, bmp.height)
                canvas.save()
                canvas.clipRect(rect)
                canvas.drawBitmap(
                    bmp,
                    src,
                    Rect(cx, cy, cx + cw, cy + ch),
                    paint
                )
                canvas.restore()
            }

            // 选中：外白框 + 内侧细墨线，浅色底上也分得清
            if (idx == sel) {
                rect.set(cell.left, cell.top, cell.right, cell.bottom)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(3f)
                paint.color = Color.WHITE
                canvas.drawRoundRect(rect, dp(4f), dp(4f), paint)
                paint.strokeWidth = dp(1f)
                paint.color = Color.argb(120, 31, 31, 31)
                canvas.drawRoundRect(rect, dp(4f), dp(4f), paint)
                paint.style = Paint.Style.FILL
            } else {
                rect.set(cell.left, cell.top, cell.right, cell.bottom)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(0.8f)
                paint.color = Color.argb(60, 31, 31, 31)
                canvas.drawRoundRect(rect, dp(4f), dp(4f), paint)
                paint.style = Paint.Style.FILL
            }
        }

        // 底部一条：文件名 + 页码，居中
        val nameY = h - dp(15f)
        paint.color = Colors.text
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(13f)
        val name = photos[sel].name
        val maxW = w - dp(40f)
        var size = dp(13f)
        paint.textSize = size
        while (size > dp(9f) && paint.measureText(name) > maxW) {
            size -= dp(0.6f)
            paint.textSize = size
        }
        canvas.drawText(name, w / 2f, nameY, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) < dp(16f) && abs(dy) < dp(16f)) {
                    // 点某格 = 选中（再点自己 = 打开大图）
                    val cs = cells(width.toFloat(), height.toFloat())
                    val from = page * PAGE
                    for (i in cs.indices) {
                        val idx = from + i
                        if (idx >= photos.size) break
                        val cell = cs[i]
                        if (event.x >= cell.left && event.x <= cell.right &&
                            event.y >= cell.top && event.y <= cell.bottom
                        ) {
                            if (idx == sel) {
                                enter()
                            } else {
                                sel = idx
                                invalidate()
                            }
                            return true
                        }
                    }
                }
                return true
            }
        }
        return true
    }
}
