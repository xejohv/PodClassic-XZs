package io.github.xejohv.podclassicxzs.view

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.xejohv.podclassicxzs.base.Core
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.util.BluetoothHelper
import io.github.xejohv.podclassicxzs.widget.RecyclerListView

/**
 * 蓝牙设置页（PodClassic 内自带，不跳系统设置）。
 *
 * 行：开关 / 本机名称 / 可被发现 / 扫描附近设备 / 已配对设备 / 自动重连
 */
class BluetoothView(context: Context) : RecyclerListView(context), ScreenView {

    companion object {
        private const val PREF = "podclassic_bluetooth"
        private const val KEY_AUTO = "auto_connect"
    }

    private val rowSwitch: Item
    private val rowName: Item
    private val rowDiscover: Item
    private val rowScan: Item
    private val rowBonded: Item
    private val rowAuto: Item

    private var lastActionMs = 0L

    // 一次物理按键可能触发两次 click（触摸抬起 + enter），开关类行必须去抖，否则会被翻回去
    private fun accept(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastActionMs < 600L) {
            return false
        }
        lastActionMs = now
        return true
    }

    init {
        rowSwitch = Item("蓝牙", object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                if (!accept()) {
                    return true
                }
                val target = !BluetoothHelper.isEnabled()
                BluetoothHelper.setEnabled(target)
                postDelayed({ refreshRows() }, 1500)
                return true
            }
        }, onOff(BluetoothHelper.isEnabled()))

        rowName = Item("本机名称", object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                return true
            }
        }, BluetoothHelper.localName())

        rowDiscover = Item("可被发现", object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                if (BluetoothHelper.isEnabled()) {
                    BluetoothHelper.setDiscoverable(120)
                }
                return true
            }
        }, "120 秒")

        rowScan = Item("扫描附近设备", object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                // 蓝牙没开也允许进页，页内会给「蓝牙未开启」的提示
                Core.addView(BluetoothDeviceListView(context, "附近设备", true))
                return true
            }
        }, true)

        rowBonded = Item("已配对设备", object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                Core.addView(BluetoothDeviceListView(context, "已配对设备", false))
                return true
            }
        }, true)

        rowAuto = Item("自动重连", object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                if (!accept()) {
                    return true
                }
                val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_AUTO, !prefs.getBoolean(KEY_AUTO, true)).apply()
                refreshRows()
                return true
            }
        }, "")

        add(rowSwitch)
        add(rowName)
        add(rowDiscover)
        add(rowScan)
        add(rowBonded)
        add(rowAuto)

        refreshRows()
    }

    // ---------------- ScreenView ----------------
    override fun enter(): Boolean = onItemClick()

    override fun enterLongClick(): Boolean = onItemLongClick()

    override fun slide(slideVal: Int): Boolean = onSlide(slideVal)

    override fun getTitle(): String? = "蓝牙"

    override fun onViewAdd() {
        try {
            context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        } catch (e: Throwable) {
        }
        BluetoothHelper.ensureProxy()
        refreshRows()
    }

    override fun onViewRemove() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Throwable) {
        }
    }

    private fun onOff(on: Boolean) = if (on) "开启" else "关闭"

    private fun refreshRows() {
        val enabled = BluetoothHelper.isEnabled()
        rowSwitch.rightText = onOff(enabled)
        rowName.rightText = BluetoothHelper.localName()
        rowDiscover.rightText = if (enabled) "120 秒" else "需先开启"
        rowBonded.rightText = if (enabled) "${BluetoothHelper.bondedDevices().size} 台" else "0 台"
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        rowAuto.rightText = onOff(prefs.getBoolean(KEY_AUTO, true))
        refreshList()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                refreshRows()
            }
        }
    }
}
