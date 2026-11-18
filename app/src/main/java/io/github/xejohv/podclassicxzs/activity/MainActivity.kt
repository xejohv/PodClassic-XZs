package io.github.xejohv.podclassicxzs.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.xejohv.podclassicxzs.R
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.fragment.SplashFragment
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.service.MediaService
import io.github.xejohv.podclassicxzs.storage.SPManager
import io.github.xejohv.podclassicxzs.util.MediaStoreUtil
import io.github.xejohv.podclassicxzs.util.ThreadUtil
import io.github.xejohv.podclassicxzs.values.Values
import io.github.xejohv.podclassicxzs.view.MusicPlayerView

class MainActivity : AppCompatActivity() {

    companion object {
        // 状态栏可见时，屏幕区距离状态栏的额外边距（px）
        const val TOP_MARGIN = 24
        // 状态栏不可见（全局沉浸）时，屏幕区距离屏幕顶端的边距（dp）
        const val TOP_MARGIN_COMPACT_DP = 5
    }

    private var statusBarHeight = 0


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val uri = intent?.data
        if (uri != null) {
            val music = MediaStoreUtil.getMusicFromUri(uri)
            if (music != null) {
                MediaPresenter.set(music)
                ThreadUtil.runOnUiThread {
                    Core.addView(MusicPlayerView(this))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initScreen()
        setContentView(R.layout.activity_main)
        initView()
        if (!SPManager.getBoolean(SPManager.SP_STARTED)) {
            SPManager.setBoolean(SPManager.SP_STARTED, true)
            SPManager.reset()
        }
        // 根据当前选择的主题来决定绑定哪个SlideController到Core
        val isIpod3rdTheme = SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id
        
        Core.bindActivity(
            if (isIpod3rdTheme) {
                findViewById<View>(R.id.slide_controller_3rd)
            } else {
                findViewById<View>(R.id.slide_controller)
            },
            findViewById(R.id.screen),
            findViewById(R.id.dark_mode),
            this
        )
        checkPermission()
    }

    private fun prepare() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.screen, SplashFragment {
                Core.lock(true)
                // 启动MediaService
                val intent = Intent(this@MainActivity, MediaService::class.java)
                try {
                    startService(intent)
                } catch (e: Exception) {
                    // Android 12+ 后台限制，尝试使用 startForegroundService
                    try {
                        startForegroundService(intent)
                    } catch (e2: Exception) {
                        android.util.Log.e("MainActivity", "Failed to start service: ${e2.message}")
                    }
                }
                Core.initUI()
                initMediaPlayer()
                Core.lock(false)
            })
            .commit()
    }

    /*
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                Core.down()
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                Core.up()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                MediaPresenter.prev()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                MediaPresenter.next()
            }
            KeyEvent.KEYCODE_DEL -> {
                Core.removeView()
            }
            KeyEvent.KEYCODE_ENTER -> {
                Core.enter()
            }
            KeyEvent.KEYCODE_BACK -> {
                //Core.home()
            }
            else -> {
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                //Core.down()
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                //Core.up()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                MediaPresenter.backward()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                MediaPresenter.forward()
            }
            KeyEvent.KEYCODE_DEL -> {
                Core.home()
            }
            KeyEvent.KEYCODE_ENTER -> {
                Core.enter()
            }
            KeyEvent.KEYCODE_BACK -> {
                Core.home()
            }
            else -> {
            }
        }
        return super.onKeyUp(keyCode, event)
    }

     */

    override fun onBackPressed() {
        if (Values.LAUNCHER) {
            if (Core.lock) {
                return
            }
            Core.removeView()
        } else {
            if (!Core.removeView()) {
                Core.exit()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            Core.setNightMode()
        }
    }

    private fun initMediaPlayer() {
        when (intent?.action) {
            MediaService.ACTION_MAIN -> {
                return
            }
            MediaService.ACTION_SHUFFLE -> {
                MediaPresenter.shufflePlay()
                ThreadUtil.runOnUiThread {
                    Core.addView(MusicPlayerView(this))
                }
                return
            }
        }
        val uri = intent?.data
        if (uri != null) {
            onNewIntent(intent)
            return
        }

        val autoStop = SPManager.getInt(SPManager.AutoStop.SP_NAME)
        if (!MediaPresenter.isPlaying() && autoStop != 0) {
            MediaPresenter.shufflePlay()
            if (autoStop != SPManager.AutoStop.FOREVER_ID) {
                MediaPresenter.setStopTime(SPManager.AutoStop.getMinute(autoStop))
            }
            ThreadUtil.runOnUiThread {
                Core.addView(MusicPlayerView(this))
            }
        }
    }

    private fun initScreen() {
        val resourceId: Int = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(resourceId)
        }

        val displayMetrics = resources.displayMetrics

        Values.screenWidth = displayMetrics.widthPixels
        Values.screenHeight = displayMetrics.heightPixels
    }

    fun initView() {
        /*
        val layoutParams = slide_controller.layoutParams as RelativeLayout.LayoutParams

        when {
            screenRatio < 16f / 9f -> {
                layoutParams.leftMargin = layoutParams.leftMargin * 7 / 6
                layoutParams.rightMargin = layoutParams.rightMargin * 7 / 6
                layoutParams.topMargin /= 2
                layoutParams.bottomMargin /= 4
            }
            screenRatio == 16f / 9f -> {
                layoutParams.topMargin /= 2
                layoutParams.bottomMargin /= 2
            }
            screenRatio <= 17f / 9f -> {
                layoutParams.bottomMargin /= 2
            }
            screenRatio >= 21f / 10f -> {
                layoutParams.topMargin = layoutParams.topMargin * 2
            }
            else -> {
                layoutParams.topMargin = layoutParams.topMargin * 3 / 2
            }
        }

        (frame_layout.layoutParams as RelativeLayout.LayoutParams).topMargin = statusBarHeight + TOP_MARGIN
         */


        val linearLayout = findViewById<ViewGroup>(R.id.layout_rectangle)
        val layoutParams = (linearLayout.layoutParams as LinearLayout.LayoutParams)
        val slideController = findViewById<View>(R.id.slide_controller)
        val slideController3rd = findViewById<View>(R.id.slide_controller_3rd)
        val slideControllerParams = (slideController.layoutParams as LinearLayout.LayoutParams)
        
        // 检查是否选择了iPod 3rd主题
        val isIpod3rdTheme = SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id
        
        // 检查是否是横屏模式且当前显示的是CoverFlowView、MainView或MusicListView
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val currentViewName = try {
            Core.getView()::class.java.simpleName
        } catch (e: Exception) {
            ""
        }
        val isCoverFlowView = currentViewName == "CoverFlowView"
        val isMainView = currentViewName == "MainView"
        val isMusicListView = currentViewName == "MusicListView"
        val shouldFullScreen = isCoverFlowView || isMainView || isMusicListView
        
        // 根据主题显示或隐藏对应的SlideController
        if (isIpod3rdTheme) {
            slideController.visibility = View.GONE
            slideController3rd.visibility = View.VISIBLE
        } else {
            slideController.visibility = View.VISIBLE
            slideController3rd.visibility = View.GONE
        }
        
        if (Core.gameExpanded) {
            // 游戏折叠模式：控制盘收起来（高度 0），屏幕区吃掉全部高度
            layoutParams.height = 0
            layoutParams.weight = 1f
            layoutParams.topMargin = 0
            layoutParams.leftMargin = 0
            layoutParams.rightMargin = 0
            slideControllerParams.height = 0
            slideController3rd.layoutParams.height = 0
        } else if (isLandscape && shouldFullScreen) {
            // 横屏且是CoverFlowView、MainView或MusicListView，全屏显示
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            layoutParams.weight = 0f
            layoutParams.topMargin = 0
            layoutParams.leftMargin = 0
            layoutParams.rightMargin = 0
            slideControllerParams.height = 0
            slideController3rd.layoutParams.height = 0
        } else {
            // 其他情况，保持默认布局
            // 使用dp单位设置高度，确保在不同分辨率屏幕上显示一致
            layoutParams.height = resources.getDimensionPixelSize(R.dimen.layout_rectangle_height)
            layoutParams.weight = 0f
            // 状态栏被隐藏（沉浸）时不要再为状态栏留白，把空间让给屏幕区和滚轮
            layoutParams.topMargin = 0
            layoutParams.leftMargin = 0
            layoutParams.rightMargin = 0
            slideControllerParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            slideController3rd.layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT
        }

        // 直接改 layoutParams 不会自己触发重新测量，这里显式请求一次布局
        linearLayout.requestLayout()
        slideController.requestLayout()
        slideController3rd.requestLayout()
        (findViewById<View>(R.id.screen) as? io.github.xejohv.podclassicxzs.widget.Screen)
            ?.setTitleBarHidden(Core.gameExpanded)

        //linearLayout.clipToOutline = true


        //隐藏虚拟按键
        //window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN;
    }

    /** 当前窗口顶部是否被状态栏占据 */
    private fun isStatusBarVisible(): Boolean {
        val frame = android.graphics.Rect()
        try {
            window.decorView.getWindowVisibleDisplayFrame(frame)
        } catch (e: Exception) {
            return statusBarHeight > 0
        }
        return frame.top > 0
    }

    fun setColor(color: Int) {
        window.statusBarColor = color
        window.navigationBarColor = color
        findViewById<View>(R.id.main_layout)?.setBackgroundColor(color)
    }

    // 重新绑定控制器，确保主题切换后控制器仍然可以触摸
    fun rebindController() {
        val isIpod3rdTheme = SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id
        val controllerView = if (isIpod3rdTheme) {
            findViewById<View>(R.id.slide_controller_3rd)
        } else {
            findViewById<View>(R.id.slide_controller)
        }
        
        Core.bindActivity(
            controllerView,
            findViewById(R.id.screen),
            findViewById(R.id.dark_mode),
            this
        )
    }

    private fun checkPermission() {
        // Android 13 及以上版本使用 READ_MEDIA_AUDIO 权限
        val READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO"
        val isAndroid13OrHigher = android.os.Build.VERSION.SDK_INT >= 33 // TIRAMISU is API 33
        
        val permissions = if (isAndroid13OrHigher) {
            // Android 13 及以上版本
            arrayOf(
                READ_MEDIA_AUDIO
            )
        } else {
            // Android 12 及以下版本
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        
        val permissionToCheck = if (isAndroid13OrHigher) {
            READ_MEDIA_AUDIO
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(
                this,
                permissionToCheck
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                permissions,
                1
            )
        } else {
            prepare()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Core.exit()
        } else {
            prepare()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 重新计算屏幕尺寸
        initScreen()
        // 重新初始化布局，适应新的屏幕方向
        initView()
        // 刷新界面，确保所有视图正确显示
        Core.refresh()
    }
}

