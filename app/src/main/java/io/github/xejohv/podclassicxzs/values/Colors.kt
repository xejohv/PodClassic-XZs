package io.github.xejohv.podclassicxzs.values

import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.core.content.ContextCompat
import io.github.xejohv.podclassicxzs.R
import io.github.xejohv.podclassicxzs.base.BaseApplication
import io.github.xejohv.podclassicxzs.storage.SPManager

object Colors {
    val text by lazy { ContextCompat.getColor(BaseApplication.context, R.color.text) }
    val text_secondary by lazy { ContextCompat.getColor(BaseApplication.context, R.color.text_secondary) }

    val color_primary by lazy {
        ContextCompat.getColor(
            BaseApplication.context,
            R.color.colorPrimary
        )
    }

    val text_light by lazy { ContextCompat.getColor(BaseApplication.context, R.color.text_light) }
    val background by lazy { ContextCompat.getColor(BaseApplication.context, R.color.background) }
    val background_light by lazy { ContextCompat.getColor(BaseApplication.context, R.color.background_light) }
    val divider_light by lazy { ContextCompat.getColor(BaseApplication.context, R.color.divider_light) }
    val background_dark_1 by lazy {
        ContextCompat.getColor(
            BaseApplication.context,
            R.color.background_dark_1
        )
    }
    val background_dark_2 by lazy {
        ContextCompat.getColor(
            BaseApplication.context,
            R.color.background_dark_2
        )
    }
    val line by lazy { ContextCompat.getColor(BaseApplication.context, R.color.line) }

    val main by lazy { ContextCompat.getColor(BaseApplication.context, R.color.main) }
    val main_light by lazy { ContextCompat.getColor(BaseApplication.context, R.color.main_light) }
    val main_dark by lazy { ContextCompat.getColor(BaseApplication.context, R.color.main_dark) }
    
    // iPod Classic 风格选中色
    val ipod_selected_top by lazy { ContextCompat.getColor(BaseApplication.context, R.color.ipod_selected_top) }
    val ipod_selected_bottom by lazy { ContextCompat.getColor(BaseApplication.context, R.color.ipod_selected_bottom) }
    val ipod_selected_border by lazy { ContextCompat.getColor(BaseApplication.context, R.color.ipod_selected_border) }

    val battery_green by lazy {
        ContextCompat.getColor(
            BaseApplication.context,
            R.color.battery_green
        )
    }
    val battery_red by lazy { ContextCompat.getColor(BaseApplication.context, R.color.battery_red) }
    val battery_yellow by lazy {
        ContextCompat.getColor(
            BaseApplication.context,
            R.color.battery_yellow
        )
    }

    val white by lazy { ContextCompat.getColor(BaseApplication.context, R.color.white) }
    val black by lazy { ContextCompat.getColor(BaseApplication.context, R.color.black) }

    val theme_red by lazy { ContextCompat.getColor(BaseApplication.context, R.color.theme_red) }
    val theme_black by lazy { ContextCompat.getColor(BaseApplication.context, R.color.theme_black) }
    val theme_white by lazy { ContextCompat.getColor(BaseApplication.context, R.color.theme_white) }
    val theme_gold_inner by lazy { ContextCompat.getColor(BaseApplication.context, R.color.theme_gold_inner) }

    val controller: Int
        get() = when (SPManager.getInt(SPManager.Theme.SP_NAME)) {
            SPManager.Theme.RED.id -> theme_red     // 默认：黑碟
            SPManager.Theme.BLACK.id -> theme_black  // 备选：白碟
            SPManager.Theme.WHITE.id -> theme_white
            else -> theme_black
        }

    // 背景统一为机身金色，不再区分黑/白主题
    val screen: Int
        get() = color_primary

    val button: Int
        get() = when (SPManager.getInt(SPManager.Theme.SP_NAME)) {
            SPManager.Theme.RED.id -> theme_gold_inner
            SPManager.Theme.BLACK.id -> black
            SPManager.Theme.WHITE.id -> white
            else -> theme_gold_inner
        }

    fun getShader(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        c1: Int,
        c2: Int,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP
    ): Shader {
        return LinearGradient(x0, y0, x1, y1, c1, c2, tileMode)
    }
}