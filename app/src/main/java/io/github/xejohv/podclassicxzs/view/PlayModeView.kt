package io.github.xejohv.podclassicxzs.view

import android.content.Context
import io.github.xejohv.podclassicxzs.media.PlayMode
import io.github.xejohv.podclassicxzs.media.RepeatMode
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.widget.RecyclerListView

/**
 * 播放模式菜单：播放页长按 MENU 进入。
 * 两行分别控制「播放顺序（顺序/随机/单曲循环）」与「全部循环」，
 * 点一下循环切换，当前值直接写在行上。
 */
class PlayModeView(context: Context) : ItemListView(context, arrayListOf(), "播放模式", null) {

    init {
        build()
    }

    override fun onViewAdd() {
        build()
    }

    private fun build() {
        itemList = arrayListOf(
            Item("播放顺序：" + playTitle(), object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    MediaPresenter.nextPlayMode()
                    build()
                    return true
                }
            }, true),
            Item("全部循环：" + repeatTitle(), object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    MediaPresenter.nextRepeatMode()
                    build()
                    return true
                }
            }, true)
        )
    }

    private fun playTitle(): String = when (MediaPresenter.getPlayMode()) {
        PlayMode.ORDER -> "顺序播放"
        PlayMode.SHUFFLE -> "随机播放"
        PlayMode.SINGLE -> "单曲循环"
        else -> "顺序播放"
    }

    private fun repeatTitle(): String =
        if (MediaPresenter.getRepeatMode() == RepeatMode.ALL) "打开" else "关闭"
}
