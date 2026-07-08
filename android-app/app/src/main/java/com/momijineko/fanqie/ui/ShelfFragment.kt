package com.momijineko.fanqie.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.momijineko.fanqie.App
import com.momijineko.fanqie.R
import com.momijineko.fanqie.data.api.BookInfo
import com.momijineko.fanqie.data.api.BookshelfItem
import com.momijineko.fanqie.data.db.BookEntity
import com.momijineko.fanqie.data.db.ProgressEntity
import com.momijineko.fanqie.databinding.FragmentShelfBinding
import com.momijineko.fanqie.util.EinkUtils
import kotlinx.coroutines.launch

class ShelfFragment : Fragment() {
    private var _binding: FragmentShelfBinding? = null
    private val binding get() = _binding!!

    private var allBooks = listOf<BookInfo>()
    private var shelfMap = mutableMapOf<String, BookshelfItem>()
    private var progressMap = mutableMapOf<String, ProgressEntity>()
    private var shelfFilter = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShelfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvShelf.layoutManager = GridLayoutManager(requireContext(), 3)
        EinkUtils.disableRecyclerViewAnimation(binding.rvShelf)

        addSearchBar()
        binding.swipeRefresh.setOnRefreshListener { loadShelf() }
        binding.btnGoSearch.setOnClickListener {
            binding.swipeRefresh.isRefreshing = false
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        loadShelf()
    }

    private var shelfSearchEt: EditText? = null

    private fun addSearchBar() {
        val rootView = binding.root
        if (rootView is ViewGroup) {
            val dp = resources.displayMetrics.density
            val et = EditText(requireContext()).apply {
                hint = "搜索书架..."
                textSize = 14f
                setBackgroundResource(R.drawable.bg_search_bar)
                val pad = (dp * 12).toInt()
                setPadding(pad, (dp * 8).toInt(), pad, (dp * 8).toInt())
                maxLines = 1
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) {
                        setFilter(s?.toString() ?: "")
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            }
            val lp = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            val margin = (dp * 8).toInt()
            (lp as? LinearLayout.LayoutParams)?.setMargins(margin, margin, margin, 0)
            et.layoutParams = lp
            rootView.addView(et, 0)
            shelfSearchEt = et
        }
    }

    private fun loadShelf() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        lifecycleScope.launch {
            val progresses = App.instance.db.progressDao().getAll()
            progressMap.clear()
            progressMap.putAll(progresses.associateBy { it.bookId })

            if (App.instance.prefs.isLoggedIn) {
                try {
                    val shelf = App.instance.api.getBookshelf()
                    shelfMap.clear()
                    shelfMap.putAll(shelf.associateBy { it.bookId })
                    allBooks = shelf.map { it.toBookInfo() }
                } catch (e: Exception) {
                    loadLocalShelf()
                    Snackbar.make(binding.root, "云端同步失败，显示本地书架", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                loadLocalShelf()
            }

            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            renderShelf()
        }
    }

    private suspend fun loadLocalShelf() {
        val books = App.instance.db.bookDao().getAll()
        shelfMap.clear()
        allBooks = books.map { it.toBookInfo() }
    }

    private fun renderShelf() {
        val filtered = if (shelfFilter.isEmpty()) allBooks
        else allBooks.filter {
            it.name.contains(shelfFilter, ignoreCase = true) ||
            it.author.contains(shelfFilter, ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            binding.rvShelf.adapter = null
            binding.emptyView.visibility = View.VISIBLE
            return
        }
        binding.emptyView.visibility = View.GONE

        val groups = shelfMap.values
            .filter { it.groupId.isNotEmpty() }
            .groupBy { it.groupId }

        val concat = ConcatAdapter()
        val lm = binding.rvShelf.layoutManager as GridLayoutManager

        if (groups.isNotEmpty() && App.instance.prefs.isLoggedIn) {
            val filteredIds = filtered.map { it.bookId }.toSet()
            val ungrouped = filtered.filter { book ->
                shelfMap[book.bookId]?.groupId.isNullOrEmpty()
            }
            val headerPositions = mutableSetOf<Int>()
            var pos = 0
            for ((_, items) in groups) {
                val groupBooks = items.map { it.toBookInfo() }
                    .filter { it.bookId in filteredIds }
                if (groupBooks.isNotEmpty()) {
                    val groupName = items.firstOrNull()?.groupName?.ifEmpty { "未分组" } ?: "未分组"
                    val headerAdapter = HeaderAdapter(groupName)
                    val bookAdapter = createBookAdapter(groupBooks)
                    concat.addAdapter(headerAdapter)
                    headerPositions.add(pos)
                    pos += 1
                    concat.addAdapter(bookAdapter)
                    pos += groupBooks.size
                }
            }
            if (ungrouped.isNotEmpty()) {
                val headerAdapter = HeaderAdapter("未分组")
                val bookAdapter = createBookAdapter(ungrouped)
                concat.addAdapter(headerAdapter)
                headerPositions.add(pos)
                pos += 1
                concat.addAdapter(bookAdapter)
                pos += ungrouped.size
            }
            lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (position in headerPositions) 3 else 1
                }
            }
        } else {
            val bookAdapter = createBookAdapter(filtered)
            concat.addAdapter(bookAdapter)
            lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = 1
            }
        }

        binding.rvShelf.adapter = concat
    }

    private fun createBookAdapter(books: List<BookInfo>): BookAdapter {
        val adapter = BookAdapter(
            onClick = { book -> openDetail(book.bookId) },
            mode = BookAdapter.Mode.GRID,
            onLongClick = { book -> showBookActions(book) },
        )
        adapter.submit(books)
        adapter.setProgress(progressMap)
        adapter.setShelfData(shelfMap)
        return adapter
    }

    private fun showBookActions(book: BookInfo) {
        val options = mutableListOf<String>()
        val currentItem = shelfMap[book.bookId]
        val currentGroupId = currentItem?.groupId ?: ""
        val currentGroupName = currentItem?.groupName ?: ""

        if (App.instance.prefs.isLoggedIn) {
            options.add("移动到分组")
            options.add("新建分组")
            if (currentGroupId.isNotEmpty()) {
                options.add("移出「$currentGroupName」")
            }
            options.add("解散分组")
        }
        options.add("移出书架")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(book.name)
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    options[which] == "移动到分组" -> showGroupPicker(book)
                    options[which] == "新建分组" -> showNewGroupDialog(book)
                    options[which].startsWith("移出「") -> moveBookToGroup(book, "", "")
                    options[which] == "解散分组" -> showDissolveGroupPicker()
                    options[which] == "移出书架" -> removeFromShelf(book)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDissolveGroupPicker() {
        val groups = shelfMap.values
            .filter { it.groupId.isNotEmpty() }
            .map { it.groupId to (it.groupName.ifEmpty { "未分组" }) }
            .distinctBy { it.first }

        if (groups.isEmpty()) {
            Snackbar.make(binding.root, "没有可解散的分组", Snackbar.LENGTH_SHORT).show()
            return
        }

        val names = groups.map { it.second }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("解散分组")
            .setItems(names.toTypedArray()) { _, which ->
                val (groupId, _) = groups[which]
                dissolveGroup(groupId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dissolveGroup(groupId: String) {
        lifecycleScope.launch {
            try {
                val booksInGroup = shelfMap.values.filter { it.groupId == groupId }
                for (item in booksInGroup) {
                    if (App.instance.prefs.isLoggedIn) {
                        App.instance.api.moveToGroup(item.bookId, "", "")
                    }
                }
                Snackbar.make(binding.root, "分组已解散", Snackbar.LENGTH_SHORT).show()
                loadShelf()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "操作失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun moveBookToGroup(book: BookInfo, groupId: String, groupName: String) {
        lifecycleScope.launch {
            try {
                if (App.instance.prefs.isLoggedIn) {
                    App.instance.api.moveToGroup(book.bookId, groupId, groupName)
                }
                Snackbar.make(binding.root, "已移动", Snackbar.LENGTH_SHORT).show()
                loadShelf()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "移动失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showGroupPicker(book: BookInfo) {
        val existingGroups = shelfMap.values
            .filter { it.groupId.isNotEmpty() }
            .map { it.groupId to (it.groupName.ifEmpty { "未分组" }) }
            .distinctBy { it.first }

        if (existingGroups.isEmpty()) {
            showNewGroupDialog(book)
            return
        }

        val names = existingGroups.map { it.second }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择分组")
            .setItems(names.toTypedArray()) { _, which ->
                val (groupId, groupName) = existingGroups[which]
                moveToGroup(book, groupId, groupName)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showNewGroupDialog(book: BookInfo) {
        val edit = EditText(requireContext())
        edit.hint = "分组名称"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新建分组")
            .setView(edit)
            .setPositiveButton("确定") { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isNotEmpty()) {
                    val groupId = "local_${System.currentTimeMillis()}"
                    moveToGroup(book, groupId, name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun moveToGroup(book: BookInfo, groupId: String, groupName: String) {
        lifecycleScope.launch {
            try {
                if (App.instance.prefs.isLoggedIn) {
                    App.instance.api.moveToGroup(book.bookId, groupId, groupName)
                }
                Snackbar.make(binding.root, "已移动到 $groupName", Snackbar.LENGTH_SHORT).show()
                loadShelf()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "移动失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun removeFromShelf(book: BookInfo) {
        lifecycleScope.launch {
            val existing = App.instance.db.bookDao().getById(book.bookId)
            if (existing != null) {
                App.instance.db.bookDao().delete(existing)
            }
            if (App.instance.prefs.isLoggedIn) {
                try {
                    App.instance.api.removeFromShelf(book.bookId)
                } catch (_: Exception) {}
            }
            Snackbar.make(binding.root, "已移出书架", Snackbar.LENGTH_SHORT).show()
            loadShelf()
        }
    }

    fun setFilter(query: String) {
        shelfFilter = query
        if (_binding != null) renderShelf()
    }

    private fun openDetail(bookId: String) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).putExtra("book_id", bookId))
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) loadShelf()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shelfSearchEt = null
        _binding = null
    }

    private class HeaderAdapter(private val title: String) :
        RecyclerView.Adapter<HeaderAdapter.VH>() {
        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = parent.context.resources.displayMetrics.density
            val tv = TextView(parent.context).apply {
                text = title
                textSize = 15f
                setTextColor(parent.context.getColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
                val pad = (dp * 16).toInt()
                setPadding(pad, (dp * 12).toInt(), pad, (dp * 8).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.tv.text = title
        }

        override fun getItemCount(): Int = 1
    }
}
