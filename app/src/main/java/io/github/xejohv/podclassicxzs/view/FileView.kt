package io.github.xejohv.podclassicxzs.view

import android.annotation.SuppressLint
import android.content.Context
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.bean.Music
import io.github.xejohv.podclassicxzs.bean.MusicList
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.storage.MusicListTable
import io.github.xejohv.podclassicxzs.storage.MusicTable
import io.github.xejohv.podclassicxzs.util.FileUtil
import io.github.xejohv.podclassicxzs.util.MediaStoreUtil
import io.github.xejohv.podclassicxzs.values.Strings
import io.github.xejohv.podclassicxzs.widget.RecyclerListView
import java.io.File
import java.text.Collator
import java.util.*


@SuppressLint("ViewConstructor")
class FileView(context: Context, val roots: List<File>, private val rootTitle: String? = null) :
    RecyclerListView(context), ScreenView {

    /** 单目录入口（子目录层级、以及别处按单 File 创建的地方继续用这个） */
    constructor(context: Context, file: File) : this(context, listOf(file), file.name)

    private var hasAudio = false
    val list: ArrayList<File> = ArrayList()
    private val musicList = ArrayList<File>(list.size)

    /** 系统目录/别的 App 的资料目录都不显示，只留用户自己的媒体文件 */
    private val hiddenNames = arrayOf(
        "android", "dcim", "lost.dir", "system volume information", "tencent", "miui"
    )

    init {
        // 多个根目录合并成一张列表：同名条目只留第一个（Music 在前），用户不用知道文件到底在哪
        val merged = ArrayList<File>()
        val seen = HashSet<String>()
        for (root in roots) {
            val array = root.listFiles() ?: continue
            for (f in array) {
                if (f.name.startsWith(".")) {
                    continue
                }
                if (hiddenNames.any { it.equals(f.name, ignoreCase = true) }) {
                    continue
                }
                val key = f.name.lowercase(Locale.getDefault())
                if (!seen.add(key)) {
                    continue
                }
                merged.add(f)
            }
        }
        val collator = Collator.getInstance(Locale.getDefault())
        merged.sortWith { o1, o2 -> collator.compare(o1?.name, o2?.name) }
        list.addAll(merged)

        if (list.isEmpty()) {
            itemList.add(Item(Strings.EMPTY, null, false))
        } else {
            itemList.ensureCapacity(list.size)
            for (file in list) {
                if (FileUtil.isAudio(file)) {
                    hasAudio = true
                    musicList.add(file)
                }
                itemList.add(Item(file.name, null, file.isDirectory))
            }
            if (hasAudio) {
                itemList.add(0, Item(Strings.SHUFFLE_PLAY, null, true))
            }
        }
        sorted = true
    }

    override fun enter(): Boolean {
        if (list.isEmpty()) {
            return false
        }
        if (hasAudio && index == 0) {
            // 合并列表里的音频合在一起随机播，不再按单个根目录拼前缀
            val musics = ArrayList<Music>(musicList.size)
            for (f in musicList) {
                val music = MediaStoreUtil.getMusicFromFile(f.path)
                if (music != null) {
                    musics.add(music)
                }
            }
            MediaPresenter.shufflePlay(musics)
            Core.addView(MusicPlayerView(context))
            return true
        }
        val file = list[if (hasAudio) index - 1 else index]
        if (!file.exists()) {
            return false
        }
        if (file.isDirectory) {
            Core.addView(FileView(context, file))
        } else {
            when (FileUtil.getFileType(file)) {
                FileUtil.TYPE_VIDEO -> Core.addView(VideoView(context, file))
                FileUtil.TYPE_IMAGE -> Core.addView(ImageView(context, arrayListOf(file), 0))
                FileUtil.TYPE_AUDIO -> {
                    val music = MediaStoreUtil.getMusicFromFile(file.path)
                    if (music != null) {
                        MediaPresenter.set(music)
                        Core.addView(MusicPlayerView(context))
                    }
                }
                FileUtil.TYPE_TEXT -> {
                    Core.addView(TxtView(context, file))
                }
                else -> return false
            }
        }
        return true
    }

    override fun enterLongClick(): Boolean {
        if (list.isEmpty()) {
            return false
        }
        if (hasAudio && index == 0) {
            return false
        }
        val id = if (hasAudio) index - 1 else index
        val file = list[id]
        if (file.isDirectory) {
            val folder = file.listFiles() ?: return false
            for (f in folder) {
                if (FileUtil.isAudio(f)) {
                    MusicListTable.folder.add(
                        MusicList.Builder().apply {
                            type = MusicList.TYPE_FOLDER
                            title = file.name
                            data = file.path
                        }.build()
                    )
                    shake()
                    return true
                }
            }
        } else if (FileUtil.isAudio(file)) {
            val music = MediaStoreUtil.getMusicFromFile(file.path)
            if (music != null) {
                // 长按统一是收藏 / 取消收藏，不重复往收藏里塞同一条
                val favourite = MusicTable.favourite
                if (favourite.contains(music)) {
                    favourite.remove(music)
                } else {
                    favourite.add(music)
                }
                shake()
                return true
            }
        }
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun getTitle(): String {
        return rootTitle ?: roots.firstOrNull()?.name ?: "文件"
    }
}
