package io.github.xejohv.podclassicxzs.service

import android.app.admin.DeviceAdminReceiver

/**
 * 只为了拿"锁屏"能力（force-lock）。不做任何数据擦除、密码策略之类的事。
 */
class PodDeviceAdminReceiver : DeviceAdminReceiver()
