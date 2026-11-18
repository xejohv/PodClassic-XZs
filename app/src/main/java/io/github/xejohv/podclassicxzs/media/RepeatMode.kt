package io.github.xejohv.podclassicxzs.media

import io.github.xejohv.podclassicxzs.R
import io.github.xejohv.podclassicxzs.values.Strings

enum class RepeatMode(val id: Int, private val stringId: Int) {
    NONE(0, R.string.repeat_mode_none), ALL(1, R.string.repeat_mode_all), UNKNOWN(
        2,
        R.string.empty
    );

    val title
        get() = Strings.getString(stringId)

    companion object {
        const val count = 2
        fun getRepeatMode(index: Int): RepeatMode {
            return when (index) {
                0 -> NONE
                1 -> ALL
                else -> UNKNOWN
            }
        }
    }
}