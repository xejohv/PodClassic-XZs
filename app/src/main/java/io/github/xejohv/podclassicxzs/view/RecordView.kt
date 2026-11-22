package io.github.xejohv.podclassicxzs.view

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.bean.Music
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.storage.SPManager
import io.github.xejohv.podclassicxzs.values.Strings
import io.github.xejohv.podclassicxzs.widget.RecyclerListView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * 录音（语音备忘录）。
 * 列表第一行是「开始录音 / 停止录音」，后面是本机录过的录音文件（按时间倒序）。
 * 录音统一存在内置存储的 Recordings 目录，插上数据线（U 盘模式）直接可见，
 * 也可以被系统媒体库扫描到，方便拷出与归档。
 */
class RecordView(context: Context) : RecyclerListView(context, 10), ScreenView {

    companion object {
        const val DIR_NAME = "Recordings"
        const val REQUEST_MIC = 2

        @JvmStatic
        fun recordingsDir(): File {
            val dir = File(Environment.getExternalStorageDirectory(), DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

        private fun formatDuration(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) {
                String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            } else {
                String.format(Locale.US, "%02d:%02d", m, s)
            }
        }
    }

    private val dir = recordingsDir()
    private val files = ArrayList<File>()
    private val durations = HashMap<String, Long>()

    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var recordStart = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (recorder == null) {
                return
            }
            if (itemList.isNotEmpty()) {
                itemList[0].rightText = formatDuration(System.currentTimeMillis() - recordStart)
                refreshList()
            }
            handler.postDelayed(this, 1000)
        }
    }

    init {
        defaultOnItemClickListener = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                return handleItemClick(index)
            }

            override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                return handleItemLongClick(index)
            }
        }
        rebuild()
    }

    /** 重扫目录并重建列表；录音中则首行给出计时 */
    private fun rebuild() {
        files.clear()
        durations.clear()
        val array = dir.listFiles()
        if (array != null) {
            for (f in array) {
                if (f.isFile && !f.name.startsWith(".") && isRecordingFile(f)) {
                    if (recorder != null && f.absolutePath == recordFile?.absolutePath) continue
                    // 正在写入的半成品不列出来
                    files.add(f)
                }
            }
        }
        files.sortByDescending { it.lastModified() }

        val rows = ArrayList<Item>()
        rows.add(
            Item(
                if (recorder != null) Strings.STOP_RECORD else Strings.START_RECORD,
                null,
                true
            ).apply {
                rightText = if (recorder != null) {
                    formatDuration(System.currentTimeMillis() - recordStart)
                } else {
                    Strings.NULL
                }
            }
        )

        if (files.isEmpty()) {
            rows.add(Item(Strings.RECORD_EMPTY, null, false))
        } else {
            for (f in files) {
                val duration = readDuration(f)
                durations[f.absolutePath] = duration
                rows.add(Item(friendlyName(f), null, false).apply {
                    extra = f
                    rightText = formatDuration(duration)
                })
            }
        }
        itemList = rows
    }

    /** 列表里显示成「录音 11-22 15:30」这种一看就懂的名字，真实文件名保持可排序的时间戳 */
    private fun friendlyName(file: File): String {
        val prefix = if (Strings.RECORD.isEmpty()) "Rec" else Strings.RECORD
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return prefix + " " + fmt.format(Date(file.lastModified()))
    }
    /** 播放页标题栏较窄，只放「录音 + 时分」，列表里仍显示完整日期 */
    private fun shortName(file: File): String {
        val prefix = if (Strings.RECORD.isEmpty()) "Rec" else Strings.RECORD
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return prefix + " " + fmt.format(Date(file.lastModified()))
    }

    private fun isRecordingFile(file: File): Boolean {
        val name = file.name.lowercase(Locale.getDefault())
        return name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".mp3") ||
                name.endsWith(".wav") || name.endsWith(".amr") || name.endsWith(".3gp")
    }

    private fun readDuration(file: File): Long {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ms?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun handleItemClick(index: Int): Boolean {
        if (index == 0) {
            if (recorder == null) {
                startRecording()
            } else {
                stopRecording(false)
            }
            return true
        }
        val file = itemList.getOrNull(index)?.extra as? File ?: return false
        if (!file.exists()) {
            rebuild()
            return false
        }
        if (recorder != null) {
            stopRecording(false)
        }
        playRecording(file)
        return true
    }

    private fun handleItemLongClick(index: Int): Boolean {
        val file = itemList.getOrNull(index)?.extra as? File ?: return false
        if (recorder != null) {
            return false
        }
        confirmDelete(file)
        return true
    }

    private fun playRecording(file: File) {
        val duration = durations[file.absolutePath] ?: readDuration(file)
        val music = Music(
            title = shortName(file),
            id = null,
            artist = Strings.NULL,
            album = Strings.RECORD,
            albumId = null,
            duration = duration,
            data = file.absolutePath,
            uri = Uri.fromFile(file)
        )
        MediaPresenter.set(music)
        Core.addView(MusicPlayerView(context))
    }

    /** 二次确认，避免误删 */
    private fun confirmDelete(file: File) {
        val itemList = arrayListOf(
            Item(Strings.DELETE, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    try {
                        file.delete()
                        MediaScannerConnection.scanFile(
                            context, arrayOf(file.absolutePath), arrayOf("audio/mp4"), null
                        )
                    } catch (_: Exception) {
                    }
                    // 正在播这首录音时先停掉并从播放列表摘掉，避免之后拿已删文件读封面/时长而闪退
                    try {
                        if (MediaPresenter.getCurrent()?.data == file.absolutePath) {
                            MediaPresenter.stop()
                        }
                        val idx = MediaPresenter.getPlaylist().indexOfFirst { it.data == file.absolutePath }
                        if (idx >= 0) {
                            MediaPresenter.removeAt(idx)
                        }
                    } catch (_: Exception) {
                    }
                    Core.removeView()
                    rebuild()
                    return true
                }
            }, true),
            Item(Strings.CANCEL, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.removeView()
                    return true
                }
            }, true)
        )
        Core.addView(ItemListView(context, itemList, Strings.DELETE_RECORD, null))
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 没授权就当场向系统要一次；用户允许后回到本页再点「开始录音」即可。
     * 彻底拒绝过的话直接给一句提示并打开本应用权限页。
     */
    private fun requestMicPermission() {
        val activity = Core.getActivityInstance()
        if (activity == null) {
            Toast.makeText(context, Strings.MIC_PERMISSION, Toast.LENGTH_SHORT).show()
            return
        }
        val shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.RECORD_AUDIO
        )
        if (shouldShow) {
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC
            )
        } else {
            // 首次进入或已被永久拒绝：先请求，若系统不再弹窗则引导到权限页
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC
            )
            Toast.makeText(context, Strings.MIC_PERMISSION, Toast.LENGTH_SHORT).show()
        }
    }

    private fun qualityConfig(): Pair<Int, Int> {
        return when (SPManager.getInt(SPManager.SP_RECORD_QUALITY)) {
            0 -> Pair(44100, 192000)
            2 -> Pair(22050, 64000)
            else -> Pair(44100, 128000)
        }
    }

    private fun startRecording() {
        if (!hasMicPermission()) {
            requestMicPermission()
            return
        }
        // 录音和播放同时占用音频通道，开始录音先把音乐停掉，避免串音
        try {
            MediaPresenter.pause()
        } catch (_: Exception) {
        }

        val now = Date()
        val name = "REC_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now) + ".m4a"
        val file = File(dir, name)
        val (rate, bitrate) = qualityConfig()

        var mediaRecorder: MediaRecorder? = null
        try {
            mediaRecorder = MediaRecorder()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(rate)
            mediaRecorder.setAudioEncodingBitRate(bitrate)
            mediaRecorder.setAudioChannels(1)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()

            recorder = mediaRecorder
            recordFile = file
            recordStart = System.currentTimeMillis()
            rebuild()
            handler.post(ticker)
        } catch (e: Exception) {
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {
            }
            try {
                file.delete()
            } catch (_: Exception) {
            }
            recorder = null
            recordFile = null
            Toast.makeText(context, Strings.MIC_PERMISSION, Toast.LENGTH_SHORT).show()
            rebuild()
        }
    }

    private fun stopRecording(silent: Boolean) {
        val mediaRecorder = recorder ?: return
        val file = recordFile
        val elapsed = System.currentTimeMillis() - recordStart

        recorder = null
        recordFile = null
        handler.removeCallbacks(ticker)

        var ok = true
        try {
            mediaRecorder.stop()
        } catch (e: RuntimeException) {
            // 录得太短（<1 秒）时 stop() 会抛 RuntimeException，这种文件本身是坏的
            ok = false
        }
        try {
            mediaRecorder.release()
        } catch (_: Exception) {
        }

        if (file != null) {
            if (!ok || elapsed < 1000L || !file.exists() || file.length() <= 0L) {
                try {
                    file.delete()
                } catch (_: Exception) {
                }
            } else {
                MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), arrayOf("audio/mp4"), null
                )
                if (!silent) {
                    Toast.makeText(
                        context,
                        Strings.RECORD + " " + formatDuration(elapsed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        if (!silent) {
            rebuild()
        }
    }

    override fun enter(): Boolean {
        return handleItemClick(index)
    }

    override fun enterLongClick(): Boolean {
        return handleItemLongClick(index)
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun getTitle(): String {
        return Strings.RECORD
    }

    /** 离开页面（返回上级 / 回主页）时把正在录的音停掉并落盘，不然文件是坏的 */
    override fun onViewDelete() {
        if (recorder != null) {
            stopRecording(true)
        }
    }
}
