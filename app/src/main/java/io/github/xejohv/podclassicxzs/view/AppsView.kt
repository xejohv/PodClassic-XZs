package io.github.xejohv.podclassicxzs.view

import android.content.Context
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.bean.App
import io.github.xejohv.podclassicxzs.gestures.GestureService
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

    // 机身配色入口：四色严格按官方发布颜色命名
    private val themeItem = Item("主题", object : OnItemClickListener {
        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
            Core.addView(ThemeView(context))
            return true
        }
    }, true)

    // 相机：直接拉起索尼原厂相机，界面、功能都用系统的；
    // 因为这台机器的导航栏被整体拿掉了，进外部 App 以后没有返回入口，
    // 所以同时盖一条自己的控制条（← 返回 / iPod），回本应用时由 onResume 收掉
    private val cameraItem = Item("相机", object : OnItemClickListener {
        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
            try {
                val intent = android.content.Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                // 按用户要求贴到屏幕最下沿：原厂相机快门中心在它上方，不会挡住拍照
                GestureService.showExitBar(false)
            } catch (e: Exception) {
                // 兜底：没有系统相机时退回内置相机
                Core.addView(CameraView(context))
            }
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
        rows.add(cameraItem)

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
        rows.add(themeItem)
        itemList = rows
    }

    // 收藏里挑出来的常用程序（相机已改为内嵌页面，不再列系统相机）
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
        return result
    }

    override fun getTitle(): String {
        return Strings.EXTRA_APPLICATION
    }
}
