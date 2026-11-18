package io.github.xejohv.podclassicxzs.bean

import android.content.Intent
import io.github.xejohv.podclassicxzs.base.BaseApplication

class App(var name: String, var packageName: String) {

    var intent: Intent? = null
        get() {
            if (field != null) {
                return field
            }
            val packageManager = BaseApplication.context.packageManager
            field = packageManager.getLaunchIntentForPackage(packageName)
            return field
        }

}