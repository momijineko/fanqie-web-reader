package com.momijineko.fanqie.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.databinding.FragmentSearchBinding
import com.momijineko.fanqie.util.EinkUtils
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val adapter = BookAdapter({ book -> openDetail(book.bookId) }, BookAdapter.Mode.LIST)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvBooks.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.rvBooks.adapter = adapter
        EinkUtils.disableRecyclerViewAnimation(binding.rvBooks)

        binding.btnSearch.setOnClickListener {
            val key = binding.etSearch.text.toString().trim()
            if (key.isNotEmpty()) doSearch(key)
        }

        loadLocalShelf()
    }

    private fun loadLocalShelf() {
        lifecycleScope.launch {
            val books = App.instance.db.bookDao().getAll()
            if (books.isNotEmpty()) adapter.submit(books.map { it.toBookInfo() })
        }
    }

    private fun doSearch(key: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val results = App.instance.api.search(key)
                if (results.isEmpty()) {
                    Snackbar.make(binding.root, "未找到结果", Snackbar.LENGTH_SHORT).show()
                }
                adapter.submit(results)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "搜索失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun openDetail(bookId: String) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).putExtra("book_id", bookId))
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
