package io.github.xejohv.podclassicxzs.view

import android.app.Activity
import android.content.Context
import io.github.xejohv.podclassicxzs.storage.SPManager
import io.github.xejohv.podclassicxzs.values.Colors
import io.github.xejohv.podclassicxzs.widget.RecyclerListView

/**
 * 「主题」页面：按机身配色切换。
 *
 * 四色严格取自 Sony Xperia XZs 官方发布颜色（维基百科条目与索尼规格页同源）：
 *   暖银 Warm Silver / 暗黑 Black / 冰蓝 Ice Blue / 金桔 Citrus（日本限定）
 *
 * 选中即整机换色（背景、标题栏、列表、控制盘、游戏），切换后重建界面，
 * 保证所有已创建的视图都用新色板重新画一遍。
 */
class ThemeView(context: Context) : ItemListView(context, arrayListOf(), TITLE, null) {

    init {
        buildRows()
    }

    override fun onViewAdd() {
        buildRows()
    }

    private fun buildRows() {
        val rows = arrayListOf<Item>()
        val current = Colors.bodyId
        for (color in SPManager.BodyColor.values()) {
            rows.add(
                Item(
                    "${color.cnTitle} · ${color.enTitle}",
                    object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                            if (Colors.bodyId == color.id) {
                                return true
                            }
                            Colors.setBody(color.id)
                            // 主题切换后重建 Activity：所有视图重新按新色板绘制
                            (context as? Activity)?.recreate()
                            return true
                        }
                    },
                    if (color.id == current) "使用中" else ""
                )
            )
        }
        itemList = rows
    }

    override fun getTitle(): String {
        return TITLE
    }

    companion object {
        private const val TITLE = "主题"
    }
}
