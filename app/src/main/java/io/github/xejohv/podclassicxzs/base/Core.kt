package io.github.xejohv.podclassicxzs.base

import io.github.xejohv.podclassicxzs.gestures.GestureService
import io.github.xejohv.podclassicxzs.view.PowerMenuView

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.view.View
import io.github.xejohv.podclassicxzs.activity.MainActivity
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.service.MediaService
import io.github.xejohv.podclassicxzs.storage.MusicListTable
import io.github.xejohv.podclassicxzs.storage.MusicTable
import io.github.xejohv.podclassicxzs.storage.SPManager
import io.github.xejohv.podclassicxzs.util.LocaleHelper
import io.github.xejohv.podclassicxzs.util.MediaStoreUtil
import io.github.xejohv.podclassicxzs.util.ThreadUtil
import io.github.xejohv.podclassicxzs.util.VolumeUtil
import io.github.xejohv.podclassicxzs.values.Colors
import io.github.xejohv.podclassicxzs.values.Strings
import io.github.xejohv.podclassicxzs.values.Values
import io.github.xejohv.podclassicxzs.view.MusicPlayerView
import io.github.xejohv.podclassicxzs.widget.Screen
import io.github.xejohv.podclassicxzs.widget.SlideController
import io.github.xejohv.podclassicxzs.widget.SlideController3rd
import java.util.*
import kotlin.system.exitProcess


@SuppressLint("StaticFieldLeak")
object Core {
    private lateinit var controller: View
    private lateinit var screen: Screen
    private lateinit var nightMode: View
    private var activity: MainActivity? = null

    fun bindActivity(
        controller: View,
        screen: Screen,
        darkMode: View,
        context: MainActivity
    ) {
        this.controller = controller
        this.screen = screen
        activity = context
        nightMode = darkMode
        setupTouchListener()
        // 三指上滑回主菜单（等同 HOME）
        screen.onTripleSwipeUp = { home() }
        // 只更新颜色和状态，不调用refresh()避免无限递归
        when (val ctrl = controller) {
            is SlideController -> {
                ctrl.colorController = Colors.controller
                ctrl.colorButton = Colors.button
                ctrl.invalidate()
            }
            is SlideController3rd -> {
                ctrl.colorController = Colors.controller
                ctrl.colorButton = Colors.button
                ctrl.invalidate()
            }
        }
        activity?.setColor(Colors.screen)
        wake()

    }

    private fun setupTouchListener() {
        when (val ctrl = controller) {
            is SlideController -> {
                ctrl.onTouchListener = object : SlideController.OnTouchListener {
                    override fun onEnterClick(): Boolean {
                        return screen.get().enter()
                    }

                    override fun onEnterLongClick(): Boolean {
                        return screen.get().enterLongClick()
                    }

                    override fun onTouch() {
                        wake()
                    }

                    override fun onSlide(slideVal: Int): Boolean {
                        return screen.get().slide(slideVal)
                    }

                    override fun onMenuClick(): Boolean {
                        return removeView()
                    }

                    override fun onMenuLongClick(): Boolean {
                        home()
                        return true
                    }

                    override fun onNextClick(): Boolean {
                        MediaPresenter.next()
                        return true
                    }

                    override fun onNextLongClick(): Boolean {
                        return MediaPresenter.forward()
                    }

                    override fun onPauseClick(): Boolean {
                        MediaPresenter.playPause()
                        return true
                    }


                    override fun onPrevClick(): Boolean {
                        MediaPresenter.prev()
                        return true
                    }

                    override fun onPrevLongClick(): Boolean {
                        return MediaPresenter.backward()
                    }
                }
            }
            is SlideController3rd -> {
                ctrl.onTouchListener = object : SlideController3rd.OnTouchListener {
                    override fun onEnterClick(): Boolean {
                        return screen.get().enter()
                    }

                    override fun onEnterLongClick(): Boolean {
                        return screen.get().enterLongClick()
                    }

                    override fun onTouch() {
                        wake()
                    }

                    override fun onSlide(slideVal: Int): Boolean {
                        return screen.get().slide(slideVal)
                    }

                    override fun onMenuClick(): Boolean {
                        return removeView()
                    }

                    override fun onMenuLongClick(): Boolean {
                        home()
                        return true
                    }

                    override fun onNextClick(): Boolean {
                        MediaPresenter.next()
                        return true
                    }

                    override fun onNextLongClick(): Boolean {
                        return MediaPresenter.forward()
                    }

                    override fun onPauseClick(): Boolean {
                        MediaPresenter.playPause()
                        return true
                    }


                    override fun onPrevClick(): Boolean {
                        MediaPresenter.prev()
                        return true
                    }

                    override fun onPrevLongClick(): Boolean {
                        return MediaPresenter.backward()
                    }
                }
            }
        }
    }


    var lock = false
        private set

    fun lock(lock: Boolean) {
        this.lock = lock
        when (val ctrl = controller) {
            is SlideController -> {
                ctrl.enable = !lock
            }
            is SlideController3rd -> {
                ctrl.enable = !lock
            }
        }
    }

    /** 游戏全屏模式：把下方控制盘折叠掉，让画面占满整块屏 */
    var gameExpanded = false
        private set

    fun setGameExpanded(expanded: Boolean) {
        // 即使状态没变也重新跑一次布局：折叠发生在游戏 View 刚加进来的时候，
        // 偶发的测量时序会把折叠结果冲掉，多刷一次保证稳定
        gameExpanded = expanded
        activity?.initView()
        activity?.window?.decorView?.post {
            activity?.initView()
        }
    }

    fun addView(view: ScreenView) {
        gameExpanded = false
        screen.add(view)
    }

    fun getView(): ScreenView {
        return screen.get()
    }

    fun removeView(): Boolean {
        gameExpanded = false
        val leavingPowerMenu = screen.get() is PowerMenuView
        val result = screen.remove()
        if (leavingPowerMenu) {
            // 退出自己的电源菜单时，把被盖在下面的系统电源菜单一并关掉
            GestureService.closeSystemPowerMenu()
        }
        activity?.initView()
        return result
    }

    fun home() {
        gameExpanded = false
        screen.home()
        activity?.initView()
    }

    fun refresh() {
        // 重新初始化视图，确保主题切换时SlideController的显示状态正确更新
        activity?.initView()
        // 更新控制器颜色和状态
        when (val ctrl = controller) {
            is SlideController -> {
                ctrl.colorController = Colors.controller
                ctrl.colorButton = Colors.button
                ctrl.invalidate()
            }
            is SlideController3rd -> {
                ctrl.colorController = Colors.controller
                ctrl.colorButton = Colors.button
                ctrl.invalidate()
            }
        }
        activity?.setColor(Colors.screen)

        // 通知当前视图配置已变化（横竖屏切换）
        screen.get().onConfigurationChanged()
    }

    fun setNightMode() {
        val id = SPManager.getInt(
            SPManager.NightMode.SP_NAME
        )
        val darkMode = if (SPManager.NightMode.AUTO.id == id) {
            val nightModeFlags =
                BaseApplication.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightModeFlags == Configuration.UI_MODE_NIGHT_UNDEFINED || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val calendar = Calendar.getInstance()
                val hour = calendar[Calendar.HOUR_OF_DAY]
                hour >= 23 || hour <= 5
            } else {
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        } else {
            SPManager.NightMode.ENABLE.id == id
        }
        nightMode.visibility = if (darkMode) View.VISIBLE else View.GONE
    }

    fun setLanguage() {
        // 之前这里把 createConfigurationContext() 的返回值丢了，等于什么都没配，
        // 所以语言选项点了也不生效。现在直接把 locale 落到应用上下文的 resources 上，
        // Activity 那层由 attachBaseContext() 负责（见 LocaleHelper）
        LocaleHelper.applyTo(BaseApplication.context)
        Strings.init()
    }

    private const val DELAY = 120000L  // 2 minutes

    private var timer = io.github.xejohv.podclassicxzs.util.Timer(DELAY) {
        if (MediaPresenter.isPlaying() && screen.currentView !is io.github.xejohv.podclassicxzs.view.MusicPlayerView && screen.currentView !is io.github.xejohv.podclassicxzs.view.MusicPlayerView3rd && activity != null) {
            ThreadUtil.runOnUiThread {
                if (SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id) {
                    addView(io.github.xejohv.podclassicxzs.view.MusicPlayerView3rd(activity!!))
                } else {
                    addView(io.github.xejohv.podclassicxzs.view.MusicPlayerView(activity!!))
                }
            }
        }
    }

    private var prevTimerSetTime = 0L
    fun wake() {
        if (!MediaPresenter.isPlaying()) {
            return
        }
        val currentMillis = System.currentTimeMillis()
        if (currentMillis - prevTimerSetTime <= 1000) {
            return
        }
        prevTimerSetTime = currentMillis
        timer.start(0L)
    }

    /** 供设置页原地重建界面（切换语言后不退出应用，直接重建 Activity） */
    fun getActivityInstance(): MainActivity? = activity

    fun reboot() {
        if (Values.LAUNCHER) {
            SPManager.save()
            exitProcess(0)
        } else {
            val context = BaseApplication.context
            activity?.finish()
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            BaseApplication.context.startActivity(intent)
            init()
        }
    }

    fun exit() {
        if (Values.LAUNCHER) {
            MediaPresenter.stop()
        } else {
            MediaPresenter.stop()
            SPManager.save()
            VolumeUtil.releaseSoundPool()
            activity?.stopService(Intent(activity, MediaService::class.java))
            activity?.finish()
            activity = null
            //exitProcess(0)
        }
    }

    fun reset() {
        SPManager.reset()
        MusicListTable.album.clear()
        MusicListTable.folder.clear()
        MusicListTable.artist.clear()
        MusicTable.favourite.clear()
    }

    fun initUI() {
        MediaStoreUtil.init()
        MediaPresenter.init()
        //AppsView.init()
    }


    /** 对外广播的蓝牙名：别的设备搜到的是 iPod Classic，而不是 Xperia XZs */
    private const val BLUETOOTH_NAME = "iPod Classic"

    /**
     * 每次启动把蓝牙名改回来。系统恢复出厂 / 换机 / 被别处改过都能自愈，
     * 名字已经对了就不写，避免每次开机都重启蓝牙。
     */
    fun applyBluetoothName() {
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) return
            if (adapter.name == BLUETOOTH_NAME) return
            adapter.name = BLUETOOTH_NAME
        } catch (e: Exception) {
            android.util.Log.w("Core", "rename bluetooth failed", e)
        }
    }

    fun init() {
        setLanguage()
        applyBluetoothName()
        // 无论是否加载Activity都需要初始化
        //MediaStoreUtil.prepare()
        //AppWidget.updateRemoteViews(null)
    }
}