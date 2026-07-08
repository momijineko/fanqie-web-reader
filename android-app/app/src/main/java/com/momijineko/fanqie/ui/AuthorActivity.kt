package com.momijineko.fanqie.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.R
import com.momijineko.fanqie.databinding.ActivityAuthorBinding
import com.momijineko.fanqie.util.EinkUtils
import kotlinx.coroutines.launch

class AuthorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthorBinding
    private val adapter = BookAdapter({ book ->
        startActivity(Intent(this, DetailActivity::class.java).putExtra("book_id", book.bookId))
    }, BookAdapter.Mode.LIST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EinkUtils.applyEinkOptimizations(this)
        binding = ActivityAuthorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val authorId = intent.getStringExtra("author_id") ?: ""
        val authorName = intent.getStringExtra("author_name") ?: "作者"

        binding.toolbar.title = authorName
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.tvAuthorName.text = authorName

        binding.rvAuthorBooks.layoutManager = LinearLayoutManager(this)
        binding.rvAuthorBooks.adapter = adapter
        EinkUtils.disableRecyclerViewAnimation(binding.rvAuthorBooks)

        if (authorId.isEmpty()) {
            Snackbar.make(binding.root, "缺少作者信息", Snackbar.LENGTH_SHORT).show()
            binding.progressBar.visibility = View.GONE
            return
        }

        loadAuthorBooks(authorId)
    }

    private fun loadAuthorBooks(authorId: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val books = App.instance.api.getAuthorBooks(authorId)
                adapter.submit(books)
                binding.tvAuthorMeta.text = "共 ${books.size} 部作品"
                if (books.isEmpty()) {
                    Snackbar.make(binding.root, "该作者暂无其他作品", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "加载失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
            binding.progressBar.visibility = View.GONE
        }
    }
}
