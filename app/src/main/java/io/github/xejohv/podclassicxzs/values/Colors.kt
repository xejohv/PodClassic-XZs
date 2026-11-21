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

    /** 暖银 Warm Silver —— 官方机身色 #C9C5BD（浅银灰偏米），与机身严格一致 */
    private val WARM_SILVER = Palette(
        bg = 0xFFC9C5BD.toInt(),
        bgLight = 0xFFE9E6E0.toInt(),
        bgDark1 = 0xFFB3AFA6.toInt(),
        bgDark2 = 0xFF9B978D.toInt(),
        text = 0xFF262626.toInt(),
        textSecondary = 0xFF55524B.toInt(),
        line = 0xFF7A766F.toInt(),
        divider = 0xFF8F8C85.toInt(),
        main = 0xFF6C6860.toInt(),
        mainLight = 0xFFA9A59B.toInt(),
        mainDark = 0xFF5B584F.toInt(),
        disc = 0xFFC81E1E.toInt(),
        discInner = 0xFFC9C5BD.toInt(),
        selTop = 0xFF5A5750.toInt(),
        selBottom = 0xFF34322E.toInt(),
        selText = 0xFFFFFFFF.toInt()
    )

    /** 暗黑 Black —— 机身 #1B1B1B */
    private val BLACK = Palette(
        bg = 0xFF2C2C2C.toInt(),
        bgLight = 0xFF3C3C3C.toInt(),
        bgDark1 = 0xFF1C1C1C.toInt(),
        bgDark2 = 0xFF101010.toInt(),
        text = 0xFFF5F5F5.toInt(),
        textSecondary = 0xFFC8C8C8.toInt(),
        line = 0xFF8A8A8A.toInt(),
        divider = 0xFF585858.toInt(),
        main = 0xFFC4C4C4.toInt(),
        mainLight = 0xFFE0E0E0.toInt(),
        mainDark = 0xFF909090.toInt(),
        disc = 0xFFC81E1E.toInt(),
        discInner = 0xFF1B1B1B.toInt(),
        selTop = 0xFF5C5C5C.toInt(),
        selBottom = 0xFF303030.toInt(),
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
        line = 0xFF667D8D.toInt(),
        divider = 0xFF7E92A0.toInt(),
        main = 0xFF46697F.toInt(),
        mainLight = 0xFF7E9CAF.toInt(),
        mainDark = 0xFF33505F.toInt(),
        disc = 0xFFC81E1E.toInt(),
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
        line = 0xFF80853A.toInt(),
        divider = 0xFF959B57.toInt(),
        main = 0xFF6E7422.toInt(),
        mainLight = 0xFFA8AE52.toInt(),
        mainDark = 0xFF4E5314.toInt(),
        disc = 0xFFC81E1E.toInt(),
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

    /** 面板文字：LCD 墨色。机身底上的字请用 ink_on_body */
    val text get() = if (lightPanel) LCD_TEXT else p.text
    val text_secondary get() = if (lightPanel) LCD_TEXT_2 else p.textSecondary
    val text_light get() = if (lightPanel) LCD_TEXT_2 else ContextCompat.getColor(BaseApplication.context, R.color.text_light)

    /**
     * 内容面板（控制盘以上的整块界面，含标题栏与列表）永远是浅色 LCD。
     *
     * 原版 iPod Classic 无论外壳是银、黑还是别的颜色，屏幕里始终是同一块
     * 白底黑字的单色 LCD —— 黑色版只是外壳黑，菜单并不是黑的。
     * 所以这里不再按机身明暗切换面板，机身色只出现在外壳、转盘、浮层边缘。
     */
    private val lightPanel: Boolean get() = true

    /** LCD 面板的墨色：近黑的中性灰，不掺机身色，四种外壳下都读得清 */
    private const val LCD_TEXT = 0xFF1D1D1D.toInt()
    private const val LCD_TEXT_2 = 0xFF5C5C5C.toInt()

    val color_primary get() = p.bg
    /** 机身色的亮阶（不受"内容面板用白底"影响），用于需要跟随机身色又不与主色糊在一起的浮层 */
    val body_light get() = p.bgLight
    /** 机身色的暗阶 */
    val body_dark get() = p.bgDark1

    /**
     * 机身色底上的墨色（锁屏页那种"直接写在机身色上"的地方用）。
     * 面板上的文字用 text（LCD 墨），这里给机身色底留一个不随面板走的入口。
     */
    val ink_on_body get() = p.text

    /** LCD 面板底色：近白，掺一点点机身色（约 8%），四种外壳都显得是同一块屏，又各自协调 */
    val lcd_panel: Int
        get() = mix(0xFFFCFCFA.toInt(), p.bg, 0.08f)

    private fun mix(a: Int, b: Int, t: Float): Int {
        val r = (android.graphics.Color.red(a) * (1 - t) + android.graphics.Color.red(b) * t).toInt()
        val g = (android.graphics.Color.green(a) * (1 - t) + android.graphics.Color.green(b) * t).toInt()
        val bl = (android.graphics.Color.blue(a) * (1 - t) + android.graphics.Color.blue(b) * t).toInt()
        return android.graphics.Color.rgb(r, g, bl)
    }

    /** 机身色的"压暗版"（明度乘以 factor），用于电源菜单那类需要深色背景的浮层 */
    fun bodyDarkened(factor: Float): Int {
        val c = p.bg
        return android.graphics.Color.rgb(
            (android.graphics.Color.red(c) * factor).toInt(),
            (android.graphics.Color.green(c) * factor).toInt(),
            (android.graphics.Color.blue(c) * factor).toInt()
        )
    }
    val background get() = if (lightPanel) 0xFFFFFFFF.toInt() else p.bg
    val background_light get() = if (lightPanel) 0xFFFDFDFB.toInt() else p.bgLight
    /** 白底面板上的分界线：原来是 D8D8D2（白底上只有 1.4，几乎看不见），提到 1.9 才分得出层级 */
    val divider_light get() = if (lightPanel) 0xFFC4C4BE.toInt() else p.divider
    val background_dark_1 get() = if (lightPanel) 0xFFF2F2EE.toInt() else p.bgDark1
    val background_dark_2 get() = if (lightPanel) 0xFFE4E4DE.toInt() else p.bgDark2
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

    /** 当前配色是否偏暗（暗黑主题需要另做对比处理） */
    val isDark: Boolean
        get() {
            val c = p.bg
            val v = (android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 +
                    android.graphics.Color.blue(c) * 114) / 1000
            return v < 110
        }

    /** 屏幕底色 = 机身色，整块屏幕与机身同色系 */
    val screen: Int get() = p.bg

    /** 屏幕纵向渐变：白底极浅渐变，暗黑机身保持深色 */
    val screenGradient: IntArray
        get() = if (lightPanel) intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFFEFEFE.toInt(),
            0xFFFCFCFA.toInt(),
            0xFFF8F8F5.toInt()
        ) else intArrayOf(p.bgDark1, p.bg, p.bgDark1, p.bgDark2)

    // 专辑封面占位音符
    val coverPlaceholderBg get() = if (lightPanel) 0xFFF4F4EF.toInt() else p.bgLight
    val coverPlaceholderInk get() = p.text

    // ---------------- 游戏层 ----------------
    /**
     * 游戏也长在屏幕上，所以底子跟主菜单一样走 LCD 浅色，不再跟机身色走。
     * 原来 gameBg 直接取机身色，暗黑机身下整块游戏区是深灰 + 深色元素，几乎看不清。
     * 现在四种外壳的游戏区都是同一块近白屏，靠"深墨元素"表达画面。
     */
    val gameBg get() = lcd_panel
    val gameInk get() = LCD_TEXT
    val gameInkSoft get() = LCD_TEXT_2
    /** 浅色游戏面板上的强调色：机身主题色如果太浅（暗黑机身的 C4C4C4）就压到中性深灰 */
    val gameAccent: Int
        get() {
            val c = p.main
            val lum = (android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 +
                    android.graphics.Color.blue(c) * 114) / 1000
            return if (lum > 148) 0xFF4A4A46.toInt() else c
        }
    /** 键帽 / 图标 / 描边用的深墨 */
    val gameDeep get() = 0xFF1F1F1F.toInt()
    /** 游戏面板上的浅灰"纸张二宫格"：棋盘、面板底、未开格子的浅色内衬 */
    val gamePaper get() = mix(0xFFECEBE4.toInt(), p.bg, 0.10f)
    /** 游戏面板上的一条细线 */
    val gameLine get() = mix(0xFFD2D1C8.toInt(), p.bg, 0.12f)

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
