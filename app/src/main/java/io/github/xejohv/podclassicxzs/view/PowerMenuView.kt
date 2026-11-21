package io.github.xejohv.podclassicxzs.view

import android.annotation.SuppressLint
import android.content.Context
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.gestures.GestureService
import io.github.xejohv.podclassicxzs.widget.RecyclerListView

/**
 * PodClassic 自己的电源菜单。
 *
 * 触发：长按电源键时系统会先弹出索尼那套白底电源菜单，无障碍服务（GestureService）
 * 一旦检测到该窗口就用本页盖住它，于是用户看到的是这一版风格统一的菜单。
 *
 * 执行能力边界（本机未 root，只能做到这些）：
 *  - 关机 / 重新启动：由无障碍服务去点被盖在下面那张系统菜单里的同名按钮，能真执行；
 *    点不到就把本页退出、把系统菜单让出来（不会假装执行成功）。
 *  - 不再放"锁屏"：锁屏由锁屏页（滑动解锁）负责，长按电源键这里只留关机 / 重启。
 */
@SuppressLint("ViewConstructor")
class PowerMenuView(context: Context) : ItemListView(context, arrayListOf<Item>(), "电源", null) {

    init {
        itemList = buildItems()
    }

    private fun buildItems(): ArrayList<Item> {
        val rows = arrayListOf<Item>()

        rows.add(Item("关机", object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                powerAction("关机")
                return true
            }
        }, true))

        rows.add(Item("重新启动", object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                powerAction("重新启动")
                return true
            }
        }, true))

        return rows
    }

    /** 关机 / 重启：让无障碍服务去点被盖住的系统菜单按钮；点不到就让出位置 */
    private fun powerAction(label: String) {
        val ok = try {
            GestureService.clickPowerLabel(label)
        } catch (e: Throwable) {
            false
        }
        if (!ok) {
            Core.removeView()
        }
    }
}
