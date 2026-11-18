package io.github.xejohv.podclassicxzs.view

import android.content.Context
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.bean.App
import io.github.xejohv.podclassicxzs.game.BreakoutGame
import io.github.xejohv.podclassicxzs.game.MinesweeperGame
import io.github.xejohv.podclassicxzs.game.ShooterGame
import io.github.xejohv.podclassicxzs.game.SnakeGame
import io.github.xejohv.podclassicxzs.game.SudokuGame
import io.github.xejohv.podclassicxzs.game.TetrisGame
import io.github.xejohv.podclassicxzs.storage.AppTable
import io.github.xejohv.podclassicxzs.values.Strings
import io.github.xejohv.podclassicxzs.values.Values
import io.github.xejohv.podclassicxzs.widget.RecyclerListView

class AppsView(context: Context) : ItemListView(
    context,
    arrayListOf(),
    Strings.EXTRA_APPLICATION,
    null
) {

    init {
        this.defaultOnItemClickListener = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                val app = listView.getItem(index).extra as? App
                if (app != null) {
                    context.startActivity(app.intent)
                    return true
                }
                return false
            }

            override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                val app = listView.getItem(index).extra as? App
                if (app != null) {
                    AppTable.favourite.remove(app)
                    onViewAdd()
                    return true
                }
                return false
            }
        }
    }

    // 游戏二级菜单（俄罗斯方块 / 贪吃蛇 / 飞机大战）
    private val gameItem = Item(Strings.GAME, object : OnItemClickListener {
        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
            val itemList = arrayListOf(
                Item("俄罗斯方块", object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                        Core.addView(TetrisGame(context))
                        return true
                    }
                }, true),
                Item("贪吃蛇", object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                        Core.addView(SnakeGame(context))
                        return true
                    }
                }, true),
                Item("飞机大战", object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                        Core.addView(ShooterGame(context))
                        return true
                    }
                }, true),
                Item("打砖块", object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                        Core.addView(BreakoutGame(context))
                        return true
                    }
                }, true),
                Item("数独", object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                        Core.addView(SudokuGame(context))
                        return true
                    }
                }, true),
                Item("扫雷", object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                        Core.addView(MinesweeperGame(context))
                        return true
                    }
                }, true)
            )
            Core.addView(ItemListView(context, itemList, Strings.GAME, null))
            return true
        }
    }, true)

    // 蓝牙设置入口（单实例，重复进页也不会重复添加）
    private val btItem = Item("蓝牙", object : OnItemClickListener {
        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
            Core.addView(BluetoothView(context))
            return true
        }
    }, true)

    init {
        // 属性都初始化完之后，页面一创建就把行填好，不完全依赖 onViewAdd 的时序
        buildRows()
    }

    override fun onViewAdd() {
        buildRows()
    }

    private fun buildRows() {
        // 整页重建，保证顺序稳定、不会因为反复进出而堆叠重复行
        val rows = arrayListOf<Item>()
        rows.add(gameItem)

        // 「所有程序」这一级去掉，程序直接摆在二级菜单里
        if (Values.LAUNCHER) {
            try {
                for (app in appList()) {
                    rows.add(Item(app.name, null, true).apply {
                        extra = app
                    })
                }
            } catch (e: Exception) {
                android.util.Log.e("AppsView", "appList failed", e)
            }
        }

        rows.add(btItem)
        itemList = rows
    }

    // 收藏里挑出来的常用程序 + 内置保留的「相机」，绝不把系统应用一股脑倒出来
    private fun appList(): ArrayList<App> {
        val result = arrayListOf<App>()
        val seen = HashSet<String>()
        for (app in AppTable.favourite.list) {
            if (app.packageName == "com.android.documentsui") {
                continue
            }
            if (seen.add(app.packageName)) {
                result.add(app)
            }
        }
        for (pair in BUILTIN) {
            val packageName = pair.second
            if (seen.contains(packageName)) {
                continue
            }
            try {
                context.packageManager.getApplicationInfo(packageName, 0)
                val app = App(pair.first, packageName)
                if (app.intent != null) {
                    result.add(app)
                    seen.add(packageName)
                }
            } catch (e: Exception) {
                // 未安装则跳过
            }
        }
        return result
    }

    companion object {
        // 只保留相机：文件走系统文件管理器，风格不统一而且退不出来，一级菜单里的「文件」已经够用
        private val BUILTIN = listOf(
            "相机" to "com.sonyericsson.android.camera"
        )
    }

    override fun getTitle(): String {
        return Strings.EXTRA_APPLICATION
    }
}
