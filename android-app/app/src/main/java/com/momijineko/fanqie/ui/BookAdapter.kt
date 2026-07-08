package com.momijineko.fanqie.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.momijineko.fanqie.R
import com.momijineko.fanqie.data.api.BookInfo
import com.momijineko.fanqie.data.api.BookshelfItem
import com.momijineko.fanqie.data.db.BookEntity
import com.momijineko.fanqie.data.db.ProgressEntity
import com.momijineko.fanqie.databinding.ItemBookBinding
import com.momijineko.fanqie.databinding.ItemBookGridBinding

class BookAdapter(
    private val onClick: (BookInfo) -> Unit,
    private val mode: Mode = Mode.LIST,
    private val onLongClick: ((BookInfo) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class Mode { LIST, GRID }

    private val items = mutableListOf<BookInfo>()
    private val progressMap = mutableMapOf<String, ProgressEntity>()
    private val shelfMap = mutableMapOf<String, BookshelfItem>()

    fun submit(list: List<BookInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setProgress(map: Map<String, ProgressEntity>) {
        progressMap.clear()
        progressMap.putAll(map)
        notifyDataSetChanged()
    }

    fun setShelfData(map: Map<String, BookshelfItem>) {
        shelfMap.clear()
        shelfMap.putAll(map)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = mode.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == Mode.GRID.ordinal) {
            GridVH(ItemBookGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            ListVH(ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is ListVH -> holder.bind(item)
            is GridVH -> holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ListVH(val binding: ItemBookBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) onClick(items[adapterPosition])
            }
            binding.root.setOnLongClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION && onLongClick != null) {
                    onLongClick.invoke(items[adapterPosition])
                    true
                } else false
            }
        }

        fun bind(item: BookInfo) {
            binding.tvName.text = item.name
            binding.tvAuthor.text = item.author
            binding.tvDesc.text = item.desc

            binding.ivCover.load(item.thumbUrl) {
                crossfade(false)
                placeholder(R.drawable.ic_book_placeholder)
                error(R.drawable.ic_book_placeholder)
            }

            if (item.category.isNotEmpty()) {
                binding.tvCategory.visibility = View.VISIBLE
                binding.tvCategory.text = item.category
            } else {
                binding.tvCategory.visibility = View.GONE
            }

            val metaParts = mutableListOf<String>()
            if (item.score.isNotEmpty()) metaParts.add("评分 ${item.score}")
            if (item.wordCount > 0) {
                val wc = item.wordCount / 10000.0
                metaParts.add("${"%.1f".format(wc)}万字")
            }
            if (item.chapterCount.isNotEmpty() && item.chapterCount != "0") metaParts.add("${item.chapterCount}章")
            if (metaParts.isNotEmpty()) {
                binding.tvMeta.visibility = View.VISIBLE
                binding.tvMeta.text = metaParts.joinToString(" · ")
            } else {
                binding.tvMeta.visibility = View.GONE
            }

            val badge = computeBadge(item)
            if (badge.isNotEmpty()) {
                binding.tvStatusBadge.visibility = View.VISIBLE
                binding.tvStatusBadge.text = badge
            } else {
                binding.tvStatusBadge.visibility = View.GONE
            }
        }
    }

    inner class GridVH(val binding: ItemBookGridBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) onClick(items[adapterPosition])
            }
            binding.root.setOnLongClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION && onLongClick != null) {
                    onLongClick.invoke(items[adapterPosition])
                    true
                } else false
            }
        }

        fun bind(item: BookInfo) {
            binding.tvName.text = item.name

            binding.ivCover.load(item.thumbUrl) {
                crossfade(false)
                placeholder(R.drawable.ic_book_placeholder)
                error(R.drawable.ic_book_placeholder)
            }

            val badge = computeBadge(item)
            if (badge.isNotEmpty()) {
                binding.tvStatusBadge.visibility = View.VISIBLE
                binding.tvStatusBadge.text = badge
            } else {
                binding.tvStatusBadge.visibility = View.GONE
            }

            val prog = progressMap[item.bookId]
            val shelfItem = shelfMap[item.bookId]
            if (prog != null && prog.totalChapters > 0) {
                binding.tvProgress.visibility = View.VISIBLE
                val pct = (prog.chapterIdx + 1) * 100 / prog.totalChapters
                binding.tvProgress.text = "${pct}%"
            } else if (shelfItem != null && shelfItem.chapterCount > 0 && shelfItem.readChapterIdx >= 0) {
                binding.tvProgress.visibility = View.VISIBLE
                val pct = (shelfItem.readChapterIdx + 1) * 100 / shelfItem.chapterCount
                binding.tvProgress.text = "${pct}%"
            } else {
                binding.tvProgress.visibility = View.GONE
            }
        }
    }

    private fun computeBadge(item: BookInfo): String {
        val shelfItem = shelfMap[item.bookId]
        if (shelfItem != null) {
            return bookBadge(
                status = shelfItem.status.ifEmpty { item.status },
                chapterCount = shelfItem.chapterCount,
                lastReadChapter = shelfItem.lastReadChapter,
                lastReadTime = shelfItem.lastReadTime,
                lastUpdateTime = shelfItem.lastUpdateTime,
                updateStopped = shelfItem.updateStopped,
            )
        }
        return when (item.status) {
            "已完结" -> "已完结"
            "连载中" -> "连载中"
            else -> ""
        }
    }
}

fun bookBadge(
    status: String,
    chapterCount: Int,
    lastReadChapter: String,
    lastReadTime: Long,
    lastUpdateTime: Long,
    updateStopped: Boolean,
): String {
    val lastNum = chapterNum(lastReadChapter)
    if (status == "已完结") {
        if (lastNum > 0 && chapterCount > 0 && lastNum >= chapterCount) return "已完结"
        if (lastNum > 0 && chapterCount > 0) return "${chapterCount - lastNum}章未读"
        return "已完结"
    }
    if (updateStopped) return "已断更"
    if (lastNum > 0 && chapterCount > 0 && lastNum >= chapterCount) return "已看完"
    if (status == "连载中" && lastUpdateTime > 0 && lastReadTime > 0 && lastUpdateTime > lastReadTime) return "有更新"
    if (status == "连载中") return "连载中"
    return ""
}

private fun chapterNum(text: String): Int {
    val regex = Regex("""第(\d+)章""")
    return regex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val sec = diff / 1000
    if (sec < 60) return "刚刚"
    val min = sec / 60
    if (min < 60) return "${min}分钟前"
    val hour = min / 60
    if (hour < 24) return "${hour}小时前"
    val day = hour / 24
    if (day < 30) return "${day}天前"
    val month = day / 30
    if (month < 12) return "${month}个月前"
    return "${month / 12}年前"
}

fun BookEntity.toBookInfo() = BookInfo(
    bookId = bookId, name = name, author = author, desc = desc,
    thumbUrl = thumbUrl, chapterCount = chapterCount.toString(), category = category,
    score = score, wordCount = wordCount, status = status, tags = "", readCount = 0,
)

fun BookshelfItem.toBookInfo() = BookInfo(
    bookId = bookId, name = name, author = "", desc = desc,
    thumbUrl = thumbUrl, chapterCount = chapterCount.toString(), category = "",
    score = "", wordCount = 0, status = status, tags = "", readCount = 0,
)
