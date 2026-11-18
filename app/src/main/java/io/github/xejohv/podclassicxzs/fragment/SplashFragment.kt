package io.github.xejohv.podclassicxzs.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.github.xejohv.podclassicxzs.R
import io.github.xejohv.podclassicxzs.util.ThreadUtil

class SplashFragment(private val runnable: Runnable) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ThreadUtil.asyncTask(runnable) {
            // 确保 Fragment 已经 attach 到 Activity
            Handler(Looper.getMainLooper()).post {
                if (isAdded && !isRemoving && !isDetached) {
                    parentFragmentManager
                        .beginTransaction()
                        .remove(this@SplashFragment)
                        .commitAllowingStateLoss()
                }
            }
        }
    }
}