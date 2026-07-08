package com.momijineko.fanqie.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.momijineko.fanqie.R
import com.momijineko.fanqie.data.api.ChapterInfo
import com.momijineko.fanqie.databinding.ItemChapterBinding

class ChapterAdapter(
    private val onClick: (chapterIdx: Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed interface Item {
        data class Header(val volumeName: String) : Item
        data class Chapter(val chapter: ChapterInfo, val originalIdx: Int) : Item
    }
    private val items = mutableListOf<Item>()
    private var currentIdx = -1
    private var allChapters: List<ChapterInfo> = emptyList()

    fun submit(list: List<ChapterInfo>, highlightIdx: Int = -1) {
        allChapters = list
        currentIdx = highlightIdx
        rebuildItems(list)
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            rebuildItems(allChapters)
        } else {
            rebuildItems(allChapters.filter { it.name.contains(q, ignoreCase = true) })
        }
        notifyDataSetChanged()
    }

    private fun rebuildItems(list: List<ChapterInfo>) {
        items.clear()
        var lastVolume = ""
        for (ch in list) {
            val origIdx = allChapters.indexOfFirst { it.chapterId == ch.chapterId }
            if (ch.volumeName.isNotEmpty() && ch.volumeName != lastVolume) {
                lastVolume = ch.volumeName
                items.add(Item.Header(ch.volumeName))
            }
            items.add(Item.Chapter(ch, origIdx))
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position] is Item.Header) TYPE_HEADER else TYPE_CHAPTER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderVH(TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setPadding(
                    (parent.context.resources.displayMetrics.density * 16).toInt(),
                    (parent.context.resources.displayMetrics.density * 12).toInt(),
                    (parent.context.resources.displayMetrics.density * 16).toInt(),
                    (parent.context.resources.displayMetrics.density * 8).toInt(),
                )
                textSize = 14f
                setTextColor(parent.context.getColor(R.color.text_secondary))
                gravity = Gravity.CENTER_VERTICAL
            })
        } else {
            ChapterVH(
                ItemChapterBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderVH).bind(item)
            is Item.Chapter -> (holder as ChapterVH).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderVH(val tv: TextView) : RecyclerView.ViewHolder(tv) {
        fun bind(item: Item.Header) {
            tv.text = item.volumeName
        }
    }

    inner class ChapterVH(val binding: ItemChapterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val item = items[pos]
                    if (item is Item.Chapter) onClick(item.originalIdx)
                }
            }
        }

        fun bind(item: Item.Chapter) {
            val ch = item.chapter
            binding.tvChapterName.text = ch.name.ifEmpty { "第${item.originalIdx + 1}章" }

            if (ch.updateTime > 0) {
                binding.tvChapterTime.visibility = View.VISIBLE
                binding.tvChapterTime.text = relativeTime(ch.updateTime)
            } else {
                binding.tvChapterTime.visibility = View.GONE
            }

            val isCurrent = item.originalIdx == currentIdx
            val isRead = item.originalIdx in 0 until currentIdx

            if (isCurrent) {
                binding.tvChapterName.setTextColor(
                    binding.root.context.getColor(R.color.accent)
                )
                binding.tvChapterName.setTypeface(null, android.graphics.Typeface.BOLD)
                binding.tvChapterBadge.visibility = View.VISIBLE
                binding.tvChapterBadge.text = "在读"
            } else if (isRead) {
                binding.tvChapterName.setTextColor(
                    binding.root.context.getColor(R.color.text_muted)
                )
                binding.tvChapterName.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.tvChapterBadge.visibility = View.GONE
            } else {
                binding.tvChapterName.setTextColor(
                    binding.root.context.getColor(R.color.text_primary)
                )
                binding.tvChapterName.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.tvChapterBadge.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CHAPTER = 1
    }
}
