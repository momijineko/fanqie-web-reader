package com.momijineko.fanqie.ui

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.R
import com.momijineko.fanqie.data.api.BookDetail
import com.momijineko.fanqie.data.api.ChapterInfo
import com.momijineko.fanqie.data.db.BookEntity
import com.momijineko.fanqie.data.db.ProgressEntity
import com.momijineko.fanqie.databinding.ActivityDetailBinding
import com.momijineko.fanqie.util.EinkUtils
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private lateinit var bookId: String
    private var detail: BookDetail? = null
    private var chapters: List<ChapterInfo> = emptyList()
    private var resumeIdx = -1
    private var descExpanded = false
    private var isOnShelf = false
    private val chapterAdapter = ChapterAdapter { idx -> openReader(idx) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EinkUtils.applyEinkOptimizations(this)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bookId = intent.getStringExtra("book_id") ?: run { finish(); return }

        binding.toolbar.title = "书籍详情"
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvChapters.layoutManager = LinearLayoutManager(this)
        binding.rvChapters.adapter = chapterAdapter
        EinkUtils.disableRecyclerViewAnimation(binding.rvChapters)

        binding.btnRead.setOnClickListener {
            if (chapters.isNotEmpty()) {
                openReader(if (resumeIdx >= 0) resumeIdx else 0)
            }
        }
        binding.btnShelf.setOnClickListener { toggleShelf() }
        binding.btnComments.setOnClickListener {
            startActivity(Intent(this, CommentsActivity::class.java).putExtra("book_id", bookId))
        }
        binding.btnShare.setOnClickListener { shareBook() }
        binding.btnExpandDesc.setOnClickListener { toggleDesc() }

        binding.tvAuthor.setOnClickListener {
            val d = detail ?: return@setOnClickListener
            if (d.authorId.isNotEmpty()) {
                startActivity(Intent(this, AuthorActivity::class.java)
                    .putExtra("author_id", d.authorId)
                    .putExtra("author_name", d.author))
            }
        }

        binding.etChapterFilter.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                chapterAdapter.filter(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadData()
    }

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val d = App.instance.api.detail(bookId)
                val c = App.instance.api.chapters(bookId)
                detail = d
                chapters = c
                d?.let { bindDetail(it) }

                val progress = App.instance.db.progressDao().get(bookId)
                if (progress != null && progress.chapterIdx in c.indices) {
                    resumeIdx = progress.chapterIdx
                    binding.btnRead.text = "继续阅读 · ${progress.chapterName.ifEmpty { "第${resumeIdx + 1}章" }}"
                }

                val existing = App.instance.db.bookDao().getById(bookId)
                isOnShelf = existing != null
                updateShelfButton()

                chapterAdapter.submit(c, resumeIdx)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "加载失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun bindDetail(d: BookDetail) {
        binding.toolbar.title = d.title
        binding.tvTitle.text = d.title
        binding.tvAuthor.text = d.author
        binding.tvDesc.text = d.abstractText

        binding.ivCover.load(d.thumbUrl) {
            crossfade(false)
            placeholder(R.drawable.ic_book_placeholder)
            error(R.drawable.ic_book_placeholder)
        }

        val metaParts = mutableListOf<String>()
        if (d.category.isNotEmpty()) metaParts.add(d.category)
        if (d.score.isNotEmpty()) metaParts.add("评分 ${d.score}")
        if (d.wordNumber.isNotEmpty()) metaParts.add("${d.wordNumber}字")
        if (d.chapterNumber.isNotEmpty()) metaParts.add("${d.chapterNumber}章")
        if (d.creationStatus == "0") metaParts.add("已完结")
        else if (d.creationStatus == "1") metaParts.add("连载中")
        binding.tvMeta.text = metaParts.joinToString(" · ")

        binding.tvDesc.post {
            if (binding.tvDesc.layout != null && binding.tvDesc.layout.lineCount > 4) {
                binding.btnExpandDesc.visibility = View.VISIBLE
            } else {
                binding.btnExpandDesc.visibility = View.GONE
            }
        }
    }

    private fun toggleDesc() {
        descExpanded = !descExpanded
        if (descExpanded) {
            binding.tvDesc.maxLines = Int.MAX_VALUE
            binding.tvDesc.ellipsize = null
            binding.btnExpandDesc.text = "收起"
        } else {
            binding.tvDesc.maxLines = 4
            binding.tvDesc.ellipsize = TextUtils.TruncateAt.END
            binding.btnExpandDesc.text = "展开"
        }
    }

    private fun updateShelfButton() {
        binding.btnShelf.text = if (isOnShelf) "移出书架" else "加入书架"
    }

    private fun toggleShelf() {
        lifecycleScope.launch {
            if (isOnShelf) {
                val existing = App.instance.db.bookDao().getById(bookId)
                if (existing != null) App.instance.db.bookDao().delete(existing)
                if (App.instance.prefs.isLoggedIn) {
                    try { App.instance.api.removeFromShelf(bookId) } catch (_: Exception) {}
                }
                isOnShelf = false
                Snackbar.make(binding.root, "已移出书架", Snackbar.LENGTH_SHORT).show()
            } else {
                val d = detail ?: return@launch
                App.instance.db.bookDao().upsert(BookEntity(
                    bookId = bookId,
                    name = d.title,
                    author = d.author,
                    thumbUrl = d.thumbUrl,
                    desc = d.abstractText,
                    chapterCount = d.chapterNumber.toIntOrNull() ?: chapters.size,
                    status = if (d.creationStatus == "0") "已完结" else if (d.creationStatus == "1") "连载中" else "",
                    category = d.category,
                    score = d.score,
                    wordCount = d.wordNumber.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0,
                ))
                if (App.instance.prefs.isLoggedIn) {
                    try { App.instance.api.addToShelf(bookId) } catch (_: Exception) {}
                }
                isOnShelf = true
                Snackbar.make(binding.root, "已加入书架", Snackbar.LENGTH_SHORT).show()
            }
            updateShelfButton()
        }
    }

    private fun shareBook() {
        val d = detail ?: return
        val shareText = "推荐《${d.title}》- ${d.author}\n${d.abstractText.take(100)}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "分享"))
    }

    private fun openReader(chapterIdx: Int) {
        startActivity(Intent(this, ReaderActivity::class.java)
            .putExtra("book_id", bookId)
            .putExtra("chapter_idx", chapterIdx))
    }

    override fun onResume() {
        super.onResume()
        if (::bookId.isInitialized && chapters.isNotEmpty()) {
            lifecycleScope.launch {
                val progress = App.instance.db.progressDao().get(bookId)
                if (progress != null && progress.chapterIdx in chapters.indices) {
                    resumeIdx = progress.chapterIdx
                    binding.btnRead.text = "继续阅读 · ${progress.chapterName.ifEmpty { "第${resumeIdx + 1}章" }}"
                    chapterAdapter.submit(chapters, resumeIdx)
                }
            }
        }
    }
}
