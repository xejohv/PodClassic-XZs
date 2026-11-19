package io.github.xejohv.podclassicxzs.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import io.github.xejohv.podclassicxzs.gestures.GestureService

/**
 * 只为了拿"锁屏"能力（force-lock）。不做任何数据擦除、密码策略之类的事。
 */
class PodDeviceAdminReceiver : DeviceAdminReceiver() {

    /** 激活完成后立刻回 PodClassic，避免把用户丢在系统设置页里出不来 */
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        GestureService.goHome()
    }
}
