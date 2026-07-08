package com.momijineko.fanqie.ui

import android.os.Bundle
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.R
import com.momijineko.fanqie.data.api.ChapterContent
import com.momijineko.fanqie.data.api.ChapterInfo
import com.momijineko.fanqie.data.api.ParaComment
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
    private var currentContent: ChapterContent? = null
    private var paraCommentCounts: Map<Int, Int> = emptyMap()
    private var toolbarVisible = true
    private val chapterListAdapter = ChapterAdapter { idx ->
        chapterIdx = idx
        binding.chapterPanel.visibility = View.GONE
        loadChapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EinkUtils.applyEinkOptimizations(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookId = intent.getStringExtra("book_id") ?: run { finish(); return }
        chapterIdx = intent.getIntExtra("chapter_idx", 0)

        binding.btnPrev.setOnClickListener { prevChapter() }
        binding.btnNext.setOnClickListener { nextChapter() }
        binding.btnChapters.setOnClickListener {
            binding.chapterPanel.visibility =
                if (binding.chapterPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            binding.settingsPanel.visibility = View.GONE
        }
        binding.btnSettings.setOnClickListener {
            binding.settingsPanel.visibility =
                if (binding.settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            binding.chapterPanel.visibility = View.GONE
            updateSettingsPanel()
        }

        binding.rvChapterList.layoutManager = LinearLayoutManager(this)
        binding.rvChapterList.adapter = chapterListAdapter
        EinkUtils.disableRecyclerViewAnimation(binding.rvChapterList)

        setupSettingsPanel()

        applyTheme()
        loadChapterList()
    }

    private fun setupSettingsPanel() {
        binding.btnFontMinus.setOnClickListener {
            val prefs = App.instance.prefs
            prefs.fontSize = (prefs.fontSize - 1).coerceIn(14, 28)
            binding.tvFontSize.text = "${prefs.fontSize}"
            rerender()
        }
        binding.btnFontPlus.setOnClickListener {
            val prefs = App.instance.prefs
            prefs.fontSize = (prefs.fontSize + 1).coerceIn(14, 28)
            binding.tvFontSize.text = "${prefs.fontSize}"
            rerender()
        }

        binding.btnLineMinus.setOnClickListener {
            val prefs = App.instance.prefs
            prefs.lineHeight = ((prefs.lineHeight * 10 - 1).toInt() / 10f).coerceIn(1.4f, 2.4f)
            updateLineHeightDisplay()
            rerender()
        }
        binding.btnLinePlus.setOnClickListener {
            val prefs = App.instance.prefs
            prefs.lineHeight = ((prefs.lineHeight * 10 + 1).toInt() / 10f).coerceIn(1.4f, 2.4f)
            updateLineHeightDisplay()
            rerender()
        }

        val fontChips = mapOf(
            "sans" to binding.chipFontSans,
            "serif" to binding.chipFontSerif,
            "kai" to binding.chipFontKai,
        )
        for ((family, chip) in fontChips) {
            chip.setOnClickListener {
                App.instance.prefs.fontFamily = family
                updateFontChips()
                applyTheme()
                rerender()
            }
        }

        val modeChips = mapOf(
            "page" to binding.chipModePage,
            "scroll" to binding.chipModeScroll,
            "no-anim" to binding.chipModeNoanim,
        )
        for ((mode, chip) in modeChips) {
            chip.setOnClickListener {
                App.instance.prefs.readMode = mode
                updateModeChips()
                rerender()
            }
        }

        val themeChips = mapOf(
            "default" to binding.chipThemeDefault,
            "sepia" to binding.chipThemeSepia,
            "green" to binding.chipThemeGreen,
            "dark" to binding.chipThemeDark,
            "eink" to binding.chipThemeEink,
        )
        for ((theme, chip) in themeChips) {
            chip.setOnClickListener {
                App.instance.prefs.readingTheme = theme
                updateThemeChips()
                applyTheme()
                EinkUtils.forceFullRefresh(this)
                rerender()
            }
        }
    }

    private fun updateSettingsPanel() {
        binding.tvFontSize.text = "${App.instance.prefs.fontSize}"
        updateLineHeightDisplay()
        updateFontChips()
        updateModeChips()
        updateThemeChips()
    }

    private fun updateLineHeightDisplay() {
        binding.tvLineHeight.text = String.format("%.1f", App.instance.prefs.lineHeight)
    }

    private fun updateFontChips() {
        val current = App.instance.prefs.fontFamily
        binding.chipFontSans.isSelected = current == "sans"
        binding.chipFontSerif.isSelected = current == "serif"
        binding.chipFontKai.isSelected = current == "kai"
    }

    private fun updateModeChips() {
        val current = App.instance.prefs.readMode
        binding.chipModePage.isSelected = current == "page"
        binding.chipModeScroll.isSelected = current == "scroll"
        binding.chipModeNoanim.isSelected = current == "no-anim"
    }

    private fun updateThemeChips() {
        val current = App.instance.prefs.readingTheme
        binding.chipThemeDefault.isSelected = current == "default"
        binding.chipThemeSepia.isSelected = current == "sepia"
        binding.chipThemeGreen.isSelected = current == "green"
        binding.chipThemeDark.isSelected = current == "dark"
        binding.chipThemeEink.isSelected = current == "eink"
    }

    private fun applyTheme() {
        val themeId = App.instance.prefs.readingTheme
        EinkUtils.applyThemeColors(binding.root, themeId)
        EinkUtils.applyThemeColors(binding.tvScrollText, themeId)
        EinkUtils.applyThemeColors(binding.tvPageText, themeId)
        EinkUtils.applyThemeColors(binding.tvChapterTitle, themeId)
        EinkUtils.applyThemeColors(binding.tvPageInfo, themeId)
    }

    private fun loadChapterList() {
        lifecycleScope.launch {
            try {
                chapters = App.instance.api.chapters(bookId)
                if (chapterIdx >= chapters.size) chapterIdx = 0
                chapterListAdapter.submit(chapters, chapterIdx)
                loadChapter()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "加载章节列表失败", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun loadChapter() {
        if (chapters.isEmpty()) return
        val ch = chapters[chapterIdx]
        binding.tvChapterTitle.text = ch.name
        binding.tvPageInfo.text = "加载中..."
        binding.progressBar.visibility = View.VISIBLE

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
                    Snackbar.make(binding.root, "加载失败", Snackbar.LENGTH_SHORT).show()
                }
            }

            binding.progressBar.visibility = View.GONE

            if (content != null) {
                currentContent = content
                loadParaCommentCounts(ch.chapterId)
                render(content!!)
                saveProgress(ch)
                chapterListAdapter.submit(chapters, chapterIdx)
            }
        }
    }

    private fun loadParaCommentCounts(chapterId: String) {
        lifecycleScope.launch {
            try {
                paraCommentCounts = App.instance.api.getParaCommentCounts(chapterId, bookId)
            } catch (_: Exception) {
                paraCommentCounts = emptyMap()
            }
        }
    }

    private fun render(content: ChapterContent) {
        val mode = App.instance.prefs.readMode
        if (mode == "page") {
            renderPaginated(content)
        } else {
            // scroll and no-anim both use scroll rendering; no-anim disables smooth scroll
            renderScroll(content)
        }
    }

    private fun buildText(content: ChapterContent): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        if (content.title.isNotEmpty()) {
            sb.append(content.title).append("\n\n")
        }
        for (i in content.paragraphs.indices) {
            sb.append("    ").append(content.paragraphs[i])
            val count = paraCommentCounts[i]
            if (count != null && count > 0) {
                val badgeStart = sb.length
                sb.append("  [$count]")
                sb.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        showParaComments(i)
                    }
                }, badgeStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append("\n\n")
        }
        if (content.authorSpeak.isNotEmpty()) {
            sb.append("\n【作者的话】\n").append(content.authorSpeak)
        }
        return sb
    }

    private fun renderScroll(content: ChapterContent) {
        binding.scrollView.visibility = View.VISIBLE
        binding.tvPageText.visibility = View.GONE

        val sb = buildText(content)
        val tv = binding.tvScrollText
        applyTextStyle(tv)
        tv.text = sb
        binding.scrollView.scrollTo(0, 0)
        binding.tvPageInfo.text = "${chapterIdx + 1}/${chapters.size}"
        EinkUtils.forceFullRefresh(binding.scrollView)
    }

    private fun renderPaginated(content: ChapterContent) {
        binding.scrollView.visibility = View.GONE
        binding.tvPageText.visibility = View.VISIBLE

        val sb = buildText(content)
        pages = paginate(sb)
        currentPage = 0
        showPage()
    }

    private fun paginate(text: CharSequence): List<CharSequence> {
        val prefs = App.instance.prefs
        val paint = TextPaint().apply {
            val colors = EinkUtils.themeColors(prefs.readingTheme)
            color = colors.fg
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, prefs.fontSize.toFloat(), resources.displayMetrics
            )
            typeface = android.graphics.Typeface.create(
                EinkUtils.fontFamilyToStack(prefs.fontFamily), android.graphics.Typeface.NORMAL
            )
        }
        val width = binding.tvPageText.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels - 64
        val fullLayout = StaticLayout(
            text, paint, width, Layout.Alignment.ALIGN_NORMAL, prefs.lineHeight, 0f, false
        )
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
            result.add(text.subSequence(start, end))
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
        EinkUtils.forceFullRefresh(tv)
    }

    private fun applyTextStyle(tv: TextView) {
        val prefs = App.instance.prefs
        val colors = EinkUtils.themeColors(prefs.readingTheme)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.fontSize.toFloat())
        tv.typeface = android.graphics.Typeface.create(
            EinkUtils.fontFamilyToStack(prefs.fontFamily), android.graphics.Typeface.NORMAL
        )
        tv.setLineSpacing(0f, prefs.lineHeight)
        tv.setTextColor(colors.fg)
    }

    private fun rerender() {
        val content = currentContent ?: return
        render(content)
        EinkUtils.forceFullRefresh(this)
    }

    private fun prevChapter() {
        if (chapterIdx > 0) {
            chapterIdx--
            loadChapter()
        }
    }

    private fun nextChapter() {
        if (chapterIdx < chapters.size - 1) {
            chapterIdx++
            loadChapter()
        }
    }

    private fun toggleToolbar() {
        toolbarVisible = !toolbarVisible
        binding.pageToolbar.visibility = if (toolbarVisible) View.VISIBLE else View.GONE
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP && App.instance.prefs.readMode == "page") {
            val w = resources.displayMetrics.widthPixels
            val x = ev.x.toInt()
            when {
                x < w / 3 -> {
                    if (currentPage > 0) { currentPage--; showPage() }
                    else prevChapter()
                }
                x > w * 2 / 3 -> {
                    if (currentPage < pages.size - 1) { currentPage++; showPage() }
                    else nextChapter()
                }
                else -> toggleToolbar()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun showParaComments(paragraphIdx: Int) {
        val content = currentContent ?: return
        val ch = chapters.getOrNull(chapterIdx) ?: return
        val dialog = BottomSheetDialog(this)
        val tv = TextView(this)
        tv.setPadding(48, 48, 48, 48)
        tv.text = "加载中..."
        dialog.setContentView(tv)
        dialog.show()

        lifecycleScope.launch {
            try {
                val comments: List<ParaComment> =
                    App.instance.api.getParaComments(ch.chapterId, paragraphIdx, bookId)
                val sb = StringBuilder()
                if (comments.isEmpty()) {
                    sb.append("暂无评论")
                } else {
                    for (c in comments) {
                        sb.append("${c.userName}: ${c.content}")
                        if (c.diggCount > 0) sb.append("  👍${c.diggCount}")
                        sb.append("\n")
                        for (r in c.replies) {
                            sb.append("  └ ${r.userName}: ${r.content}\n")
                        }
                        sb.append("\n")
                    }
                }
                tv.text = sb.toString()
            } catch (e: Exception) {
                tv.text = "加载失败: ${e.message}"
            }
        }
    }

    private fun saveProgress(ch: ChapterInfo) {
        lifecycleScope.launch {
            App.instance.db.progressDao().upsert(ProgressEntity(
                bookId = bookId,
                chapterIdx = chapterIdx,
                chapterId = ch.chapterId,
                chapterName = ch.name,
                totalChapters = chapters.size,
            ))
            if (App.instance.prefs.isLoggedIn) {
                try { App.instance.api.updateProgress(bookId, ch.chapterId, chapterIdx) } catch (_: Exception) {}
            }
        }
    }
}
