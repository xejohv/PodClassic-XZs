package io.github.xejohv.podclassicxzs.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.BatteryManager
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import io.github.xejohv.podclassicxzs.base.Observer
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.util.LiveData
import io.github.xejohv.podclassicxzs.values.Colors
import io.github.xejohv.podclassicxzs.values.Icons
import io.github.xejohv.podclassicxzs.values.Values.DEFAULT_PADDING
import java.text.SimpleDateFormat
import java.util.*


/**
 * 标题栏：左时间/标题 + 播放状态图标 + 右侧电池。
 *
 * 布局规则（三级自适应，按实测像素定的阈值见 git 记录）：
 *  1) 标题较短：播放状态图标水平居中；
 *  2) 标题变长、要碰到居中图标：图标右移，贴到电池左侧；
 *  3) 标题继续变长、会挤占图标：图标隐藏，标题用省略号截断到电池左侧。
 * 电池永远钉在最右，任何情况下不与文字或图标重叠。
 */
class TitleBar : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)

    private val paint = Paint()

    private val title = TextView(context)
    private val playState = ImageView(context)
    private val battery = BatteryView(context)

    private val observer = Observer()
    private val onDataChangeListener = object : LiveData.OnDataChangeListener {
        override fun onDataChange() {
            onPlayStateChange()
        }
    }

    private val musicObserver = Observer()
    private val onMusicChangeListener = object : LiveData.OnDataChangeListener {
        override fun onDataChange() {
            onPlayStateChange()
        }
    }

    // onMeasure 算好、onLayout 使用的字符位置
    private var iconLeft = 0
    private var iconVisible = false

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // 三个子视图全部手动定位，不再依赖 gravity + 固定 margin
        addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(battery, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(playState, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        // iPod Classic 风格的标题文字
        title.textSize = 17f
        title.setTextColor(Colors.text)
        title.setPadding(DEFAULT_PADDING, DEFAULT_PADDING / 2, DEFAULT_PADDING, DEFAULT_PADDING / 2)
        title.maxLines = 1
        title.isSingleLine = true
        title.ellipsize = TextUtils.TruncateAt.END
        // 添加微妙的阴影效果
        title.setShadowLayer(0.5f, 0f, 1f, android.graphics.Color.argb(90, 0, 0, 0))

        playState.scaleType = ImageView.ScaleType.FIT_CENTER

        MediaPresenter.playState.addObserver(observer, onDataChangeListener)
        MediaPresenter.music.addObserver(musicObserver, onMusicChangeListener)
        observer.enable = true
        musicObserver.enable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val density = resources.displayMetrics.density

        // 电池：高度 = 栏高 - padding，宽度由自身 onMeasure 按 1.8 倍高度给出
        val batteryH = h - DEFAULT_PADDING
        battery.measure(
            MeasureSpec.makeMeasureSpec((batteryH * 1.8f).toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(batteryH, MeasureSpec.EXACTLY)
        )
        val batteryLeft = w - DEFAULT_PADDING - battery.measuredWidth

        // 播放状态图标：固定 18dp
        val iconSize = (18f * density).toInt()
        playState.measure(
            MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY)
        )

        // 文字实际需要多宽（含左右内边距）
        val text = title.text?.toString() ?: ""
        val textNeed = title.paint.measureText(text).toInt() + DEFAULT_PADDING * 2
        val gap = (5f * density).toInt()                       // 文字与图标间距
        val centerLeft = w / 2 - iconSize / 2                   // 居中态图标左边界
        val centerLimit = centerLeft - gap                      // 居中态文字最大宽度
        val batteryLimit = batteryLeft - gap                    // 极限态文字最大宽度

        var limit: Int
        when {
            textNeed <= centerLimit -> {
                iconVisible = true
                iconLeft = centerLeft
                limit = centerLimit
            }
            textNeed <= batteryLimit -> {
                iconVisible = true
                iconLeft = batteryLimit - iconSize
                limit = batteryLimit
            }
            else -> {
                iconVisible = false
                limit = batteryLimit
            }
        }
        playState.visibility = if (iconVisible) View.VISIBLE else View.INVISIBLE

        // 单行 + 省略号：超过 limit 自动截断
        title.measure(
            MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST)
        )

        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = width
        val h = height

        val titleH = title.measuredHeight
        val titleY = (h - titleH) / 2
        title.layout(0, titleY, title.measuredWidth, titleY + titleH)

        val batteryH = battery.measuredHeight
        val batteryY = (h - batteryH) / 2
        val batteryRight = w - DEFAULT_PADDING
        battery.layout(batteryRight - battery.measuredWidth, batteryY, batteryRight, batteryY + batteryH)

        if (iconVisible) {
            val iconH = playState.measuredHeight
            val iconY = (h - iconH) / 2
            playState.layout(iconLeft, iconY, iconLeft + playState.measuredWidth, iconY + iconH)
        } else {
            playState.layout(0, 0, 0, 0)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        battery.updateBatteryStatus()
        requestLayout()
    }

    private val simpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun onPlayStateChange() {
        if (MediaPresenter.isPlaying()) {
            playState.setImageDrawable(Icons.PLAY_BLUE.drawable)
        } else {
            playState.setImageDrawable(if (MediaPresenter.getCurrent() == null) null else Icons.PAUSE_BLUE.drawable)
        }
        requestLayout()
    }

    fun showTime() {
        showTime = true
        registerTimeBroadcastReceiver()
        onPlayStateChange()
    }

    fun showTitle(title: String) {
        showTime = false
        unregisterTimeBroadcastReceiver()
        this.title.text = title
        requestLayout()
        onPlayStateChange()
    }

    private var batteryBroadcastReceiverRegistered = false
    private var timeBroadcastReceiverRegistered = false
    private var showTime = false

    private fun onStart() {
        MediaPresenter.playState.addObserver(observer, onDataChangeListener)
        MediaPresenter.music.addObserver(musicObserver, onMusicChangeListener)

        registerBatteryBroadcastReceiver()
        battery.updateBatteryStatus()
        if (showTime) {
            registerTimeBroadcastReceiver()
        }
        observer.enable = true
        musicObserver.enable = true
        onPlayStateChange()
    }

    private fun onStop() {
        MediaPresenter.playState.removeObserver(observer)
        MediaPresenter.music.removeObserver(musicObserver)
        unregisterBatteryBroadcastReceiver()
        unregisterTimeBroadcastReceiver()
        observer.enable = false
        musicObserver.enable = false
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            onStart()
        } else {
            onStop()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onStop()
        MediaPresenter.playState.removeObserver(observer)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.shader = Colors.getShader(
            0f,
            0f,
            0f,
            height.toFloat(),
            Colors.background_light,
            Colors.background_dark_1
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // 顶部高光线（金色背景下用柔和高光，避免刺眼的白线）
        paint.color = android.graphics.Color.argb(110, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), 1f, paint)

        // 底部分隔线
        paint.color = Colors.background_dark_2
        canvas.drawRect(0f, height - 2f, width.toFloat(), height - 1f, paint)
        paint.color = Colors.divider_light
        canvas.drawRect(0f, height - 1f, width.toFloat(), height.toFloat(), paint)
    }

    private fun registerBatteryBroadcastReceiver() {
        if (!batteryBroadcastReceiverRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryBroadcastReceiver, intentFilter)
            batteryBroadcastReceiverRegistered = true
        }
    }

    private fun unregisterBatteryBroadcastReceiver() {
        if (batteryBroadcastReceiverRegistered) {
            context.unregisterReceiver(batteryBroadcastReceiver)
            batteryBroadcastReceiverRegistered = false
        }
    }

    private fun registerTimeBroadcastReceiver() {
        if (!timeBroadcastReceiverRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_TIME_TICK)
            context.registerReceiver(timeBroadcastReceiver, intentFilter)
            timeBroadcastReceiverRegistered = true
            title.text = simpleDateFormat.format(Date())
            requestLayout()
        }
    }

    private fun unregisterTimeBroadcastReceiver() {
        if (timeBroadcastReceiverRegistered) {
            context.unregisterReceiver(timeBroadcastReceiver)
            timeBroadcastReceiverRegistered = false
        }
    }

    private val timeBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            title.text = simpleDateFormat.format(Date())
            requestLayout()
        }
    }

    private val batteryBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            battery.batteryLevel =
                intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) * 100 / intent.getIntExtra(
                    BatteryManager.EXTRA_SCALE,
                    100
                )
            battery.isCharging = (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0).or(
                intent.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    -1
                ) == BatteryManager.BATTERY_STATUS_CHARGING
            )
            battery.refreshBattery()
        }
    }

    class BatteryView(context: Context) : View(context) {

        private val paint by lazy { Paint() }

        var isCharging = false
        var batteryLevel = 100

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val padding = height / 5f

            paint.color = Colors.line
            paint.shader = null

            canvas.drawRoundRect(0f, padding, width - padding * 3, height - padding, 4f, 4f, paint)
            canvas.drawRoundRect(
                width - padding * 3 - 2f,
                padding * 1.5f,
                width - padding * 2.5f + 2f,
                height - padding * 1.5f,
                4f,
                4f,
                paint
            )
            val batteryWidth = batteryLevel * (width - padding * 3) / 100f

            paint.shader = when {
                (isCharging && batteryLevel != 100) -> Colors.getShader(
                    0f,
                    0f,
                    0f,
                    height / 1.8f,
                    Colors.theme_white,
                    Colors.battery_yellow
                )
                (batteryLevel <= 20) -> Colors.getShader(
                    0f,
                    0f,
                    0f,
                    height / 1.8f,
                    Colors.theme_white,
                    Colors.battery_red
                )
                else -> Colors.getShader(
                    0f,
                    0f,
                    0f,
                    height / 1.8f,
                    Colors.theme_white,
                    Colors.battery_green
                )
            }
            canvas.drawRoundRect(
                2f,
                padding + 2,
                batteryWidth - 2,
                height - padding - 2,
                4f,
                4f,
                paint
            )
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            // 固定宽度，避免横屏时被拉长
            setMeasuredDimension((height * 1.8f).toInt(), height)
        }

        fun refreshBattery() {
            invalidate()
        }

        fun updateBatteryStatus() {
            val batteryStatus: Intent? = try {
                context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
            } catch (e: Exception) {
                null
            }

            batteryStatus?.let {
                batteryLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) *
                    100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                isCharging = (it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0).or(
                    it.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
                )
                refreshBattery()
            }
        }
    }
}
