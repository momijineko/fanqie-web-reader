package com.momijineko.fanqie.util

import android.app.Activity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

object EinkUtils {

    data class ThemeColors(val bg: Int, val fg: Int, val secondary: Int, val border: Int)

    val THEME_DEFAULT = ThemeColors(0xFFF5F0E8.toInt(), 0xFF2C2C2C.toInt(), 0xFF666666.toInt(), 0xFFDDD5C8.toInt())
    val THEME_SEPIA  = ThemeColors(0xFFF4ECD8.toInt(), 0xFF3D2B1F.toInt(), 0xFF7A6650.toInt(), 0xFFD8CDB5.toInt())
    val THEME_GREEN  = ThemeColors(0xFFC8DCC8.toInt(), 0xFF1A3A1A.toInt(), 0xFF4A6A4A.toInt(), 0xFFA8C0A8.toInt())
    val THEME_DARK   = ThemeColors(0xFF1A1A2E.toInt(), 0xFFD4D4D4.toInt(), 0xFF888899.toInt(), 0xFF333344.toInt())
    val THEME_EINK   = ThemeColors(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF444444.toInt(), 0xFF000000.toInt())

    fun themeColors(id: String): ThemeColors = when (id) {
        "sepia" -> THEME_SEPIA
        "green" -> THEME_GREEN
        "dark" -> THEME_DARK
        "eink" -> THEME_EINK
        else -> THEME_DEFAULT
    }

    fun applyEinkOptimizations(activity: AppCompatActivity) {
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
            val animatorScale = Class.forName("android.animation.Animator")
                .getField("sDurationScale").also { it.isAccessible = true }
            animatorScale.set(null, 0f)
        } catch (_: Exception) {}
    }

    fun forceFullRefresh(activity: Activity) {
        try {
            val decorView = activity.window.decorView
            decorView.postInvalidate()
            decorView.postDelayed({ decorView.postInvalidate() }, 50)

            // Try Hisense EPD refresh via reflection
            try {
                val cls = Class.forName("com.hisense.epd.EpdManager")
                val method = cls.getMethod("refreshScreen")
                method.invoke(null)
            } catch (_: Exception) {}

            // Try Onyx Boox refresh
            try {
                val cls = Class.forName("com.onyx.android.sdk.device.Device")
                val instance = cls.getMethod("currentDevice").invoke(null)
                val method = cls.getMethod("requestEpdRefresh", java.lang.String::class.java)
                method.invoke(instance, "GU")
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun forceFullRefresh(view: View) {
        view.postInvalidate()
        view.postDelayed({ view.postInvalidate() }, 50)
    }

    fun disableRecyclerViewAnimation(recyclerView: RecyclerView) {
        recyclerView.layoutAnimation = null
        recyclerView.itemAnimator = null
    }

    fun fontFamilyToStack(family: String): String = when (family) {
        "serif" -> "serif"
        "kai" -> "serif"  // KaiTi falls back to serif on e-ink (monospace mapping was wrong)
        else -> "sans-serif"
    }

    fun applyThemeColors(view: View, themeId: String) {
        val colors = themeColors(themeId)
        view.setBackgroundColor(colors.bg)
        if (view is TextView) {
            view.setTextColor(colors.fg)
        }
    }
}
