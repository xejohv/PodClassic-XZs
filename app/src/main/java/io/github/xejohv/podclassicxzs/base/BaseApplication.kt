package io.github.xejohv.podclassicxzs.base

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Environment
import io.github.xejohv.podclassicxzs.util.LocaleHelper
import io.github.xejohv.podclassicxzs.util.MediaScanUtil
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class BaseApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun attachBaseContext(base: Context) {
        // 先按用户选择的语言包一层，Application 的 resources 就是目标语言，
        // 后面 Core.init() -> setLanguage() -> Strings.init() 全部生效
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        Core.init()
        MediaScanUtil.scanMissingAsync(this)

        /*
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val file =
                File(Environment.getExternalStorageDirectory().path + File.separator + "PodClass Bug Report.txt")
            val fw = FileWriter(file, true)
            fw.write("\n----------------------------------------------------\n")
            fw.write(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            fw.write(e.message + "\n")
            fw.write(e.localizedMessage + "\n")
            for (x in e.stackTrace) {
                fw.write(x.toString() + "\n")
            }
            fw.close()
        }

  //       */
    }
}