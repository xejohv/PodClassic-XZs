package io.github.xejohv.podclassicxzs.util

import android.os.Handler
import android.os.Looper
import io.github.xejohv.podclassicxzs.base.Observer

class LiveData<E>(e: E? = null) {
    interface OnDataChangeListener {
        fun onStart() {}
        fun onStop() {}
        fun onDataChange() {}
    }

    private var data: E? = e
    private val functionList = HashMap<Observer, OnDataChangeListener>()
    fun get(): E? {
        return data
    }

    fun set(e: E?) {
        // 播放器服务在 HandlerThread / MediaSession 回调线程里更新数据，
        // 若直接把通知同步派发给观察者，观察者会在后台线程刷新 View，
        // 触发 ViewRootImpl$CalledFromWrongThreadException 并打断当前界面布局（表现为整页空白）。
        if (Looper.myLooper() == Looper.getMainLooper()) {
            this.data = e
            onDataChange()
        } else {
            mainHandler.post {
                this.data = e
                onDataChange()
            }
        }
    }

    private fun onDataChange() {
        for ((observer, listener) in functionList) {
            if (observer.enable) {
                listener.onDataChange()
            }
        }
    }

    fun onStart(observer: Observer) {
        if (observer.enable) {
            functionList[observer]?.onStart()
        }
    }

    fun onStop(observer: Observer) {
        if (observer.enable) {
            functionList[observer]?.onStop()
        }
    }

    fun addObserver(observer: Observer, listener: OnDataChangeListener) {
        functionList[observer] = listener
    }

    fun removeObserver(observer: Observer) {
        functionList.remove(observer)
    }

    companion object {
        private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    }
}