package io.github.xejohv.podclassicxzs.view

import android.content.Context
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.bean.MusicList
import io.github.xejohv.podclassicxzs.storage.MusicListTable
import io.github.xejohv.podclassicxzs.values.Strings
import io.github.xejohv.podclassicxzs.widget.RecyclerListView
import io.github.xejohv.podclassicxzs.widget.RecyclerListView.OnItemClickListener

class AlbumListView(
    context: Context,
    list: ArrayList<MusicList>,
    private val title: String,
    private var longClick: Int
) : RecyclerListView(context), ScreenView {
    companion object {
        const val LONG_CLICK_ADD = 0
        const val LONG_CLICK_REMOVE = 1
    }

    private var musicList: ArrayList<MusicList> = list
    private var hasAll = false

    init {
        hasAll = list.size >= 2 && list[0].type != list[1].type
        itemList.ensureCapacity(list.size)
        for (musicList in list) {
            itemList.add(Item(titleOf(musicList), null, true))
        }
        if (hasAll) {
            itemList[0].name = Strings.ALL
        }
        sorted = longClick == LONG_CLICK_ADD
        
        // 设置默认点击监听器，处理触摸播放歌曲的功能
        defaultOnItemClickListener = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                return enter()
            }
        }
    }

    override fun enter(): Boolean {
        return if (itemList.isEmpty()) {
            false
        } else {
            Core.addView(MusicListView(context, musicList[index]))
            true
        }
    }

    /**
     * 长按中央键统一为收藏 / 取消收藏。
     * 浏览页（LONG_CLICK_ADD）里长按收藏、再长按取消，标题前用 ★ 标出状态；
     * 收藏页（LONG_CLICK_REMOVE）里长按直接取消并把这条从列表拿走。
     */
    override fun enterLongClick(): Boolean {
        if (itemList.isEmpty() || (hasAll && index == 0)) {
            return false
        }
        val ml = musicList[index]
        val table = when (ml.type) {
            MusicList.TYPE_ALBUM -> MusicListTable.album
            MusicList.TYPE_ARTIST -> MusicListTable.artist
            else -> return false
        }
        if (table.contains(ml)) {
            table.delete(ml)
            if (longClick == LONG_CLICK_REMOVE) {
                this.musicList.removeAt(index)
                removeCurrentItem()
                return true
            }
        } else {
            table.add(ml)
        }
        shake()
        // 浏览页里刷新标题前的 ★
        if (index in itemList.indices && index < musicList.size) {
            itemList[index].name = titleOf(musicList[index])
        }
        refreshList()
        return true
    }

    /** 收藏过的专辑 / 艺术家，名字前顶一个 ★ */
    private fun titleOf(ml: MusicList): String = if (isFavourite(ml)) "★ " + ml.title else ml.title

    private fun isFavourite(ml: MusicList): Boolean = when (ml.type) {
        MusicList.TYPE_ALBUM -> MusicListTable.album.contains(ml)
        MusicList.TYPE_ARTIST -> MusicListTable.artist.contains(ml)
        else -> false
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun getTitle(): String {
        return title
    }
}