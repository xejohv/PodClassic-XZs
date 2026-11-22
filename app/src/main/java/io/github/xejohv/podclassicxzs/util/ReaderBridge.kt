package io.github.xejohv.podclassicxzs.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File

/**
 * 阅读器桥接：PodClassic 主进程只负责拉起独立进程的阅读器，绝不在此加载任何阅读类。
 * 阅读器包可缺省；缺省时只提示，不影响桌面与播放。
 */
object ReaderBridge {
    const val READER_PACKAGE = "io.github.xejohv.libreraclassic"
    const val READER_ENTRY = "io.github.xejohv.classic.ClassicReaderActivity"

    /** 打开书库；path 非空时直接打开该书 */
    fun open(context: Context, path: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(READER_PACKAGE, READER_ENTRY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (path != null) {
                action = Intent.ACTION_VIEW
                val file = File(path)
                val uri = Uri.fromFile(file)
                setDataAndType(uri, mimeOf(path))
                putExtra("EXTRA_FILE", path)
            }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "未安装阅读器组件", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        "mobi" -> "application/x-mobipocket-ebook"
        "azw3" -> "application/vnd.amazon.ebook"
        "fb2" -> "application/x-fictionbook+xml"
        "cbz" -> "application/vnd.comicbook+zip"
        "txt" -> "text/plain"
        else -> "*/*"
    }
}