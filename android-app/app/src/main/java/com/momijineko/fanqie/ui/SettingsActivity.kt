package com.momijineko.fanqie.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.momijineko.fanqie.App
import com.momijineko.fanqie.R
import com.momijineko.fanqie.databinding.ActivitySettingsBinding
import com.momijineko.fanqie.util.EinkUtils

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

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

        binding.etServerUrl.setText(prefs.serverUrl)
        binding.btnSaveServer.setOnClickListener {
            prefs.serverUrl = binding.etServerUrl.text.toString().trim()
            App.instance.reconnectApi()
            finish()
        }
    }
}
