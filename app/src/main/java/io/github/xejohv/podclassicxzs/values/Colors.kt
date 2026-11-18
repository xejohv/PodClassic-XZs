package io.github.xejohv.podclassicxzs.values

import android.graphics.LinearGradient
import android.graphics.Shader
import io.github.xejohv.podclassicxzs.R
import io.github.xejohv.podclassicxzs.base.BaseApplication
import io.github.xejohv.podclassicxzs.storage.SPManager
import androidx.core.content.ContextCompat

/**
 * 主题色板。
 *
 * 主题 = 机身配色，严格按 Sony Xperia XZs 官方发布颜色：
 *   暖银 Warm Silver / 暗黑 Black / 冰蓝 Ice Blue / 金桔 Citrus（日本限定）
 *
 * 色值取自官方产品渲染图的后盖主色，再按界面可读性微调
 * （同一色的明暗梯度用于标题栏、列表、选中条，保证文字与背景对比度）。
 */
object Colors {

    data class Palette(
        val bg: Int,
        val bgLight: Int,
        val bgDark1: Int,
        val bgDark2: Int,
        val text: Int,
        val textSecondary: Int,
        val line: Int,
        val divider: Int,
        val main: Int,
        val mainLight: Int,
        val mainDark: Int,
        val disc: Int,
        val discInner: Int,
        val selTop: Int,
        val selBottom: Int,
        val selText: Int
    )

    /** 暖银 Warm Silver —— 机身 #C9C3B4，暖光下呈香槟金 */
    private val WARM_SILVER = Palette(
        bg = 0xFFC9AE68.toInt(),
        bgLight = 0xFFF6EAC0.toInt(),
        bgDark1 = 0xFFDCC98D.toInt(),
        bgDark2 = 0xFFC0A961.toInt(),
        text = 0xFF2A2415.toInt(),
        textSecondary = 0xFF5A4C22.toInt(),
        line = 0xFFA08B48.toInt(),
        divider = 0xFFC4AC66.toInt(),
        main = 0xFF8C8752.toInt(),
        mainLight = 0xFFBDB47C.toInt(),
        mainDark = 0xFF6B6539.toInt(),
        disc = 0xFF000000.toInt(),
        discInner = 0xFFB29B54.toInt(),
        selTop = 0xFF4A4022.toInt(),
        selBottom = 0xFF2A2415.toInt(),
        selText = 0xFFFFFFFF.toInt()
    )

    /** 暗黑 Black —— 机身 #1B1B1B */
    private val BLACK = Palette(
        bg = 0xFF242424.toInt(),
        bgLight = 0xFF333333.toInt(),
        bgDark1 = 0xFF1B1B1B.toInt(),
        bgDark2 = 0xFF141414.toInt(),
        text = 0xFFF2F2F2.toInt(),
        textSecondary = 0xFFB5B5B5.toInt(),
        line = 0xFF5A5A5A.toInt(),
        divider = 0xFF3D3D3D.toInt(),
        main = 0xFF9E9E9E.toInt(),
        mainLight = 0xFFC9C9C9.toInt(),
        mainDark = 0xFF6B6B6B.toInt(),
        disc = 0xFFF5F5F5.toInt(),
        discInner = 0xFF2C2C2C.toInt(),
        selTop = 0xFF4E4E4E.toInt(),
        selBottom = 0xFF2A2A2A.toInt(),
        selText = 0xFFFFFFFF.toInt()
    )

    /** 冰蓝 Ice Blue —— 机身 #BFD3E4 */
    private val ICE_BLUE = Palette(
        bg = 0xFFB7CCDF.toInt(),
        bgLight = 0xFFD7E5F0.toInt(),
        bgDark1 = 0xFF9FB6C8.toInt(),
        bgDark2 = 0xFF8AA2B5.toInt(),
        text = 0xFF12242F.toInt(),
        textSecondary = 0xFF3A5464.toInt(),
        line = 0xFF7B96A9.toInt(),
        divider = 0xFF99B1C2.toInt(),
        main = 0xFF46697F.toInt(),
        mainLight = 0xFF7E9CAF.toInt(),
        mainDark = 0xFF33505F.toInt(),
        disc = 0xFF22303A.toInt(),
        discInner = 0xFFB7CCDF.toInt(),
        selTop = 0xFF2E4A5B.toInt(),
        selBottom = 0xFF1B2F3A.toInt(),
        selText = 0xFFF4F8FB.toInt()
    )

    /** 金桔 Citrus（日本限定）—— 机身 #D7E04A */
    private val CITRUS = Palette(
        bg = 0xFFD2D96A.toInt(),
        bgLight = 0xFFEFF2B5.toInt(),
        bgDark1 = 0xFFC2CA59.toInt(),
        bgDark2 = 0xFFACB44A.toInt(),
        text = 0xFF232709.toInt(),
        textSecondary = 0xFF4A4F16.toInt(),
        line = 0xFF8E9440.toInt(),
        divider = 0xFFAAB063.toInt(),
        main = 0xFF6E7422.toInt(),
        mainLight = 0xFFA8AE52.toInt(),
        mainDark = 0xFF4E5314.toInt(),
        disc = 0xFF1D2008.toInt(),
        discInner = 0xFFD2D96A.toInt(),
        selTop = 0xFF3C4012.toInt(),
        selBottom = 0xFF24280A.toInt(),
        selText = 0xFFF7F9D8.toInt()
    )

    private var cached: Palette? = null
    private var cachedId: Int = Int.MIN_VALUE

    /** 当前机身配色 id（用于主题页勾选状态） */
    val bodyId: Int
        get() = SPManager.getInt(SPManager.BodyColor.SP_NAME)

    private val p: Palette
        get() {
            val id = bodyId
            val cur = cached
            if (cur != null && cachedId == id) {
                return cur
            }
            val palette = when (id) {
                SPManager.BodyColor.BLACK.id -> BLACK
                SPManager.BodyColor.ICE_BLUE.id -> ICE_BLUE
                SPManager.BodyColor.CITRUS.id -> CITRUS
                else -> WARM_SILVER
            }
            cached = palette
            cachedId = id
            return palette
        }

    /** 切换机身配色后调用，下一次取色重新解析 */
    fun invalidateTheme() {
        cached = null
        cachedId = Int.MIN_VALUE
    }

    fun setBody(id: Int) {
        SPManager.setInt(SPManager.BodyColor.SP_NAME, id)
        invalidateTheme()
    }

    val text get() = p.text
    val text_secondary get() = p.textSecondary
    val text_light get() = ContextCompat.getColor(BaseApplication.context, R.color.text_light)

    val color_primary get() = p.bg
    val background get() = p.bg
    val background_light get() = p.bgLight
    val divider_light get() = p.divider
    val background_dark_1 get() = p.bgDark1
    val background_dark_2 get() = p.bgDark2
    val line get() = p.line

    val main get() = p.main
    val main_light get() = p.mainLight
    val main_dark get() = p.mainDark

    // 选中条目：深色选中条 + 亮字，任何机身色下都有对比
    val ipod_selected_top get() = p.selTop
    val ipod_selected_bottom get() = p.selBottom
    val ipod_selected_border get() = p.selBottom
    val selected_text get() = p.selText

    val battery_green get() = ContextCompat.getColor(BaseApplication.context, R.color.battery_green)
    val battery_red get() = ContextCompat.getColor(BaseApplication.context, R.color.battery_red)
    val battery_yellow get() = ContextCompat.getColor(BaseApplication.context, R.color.battery_yellow)

    val white get() = ContextCompat.getColor(BaseApplication.context, R.color.white)
    val black get() = ContextCompat.getColor(BaseApplication.context, R.color.black)

    // 转盘：盘面用主题碟片色，圆心用机身色（暖银=金圆心、暗黑=黑圆心…）
    val theme_red get() = p.disc
    val theme_black get() = p.disc
    val theme_white get() = p.discInner
    val theme_gold_inner get() = p.discInner

    val controller: Int get() = p.disc
    val button: Int get() = p.discInner

    /** 屏幕底色 = 机身色，整块屏幕与机身同色系 */
    val screen: Int get() = p.bg

    /** 屏幕纵向渐变（顶部略亮，底部略深） */
    val screenGradient: IntArray
        get() = intArrayOf(p.bgDark1, p.bg, p.bgDark1, p.bgDark2)

    // 专辑封面占位音符
    val coverPlaceholderBg get() = p.bgLight
    val coverPlaceholderInk get() = p.text

    // 游戏通用三色（与播放器同一套）
    val gameBg get() = p.bg
    val gameInk get() = p.text
    val gameAccent get() = p.main
    val gameDeep get() = p.selBottom

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
