package io.github.xejohv.podclassicxzs.util

import android.os.Environment
import android.os.StatFs
import java.util.Locale

/**
 * 容量统计。
 *
 * 之前「关于本机」里的容量是拿 blockCount*blockSize 直接除以 1024^3 再取整，
 * 有两个毛病：
 * 1. 整数截断：21.6 GB 会显示成 21 GB、16.4 GB 显示成 16 GB，看起来就像算错了；
 * 2. 用 blockCountLong 当分子、availableBlocksLong 当分母，两者口径其实不一样
 *    （前者含文件系统保留块，后者只算普通用户能写的块），差出来的 1 GB 会被误当成"已用"。
 *
 * 现在统一走 StatFs 的字节接口：total = 全部块 × 块大小，free = 可分配块 × 块大小，
 * 保留一位小数并按 iPod classic 的写法输出（整数值就省掉小数点）。
 */
object StorageUtil {

    private const val KIB = 1024.0
    private const val MIB = KIB * 1024.0
    private const val GIB = MIB * 1024.0

    /** 总容量（字节），读数据分区 /data */
    fun totalBytes(): Long = statFs()?.totalBytes ?: 0L

    /** 可用容量（字节），只算普通应用/用户能写入的块，不含文件系统保留块 */
    fun freeBytes(): Long = statFs()?.availableBytes ?: 0L

    /** 已用容量（字节） */
    fun usedBytes(): Long = (totalBytes() - freeBytes()).coerceAtLeast(0L)

    private fun statFs(): StatFs? {
        return try {
            StatFs(Environment.getDataDirectory().path)
        } catch (e: Exception) {
            null
        }
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
