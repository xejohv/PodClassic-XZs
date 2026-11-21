package io.github.xejohv.podclassicxzs.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 语言切换。
 *
 * 之前只在 Core.setLanguage() 里把 configuration 设好，却把 createConfigurationContext()
 * 的返回值直接丢掉，所以界面上永远还是系统语言。这里改成两件事：
 *
 *  1. Application / Activity 在 attachBaseContext 时就把 base 换成本地化 Context，
 *     这样所有字符串资源在 inflate 阶段就是目标语言；
 *  2. 应用上下文再 updateConfiguration() 一次，保证 Strings.init() 这种
 *     直接从 BaseApplication.context 取文案的地方也拿到新语言。
 *
 * 读取语言偏好的时机早于 SPManager 初始化（Application 还在 attachBaseContext），
 * 所以这里直接读默认 SharedPreferences，不依赖 SPManager。
 */
object LocaleHelper {

    /** PreferenceManager.getDefaultSharedPreferences 的文件名规则 */
    private const val PREF_SUFFIX = "_preferences"

    /** 与 SPManager.Language.SP_NAME 保持一致 */
    const val KEY_LANGUAGE = "language"

    const val LANG_AUTO = 0
    const val LANG_CN = 1
    const val LANG_TW = 2
    const val LANG_EN = 3

    fun languageId(base: Context): Int = try {
        base.getSharedPreferences(
            base.packageName + PREF_SUFFIX,
            Context.MODE_PRIVATE
        ).getInt(KEY_LANGUAGE, LANG_AUTO)
    } catch (e: Exception) {
        LANG_AUTO
    }

    /** AUTO 返回 null，表示跟随系统 */
    fun localeOf(id: Int): Locale? = when (id) {
        LANG_CN -> Locale.SIMPLIFIED_CHINESE
        LANG_TW -> Locale.TRADITIONAL_CHINESE
        LANG_EN -> Locale.US
        else -> null
    }

    /** 给 Application / Activity 的 base context 套上目标语言 */
    fun wrap(base: Context): Context {
        val locale = localeOf(languageId(base)) ?: return base
        return try {
            Locale.setDefault(locale)
            val configuration = Configuration(base.resources.configuration)
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            base.createConfigurationContext(configuration)
        } catch (e: Exception) {
            base
        }
    }

    /** 把语言写进应用上下文自身的 resources（Strings.init 等直接取资源的地方生效） */
    fun applyTo(context: Context) {
        val locale = localeOf(languageId(context)) ?: run {
            // 跟随系统：把默认 Locale 还给系统，避免上一次 setDefault 残留
            try {
                @Suppress("DEPRECATION")
                val system = context.resources.configuration.locale
                if (system != null && system != Locale.getDefault()) {
                    Locale.setDefault(system)
                }
            } catch (e: Exception) {
            }
            return
        }
        try {
            Locale.setDefault(locale)
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        } catch (e: Exception) {
        }
    }
}
