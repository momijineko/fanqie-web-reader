package com.momijineko.fanqie.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.R
import com.momijineko.fanqie.data.api.BookInfo
import com.momijineko.fanqie.databinding.FragmentSearchBinding
import com.momijineko.fanqie.util.EinkUtils
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val adapter = BookAdapter({ book -> openDetail(book.bookId) }, BookAdapter.Mode.LIST)

    private val tabs = listOf(
        TabData(3, "小说"),
        TabData(2, "听书"),
        TabData(8, "漫画"),
        TabData(11, "短剧"),
    )

    private var currentTab = 3
    private var searchKey = ""
    private var allResults = mutableListOf<BookInfo>()
    private var filteredResults = mutableListOf<BookInfo>()
    private var offset = 0
    private var hasMore = false
    private var loading = false
    private var sortBy = "default"
    private var tagFilter = ""

    private data class TabData(val id: Int, val name: String)

    private var sortControls: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvBooks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBooks.adapter = adapter
        EinkUtils.disableRecyclerViewAnimation(binding.rvBooks)

        setupTabs()
        setupScrollListener()

        binding.btnSearch.setOnClickListener {
            val key = binding.etSearch.text.toString().trim()
            if (key.isNotEmpty()) doSearch(key)
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val key = binding.etSearch.text.toString().trim()
                if (key.isNotEmpty()) doSearch(key)
                true
            } else false
        }

        showHome()
    }

    private fun setupTabs() {
        binding.tabContainer.removeAllViews()
        val dp = resources.displayMetrics.density
        for (tab in tabs) {
            val tv = TextView(requireContext())
            tv.text = tab.name
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val pad = (dp * 12).toInt()
            tv.setPadding(pad, (dp * 8).toInt(), pad, (dp * 8).toInt())
            val isActive = tab.id == currentTab
            val isEnabled = tab.id == 3
            tv.isEnabled = isEnabled
            if (isActive && isEnabled) {
                tv.setTextColor(requireContext().getColor(R.color.white))
                tv.setBackgroundResource(R.color.accent)
                tv.setTypeface(null, Typeface.BOLD)
            } else if (isEnabled) {
                tv.setTextColor(requireContext().getColor(R.color.text_primary))
                tv.setBackgroundResource(R.color.bg)
            } else {
                tv.setTextColor(requireContext().getColor(R.color.text_muted))
                tv.setBackgroundResource(R.color.bg)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.marginEnd = (dp * 8).toInt()
            tv.layoutParams = lp
            if (isEnabled) {
                tv.setOnClickListener {
                    currentTab = tab.id
                    App.instance.prefs.searchTab = tab.id
                    setupTabs()
                    if (allResults.isNotEmpty()) {
                        applyFilters()
                        renderResults()
                    } else {
                        showHome()
                    }
                }
            }
            binding.tabContainer.addView(tv)
        }
    }

    private fun setupScrollListener() {
        binding.rvBooks.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                val last = lm.findLastVisibleItemPosition()
                val total = lm.itemCount
                if (hasMore && !loading && last >= total - 2) {
                    loadMore()
                }
            }
        })
    }

    private fun showHome() {
        binding.homeContainer.visibility = View.VISIBLE
        binding.rvBooks.visibility = View.GONE
        sortControls?.visibility = View.GONE
        buildHomeContent()
    }

    private fun showResults() {
        binding.homeContainer.visibility = View.GONE
        binding.rvBooks.visibility = View.VISIBLE
        ensureSortControls()
        sortControls?.visibility = View.VISIBLE
    }

    private fun buildHomeContent() {
        val container = binding.homeContainer
        container.removeAllViews()
        val dp = resources.displayMetrics.density

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (dp * 16).toInt()
            setPadding(pad, pad, pad, pad)
        }

        lifecycleScope.launch {
            val progresses = App.instance.db.progressDao().getAll()
            if (progresses.isNotEmpty()) {
                val rh = progresses[0]
                val book = App.instance.db.bookDao().getById(rh.bookId)
                val name = book?.name ?: "未知书名"
                val pct = if (rh.totalChapters > 0) (rh.chapterIdx + 1) * 100 / rh.totalChapters else 0
                val card = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((dp * 16).toInt(), (dp * 12).toInt(), (dp * 16).toInt(), (dp * 12).toInt())
                    setBackgroundResource(R.color.card_bg)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    lp.bottomMargin = (dp * 16).toInt()
                    layoutParams = lp
                }
                val info = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                info.addView(TextView(requireContext()).apply {
                    text = "继续阅读"
                    textSize = 12f
                    setTextColor(requireContext().getColor(R.color.text_muted))
                })
                info.addView(TextView(requireContext()).apply {
                    text = name
                    textSize = 16f
                    setTextColor(requireContext().getColor(R.color.text_primary))
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                })
                info.addView(TextView(requireContext()).apply {
                    text = rh.chapterName
                    textSize = 13f
                    setTextColor(requireContext().getColor(R.color.text_secondary))
                    maxLines = 1
                })
                card.addView(info)
                card.addView(TextView(requireContext()).apply {
                    text = "${pct}%"
                    textSize = 18f
                    setTextColor(requireContext().getColor(R.color.accent))
                    setTypeface(null, Typeface.BOLD)
                })
                card.setOnClickListener {
                    startActivity(Intent(requireContext(), ReaderActivity::class.java)
                        .putExtra("book_id", rh.bookId)
                        .putExtra("chapter_idx", rh.chapterIdx))
                }
                root.addView(card)
            }

            val welcomeTitle = TextView(requireContext()).apply {
                text = "发现好书"
                textSize = 20f
                setTextColor(requireContext().getColor(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.bottomMargin = (dp * 4).toInt()
                layoutParams = lp
            }
            root.addView(welcomeTitle)

            val welcomeDesc = TextView(requireContext()).apply {
                text = "搜索书名或作者，找到你的下一本读物"
                textSize = 13f
                setTextColor(requireContext().getColor(R.color.text_muted))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.bottomMargin = (dp * 16).toInt()
                layoutParams = lp
            }
            root.addView(welcomeDesc)

            val sectionTitle = TextView(requireContext()).apply {
                text = "热门分类"
                textSize = 15f
                setTextColor(requireContext().getColor(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.bottomMargin = (dp * 8).toInt()
                layoutParams = lp
            }
            root.addView(sectionTitle)

            val genreTags = listOf(
                "热门" to "🔥", "玄幻" to "🗡️", "都市" to "🏙️", "言情" to "💕",
                "仙侠" to "🔮", "游戏" to "🎮", "历史" to "🏰", "科幻" to "🔬",
            )
            val tagRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.bottomMargin = (dp * 16).toInt()
                layoutParams = lp
            }
            for ((name, icon) in genreTags) {
                val tag = TextView(requireContext()).apply {
                    text = "$icon $name"
                    textSize = 13f
                    setTextColor(requireContext().getColor(R.color.text_primary))
                    setBackgroundResource(R.color.card_bg)
                    val pad = (dp * 10).toInt()
                    setPadding(pad, (dp * 6).toInt(), pad, (dp * 6).toInt())
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    lp.marginEnd = (dp * 8).toInt()
                    layoutParams = lp
                    setOnClickListener {
                        binding.etSearch.setText(name)
                        doSearch(name)
                    }
                }
                tagRow.addView(tag)
            }
            val tagScroll = HorizontalScrollView(requireContext()).apply {
                isHorizontalScrollBarEnabled = false
                addView(tagRow)
            }
            root.addView(tagScroll)

            val quickTitle = TextView(requireContext()).apply {
                text = "探索发现"
                textSize = 15f
                setTextColor(requireContext().getColor(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.bottomMargin = (dp * 8).toInt()
                layoutParams = lp
            }
            root.addView(quickTitle)

            val quickCats = listOf(
                "都市小说" to "都市", "玄幻奇幻" to "玄幻", "仙侠修真" to "仙侠",
                "历史军事" to "历史", "游戏竞技" to "游戏", "科幻世界" to "科幻",
                "悬疑灵异" to "悬疑", "古代言情" to "言情",
            )
            val grid = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
            }
            for (i in quickCats.indices step 2) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    weightSum = 2f
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    lp.bottomMargin = (dp * 8).toInt()
                    layoutParams = lp
                }
                for (j in 0..1) {
                    if (i + j < quickCats.size) {
                        val (name, q) = quickCats[i + j]
                        val card = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setBackgroundResource(R.color.card_bg)
                            val pad = (dp * 16).toInt()
                            setPadding(pad, (dp * 16).toInt(), pad, (dp * 16).toInt())
                            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            lp.marginEnd = if (j == 0) (dp * 8).toInt() else 0
                            lp.marginStart = if (j == 1) (dp * 8).toInt() else 0
                            layoutParams = lp
                            setOnClickListener {
                                binding.etSearch.setText(q)
                                doSearch(q)
                            }
                        }
                        card.addView(TextView(requireContext()).apply {
                            text = name
                            textSize = 14f
                            setTextColor(requireContext().getColor(R.color.text_primary))
                            setTypeface(null, Typeface.BOLD)
                        })
                        row.addView(card)
                    }
                }
                grid.addView(row)
            }
            root.addView(grid)

            val history = App.instance.db.searchHistoryDao().getRecent(6)
            if (history.isNotEmpty()) {
                val histTitle = TextView(requireContext()).apply {
                    text = "搜索历史"
                    textSize = 15f
                    setTextColor(requireContext().getColor(R.color.text_primary))
                    setTypeface(null, Typeface.BOLD)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    lp.topMargin = (dp * 16).toInt()
                    lp.bottomMargin = (dp * 8).toInt()
                    layoutParams = lp
                }
                root.addView(histTitle)

                val histRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                for (h in history) {
                    val tag = TextView(requireContext()).apply {
                        text = h.keyword
                        textSize = 13f
                        setTextColor(requireContext().getColor(R.color.text_secondary))
                        setBackgroundResource(R.color.card_bg)
                        val pad = (dp * 10).toInt()
                        setPadding(pad, (dp * 6).toInt(), pad, (dp * 6).toInt())
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                        lp.marginEnd = (dp * 8).toInt()
                        layoutParams = lp
                        setOnClickListener {
                            binding.etSearch.setText(h.keyword)
                            doSearch(h.keyword)
                        }
                    }
                    histRow.addView(tag)
                }
                val histScroll = HorizontalScrollView(requireContext()).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(histRow)
                }
                root.addView(histScroll)
            }

            container.addView(root)
        }
    }

    private fun ensureSortControls() {
        if (sortControls != null) return
        val dp = resources.displayMetrics.density
        val controls = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = (dp * 12).toInt()
            setPadding(pad, (dp * 6).toInt(), pad, (dp * 6).toInt())
            setBackgroundResource(R.color.card_bg)
        }

        val sorts = listOf(
            "default" to "默认", "read" to "热度",
            "words" to "字数", "chapters" to "章节",
        )
        for ((key, label) in sorts) {
            val tv = TextView(requireContext())
            tv.text = label
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val pad = (dp * 8).toInt()
            tv.setPadding(pad, (dp * 4).toInt(), pad, (dp * 4).toInt())
            tv.tag = "sort_$key"
            updateSortBtnStyle(tv, key == sortBy)
            tv.setOnClickListener {
                sortBy = if (sortBy == key) "default" else key
                for (i in 0 until controls.childCount) {
                    val child = controls.getChildAt(i)
                    if (child is TextView && child.tag is String && (child.tag as String).startsWith("sort_")) {
                        val childKey = (child.tag as String).removePrefix("sort_")
                        updateSortBtnStyle(child, childKey == sortBy)
                    }
                }
                applyFilters()
                renderResults()
                updateTagFilters(controls, dp)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.marginEnd = (dp * 6).toInt()
            tv.layoutParams = lp
            controls.addView(tv)
        }

        sortControls = controls
        val parent = binding.rvBooks.parent as ViewGroup
        val idx = parent.indexOfChild(binding.rvBooks)
        parent.addView(controls, idx)
        updateTagFilters(controls, dp)
    }

    private fun updateTagFilters(parent: LinearLayout, dp: Float) {
        // Remove old tag chips (tag starts with "tag_")
        val toRemove = mutableListOf<View>()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is TextView && child.tag is String && (child.tag as String).startsWith("tag_")) {
                toRemove.add(child)
            }
        }
        for (v in toRemove) parent.removeView(v)

        // Extract unique tags from results
        val allTags = mutableSetOf<String>()
        for (b in allResults) {
            val tags = b.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            allTags.addAll(tags)
        }
        if (allTags.isEmpty()) return

        // Add a separator
        val sep = TextView(requireContext()).apply {
            text = "|"
            setTextColor(requireContext().getColor(R.color.text_muted))
            val pad = (dp * 4).toInt()
            setPadding(pad, 0, pad, 0)
            tag = "tag_sep"
        }
        parent.addView(sep)

        for (tag in allTags) {
            val chip = TextView(requireContext())
            chip.text = tag
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val pad = (dp * 6).toInt()
            chip.setPadding(pad, (dp * 3).toInt(), pad, (dp * 3).toInt())
            chip.tag = "tag_$tag"
            val isActive = tagFilter == tag
            if (isActive) {
                chip.setTextColor(requireContext().getColor(R.color.white))
                chip.setBackgroundResource(R.color.accent)
            } else {
                chip.setTextColor(requireContext().getColor(R.color.text_secondary))
                chip.setBackgroundResource(R.color.bg)
            }
            chip.setOnClickListener {
                tagFilter = if (tagFilter == tag) "" else tag
                applyFilters()
                renderResults()
                updateTagFilters(parent, dp)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.marginEnd = (dp * 4).toInt()
            chip.layoutParams = lp
            parent.addView(chip)
        }
    }

    private fun updateSortBtnStyle(tv: TextView, active: Boolean) {
        if (active) {
            tv.setTextColor(requireContext().getColor(R.color.white))
            tv.setBackgroundResource(R.color.accent)
            tv.setTypeface(null, Typeface.BOLD)
        } else {
            tv.setTextColor(requireContext().getColor(R.color.text_secondary))
            tv.setBackgroundResource(R.color.bg)
            tv.setTypeface(null, Typeface.NORMAL)
        }
    }

    private fun doSearch(key: String) {
        searchKey = key
        allResults.clear()
        filteredResults.clear()
        offset = 0
        hasMore = false
        sortBy = "default"
        tagFilter = ""

        binding.progressBar.visibility = View.VISIBLE
        showResults()

        lifecycleScope.launch {
            try {
                App.instance.db.searchHistoryDao().upsert(
                    com.momijineko.fanqie.data.db.SearchHistoryEntity(keyword = key)
                )
            } catch (_: Exception) {}

            loading = true
            try {
                val results = App.instance.api.search(key, currentTab, 0)
                allResults.addAll(results)
                hasMore = results.size >= 10
                applyFilters()
                renderResults()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "搜索失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
            loading = false
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        offset += 10
        lifecycleScope.launch {
            try {
                val more = App.instance.api.search(searchKey, currentTab, offset)
                allResults.addAll(more)
                hasMore = more.size >= 10
                applyFilters()
                renderResults()
            } catch (e: Exception) {
                hasMore = false
            }
            loading = false
        }
    }

    private fun applyFilters() {
        var list = allResults.toList()
        if (tagFilter.isNotEmpty()) {
            list = list.filter { it.tags.lowercase().contains(tagFilter.lowercase()) }
        }
        when (sortBy) {
            "read" -> list = list.sortedByDescending { it.readCount }
            "words" -> list = list.sortedByDescending { it.wordCount }
            "chapters" -> list = list.sortedByDescending { it.chapterCount.toIntOrNull() ?: 0 }
        }
        filteredResults = list.toMutableList()
    }

    private fun renderResults() {
        adapter.submit(filteredResults)
        if (filteredResults.isEmpty() && !loading) {
            Snackbar.make(binding.root, "未找到结果", Snackbar.LENGTH_SHORT).show()
            showHome()
        }
    }

    private fun openDetail(bookId: String) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).putExtra("book_id", bookId))
    }

    override fun onResume() {
        super.onResume()
        if (searchKey.isEmpty()) showHome()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
