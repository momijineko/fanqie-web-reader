package com.momijineko.fanqie.ui

import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.momijineko.fanqie.App
import com.momijineko.fanqie.R
import com.momijineko.fanqie.databinding.ActivitySettingsBinding
import com.momijineko.fanqie.util.EinkUtils

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private val themes = listOf(
        "default" to "默认",
        "sepia" to "羊皮纸",
        "green" to "护眼绿",
        "dark" to "暗黑",
        "eink" to "墨水屏",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EinkUtils.applyEinkOptimizations(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = App.instance.prefs

        binding.sliderFontSize.value = prefs.fontSize.toFloat()
        binding.sliderFontSize.addOnChangeListener { _, value, _ ->
            prefs.fontSize = value.toInt()
            binding.tvFontSizeValue.text = "${value.toInt()}px"
        }
        binding.tvFontSizeValue.text = "${prefs.fontSize}px"

        binding.sliderLineHeight.value = prefs.lineHeight
        binding.sliderLineHeight.addOnChangeListener { _, value, _ ->
            prefs.lineHeight = value
            binding.tvLineHeightValue.text = String.format("%.1f", value)
        }
        binding.tvLineHeightValue.text = String.format("%.1f", prefs.lineHeight)

        binding.rgFont.setOnCheckedChangeListener { _, id ->
            prefs.fontFamily = when (id) {
                R.id.rb_font_serif -> "serif"
                R.id.rb_font_kai -> "kai"
                else -> "sans"
            }
        }
        when (prefs.fontFamily) {
            "serif" -> binding.rbFontSerif.isChecked = true
            "kai" -> binding.rbFontKai.isChecked = true
            else -> binding.rbFontSans.isChecked = true
        }

        binding.rgReadMode.setOnCheckedChangeListener { _, id ->
            prefs.readMode = when (id) {
                R.id.rb_mode_scroll -> "scroll"
                else -> "page"
            }
        }
        if (prefs.readMode == "scroll") binding.rbModeScroll.isChecked = true
        else binding.rbModePage.isChecked = true

        setupThemePicker()

        binding.switchEink.isChecked = prefs.einkMode
        binding.switchEink.setOnCheckedChangeListener { _, checked ->
            prefs.einkMode = checked
            if (checked) EinkUtils.applyEinkOptimizations(this)
            EinkUtils.forceFullRefresh(this)
        }

        binding.etServerUrl.setText(prefs.serverUrl)
        binding.btnSaveServer.setOnClickListener {
            prefs.serverUrl = binding.etServerUrl.text.toString().trim()
            App.instance.reconnectApi()
            finish()
        }
    }

    private fun setupThemePicker() {
        val container = binding.themeContainer
        container.removeAllViews()
        val dp = resources.displayMetrics.density
        val current = App.instance.prefs.readingTheme

        for ((id, name) in themes) {
            val chip = TextView(this)
            chip.text = name
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val pad = (dp * 12).toInt()
            chip.setPadding(pad, (dp * 8).toInt(), pad, (dp * 8).toInt())
            val colors = EinkUtils.themeColors(id)
            chip.setBackgroundColor(colors.bg)
            chip.setTextColor(colors.fg)
            if (id == current) {
                chip.setTypeface(null, android.graphics.Typeface.BOLD)
                chip.setBackgroundResource(R.color.accent)
                chip.setTextColor(getColor(R.color.white))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.marginEnd = (dp * 8).toInt()
            chip.layoutParams = lp
            chip.setOnClickListener {
                App.instance.prefs.readingTheme = id
                setupThemePicker()
                EinkUtils.forceFullRefresh(this)
            }
            container.addView(chip)
        }
    }
}
