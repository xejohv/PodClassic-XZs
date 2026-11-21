package io.github.xejohv.podclassicxzs.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaScannerConnection
import android.os.Environment
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.gestures.GestureService
import io.github.xejohv.podclassicxzs.values.Colors
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 内嵌相机页面（Camera1 实现）。
 *
 * 与系统相机的区别：这是 PodClassic 自己的页面，出口由自己掌握（左上返回 / MENU 键 / 三指上滑），
 * 不会出现"进去退不出来"的死局。拍照直接落盘到 DCIM/PodClassic 并入库，"照片"里能直接看到。
 */
@SuppressLint("ViewConstructor")
class CameraView(context: Context) : FrameLayout(context), ScreenView {

    companion object {
        private const val FLASH_OFF = 0
        private const val FLASH_AUTO = 1
        private const val FLASH_TORCH = 2
        private const val DIR_NAME = "PodClassic"
    }

    private val preview = TextureView(context)
    private val ui: CameraUi

    private var camera: Camera? = null
    private var facing = Camera.CameraInfo.CAMERA_FACING_BACK
    private var flashMode = FLASH_OFF
    private var previewW = 0
    private var previewH = 0
    private var shooting = false
    private var errorText: String? = null
    private var lastPhoto: File? = null
    private var lastThumb: Bitmap? = null
    private var toastText: String? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    init {
        setBackgroundColor(Color.BLACK)
        addView(
            preview,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        ui = CameraUi(context)
        addView(ui, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                applyTransform()
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                applyTransform()
            }

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                releaseCamera()
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 沉浸/展回控制盘时页面高度会变，预览的裁剪矩阵要跟着重算
        applyTransform()
    }

    // ---------------- ScreenView ----------------

    override fun getTitle(): String = "相机"

    override fun getLaunchMode(): Int = ScreenView.LAUNCH_MODE_SINGLE

    private val immerseAgain = Runnable {
        if (ui.isAttachedToWindow) Core.setGameExpanded(true)
    }

    override fun onViewAdd() {
        if (preview.isAvailable) openCamera()
        loadLastThumb()
        ui.invalidate()
        // 相机页进入即沉浸：收起控制盘、隐藏标题栏，取景铺满整屏
        Core.setGameExpanded(true)
        // 刚加入的 View 还没测量完，折叠可能被时序冲掉，补一帧
        ui.postDelayed(immerseAgain, 260)
        // 全屏取景时没有任何可见的退出控件，交给无障碍层盖一条「返回 / iPod」；
        // 和系统设置页一样贴到屏幕最下沿（raised=false），快门键在它上方，不会被挡住
        GestureService.showExitBar(false)
    }

    override fun onViewRemove() {
        ui.removeCallbacks(immerseAgain)
        releaseCamera()
        GestureService.hideExitBar()
    }

    override fun onViewDelete() {
        ui.removeCallbacks(immerseAgain)
        releaseCamera()
        GestureService.hideExitBar()
    }

    override fun onConfigurationChanged() {
        releaseCamera()
        post { if (preview.isAvailable) openCamera() }
    }

    override fun enter(): Boolean {
        takePhoto()
        return true
    }

    override fun enterLongClick(): Boolean {
        switchCamera()
        return true
    }

    override fun slide(slideVal: Int): Boolean {
        cycleFlash()
        return true
    }

    // ---------------- 相机控制 ----------------

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun openCamera() {
        if (camera != null) return
        if (!hasPermission()) {
            errorText = "未授予相机权限\n请在系统设置里允许后重开此页"
            ui.invalidate()
            return
        }
        val count = try {
            Camera.getNumberOfCameras()
        } catch (e: Exception) {
            0
        }
        if (count <= 0) {
            errorText = "没有可用的摄像头"
            ui.invalidate()
            return
        }
        var index = -1
        val info = Camera.CameraInfo()
        for (i in 0 until count) {
            Camera.getCameraInfo(i, info)
            if (info.facing == facing) {
                index = i
                break
            }
        }
        if (index < 0) index = 0

        try {
            val cam = Camera.open(index)
            cam.setDisplayOrientation(displayOrientation(index, info))
            val params = cam.parameters
            // 预览：挑 16:9 里较大的一档，画面更清楚
            val previewSizes = params.supportedPreviewSizes ?: emptyList()
            val bestPreview = previewSizes
                .filter { abs(it.width.toFloat() / it.height - 16f / 9f) < 0.02f && it.width <= 1920 }
                .maxByOrNull { it.width * it.height }
                ?: previewSizes.maxByOrNull { it.width * it.height }
            if (bestPreview != null) {
                params.setPreviewSize(bestPreview.width, bestPreview.height)
                previewW = bestPreview.width
                previewH = bestPreview.height
            }
            // 拍照：4:3 最大
            val pictureSizes = params.supportedPictureSizes ?: emptyList()
            val bestPicture = pictureSizes
                .filter { abs(it.width.toFloat() / it.height - 4f / 3f) < 0.03f }
                .maxByOrNull { it.width * it.height }
                ?: pictureSizes.maxByOrNull { it.width * it.height }
            if (bestPicture != null) {
                params.setPictureSize(bestPicture.width, bestPicture.height)
            }
            params.pictureFormat = android.graphics.ImageFormat.JPEG

            val focusModes = params.supportedFocusModes ?: emptyList<String>()
            params.focusMode = when {
                focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) ->
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO) ->
                    Camera.Parameters.FOCUS_MODE_AUTO
                else -> params.focusMode
            }
            applyFlashMode(params)

            val rot = when (info.facing) {
                Camera.CameraInfo.CAMERA_FACING_FRONT -> 270
                else -> 90
            }
            params.setRotation(rot)

            cam.parameters = params
            val st = preview.surfaceTexture ?: throw IllegalStateException("surface 未就绪")
            cam.setPreviewTexture(st)
            cam.startPreview()
            camera = cam
            errorText = null
            applyTransform()
        } catch (e: Exception) {
            camera = null
            errorText = "相机不可用：" + (e.message ?: e.javaClass.simpleName)
        }
        ui.invalidate()
    }

    private fun displayOrientation(index: Int, info: Camera.CameraInfo): Int {
        return try {
            val deg = 0
            val result: Int
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + deg) % 360
                (360 - result) % 360
            } else {
                (info.orientation - deg + 360) % 360
            }
        } catch (e: Exception) {
            90
        }
    }

    private fun applyFlashMode(params: Camera.Parameters) {
        try {
            when (flashMode) {
                FLASH_OFF -> params.flashMode = Camera.Parameters.FLASH_MODE_OFF
                FLASH_AUTO -> params.flashMode = Camera.Parameters.FLASH_MODE_AUTO
                FLASH_TORCH -> params.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            }
        } catch (ignored: Exception) {
        }
    }

    private fun releaseCamera() {
        val cam = camera ?: return
        camera = null
        try {
            cam.stopPreview()
        } catch (ignored: Exception) {
        }
        try {
            cam.release()
        } catch (ignored: Exception) {
        }
    }

    private fun switchCamera() {
        facing = if (facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
            Camera.CameraInfo.CAMERA_FACING_FRONT
        } else {
            Camera.CameraInfo.CAMERA_FACING_BACK
        }
        releaseCamera()
        preview.post {
            applyTransform()
            openCamera()
        }
    }

    private fun cycleFlash() {
        flashMode = (flashMode + 1) % 3
        val cam = camera ?: run { ui.invalidate(); return }
        try {
            val params = cam.parameters
            applyFlashMode(params)
            cam.parameters = params
        } catch (ignored: Exception) {
        }
        if (flashMode == FLASH_TORCH) {
            showToast("闪光灯常亮")
        } else if (flashMode == FLASH_AUTO) {
            showToast("闪光自动")
        } else {
            showToast("闪光关闭")
        }
        ui.invalidate()
    }

    private fun showToast(text: String) {
        toastText = text
        ui.invalidate()
        ui.postDelayed({
            if (toastText == text) {
                toastText = null
                ui.invalidate()
            }
        }, 1600)
    }

    /** 预览按"铺满并居中裁剪"显示：所选 16:9 预览与内容区比例不同时不会变形 */
    private fun applyTransform() {
        if (previewW <= 0 || previewH <= 0) return
        val vw = preview.width.toFloat()
        val vh = preview.height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val pw = previewH.toFloat()   // 旋转 90 后宽高互换
        val ph = previewW.toFloat()
        val scale = max(vw / pw, vh / ph)
        val m = Matrix()
        m.setScale(scale, scale, vw / 2f, vh / 2f)
        if (facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            m.postScale(-1f, 1f, vw / 2f, vh / 2f)
        }
        preview.setTransform(m)
    }

    private fun takePhoto() {
        val cam = camera ?: run {
            showToast(if (errorText != null) "相机未就绪" else "正在准备相机…")
            return
        }
        if (shooting) return
        shooting = true
        try {
            cam.takePicture(null, null, Camera.PictureCallback { data, _ ->
                try {
                    val file = saveJpeg(data)
                    lastPhoto = file
                    loadLastThumb()
                    MediaScannerConnection.scanFile(
                        context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null
                    )
                    showToast("已保存 · 照片")
                } catch (e: Exception) {
                    showToast("保存失败")
                } finally {
                    shooting = false
                    try {
                        cam.startPreview()
                    } catch (ignored: Exception) {
                    }
                    ui.invalidate()
                }
            })
        } catch (e: Exception) {
            shooting = false
            showToast("拍照失败")
        }
    }

    private fun saveJpeg(data: ByteArray): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            DIR_NAME
        )
        if (!dir.exists()) dir.mkdirs()
        val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val file = File(dir, name)
        FileOutputStream(file).use { it.write(data) }
        return file
    }

    private fun loadLastThumb() {
        try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                DIR_NAME
            )
            val newest = dir.listFiles { f -> f.isFile && f.name.endsWith(".jpg", true) }
                ?.maxByOrNull { it.lastModified() }
            if (newest == null) {
                lastPhoto = null
                lastThumb = null
                ui.invalidate()
                return
            }
            lastPhoto = newest
            val opts = BitmapFactory.Options()
            opts.inSampleSize = 8
            lastThumb = BitmapFactory.decodeFile(newest.absolutePath, opts)
            ui.invalidate()
        } catch (ignored: Exception) {
        }
    }

    private fun openLastPhoto() {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            DIR_NAME
        )
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".jpg", true) }
            ?.sortedByDescending { it.lastModified() }
            ?.let { ArrayList(it) }
        if (files.isNullOrEmpty()) {
            showToast("还没有照片")
            return
        }
        Core.addView(ImageView(context, files, 0))
    }

    /** 自绘控件层：返回、闪光、快门、前/后摄、最近一张 */
    @SuppressLint("ViewConstructor")
    inner class CameraUi(context: Context) : View(context) {

        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val err = errorText
            if (err != null) {
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                p.textAlign = Paint.Align.CENTER
                p.textSize = dp(17f)
                val lines = err.split("\n")
                var y = h / 2f - (lines.size - 1) * dp(14f)
                for (line in lines) {
                    canvas.drawText(line, w / 2f, y, p)
                    y += dp(28f)
                }
                drawBack(canvas)
                drawToast(canvas, w, h)
                return
            }

            // 顶部渐隐，保证小按钮在亮画面上也看得清
            p.style = Paint.Style.FILL
            p.color = Color.argb(96, 0, 0, 0)
            canvas.drawRect(0f, 0f, w, dp(64f), p)
            canvas.drawRect(0f, h - dp(96f), w, h, p)

            drawBack(canvas)
            drawFlash(canvas, w)
            drawShutter(canvas, w, h)
            drawSwap(canvas, w, h)
            drawThumb(canvas, h)
            drawGestureHint(canvas, w)
            drawToast(canvas, w, h)
        }

        private fun circleFill(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
            p.style = Paint.Style.FILL
            p.color = color
            canvas.drawCircle(cx, cy, r, p)
        }

        private fun drawBack(canvas: Canvas) {
            val cx = dp(34f)
            val cy = dp(34f)
            circleFill(canvas, cx, cy, dp(24f), Color.argb(120, 0, 0, 0))
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            path.reset()
            path.moveTo(cx - dp(6f), cy)
            path.lineTo(cx + dp(6f), cy - dp(10f))
            path.lineTo(cx + dp(6f), cy + dp(10f))
            path.close()
            canvas.drawPath(path, p)
        }

        private fun drawFlash(canvas: Canvas, w: Float) {
            val cx = w - dp(34f)
            val cy = dp(34f)
            circleFill(canvas, cx, cy, dp(24f), Color.argb(120, 0, 0, 0))
            val on = flashMode != FLASH_OFF
            p.style = Paint.Style.FILL
            p.color = if (on) Color.argb(255, 255, 214, 120) else Color.argb(190, 255, 255, 255)
            path.reset()
            path.moveTo(cx + dp(3f), cy - dp(12f))
            path.lineTo(cx - dp(6f), cy + dp(2f))
            path.lineTo(cx - dp(0.5f), cy + dp(2f))
            path.lineTo(cx - dp(3f), cy + dp(12f))
            path.lineTo(cx + dp(6f), cy - dp(2f))
            path.lineTo(cx + dp(0.5f), cy - dp(2f))
            path.close()
            canvas.drawPath(path, p)
            // 自动档加一个小 A 标记
            if (flashMode == FLASH_AUTO) {
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                p.textAlign = Paint.Align.CENTER
                p.textSize = dp(11f)
                canvas.drawText("A", cx + dp(13f), cy + dp(14f), p)
            }
        }

        private fun drawShutter(canvas: Canvas, w: Float, h: Float) {
            val cx = w / 2f
            val cy = h - dp(54f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(3f)
            p.color = Color.argb(220, 255, 255, 255)
            canvas.drawCircle(cx, cy, dp(30f), p)
            p.style = Paint.Style.FILL
            p.color = if (shooting) Color.argb(150, 255, 214, 120) else Color.WHITE
            canvas.drawCircle(cx, cy, dp(24f), p)
        }

        private fun drawSwap(canvas: Canvas, w: Float, h: Float) {
            val cx = w - dp(46f)
            val cy = h - dp(54f)
            circleFill(canvas, cx, cy, dp(22f), Color.argb(110, 0, 0, 0))
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            path.reset()
            // 两个反向箭头
            path.moveTo(cx - dp(11f), cy - dp(6f))
            path.lineTo(cx + dp(9f), cy - dp(6f))
            path.lineTo(cx + dp(9f), cy - dp(11f))
            path.lineTo(cx + dp(15f), cy - dp(3f))
            path.lineTo(cx + dp(9f), cy + dp(5f))
            path.lineTo(cx + dp(9f), cy)
            path.lineTo(cx - dp(9f), cy)
            path.lineTo(cx - dp(9f), cy + dp(5f))
            path.lineTo(cx - dp(15f), cy - dp(3f))
            path.lineTo(cx - dp(9f), cy - dp(11f))
            path.close()
            canvas.drawPath(path, p)
        }

        private fun drawThumb(canvas: Canvas, h: Float) {
            val cx = dp(46f)
            val cy = h - dp(54f)
            val r = dp(22f)
            val thumb = lastThumb
            if (thumb != null) {
                // 居中裁剪成正方形
                val side = min(thumb.width, thumb.height)
                val src = android.graphics.Rect(
                    (thumb.width - side) / 2, (thumb.height - side) / 2,
                    (thumb.width + side) / 2, (thumb.height + side) / 2
                )
                val dst = RectF(cx - r, cy - r, cx + r, cy + r)
                canvas.drawBitmap(thumb, src, dst, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(2f)
                p.color = Color.argb(200, 255, 255, 255)
                canvas.drawCircle(cx, cy, r, p)
            } else {
                circleFill(canvas, cx, cy, r, Color.argb(110, 0, 0, 0))
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(2f)
                p.color = Color.argb(150, 255, 255, 255)
                canvas.drawCircle(cx, cy, r, p)
            }
        }

        /** 右上角下方一行小字：告诉用户怎么收起控制盘 / 怎么退出 */
        private fun drawGestureHint(canvas: Canvas, w: Float) {
            p.style = Paint.Style.FILL
            p.color = Color.argb(150, 255, 255, 255)
            p.textAlign = Paint.Align.CENTER
            p.textSize = dp(12f)
            canvas.drawText("上滑展回控制盘 · 左上长按回主菜单", w / 2f, dp(84f), p)
        }

        private fun drawToast(canvas: Canvas, w: Float, h: Float) {
            val t = toastText ?: return
            p.style = Paint.Style.FILL
            p.color = Color.argb(190, 0, 0, 0)
            val tw = dp(96f)
            canvas.drawRoundRect(
                RectF(w / 2f - tw, h - dp(150f), w / 2f + tw, h - dp(112f)),
                dp(8f), dp(8f), p
            )
            p.color = Color.WHITE
            p.textAlign = Paint.Align.CENTER
            p.textSize = dp(14f)
            canvas.drawText(t, w / 2f, h - dp(124f), p)
        }

        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var longPressed = false
        private val longPressRunnable = Runnable {
            longPressed = true
            Core.home()
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = System.currentTimeMillis()
                    longPressed = false
                    // 报错态：先试着重来一次（例如用户刚在系统里授予了相机权限）
                    if (errorText != null) {
                        errorText = null
                        invalidate()
                        openCamera()
                        return true
                    }
                    // 左上长按 0.6 秒 = 直接回主菜单
                    if (event.x < dp(80f) && event.y < dp(80f)) {
                        postDelayed(longPressRunnable, 600)
                    }
                    return true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!longPressed && abs(event.y - downY) > dp(28f)) {
                        removeCallbacks(longPressRunnable)
                    }
                    return true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPressRunnable)
                    if (longPressed) {
                        longPressed = false
                        return true
                    }
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val dt = System.currentTimeMillis() - downTime
                    // 纵向滑动：上滑展回控制盘（退出沉浸），下滑收回（全屏）—— 与游戏手势一致
                    if (dt > 120 && abs(dy) > dp(70f) && abs(dy) > abs(dx) * 1.4f) {
                        if (dy < 0) {
                            Core.setGameExpanded(false)
                        } else {
                            Core.setGameExpanded(true)
                        }
                        return true
                    }
                    handleTap(event.x, event.y)
                    return true
                }
                else -> return true
            }
        }

        private fun handleTap(x: Float, y: Float) {
            val w = width.toFloat()
            val h = height.toFloat()
            // 左上返回
            if (x < dp(80f) && y < dp(80f)) {
                Core.removeView()
                return
            }
            // 右上闪光
            if (x > w - dp(80f) && y < dp(80f)) {
                cycleFlash()
                return
            }
            // 快门
            if (abs(x - w / 2f) < dp(38f) && abs(y - (h - dp(54f))) < dp(38f)) {
                takePhoto()
                return
            }
            // 右下切换镜头
            if (abs(x - (w - dp(46f))) < dp(30f) && abs(y - (h - dp(54f))) < dp(30f)) {
                switchCamera()
                return
            }
            // 左下看最近一张
            if (abs(x - dp(46f)) < dp(30f) && abs(y - (h - dp(54f))) < dp(30f)) {
                openLastPhoto()
            }
        }
    }
}
