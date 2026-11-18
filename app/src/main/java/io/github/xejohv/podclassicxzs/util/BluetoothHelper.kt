package io.github.xejohv.podclassicxzs.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import io.github.xejohv.podclassicxzs.base.BaseApplication

/**
 * 蓝牙能力的薄封装。
 *
 * 权限说明（Android 8.0 / API 26）：
 *  - BLUETOOTH、BLUETOOTH_ADMIN 都是安装即授予，不会弹系统框；
 *  - 扫描/配对需要定位权限（系统在 API 23+ 要求），由 adb 预先授予；
 *  - A2DP 的连接/断开在 AOSP 里只校验 BLUETOOTH_ADMIN，普通应用可以调，
 *    但个别 ROM 会额外要求特权，所以这里统一用反射调用并做失败兜底。
 */
object BluetoothHelper {

    private val context: Context
        get() = BaseApplication.context

    val adapter: BluetoothAdapter?
        get() = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        } catch (e: Throwable) {
            null
        }

    fun isEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (e: Throwable) {
        false
    }

    fun localName(): String = try {
        adapter?.name ?: ""
    } catch (e: Throwable) {
        ""
    }

    fun setEnabled(on: Boolean) {
        try {
            if (on) adapter?.enable() else adapter?.disable()
        } catch (e: Throwable) {
        }
    }

    fun setDiscoverable(seconds: Int) {
        val a = adapter ?: return
        // setScanMode(mode, duration) 在 SDK 里是隐藏接口，运行时存在，用反射调用
        try {
            val method = a.javaClass.getMethod(
                "setScanMode",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(a, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, seconds)
        } catch (e: Throwable) {
            // 退路：单参数版本（一直可被发现）
            try {
                a.javaClass.getMethod("setScanMode", Int::class.javaPrimitiveType)
                    .invoke(a, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
            } catch (e2: Throwable) {
            }
        }
    }

    fun bondedDevices(): List<BluetoothDevice> = try {
        adapter?.bondedDevices?.toList() ?: emptyList()
    } catch (e: Throwable) {
        emptyList()
    }

    fun startDiscovery() {
        try {
            adapter?.cancelDiscovery()
            adapter?.startDiscovery()
        } catch (e: Throwable) {
        }
    }

    fun cancelDiscovery() {
        try {
            adapter?.cancelDiscovery()
        } catch (e: Throwable) {
        }
    }

    fun isDiscovering(): Boolean = try {
        adapter?.isDiscovering == true
    } catch (e: Throwable) {
        false
    }

    // ------------------------------------------------------------------
    // A2DP 代理
    // ------------------------------------------------------------------
    private var a2dpProxy: BluetoothProfile? = null
    private var requesting = false

    fun ensureProxy() {
        if (a2dpProxy != null || requesting) return
        requesting = true
        try {
            adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.A2DP) a2dpProxy = proxy
                    requesting = false
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) a2dpProxy = null
                    requesting = false
                }
            }, BluetoothProfile.A2DP)
        } catch (e: Throwable) {
            requesting = false
        }
    }

    private fun invoke(name: String, device: BluetoothDevice): Boolean {
        val proxy = a2dpProxy ?: return false
        return try {
            val method = proxy.javaClass.getMethod(name, BluetoothDevice::class.java)
            (method.invoke(proxy, device) as? Boolean) == true
        } catch (e: Throwable) {
            false
        }
    }

    fun isConnected(device: BluetoothDevice): Boolean = try {
        a2dpProxy?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
    } catch (e: Throwable) {
        false
    }

    fun connect(device: BluetoothDevice): Boolean {
        ensureProxy()
        return invoke("connect", device)
    }

    fun disconnect(device: BluetoothDevice): Boolean = invoke("disconnect", device)

    fun isBonded(device: BluetoothDevice): Boolean = try {
        device.bondState == BluetoothDevice.BOND_BONDED
    } catch (e: Throwable) {
        false
    }

    fun isBonding(device: BluetoothDevice): Boolean = try {
        device.bondState == BluetoothDevice.BOND_BONDING
    } catch (e: Throwable) {
        false
    }

    fun bond(device: BluetoothDevice): Boolean = try {
        device.createBond()
    } catch (e: Throwable) {
        false
    }

    fun isSupported(): Boolean = adapter != null
}
