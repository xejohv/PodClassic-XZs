package io.github.xejohv.podclassicxzs.util

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * 音乐库补齐：电脑/其他设备拷进存储的音频文件不会自动进 MediaStore，
 * 这里在启动时比对磁盘与索引，把缺失的文件交给系统 MediaScanner 入库。
 */
object MediaScanUtil {
    private val AUDIO_EXT = setOf(
        "flac", "mp3", "wav", "m4a", "aac", "ogg", "opus", "ape", "wma", "dsf", "dff"
    )

    @Volatile
    private var running = false

    fun scanMissingAsync(context: Context) {
        if (running) return
        running = true
        Thread {
            val start = System.currentTimeMillis()
            try {
                val missing = ArrayList<String>()
                for (root in musicRoots()) {
                    if (!root.isDirectory) continue
                    root.walkTopDown().forEach { f ->
                        try {
                            if (f.isFile && f.extension.lowercase() in AUDIO_EXT && !isIndexed(context, f.absolutePath)) {
                                missing.add(f.absolutePath)
                            }
                        } catch (ignored: Exception) {
                        }
                    }
                }
                Log.d("MediaScanUtil", "missing audio files: ${missing.size}")
                if (missing.isEmpty()) {
                    running = false
                    return@Thread
                }
                var remaining = missing.size
                val lock = Any()
                MediaScannerConnection.scanFile(context, missing.toTypedArray(), null) { path, uri ->
                    synchronized(lock) {
                        remaining--
                        if (remaining <= 0) {
                            MediaStoreUtil.invalidateCache()
                            Log.d("MediaScanUtil", "scan done in ${System.currentTimeMillis() - start}ms, reindexed ${missing.size}")
                            running = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaScanUtil", "scan failed", e)
                running = false
            }
        }.start()
    }

    private fun musicRoots(): List<File> {
        val roots = ArrayList<File>()
        val primary = Environment.getExternalStorageDirectory()
        roots.add(File(primary, "Music"))
        try {
            File("/storage").listFiles()?.forEach { vol ->
                if (vol.isDirectory && vol.name != "emulated" && vol.name != "self") {
                    val m = File(vol, "Music")
                    if (m.isDirectory) roots.add(m)
                }
            }
        } catch (ignored: Exception) {
        }
        return roots
    }

    private fun isIndexed(context: Context, path: String): Boolean {
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                MediaStore.Audio.Media.DATA + "=?",
                arrayOf(path),
                null
            )?.use { cursor ->
                return cursor.count > 0
            }
        } catch (ignored: Exception) {
        }
        return false
    }
}
