package io.github.xejohv.podclassicxzs.view

import android.content.Context
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.values.Colors
import io.github.xejohv.podclassicxzs.values.Values
import java.io.File

class TxtView :
    androidx.appcompat.widget.AppCompatTextView, ScreenView {

    private val title : String

    /** 点击中间键时的动作；彩蛋页用它跳系统设置，普通文本页为 null */
    var onEnter: (() -> Boolean)? = null

    constructor(context: Context, title : String, text : String) : super(context) {
        this.text = text
        this.title = title
    }

    constructor(context: Context, file : File) : super(context) {
        this.title = file.name
        this.text = file.readText()
    }

    init {
        val padding = (Values.DEFAULT_PADDING / 2)
        setPadding(padding, 0, padding, 0)
        setTextColor(Colors.text)
        this.text = text
    }

    override fun enter(): Boolean {
        return onEnter?.invoke() ?: false
    }

    override fun enterLongClick(): Boolean {
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        if (!canScrollVertically(slideVal)) {
            return false
        }
        scrollY += slideVal * 24
        return true
    }

    override fun getTitle(): String {
        return title
    }
}