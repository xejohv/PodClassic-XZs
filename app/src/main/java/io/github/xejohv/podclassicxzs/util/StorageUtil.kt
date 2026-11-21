package io.github.xejohv.podclassicxzs.util

import android.os.Environment
import android.os.StatFs
import io.github.xejohv.podclassicxzs.base.BaseApplication
import java.util.Locale

/**
 * 容量统计。
 *
 * 之前「关于本机」里的容量是拿 blockCount*blockSize 直接除以 1024^3 再取整，
 * 有三个毛病：
 * 1. 整数截断：21.6 GB 会显示成 21 GB、16.4 GB 显示成 16 GB，看起来就像算错了；
 * 2. 用 blockCountLong 当分子、availableBlocksLong 当分母，两者口径其实不一样
 *    （前者含文件系统保留块，后者只算普通用户能写的块），差出来的 1 GB 会被误当成"已用"；
 * 3. 只 stat 了 /data，插了 SD 卡时卡上的空间完全没算进去，而曲库是扫 MediaStore 的，
 *    歌放在卡上就会出现"歌有一堆、容量却说只用了 4 GB"的怪现象。
 *
 * 现在统一走 StatFs 的字节接口：total = 全部块 × 块大小，free = 可分配块 × 块大小，
 * 内置存储之外再单独 stat 外置 SD 卡，两者相加作为总量，并单独给出各自明细。
 */
object StorageUtil {

    private const val KIB = 1024.0
    private const val MIB = KIB * 1024.0
    private const val GIB = MIB * 1024.0

    /** 内置存储（/data）：(总量, 可用)，读不到就返回 null */
    fun internal(): Pair<Long, Long>? = volume(Environment.getDataDirectory().path)

    /** 外置 SD 卡：(总量, 可用)，没插卡或不可读返回 null */
    fun external(): Pair<Long, Long>? {
        val root = sdRoot() ?: return null
        return volume(root)
    }

    /** 合计总量：内置 + 外置卡 */
    fun totalBytes(): Long = (internal()?.first ?: 0L) + (external()?.first ?: 0L)

    /** 合计可用：内置 + 外置卡 */
    fun freeBytes(): Long = (internal()?.second ?: 0L) + (external()?.second ?: 0L)

    /** 合计已用 */
    fun usedBytes(): Long = (totalBytes() - freeBytes()).coerceAtLeast(0L)

    /** 是否插了可用的外置卡 */
    fun hasExternal(): Boolean = external() != null

    private fun volume(path: String?): Pair<Long, Long>? {
        if (path.isNullOrBlank()) return null
        return try {
            val stat = StatFs(path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            if (total <= 0L) null else total to free
        } catch (e: Exception) {
            null
        }
    }

    /** 找外置卡的挂载根目录，找不到返回 null */
    fun sdRoot(): String? {
        // 1) 老路子：反射 StorageManager.getVolumeList 取第二个卷（Android 9 以下有效）
        try {
            val p = FileUtil.getSDCardPath()
            if (!p.isNullOrBlank() && volume(p) != null) {
                return p
            }
        } catch (e: Throwable) {
            // 忽略，走下面兜底
        }
        // 2) 兜底：getExternalFilesDirs 的第二项一定落在可移动卷上
        try {
            val dirs = BaseApplication.context.getExternalFilesDirs(null)
            if (dirs.size >= 2 && dirs[1] != null) {
                val abs = dirs[1]!!.absolutePath
                val idx = abs.indexOf("/Android/")
                val root = if (idx > 0) abs.substring(0, idx) else abs
                if (volume(root) != null) return root
            }
        } catch (e: Throwable) {
            // 忽略
        }
        return null
    }

    /** 字节 → iPod classic 风格文案，如 "20.4 GB" / "512 MB" */
    fun format(bytes: Long): String {
        if (bytes <= 0L) return "0 GB"
        val gib = bytes / GIB
        return when {
            gib >= 1.0 -> trim(String.format(Locale.US, "%.1f", gib)) + " GB"
            bytes >= MIB * 100 -> String.format(Locale.US, "%.0f MB", bytes / MIB)
            bytes >= MIB -> trim(String.format(Locale.US, "%.1f", bytes / MIB)) + " MB"
            else -> String.format(Locale.US, "%.0f KB", bytes / KIB)
        }
    }

    /** 去掉多余的 ".0"，让 20 GB 看起来跟 20.4 GB 一样自然 */
    private fun trim(s: String): String = if (s.endsWith(".0")) s.substring(0, s.length - 2) else s

    fun totalText(): String = format(totalBytes())

    fun freeText(): String = format(freeBytes())

    fun usedText(): String = format(usedBytes())
}
