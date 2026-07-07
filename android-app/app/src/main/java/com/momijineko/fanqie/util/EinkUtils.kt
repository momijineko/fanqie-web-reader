package com.momijineko.fanqie.util

import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.LayoutAnimationController
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

object EinkUtils {

    fun applyEinkOptimizations(activity: AppCompatActivity) {
        val window = activity.window
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
        disableAnimations(activity)
    }

    private fun disableAnimations(activity: AppCompatActivity) {
        try {
            val scaleSettings = Class.forName("android.animation.ValueAnimator")
                .getField("sDurationScale").also { it.isAccessible = true }
            scaleSettings.set(null, 0f)
            val transitionScale = Class.forName("android.app.ActivityManager")
                .getField("sTransitionScale").also { it.isAccessible = true }
            transitionScale.set(null, 0f)
        } catch (_: Exception) {}
    }

    fun disableRecyclerViewAnimation(recyclerView: RecyclerView) {
        recyclerView.layoutAnimation = null
        recyclerView.itemAnimator = null
    }

    fun fontFamilyToStack(family: String): String = when (family) {
        "serif" -> "serif"
        "kai" -> "serif-monospace"
        else -> "sans-serif"
    }
}
