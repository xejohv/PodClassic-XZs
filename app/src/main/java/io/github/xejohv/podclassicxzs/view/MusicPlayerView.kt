package io.github.xejohv.podclassicxzs.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import io.github.xejohv.podclassicxzs.R
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.Observer
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.bean.Music
import io.github.xejohv.podclassicxzs.media.PlayMode
import io.github.xejohv.podclassicxzs.media.RepeatMode
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.storage.MusicTable
import io.github.xejohv.podclassicxzs.storage.SPManager
import io.github.xejohv.podclassicxzs.util.LiveData
import io.github.xejohv.podclassicxzs.util.ThreadUtil
import io.github.xejohv.podclassicxzs.util.VolumeUtil
import io.github.xejohv.podclassicxzs.values.Colors
import io.github.xejohv.podclassicxzs.values.Icons
import io.github.xejohv.podclassicxzs.values.Strings
import io.github.xejohv.podclassicxzs.widget.ScreenLayout
import io.github.xejohv.podclassicxzs.widget.SeekBar
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import android.util.Log


class MusicPlayerView(context: Context) : FrameLayout(context), ScreenView {
    init {
        // 设置布局参数为 match_parent
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private val observer = Observer()

    override fun getObserver(): Observer {
        return observer
    }


    private var timer: Timer? = null
    private var prevTimerSetTime = 0L

    override fun getTitle(): String? {
        return null
    }

    private var seekMode = false

    private val progressBar: SeekBar = SeekBar(context)
    private var bubble: android.widget.TextView? = null

    private val screenLayout: ScreenLayout
    private val index: androidx.appcompat.widget.AppCompatTextView
    private val currentTime: androidx.appcompat.widget.AppCompatTextView
    private val remainingTime: androidx.appcompat.widget.AppCompatTextView
    private val image: io.github.xejohv.podclassicxzs.widget.ImageView
    private val title: androidx.appcompat.widget.AppCompatTextView
    private val artist: androidx.appcompat.widget.AppCompatTextView
    private val album: androidx.appcompat.widget.AppCompatTextView
    private val stopTime: androidx.appcompat.widget.AppCompatTextView?
    private val lyric: io.github.xejohv.podclassicxzs.widget.TextView?
    private val icon1: androidx.appcompat.widget.AppCompatImageView
    private val icon2: androidx.appcompat.widget.AppCompatImageView
    private val icon3: androidx.appcompat.widget.AppCompatImageView
    private var imageCenter = 0;

    private val progressTimer =
        io.github.xejohv.podclassicxzs.util.Timer(500L) { a -> ThreadUtil.runOnUiThread { onProgress(a.toInt()) } }

    

    private val container : ViewGroup

    init {
        Log.d("MusicPlayerView", "Init started")
        val view = LayoutInflater.from(context).inflate(R.layout.view_player, this, true)
        // 播放页底色跟随机身配色（XML 里的静态色在这里统一覆盖）
        setBackgroundColor(Colors.background_light)
        (view as? android.view.ViewGroup)?.getChildAt(0)?.setBackgroundColor(Colors.background_light)
        album = view.findViewById(R.id.tv_album)
        artist = view.findViewById(R.id.tv_artist)
        title = view.findViewById(R.id.tv_title)
        image = view.findViewById(R.id.image)
        
        Log.d("MusicPlayerView", "Views found: album=$album, artist=$artist, title=$title, image=$image")
        
        // 初始化所有视图
        index = view.findViewById(R.id.tv_index)
        
        // iPod Classic 风格的文本样式
        title.apply {
            setTextColor(Colors.text)
            setShadowLayer(0.5f, 0f, 1f, Colors.white)
            // 标准TextView没有scrollable属性，使用标准的跑马灯设置
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            isSelected = true
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(0, 0, 0, 0)
            gravity = Gravity.START
        }
        artist.apply {
            setTextColor(Colors.text)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.START
        }
        album.apply {
            setTextColor(Colors.text)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.START
        }
        index.apply {
            setPadding(0, 0, 0, 0)
            setTextColor(Colors.text)
            gravity = Gravity.START
        }

        // 当前播放时间和剩余时间（底部进度条区域）
        currentTime = view.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.tv_current_time).apply {
            setTextColor(Colors.text)
            setSingleLine(true)
            maxLines = 1
            includeFontPadding = false
            setHorizontallyScrolling(true)
        }
        remainingTime = view.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.tv_remaining_time).apply {
            setTextColor(Colors.text)
            setSingleLine(true)
            maxLines = 1
            includeFontPadding = false
            setHorizontallyScrolling(true)
        }
        
        Log.d("MusicPlayerView", "Time views found: index=$index, currentTime=$currentTime, remainingTime=$remainingTime")

        // 禁用SeekBar内置的时间显示，使用独立的TextView
        progressBar.textVisibility = View.GONE
        
        screenLayout = (view.findViewById(R.id.seek_bar) as ScreenLayout).apply { add(progressBar) }
        icon1 = view.findViewById(R.id.ic_play_mode)
        icon2 = view.findViewById(R.id.ic_repeat_mode)
        bubble = view.findViewById(R.id.tv_mode_bubble)
        // 右上角两个图标：点一下切换，并立刻把"切换成了什么"写出来
        icon1.setOnClickListener {
            MediaPresenter.nextPlayMode()
            showModeBubble(nextModeText())
        }
        icon2.setOnClickListener {
            MediaPresenter.nextRepeatMode()
            showModeBubble(if (MediaPresenter.getRepeatMode() == RepeatMode.ALL) "不循环" else "全部循环")
        }
        icon3 = view.findViewById(R.id.ic_stop_time)
        stopTime = view.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.tv_stop_time).apply {
            setTextColor(Colors.text)
        }
        container = view.findViewById(R.id.container)
        lyric = if (SPManager.getBoolean(SPManager.SP_SHOW_LYRIC)) {
            view.findViewById<io.github.xejohv.podclassicxzs.widget.TextView>(R.id.tv_lyric).apply {
                scrollable = true
                typeface = Typeface.defaultFromStyle(Typeface.NORMAL)
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
            }
        } else {
            null
        }
        
        observer.addLiveData(MediaPresenter.stopTime, object : LiveData.OnDataChangeListener {
            override fun onStart() { setStopTime() }
            override fun onDataChange() = ThreadUtil.runOnUiThread { setStopTime() }
        })

        observer.addLiveData(MediaPresenter.music, object : LiveData.OnDataChangeListener {
            override fun onStart() { onMusicChange() }
            override fun onDataChange() = ThreadUtil.runOnUiThread { onMusicChange() }
        })

        observer.addLiveData(MediaPresenter.playState, object : LiveData.OnDataChangeListener {
            override fun onStart() { onPlayStateChange() }
            override fun onDataChange() = ThreadUtil.runOnUiThread { onPlayStateChange() }
            override fun onStop() { progressTimer.pause() }
        })
        observer.addLiveData(MediaPresenter.playMode, object : LiveData.OnDataChangeListener {
            override fun onStart() { setPlayMode(); }
            override fun onDataChange() = ThreadUtil.runOnUiThread { setPlayMode(); }
        })


        //setPlayMode()
        //setStopTime()
        //onPlayStateChange()
        //onMusicChange()
    }


    private fun sgn(x: Float): Float {
        return when {
            x < 0f -> -1f
            x > 0f -> 1f
            else -> 0f
        }
    }
    override fun onMeasure(widthMeasureSec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSec, heightMeasureSpec)
        imageCenter = measuredWidth / 2
    }

    override fun enter(): Boolean {
        seekMode = !seekMode
        progressBar.setSeekMode(seekMode)
        if (seekMode) {
            backToProgress()
        } else {
            val seekProgress = progressBar.getProgress()
            MediaPresenter.seekTo(seekProgress)
            // 确认快进后立即更新时间显示，避免显示旧时间
            setCurrentTimeText(formatTime(seekProgress))
            setRemainingTimeText("-" + formatTime(progressBar.getMax() - seekProgress))
        }
        return true
    }

    /**
     * 长按中央键 = 收藏 / 取消收藏正在播放的这首（气泡提示）。
     * 全应用统一：列表里长按收藏选中项，播放页长按收藏当前曲，播放模式只从右上角图标切。
     */
    override fun enterLongClick(): Boolean {
        val music = MediaPresenter.getCurrent() ?: return false
        val favourite = MusicTable.favourite
        val nowFavourite = !favourite.contains(music)
        if (nowFavourite) {
            favourite.add(music)
        } else {
            favourite.remove(music)
        }
        showModeBubble(if (nowFavourite) "已收藏" else "已取消收藏")
        return true
    }

    /** 点了右上角图标后，短暂在标题栏左侧显示当前模式，避免"点了不知道变成了什么" */
    /**
     * 点图标 / 长按收藏后的即时反馈。
     *
     * 原来把文字塞进标题栏的 tv_mode_bubble，实测在真机上根本看不到——标题栏那一行
     * 正好被系统状态栏压着。改成顶部居中的 Toast：走系统窗口，不会被任何东西盖住。
     */
    private fun showModeBubble(text: String) {
        try {
            val toast = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 150)
            toast.show()
        } catch (e: Exception) {
        }
    }

    /** 点击后即将切换到的模式名（切换是异步落到服务里，所以按当前值推下一个） */
    private fun nextModeText(): String = when (MediaPresenter.getPlayMode()) {
        PlayMode.ORDER -> "随机播放"
        PlayMode.SHUFFLE -> "单曲循环"
        PlayMode.SINGLE -> "顺序播放"
        else -> "顺序播放"
    }

    /** 当前显示的是音量条（而非进度条） */
    private fun inVolumeMode(): Boolean = progressBar.isShowingVolume()

    /**
     * 音量模式下，两端时间位换成喇叭图标、文字清空。
     * 图标画在时间文字那两格里，SeekBar 内部不再额外占宽，
     * 所以进度条与音量条的条身长度逐像素一致，切换时不跳动。
     */
    private fun applyVolumeIcons(show: Boolean) {
        currentTime.setCompoundDrawablesWithIntrinsicBounds(
            if (show) Icons.VOLUME_DOWN.drawable else null, null, null, null
        )
        remainingTime.setCompoundDrawablesWithIntrinsicBounds(
            null, null, if (show) Icons.VOLUME_UP.drawable else null, null
        )
        if (show) {
            currentTime.text = ""
            remainingTime.text = ""
        } else {
            restoreTimeLabels()
        }
    }

    private fun restoreTimeLabels() {
        val cur = progressBar.getCurrent()
        val max = progressBar.getMax()
        currentTime.text = formatTime(cur)
        remainingTime.text = if (max <= 1) "--:--" else "-" + formatTime(max - cur)
    }

    private fun setCurrentTimeText(text: String) {
        if (inVolumeMode()) return
        currentTime.text = text
    }

    private fun setRemainingTimeText(text: String) {
        if (inVolumeMode()) return
        remainingTime.text = text
    }
    /**
     * 进入"音量显示"：不换控件、不换布局 —— 还是这条进度条，
     * 只是临时用它画音量格数，两端时间位换成喇叭图标。
     */
    private fun showVolumeBar() {
        applyVolumeIcons(true)
        progressBar.showVolume(VolumeUtil.getCurrentVolume(), VolumeUtil.maxVolume)
    }

    /** 回到进度显示（条身位置、长度自始至终没动过，所以切换不会有跳动） */
    private fun backToProgress() {
        applyVolumeIcons(false)
        progressBar.hideVolume()
    }

    fun setSeekBar(seekBar: SeekBar) {
        if (screenLayout.curr() != seekBar) {
            if (screenLayout.stackSize() == 0) {
                screenLayout.add(seekBar)
            } else {
                screenLayout.remove()
            }
        }
    }

    private fun setStopTime() {
        val time = MediaPresenter.getStopTime()
        if (time == 0) {
            stopTime?.visibility = View.GONE
            icon3.visibility = View.GONE
        } else {
            stopTime?.visibility = View.VISIBLE
            icon3.visibility = View.VISIBLE
            stopTime?.text = "${time}分钟"
        }
    }

    private fun setPlayMode() {
        val playMode = when (MediaPresenter.getPlayMode()) {
            PlayMode.SHUFFLE -> Icons.PLAY_MODE_SHUFFLE.drawable
            PlayMode.SINGLE -> Icons.PLAY_MODE_SINGLE.drawable
            else -> null
        }
        if (SPManager.getInt(SPManager.SP_REPEAT_MODE) == RepeatMode.ALL.id) {
            if (playMode == null) {
                icon1.setImageDrawable(Icons.PLAY_MODE_REPEAT.drawable)
                icon2.setImageDrawable(null)
            } else {
                icon1.setImageDrawable(playMode)
                icon2.setImageDrawable(Icons.PLAY_MODE_REPEAT.drawable)
            }
        } else {
            icon1.setImageDrawable(playMode)
            icon2.setImageDrawable(null)
        }
    }

    /**
     * 硬件音量键 / 系统音量变化 → 同步那条"转盘换出来的音量条"。
     * 用 VolumeUtil.Watcher（ContentObserver + 轮询兜底），不再依赖不可靠的 VOLUME_CHANGED 广播。
     */
    private val volumeWatcher by lazy {
        VolumeUtil.Watcher { v -> onHardwareVolumeChanged(v) }
    }
    private var volumeWatcherStarted = false

    /** 按音量键时切到音量显示、刷新格数，停手 2 秒回到进度条（与转盘同一套逻辑） */
    private fun onHardwareVolumeChanged(v: Int) {
        if (seekMode) return
        val max = VolumeUtil.maxVolume
        showVolumeBar()
        progressBar.showVolume(v.coerceIn(0, max), max)

        // 连续按键时不断重置计时，停手 2 秒才回到进度条
        if (System.currentTimeMillis() - prevTimerSetTime >= 1000) {
            prevTimerSetTime = System.currentTimeMillis()
            timer?.cancel()
            timer = Timer()
            timer?.schedule(object : TimerTask() {
                override fun run() {
                    ThreadUtil.runOnUiThread {
                        if (progressBar.isShowingVolume()) backToProgress()
                    }
                }
            }, 2000)
        }
    }

    override fun onViewAdd() {
        Log.d("MusicPlayerView", "========== onViewAdd() started ==========")
        Log.d("MusicPlayerView", "MusicPlayerView width=$width, height=$height")
        Log.d("MusicPlayerView", "MusicPlayerView measuredWidth=$measuredWidth, measuredHeight=$measuredHeight")
        Log.d("MusicPlayerView", "Parent=${parent?.javaClass?.simpleName}, Parent size=${(parent as? ViewGroup)?.width}x${(parent as? ViewGroup)?.height}")
        
        if (!volumeWatcherStarted) {
            volumeWatcher.start()
            volumeWatcherStarted = true
        }
        
        // 获取根视图并记录其状态
        val rootView = getChildAt(0)
        Log.d("MusicPlayerView", "RootView=$rootView, RootView type=${rootView?.javaClass?.simpleName}")
        Log.d("MusicPlayerView", "RootView width=${rootView?.width}, height=${rootView?.height}")
        Log.d("MusicPlayerView", "RootView measuredWidth=${rootView?.measuredWidth}, measuredHeight=${rootView?.measuredHeight}")
        Log.d("MusicPlayerView", "RootView paddingTop=${rootView?.paddingTop}, paddingBottom=${rootView?.paddingBottom}")
        Log.d("MusicPlayerView", "RootView layoutParams=${rootView?.layoutParams}")
        
        // 记录container状态
        Log.d("MusicPlayerView", "Container width=${container.width}, height=${container.height}")
        Log.d("MusicPlayerView", "Container measuredWidth=${container.measuredWidth}, measuredHeight=${container.measuredHeight}")
        Log.d("MusicPlayerView", "Container layoutParams=${container.layoutParams}")
        
        // 记录图片状态
        Log.d("MusicPlayerView", "Image width=${image.width}, height=${image.height}")
        Log.d("MusicPlayerView", "Image y=${image.y}, top=${image.top}, bottom=${image.bottom}")
        
        // 重置专辑封面状态
        image.apply {
            scaleX = 1f
            scaleY = 1f
            rotationY = 15f
            z = 0f
            visibility = VISIBLE
            alpha = 1f
        }
        
        // 重置所有文本视图的状态
        listOf(title, artist, album, index, currentTime, remainingTime).forEach {
            it.alpha = 1f
            it.visibility = VISIBLE
        }
        
        // 延迟执行布局检查
        post {
            Log.d("MusicPlayerView", "---------- post() callback ----------")
            val postRootView = getChildAt(0) as? LinearLayout
            Log.d("MusicPlayerView", "After post - RootView width=${postRootView?.width}, height=${postRootView?.height}")
            Log.d("MusicPlayerView", "After post - RootView paddingTop=${postRootView?.paddingTop}, paddingBottom=${postRootView?.paddingBottom}")
            Log.d("MusicPlayerView", "After post - MusicPlayerView width=$width, height=$height")
            Log.d("MusicPlayerView", "After post - Container width=${container.width}, height=${container.height}")
            Log.d("MusicPlayerView", "After post - Container top=${container.top}, y=${container.y}")
            Log.d("MusicPlayerView", "After post - Image y=${image.y}, top=${image.top}")
            Log.d("MusicPlayerView", "---------- post() end ----------")
        }
        
        // 注意：onMusicChange() 和 onPlayStateChange() 会通过 Observer.onViewAdd() -> LiveData.onStart() 自动调用
        // 这里不需要手动调用，避免重复执行
        // 只需要更新进度显示
        onProgress(MediaPresenter.getProgress())
        Log.d("MusicPlayerView", "onProgress() called to update time display")

    }

    override fun onViewRemove() {
        if (volumeWatcherStarted) {
            volumeWatcher.stop()
            volumeWatcherStarted = false
        }
    }

    override fun slide(slideVal: Int): Boolean {
        if (seekMode) {
            var progress = progressBar.getProgress()
            val duration = progressBar.getMax()

            if ((progress == 0 && slideVal < 0) || (progress == duration && slideVal > 0)) {
                return false
            }

            progress += slideVal * 2000
            progress = progress.coerceAtLeast(0).coerceAtMost(duration)
            progressBar.setCurrent(progress)
            // 拖动进度条时也更新时间显示
            setCurrentTimeText(formatTime(progress))
            setRemainingTimeText(if (duration <= 0) "--:--" else "-" + formatTime((duration - progress).coerceAtLeast(0)))
            return true
        } else {
            val currentTime = System.currentTimeMillis()

            if (currentTime - prevTimerSetTime >= 1000) {
                prevTimerSetTime = currentTime
                timer?.cancel()
                timer = Timer()
                timer?.schedule(object : TimerTask() {
                    override fun run() {
                        ThreadUtil.runOnUiThread {
                            backToProgress()
                        }
                    }
                }, 2000)
            }

            if (progressBar.isShowingVolume()) {
                var curVolume = VolumeUtil.getCurrentVolume()
                val maxVolume = VolumeUtil.maxVolume

                if ((curVolume == 0 && slideVal < 0) || (curVolume == maxVolume && slideVal > 0)) {
                    return false
                }

                curVolume += slideVal
                VolumeUtil.setCurrentVolume(curVolume)
                progressBar.showVolume(curVolume, maxVolume)
                return true
            } else {
                // 第一次转动：进入音量显示，同时这一步就生效，不用再转一下才有反应
                showVolumeBar()
                val maxVolume = VolumeUtil.maxVolume
                val curVolume = (VolumeUtil.getCurrentVolume() + slideVal).coerceIn(0, maxVolume)
                VolumeUtil.setCurrentVolume(curVolume)
                progressBar.showVolume(curVolume, maxVolume)
                return true
            }
        }
    }

    private fun onMusicChange() {
        Log.d("MusicPlayerView", "onMusicChange() started")
        if (seekMode) {
            seekMode = false
            progressBar.setSeekMode(false)
        }
        val music = MediaPresenter.getCurrent()
        Log.d("MusicPlayerView", "Current music: $music")
        if (music == null) {
            Log.d("MusicPlayerView", "Music is null, waiting for media controller")
            // 延迟重试，不要移除视图
            postDelayed({  
                if (observer.enable) {
                    Log.d("MusicPlayerView", "Retrying onMusicChange()")
                    onMusicChange()
                }
            }, 1000)
            return
        }
        Log.d("MusicPlayerView", "Music data: title=${music.title}, artist=${music.artist}, album=${music.album}, duration=${music.duration}")
        loadImage(music)
        val duration = if (music.duration > 1) {
            music.duration.toInt()
        } else {
            MediaPresenter.getDuration()
        }
        progressBar.set(MediaPresenter.getProgress(), duration)

        

        // iPod Classic 风格：直接设置文本，不使用动画避免显示问题
        title.text = music.title
        title.alpha = 1f
        
        artist.text = music.artist
        artist.alpha = 1f
        
        album.text = music.album
        album.alpha = 1f

        index.text = "${(MediaPresenter.getIndex() + 1)}/${MediaPresenter.getPlaylist().size}"

        // 初始化时间显示 - 使用当前实际进度，避免显示00:00
        val currentProgress = MediaPresenter.getProgress()
        setCurrentTimeText(formatTime(currentProgress))
        setRemainingTimeText("-" + formatTime(duration - currentProgress))

        // 确保图片可见
        image.apply {
            visibility = VISIBLE
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }

        lyric?.setBufferedText(null)
    }

    private val empty : Bitmap?
    get() = if (image.height == 0) null else {
        Icons.EMPTY.let {

            val scaleWidth: Float = image.height / it.width.toFloat()
            val scaleHeight: Float = image.height / it.height.toFloat()
            val matrix = Matrix()
            matrix.postScale(scaleWidth, scaleHeight)
            val result = Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, false)
            result
        }
    }

    private fun loadImage(music : Music) {
        Log.d("MusicPlayerView", "loadImage() started for music: ${music.title}")
        var bitmap: Bitmap? = null

        // 先显示占位图或保持可见
        Log.d("MusicPlayerView", "Setting image visibility to VISIBLE")
        image.visibility = VISIBLE
        Log.d("MusicPlayerView", "Setting placeholder bitmap: ${empty != null}")
        image.setImageBitmap(empty)

        ThreadUtil.asyncTask({
            bitmap = music.image
            Log.d("MusicPlayerView", "Bitmap loaded: ${bitmap != null}")
        }, {
            Log.d("MusicPlayerView", "Image load callback: bitmap=$bitmap")
            if (bitmap == null) {
                Log.d("MusicPlayerView", "Bitmap is null, using default placeholder")
                // 没有封面时使用默认占位图
                // 无封面：用矢量绘制占位图案，避免小图标被放大成模糊的三角
                image.scaleType = android.widget.ImageView.ScaleType.FIT_XY
                image.setImageDrawable(
                    io.github.xejohv.podclassicxzs.widget.CoverPlaceholderDrawable(
                        resources.displayMetrics.density
                    )
                )
                title.gravity = Gravity.START
                title.setPadding(0, 0, 0, 0)
                artist.gravity = Gravity.START
                artist.setPadding(0, 0, 0, 0)
                album.gravity = Gravity.START
                album.setPadding(0, 0, 0, 0)
                index.gravity = Gravity.START
                index.setPadding(0, 0, 0, 0)
                lyric?.gravity = Gravity.CENTER
            } else {
                Log.d("MusicPlayerView", "Bitmap is not null, setting to image view")
                // 设置专辑封面
                image.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                image.setImageBitmap(bitmap)
                image.visibility = VISIBLE

                title.gravity = Gravity.START
                title.setPadding(0, 0, 0, 0)
                artist.gravity = Gravity.START
                artist.setPadding(0, 0, 0, 0)
                album.gravity = Gravity.START
                album.setPadding(0, 0, 0, 0)
                index.gravity = Gravity.START
                index.setPadding(0, 0, 0, 0)
                lyric?.gravity = Gravity.CENTER
                
                // 显示歌词区域（如果启用）
                lyric?.visibility = if (lyric?.text.isNullOrEmpty()) GONE else VISIBLE
            }
            Log.d("MusicPlayerView", "Image load completed")
        })
    }

    private fun onPlayStateChange() {
        val progress = MediaPresenter.getProgress()
        progressBar.set(progress, MediaPresenter.getDuration())
        if (MediaPresenter.isPlaying()) {
            progressTimer.start(progress.toLong())
        } else {
            progressTimer.pause()
        }
    }

    private fun onProgress(progress: Int) {
        if (!seekMode) {
            progressBar.setCurrent(progress)
            // 只在非拖动模式下更新时间显示，保持拖动后的时间显示方便确认
            val duration = MediaPresenter.getDuration()
            setCurrentTimeText(formatTime(progress))
            setRemainingTimeText(if (duration <= 0) "--:--" else "-" + formatTime((duration - progress).coerceAtLeast(0)))
        }
        lyric?.let {
            val line = MediaPresenter.getCurrent()?.lyric?.getLyric(progress)
            it.setBufferedText(line)
            // 逐秒刷新时一并决定可见性：封面回调那一刻还没有歌词文本，
            // 早前只在那边设过一次 GONE，之后就再也亮不起来
            it.visibility = if (line.isNullOrEmpty()) GONE else VISIBLE
        }
    }
    
    // 格式化时间为 mm:ss 格式
    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }


    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }
}
