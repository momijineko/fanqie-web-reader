package com.momijineko.fanqie.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardLogin.setOnClickListener { showLoginDialog() }
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.cardServerStatus.setOnClickListener { checkServer() }

        refreshLoginStatus()
    }

    private fun refreshLoginStatus() {
        if (App.instance.prefs.isLoggedIn) {
            binding.tvLoginStatus.text = "已登录（点击重新设置 Cookie）"
            lifecycleScope.launch {
                try {
                    val info = App.instance.api.getUserInfo()
                    if (info != null) binding.tvLoginStatus.text = "已登录：${info.userName}"
                } catch (_: Exception) {}
            }
        } else {
            binding.tvLoginStatus.text = "未登录（点击设置 Cookie 登录）"
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

    private fun checkServer() {
        binding.tvServerStatus.text = "检测中..."
        lifecycleScope.launch {
            val ok = App.instance.api.health()
            binding.tvServerStatus.text = if (ok) "服务器正常 ✓" else "服务器不可达 ✗"
        }
    }

    override fun onResume() { super.onResume(); refreshLoginStatus() }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
