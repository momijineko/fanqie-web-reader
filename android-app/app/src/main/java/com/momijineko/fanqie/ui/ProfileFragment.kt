package com.momijineko.fanqie.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.R
import com.momijineko.fanqie.databinding.FragmentProfileBinding
import com.momijineko.fanqie.util.EinkUtils
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val themes = listOf(
        "default" to "默认", "sepia" to "羊皮纸", "green" to "护眼绿",
        "dark" to "暗黑", "eink" to "墨水屏",
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardLogin.setOnClickListener {
            if (App.instance.prefs.isLoggedIn) showLogoutDialog() else showLoginDialog()
        }

        binding.cardClearCache.setOnClickListener { clearCache() }

        binding.einkSwitch.isChecked = App.instance.prefs.einkMode
        binding.einkSwitch.setOnCheckedChangeListener { _, checked ->
            App.instance.prefs.einkMode = checked
            if (checked && activity is androidx.appcompat.app.AppCompatActivity) {
                EinkUtils.applyEinkOptimizations(activity as androidx.appcompat.app.AppCompatActivity)
            }
        }

        addSettingsLink()
        setupThemePicker()
        loadVersion()
        refreshLoginStatus()
    }

    private fun addSettingsLink() {
        val rootView = binding.root
        if (rootView is android.widget.ScrollView) {
            val inner = rootView.getChildAt(0)
            if (inner is LinearLayout) {
                val dp = resources.displayMetrics.density
                val settingsTv = TextView(requireContext()).apply {
                    text = "阅读设置 →"
                    textSize = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics
                    )
                    setTextColor(requireContext().getColor(R.color.accent))
                    val pad = (dp * 16).toInt()
                    setPadding(pad, (dp * 12).toInt(), pad, (dp * 12).toInt())
                    setOnClickListener {
                        startActivity(Intent(requireContext(), SettingsActivity::class.java))
                    }
                }
                inner.addView(settingsTv, 0)
            }
        }
    }

    private fun setupThemePicker() {
        val container = binding.themeContainer
        container.removeAllViews()
        val dp = resources.displayMetrics.density
        val current = App.instance.prefs.readingTheme

        for ((id, name) in themes) {
            val chip = TextView(requireContext())
            chip.text = name
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val pad = (dp * 12).toInt()
            chip.setPadding(pad, (dp * 8).toInt(), pad, (dp * 8).toInt())
            val colors = EinkUtils.themeColors(id)
            chip.setBackgroundColor(colors.bg)
            chip.setTextColor(colors.fg)
            if (id == current) {
                chip.setTypeface(null, android.graphics.Typeface.BOLD)
                chip.setTextColor(requireContext().getColor(R.color.white))
                chip.setBackgroundResource(R.color.accent)
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
            }
            container.addView(chip)
        }
    }

    private fun refreshLoginStatus() {
        if (App.instance.prefs.isLoggedIn) {
            val cachedName = App.instance.prefs.cachedUserName
            binding.tvLoginStatus.text = if (cachedName.isNotEmpty()) "已登录：$cachedName" else "已登录（点击管理）"
            lifecycleScope.launch {
                try {
                    val info = App.instance.api.getUserInfo()
                    if (info != null) {
                        App.instance.prefs.cachedUserName = info.userName
                        App.instance.prefs.cachedUserAvatar = info.avatarUrl
                        binding.tvLoginStatus.text = "已登录：${info.userName}"
                    }
                } catch (_: Exception) {}
            }
        } else {
            binding.tvLoginStatus.text = "未登录（点击 Cookie 登录）"
        }
    }

    private fun showLoginDialog() {
        val edit = EditText(requireContext())
        edit.hint = "粘贴 fanqienovel.com 的 Cookie"
        edit.minLines = 3
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cookie 登录")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                val cookie = edit.text.toString().trim()
                if (cookie.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            val ok = App.instance.api.saveCookie(cookie)
                            if (ok) {
                                App.instance.prefs.isLoggedIn = true
                                Snackbar.make(binding.root, "登录成功", Snackbar.LENGTH_SHORT).show()
                            } else {
                                Snackbar.make(binding.root, "保存失败", Snackbar.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_LONG).show()
                        }
                        refreshLoginStatus()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("退出") { _, _ ->
                lifecycleScope.launch {
                    try { App.instance.api.deleteCookie() } catch (_: Exception) {}
                    App.instance.prefs.isLoggedIn = false
                    App.instance.prefs.cachedUserName = ""
                    App.instance.prefs.cachedUserAvatar = ""
                    Snackbar.make(binding.root, "已退出登录", Snackbar.LENGTH_SHORT).show()
                    refreshLoginStatus()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearCache() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清除缓存")
            .setMessage("将清除搜索历史和章节缓存，确定吗？")
            .setPositiveButton("清除") { _, _ ->
                lifecycleScope.launch {
                    try {
                        App.instance.db.searchHistoryDao().clearAll()
                        val books = App.instance.db.bookDao().getAll()
                        for (book in books) {
                            App.instance.db.chapterDao().deleteByBook(book.bookId)
                        }
                        Snackbar.make(binding.root, "已清除缓存", Snackbar.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Snackbar.make(binding.root, "清除失败: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadVersion() {
        val appVersion = "0.1.0"
        binding.tvVersion.text = "版本 $appVersion"
        lifecycleScope.launch {
            try {
                val serverVersion = App.instance.api.getVersion()
                binding.tvVersion.text = "App v$appVersion · 服务端 v$serverVersion"
            } catch (_: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            refreshLoginStatus()
            setupThemePicker()
            binding.einkSwitch.isChecked = App.instance.prefs.einkMode
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
