package com.momijineko.fanqie.ui

import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.data.api.ChapterContent
import com.momijineko.fanqie.data.api.ChapterInfo
import com.momijineko.fanqie.data.db.ChapterEntity
import com.momijineko.fanqie.data.db.ProgressEntity
import com.momijineko.fanqie.databinding.ActivityReaderBinding
import com.momijineko.fanqie.util.EinkUtils
import kotlinx.coroutines.launch
import org.json.JSONArray

class ReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReaderBinding
    private lateinit var bookId: String
    private var chapterIdx = 0
    private var chapters: List<ChapterInfo> = emptyList()
    private var pages: List<CharSequence> = emptyList()
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EinkUtils.applyEinkOptimizations(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())

        bookId = intent.getStringExtra("book_id") ?: run { finish(); return }
        chapterIdx = intent.getIntExtra("chapter_idx", 0)

        binding.btnPrev.setOnClickListener { if (chapterIdx > 0) { chapterIdx--; loadChapter() } }
        binding.btnNext.setOnClickListener { if (chapterIdx < chapters.size - 1) { chapterIdx++; loadChapter() } }
        binding.btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        loadChapterList()
    }

    private fun loadChapterList() {
        lifecycleScope.launch {
            try {
                chapters = App.instance.api.chapters(bookId)
                if (chapterIdx >= chapters.size) chapterIdx = 0
                loadChapter()
            } catch (e: Exception) {
                Snackbar.make(binding.getRoot(), "加载章节列表失败", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun loadChapter() {
        if (chapters.isEmpty()) return
        val ch = chapters[chapterIdx]
        binding.tvChapterTitle.text = ch.name
        binding.tvPageInfo.text = "加载中..."

        lifecycleScope.launch {
            var content: ChapterContent? = null
            val cached = App.instance.db.chapterDao().getById(ch.chapterId)
            if (cached != null) {
                val paras = mutableListOf<String>()
                val arr = JSONArray(cached.paragraphsJson)
                for (i in 0 until arr.length()) paras.add(arr.getString(i))
                content = ChapterContent(ch.chapterId, cached.title, paras, cached.authorSpeak)
            } else {
                try {
                    content = App.instance.api.content(ch.chapterId)
                    if (content != null) {
                        val arr = JSONArray()
                        content.paragraphs.forEach { arr.put(it) }
                        App.instance.db.chapterDao().upsert(ChapterEntity(
                            chapterId = ch.chapterId,
                            bookId = bookId,
                            title = content.title,
                            paragraphsJson = arr.toString(),
                            authorSpeak = content.authorSpeak,
                        ))
                    }
                } catch (e: Exception) {
                    Snackbar.make(binding.getRoot(), "加载失败", Snackbar.LENGTH_SHORT).show()
                }
            }

            if (content != null) {
                val mode = App.instance.prefs.readMode
                if (mode == "scroll") {
                    renderScroll(content!!)
                } else {
                    renderPaginated(content!!)
                }
                saveProgress(ch)
            }
        }
    }

    private fun renderScroll(content: ChapterContent) {
        binding.scrollView.visibility = View.VISIBLE
        binding.tvPageText.visibility = View.GONE
        binding.btnPrev.visibility = View.GONE
        binding.btnNext.visibility = View.GONE

        val sb = StringBuilder()
        if (content.title.isNotEmpty()) sb.append(content.title).append("\n\n")
        for (p in content.paragraphs) {
            sb.append("    ").append(p).append("\n\n")
        }
        if (content.authorSpeak.isNotEmpty()) {
            sb.append("\n【作者的话】\n").append(content.authorSpeak)
        }
        val tv = binding.tvScrollText
        applyTextStyle(tv)
        tv.text = sb.toString()
        binding.scrollView.scrollTo(0, 0)
        binding.tvPageInfo.text = "${chapterIdx + 1}/${chapters.size}"
    }

    private fun renderPaginated(content: ChapterContent) {
        binding.scrollView.visibility = View.GONE
        binding.tvPageText.visibility = View.VISIBLE
        binding.btnPrev.visibility = View.VISIBLE
        binding.btnNext.visibility = View.VISIBLE

        val sb = StringBuilder()
        if (content.title.isNotEmpty()) sb.append(content.title).append("\n\n")
        for (p in content.paragraphs) {
            sb.append("    ").append(p).append("\n\n")
        }
        if (content.authorSpeak.isNotEmpty()) {
            sb.append("\n【作者的话】\n").append(content.authorSpeak)
        }
        pages = paginate(sb.toString())
        currentPage = 0
        showPage()
    }

    private fun paginate(text: String): List<CharSequence> {
        val prefs = App.instance.prefs
        val paint = TextPaint().apply {
            color = 0xFF000000.toInt()
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, prefs.fontSize.toFloat(), resources.displayMetrics
            )
            typeface = android.graphics.Typeface.create(EinkUtils.fontFamilyToStack(prefs.fontFamily), android.graphics.Typeface.NORMAL)
        }
        val width = binding.tvPageText.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels - 64
        val fullLayout = StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, prefs.lineHeight, 0f, false)
        val lineHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent) * prefs.lineHeight
        val pageHeight = binding.tvPageText.height.takeIf { it > 0 }
            ?: (resources.displayMetrics.heightPixels - 200)
        val linesPerPage = (pageHeight / lineHeight).toInt().coerceAtLeast(1)

        val result = mutableListOf<CharSequence>()
        var startLine = 0
        while (startLine < fullLayout.lineCount) {
            val endLine = minOf(startLine + linesPerPage, fullLayout.lineCount)
            val start = fullLayout.getLineStart(startLine)
            val end = fullLayout.getLineEnd(endLine - 1)
            result.add(text.substring(start, end))
            startLine = endLine
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    private fun showPage() {
        if (pages.isEmpty()) return
        val tv = binding.tvPageText
        applyTextStyle(tv)
        tv.text = pages[currentPage]
        binding.tvPageInfo.text = "${chapterIdx + 1}/${chapters.size} · ${currentPage + 1}/${pages.size}"
    }

    private fun applyTextStyle(tv: TextView) {
        val prefs = App.instance.prefs
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.fontSize.toFloat())
        tv.typeface = android.graphics.Typeface.create(EinkUtils.fontFamilyToStack(prefs.fontFamily), android.graphics.Typeface.NORMAL)
        tv.setLineSpacing(0f, prefs.lineHeight)
        tv.setTextColor(0xFF000000.toInt())
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP && App.instance.prefs.readMode == "page") {
            val w = resources.displayMetrics.widthPixels
            val x = ev.x.toInt()
            when {
                x < w / 3 -> { if (currentPage > 0) { currentPage--; showPage() } else if (chapterIdx > 0) { chapterIdx--; loadChapter() } }
                x > w * 2 / 3 -> { if (currentPage < pages.size - 1) { currentPage++; showPage() } else if (chapterIdx < chapters.size - 1) { chapterIdx++; loadChapter() } }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun saveProgress(ch: ChapterInfo) {
        lifecycleScope.launch {
            App.instance.db.progressDao().upsert(ProgressEntity(
                bookId = bookId,
                chapterIdx = chapterIdx,
                chapterId = ch.chapterId,
                chapterName = ch.name,
            ))
            if (App.instance.prefs.isLoggedIn) {
                try { App.instance.api.updateProgress(bookId, ch.chapterId, chapterIdx) } catch (_: Exception) {}
            }
        }
    }
}
