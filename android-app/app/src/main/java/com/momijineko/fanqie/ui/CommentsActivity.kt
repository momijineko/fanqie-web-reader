package com.momijineko.fanqie.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.R
import com.momijineko.fanqie.databinding.ActivityCommentsBinding
import com.momijineko.fanqie.util.EinkUtils
import kotlinx.coroutines.launch

class CommentsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCommentsBinding
    private lateinit var bookId: String
    private val adapter = CommentAdapter()
    private var offset = 0
    private var loading = false
    private var hasMore = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EinkUtils.applyEinkOptimizations(this)
        binding = ActivityCommentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookId = intent.getStringExtra("book_id") ?: run { finish(); return }

        binding.toolbar.title = "评论"
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvComments.layoutManager = LinearLayoutManager(this)
        binding.rvComments.adapter = adapter
        EinkUtils.disableRecyclerViewAnimation(binding.rvComments)

        binding.rvComments.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                val last = lm.findLastVisibleItemPosition()
                val total = lm.itemCount
                if (hasMore && !loading && total > 0 && last >= total - 3) {
                    loadMore()
                }
            }
        })

        loadComments()
    }

    private fun loadComments() {
        binding.progressBar.visibility = View.VISIBLE
        loading = true
        offset = 0
        lifecycleScope.launch {
            try {
                val comments = App.instance.api.getComments(bookId, offset = 0, count = 20)
                adapter.submit(comments)
                hasMore = comments.size >= 20
                if (comments.isEmpty()) {
                    Snackbar.make(binding.root, "暂无评论", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "加载失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
            loading = false
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun loadMore() {
        loading = true
        offset += 20
        lifecycleScope.launch {
            try {
                val more = App.instance.api.getComments(bookId, offset = offset, count = 20)
                if (more.isNotEmpty()) {
                    adapter.addAll(more)
                }
                hasMore = more.size >= 20
            } catch (e: Exception) {
                hasMore = false
            }
            loading = false
        }
    }
}
