package io.github.xejohv.podclassicxzs.util

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import io.github.xejohv.podclassicxzs.R
import io.github.xejohv.podclassicxzs.base.BaseApplication
import io.github.xejohv.podclassicxzs.storage.SPManager

object VolumeUtil {
    private val audioManager by lazy {
        BaseApplication.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val maxVolume by lazy { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    private var streamId = 0
    private val soundPool = SoundPool.Builder().apply {
        setMaxStreams(4)
        /*
        setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        */
        build()
    }.build().apply {
        streamId = load(BaseApplication.context, R.raw.click, 0)
    }

    fun getCurrentVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun setCurrentVolume(currentVolume: Int) {
        var curVolume = currentVolume
        if (curVolume < 0) {
            curVolume = 0
        }
        if (curVolume > maxVolume) {
            curVolume = maxVolume
        }

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, curVolume, 0)
    }

    fun volumeUp() {
        setCurrentVolume(getCurrentVolume() + 1)
    }

    fun volumeDown() {
        setCurrentVolume(getCurrentVolume() - 1)
    }

    fun vibrate(view: View) {
        val sound = SPManager.getInt(
            SPManager.Sound.SP_NAME
        )
        if ((sound and SPManager.Sound.SOUND.id) != 0) {
            soundPool.play(streamId, 1f, 1f, 0, 0, 1f)
        }
        if (sound and SPManager.Sound.VIBRATE.id != 0) {
            ThreadUtil.newThread {
                view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
        }
    }

    /**
     * 系统媒体音量监听（硬件音量键同步用）。
     *
     * Android 8 上 "android.media.VOLUME_CHANGED_ACTION" 这条隐式广播并不可靠——
     * 很多机型根本不发，或者被系统拦掉，所以之前按音量键，播放页那条换出来的音量条不会动。
     * 这里用 ContentObserver 盯 Settings.System 的音量项，再叠一个 300ms 的轻量轮询兜底，
     * 两条路任意一条能拿到变化就够了。
     */
    class Watcher(private val onChanged: (Int) -> Unit) {
        private var last = getCurrentVolume()
        private var observer: ContentObserver? = null
        private val handler = Handler(Looper.getMainLooper())
        private var running = false

        private val tick = object : Runnable {
            override fun run() {
                if (!running) return
                val v = getCurrentVolume()
                if (v != last) {
                    last = v
                    onChanged(v)
                }
                handler.postDelayed(this, POLL_MS)
            }
        }

        fun start() {
            if (running) return
            running = true
            last = getCurrentVolume()
            try {
                val o = object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) {
                        val v = getCurrentVolume()
                        if (v != last) {
                            last = v
                            onChanged(v)
                        }
                    }
                }
                val resolver = BaseApplication.context.contentResolver
                // 两种命名在不同机型 / Android 版本上都出现过，都盯上
                resolver.registerContentObserver(Settings.System.getUriFor("volume_music_speaker"), false, o)
                resolver.registerContentObserver(Settings.System.getUriFor("volume_music"), false, o)
                observer = o
            } catch (e: Exception) {
            }
            handler.post(tick)
        }

        fun stop() {
            running = false
            handler.removeCallbacks(tick)
            observer?.let {
                try {
                    BaseApplication.context.contentResolver.unregisterContentObserver(it)
                } catch (e: Exception) {
                }
            }
            observer = null
        }
    }

    private const val POLL_MS = 300L

    fun releaseSoundPool() {
        soundPool.release()
    }
}