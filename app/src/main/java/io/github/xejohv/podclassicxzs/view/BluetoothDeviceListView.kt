package io.github.xejohv.podclassicxzs.view

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.util.BluetoothHelper
import io.github.xejohv.podclassicxzs.widget.RecyclerListView

/**
 * 蓝牙设备列表：已配对设备 + 扫描到的新设备。
 * 右侧状态：已连接 / 已配对 / 待配对 / 配对中。
 */
class BluetoothDeviceListView(
    context: Context,
    private val pageTitle: String,
    private val doDiscovery: Boolean
) : RecyclerListView(context), ScreenView {

    companion object {
        private const val ACTION_A2DP_STATE = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
    }

    private val items = HashMap<String, Item>()

    // 空列表时的提示行（用对象自身做相等判断，改名后也能删掉）
    private val hintItem = object : Item("", null, "") {
        override fun equals(other: Any?): Boolean = other === this
        override fun hashCode(): Int = System.identityHashCode(this)
    }
    private var hintShown = false

    private var lastActionMs = 0L

    // 同一个按键可能触发两次 click，连接/断开来回切，需去抖
    private fun accept(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastActionMs < 600L) {
            return false
        }
        lastActionMs = now
        return true
    }

    init {
        BluetoothHelper.ensureProxy()
        reloadBonded()
    }

    // ---------------- ScreenView ----------------
    override fun enter(): Boolean = onItemClick()

    override fun enterLongClick(): Boolean = onItemLongClick()

    override fun slide(slideVal: Int): Boolean = onSlide(slideVal)

    override fun getTitle(): String? = pageTitle

    override fun onViewAdd() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(ACTION_A2DP_STATE)
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Throwable) {
        }
        if (doDiscovery && BluetoothHelper.isEnabled()) {
            BluetoothHelper.startDiscovery()
        }
        updateHint()
    }

    override fun onViewRemove() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Throwable) {
        }
        if (doDiscovery) {
            BluetoothHelper.cancelDiscovery()
        }
    }

    // ---------------- 列表构建 ----------------
    private fun statusOf(device: BluetoothDevice): String = when {
        BluetoothHelper.isConnected(device) -> "已连接"
        BluetoothHelper.isBonded(device) -> "已配对"
        BluetoothHelper.isBonding(device) -> "配对中"
        else -> "待配对"
    }

    private fun labelOf(device: BluetoothDevice): String = try {
        val name = device.name
        if (name.isNullOrEmpty()) device.address else name
    } catch (e: Throwable) {
        "未知设备"
    }

    private fun addDevice(device: BluetoothDevice) {
        val address = try {
            device.address
        } catch (e: Throwable) {
            return
        }
        val old = items[address]
        if (old != null) {
            old.name = labelOf(device)
            old.rightText = statusOf(device)
            refreshList()
            return
        }
        val item = Item(labelOf(device), object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                if (!accept()) {
                    return true
                }
                when {
                    BluetoothHelper.isConnected(device) -> BluetoothHelper.disconnect(device)
                    BluetoothHelper.isBonded(device) -> BluetoothHelper.connect(device)
                    else -> BluetoothHelper.bond(device)
                }
                // 状态要靠广播回来，这里先给个即时反馈
                val cur = items[address]
                if (cur != null) {
                    cur.rightText = if (BluetoothHelper.isConnected(device)) "断开中" else "连接中"
                    refreshList()
                }
                postDelayed({ refreshStatus() }, 800)
                return true
            }
        }, statusOf(device))
        items[address] = item
        addIfNotExist(item)
        updateHint()
    }

    private fun reloadBonded() {
        if (!BluetoothHelper.isEnabled()) {
            updateHint()
            refreshList()
            return
        }
        for (device in BluetoothHelper.bondedDevices()) {
            addDevice(device)
        }
        refreshStatus()
        updateHint()
    }

    private fun refreshStatus() {
        for ((address, item) in items) {
            item.rightText = try {
                val adapter = BluetoothHelper.adapter
                val device = adapter?.getRemoteDevice(address)
                if (device == null) item.rightText else statusOf(device)
            } catch (e: Throwable) {
                item.rightText
            }
        }
        refreshList()
    }

    private var discoveryFinished = false

    private fun updateHint() {
        if (items.isNotEmpty()) {
            if (hintShown) {
                remove(hintItem)
                hintShown = false
            }
            return
        }
        hintItem.name = when {
            !BluetoothHelper.isEnabled() -> "蓝牙未开启，请先在蓝牙页打开"
            doDiscovery && (BluetoothHelper.isDiscovering() || !discoveryFinished) -> "正在扫描附近设备…"
            doDiscovery -> "没有找到设备，按 MENU 返回后重试"
            else -> "还没有已配对的设备"
        }
        if (!hintShown) {
            add(hintItem, 0)
            hintShown = true
        } else {
            refreshList()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) addDevice(device)
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> refreshStatus()
                ACTION_A2DP_STATE -> refreshStatus()
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (BluetoothHelper.isEnabled()) {
                        discoveryFinished = false
                        reloadBonded()
                        if (doDiscovery) BluetoothHelper.startDiscovery()
                    } else {
                        updateHint()
                        refreshList()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    discoveryFinished = false
                    updateHint()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    discoveryFinished = true
                    updateHint()
                    refreshStatus()
                }
            }
        }
    }
}
