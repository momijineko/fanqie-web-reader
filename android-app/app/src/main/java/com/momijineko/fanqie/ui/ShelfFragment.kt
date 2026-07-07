package com.momijineko.fanqie.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.data.api.BookshelfItem
import com.momijineko.fanqie.databinding.FragmentShelfBinding
import com.momijineko.fanqie.util.EinkUtils
import kotlinx.coroutines.launch

class ShelfFragment : Fragment() {
    private var _binding: FragmentShelfBinding? = null
    private val binding get() = _binding!!
    private val adapter = BookAdapter({ book -> openDetail(book.bookId) }, BookAdapter.Mode.GRID)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShelfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvShelf.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvShelf.adapter = adapter
        EinkUtils.disableRecyclerViewAnimation(binding.rvShelf)

        binding.btnRefresh.setOnClickListener { loadShelf() }
        loadShelf()
    }

    private fun loadShelf() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            if (App.instance.prefs.isLoggedIn) {
                try {
                    val shelf = App.instance.api.getBookshelf()
                    adapter.submit(shelf.map { it.toBookInfo() })
                } catch (e: Exception) {
                    loadLocalShelf()
                    Snackbar.make(binding.root, "云端同步失败，显示本地书架", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                loadLocalShelf()
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun loadLocalShelf() {
        lifecycleScope.launch {
            val books = App.instance.db.bookDao().getAll()
            adapter.submit(books.map { it.toBookInfo() })
        }
    }

    private fun openDetail(bookId: String) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).putExtra("book_id", bookId))
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
