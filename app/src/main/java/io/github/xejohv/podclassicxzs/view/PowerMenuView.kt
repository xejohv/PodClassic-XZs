package io.github.xejohv.podclassicxzs.view

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.gestures.GestureService
import io.github.xejohv.podclassicxzs.service.PodDeviceAdminReceiver
import io.github.xejohv.podclassicxzs.widget.RecyclerListView

/**
 * PodClassic 自己的电源菜单。
 *
 * 触发：长按电源键时系统会先弹出索尼那套白底电源菜单，无障碍服务（GestureService）
 * 一旦检测到该窗口就用本页盖住它，于是用户看到的是这一版风格统一的菜单。
 *
 * 执行能力边界（本机未 root，只能做到这些）：
 *  - 锁屏：设备管理员 lockNow，真执行；
 *  - 关机 / 重新启动：由无障碍服务去点被盖在下面那张系统菜单里的同名按钮，能真执行；
 *    点不到就把本页退出、把系统菜单让出来（不会假装执行成功）。
 *  - 屏幕截图：Android 8 下每次都要过一次系统"开始截取屏幕"授权框，代价比收益大，
 *    这里不放；用物理组合键（电源 + 音量下）截屏即可。
 */
@SuppressLint("ViewConstructor")
class PowerMenuView(context: Context) : ItemListView(context, arrayListOf<Item>(), "电源", null) {

    init {
        itemList = buildItems()
    }

    private fun buildItems(): ArrayList<Item> {
        val rows = arrayListOf<Item>()

        rows.add(Item("锁屏", object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                lockScreen()
                return true
            }
        }, true))

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

    /** 锁屏：已激活设备管理员就立刻锁；没激活就引导激活一次 */
    private fun lockScreen() {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val cn = ComponentName(context, PodDeviceAdminReceiver::class.java)
        if (dpm != null && dpm.isAdminActive(cn)) {
            try {
                dpm.lockNow()
                Core.removeView()
                return
            } catch (ignored: Exception) {
            }
        }
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "只用于在 PodClassic 电源菜单里锁屏，不会清除数据或改密码"
            )
            context.startActivity(intent)
        } catch (ignored: Exception) {
        }
        Core.removeView()
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
