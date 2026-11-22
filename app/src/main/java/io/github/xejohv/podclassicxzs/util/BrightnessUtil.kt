package io.github.xejohv.podclassicxzs.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import io.github.xejohv.podclassicxzs.storage.SPManager

/**
 * 屏幕亮度调节：只改本应用窗口的 `screenBrightness`，不申请 WRITE_SETTINGS 权限，
 * 也不会去动系统的全局亮度——纯 iPod 场景下这就够了，退回桌面自然交还系统亮度。
 *
 * 取值约定：0 = 自动（跟随系统，不覆盖）；10~100 = 手动百分比。
 * 默认是「自动」，老用户升级上来亮度一点不变，只有自己点过才改。
 */
object BrightnessUtil {

    const val SP_NAME = "screen_brightness_percent"

    /** 自动（不覆盖系统亮度） */
    const val AUTO = 0

    const val MIN = 10
    const val MAX = 100
    const val STEP = 10

    /** 一次点击的档位序列：自动 → 20 → 40 → 60 → 80 → 100 → 自动 */
    private val LEVELS = intArrayOf(AUTO, 20, 40, 60, 80, 100)

    fun raw(): Int {
        val v = SPManager.getInt(SP_NAME)
        return if (v < 0 || v > MAX) AUTO else v
    }

    fun isAuto(): Boolean = raw() == AUTO

    fun next(current: Int = raw()): Int {
        val i = LEVELS.indexOf(current)
        return if (i < 0) LEVELS[1] else LEVELS[(i + 1) % LEVELS.size]
    }

    fun set(value: Int) {
        val v = if (value == AUTO) AUTO else value.coerceIn(MIN, MAX)
        SPManager.setInt(SP_NAME, v)
    }

    /** 右侧显示文本：自动 / 60% */
    fun label(value: Int = raw(), autoText: String): String {
        return if (value == AUTO) autoText else "$value%"
    }

    /** 应用到当前窗口；自动档就交还系统亮度 */
    fun apply(context: Context?) {
        val activity = findActivity(context) ?: return
        try {
            val window = activity.window ?: return
            val params = window.attributes
            val v = raw()
            params.screenBrightness =
                if (v == AUTO) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                else v / 100f
            window.attributes = params
        } catch (ignored: Exception) {
        }
    }

    private fun findActivity(context: Context?): Activity? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
