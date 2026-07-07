package com.momijineko.fanqie.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.data.api.BookDetail
import com.momijineko.fanqie.data.api.ChapterInfo
import com.momijineko.fanqie.data.db.BookEntity
import com.momijineko.fanqie.databinding.ActivityDetailBinding
import com.momijineko.fanqie.util.EinkUtils
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private lateinit var bookId: String
    private var detail: BookDetail? = null
    private var chapters: List<ChapterInfo> = emptyList()
    private val chapterAdapter = ChapterAdapter { idx -> openReader(idx) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EinkUtils.applyEinkOptimizations(this)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bookId = intent.getStringExtra("book_id") ?: run { finish(); return }

        binding.rvChapters.layoutManager = LinearLayoutManager(this)
        binding.rvChapters.adapter = chapterAdapter
        EinkUtils.disableRecyclerViewAnimation(binding.rvChapters)

        binding.btnRead.setOnClickListener {
            if (chapters.isNotEmpty()) openReader(0)
        }
        binding.btnAddShelf.setOnClickListener { toggleShelf() }

        loadData()
    }

    private fun loadData() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val d = App.instance.api.detail(bookId)
                val c = App.instance.api.chapters(bookId)
                detail = d
                chapters = c
                d?.let { bindDetail(it) }
                chapterAdapter.submit(c)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "加载失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
            binding.progressBar.visibility = android.view.View.GONE
        }
    }

    private fun bindDetail(d: BookDetail) {
        binding.tvTitle.text = d.title
        binding.tvAuthor.text = d.author
        binding.tvDesc.text = d.abstractText
        binding.tvCategory.text = d.category
        binding.tvScore.text = d.score
        binding.tvWordCount.text = d.wordNumber
    }

    private fun toggleShelf() {
        lifecycleScope.launch {
            val existing = App.instance.db.bookDao().getById(bookId)
            if (existing != null) {
                App.instance.db.bookDao().delete(existing)
                Snackbar.make(binding.root, "已移出书架", Snackbar.LENGTH_SHORT).show()
            } else {
                val d = detail ?: return@launch
                App.instance.db.bookDao().upsert(BookEntity(
                    bookId = bookId,
                    name = d.title,
                    author = d.author,
                    thumbUrl = d.thumbUrl,
                    desc = d.abstractText,
                ))
                if (App.instance.prefs.isLoggedIn) {
                    try { App.instance.api.addToShelf(bookId) } catch (_: Exception) {}
                }
                Snackbar.make(binding.root, "已加入书架", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun openReader(chapterIdx: Int) {
        startActivity(Intent(this, ReaderActivity::class.java)
            .putExtra("book_id", bookId)
            .putExtra("chapter_idx", chapterIdx))
    }
}
