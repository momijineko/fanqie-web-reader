package com.momijineko.fanqie.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.momijineko.fanqie.data.api.BookInfo
import com.momijineko.fanqie.data.api.BookshelfItem
import com.momijineko.fanqie.data.db.BookEntity
import com.momijineko.fanqie.databinding.ItemBookBinding
import com.momijineko.fanqie.databinding.ItemBookGridBinding

class BookAdapter(
    private val onClick: (BookInfo) -> Unit,
    private val mode: Mode = Mode.LIST,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class Mode { LIST, GRID }

    private val items = mutableListOf<BookInfo>()

    fun submit(list: List<BookInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemViewType(): Int = mode.ordinal

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
        init { binding.root.setOnClickListener { onClick(items[adapterPosition]) } }
        fun bind(item: BookInfo) {
            binding.tvName.text = item.name
            binding.tvAuthor.text = item.author
            binding.tvDesc.text = item.desc
            if (item.category.isNotEmpty()) {
                binding.tvCategory.visibility = View.VISIBLE
                binding.tvCategory.text = item.category
            } else {
                binding.tvCategory.visibility = View.GONE
            }
            if (item.status.isNotEmpty()) {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = item.status
            } else {
                binding.tvStatus.visibility = View.GONE
            }
        }
    }

    inner class GridVH(val binding: ItemBookGridBinding) : RecyclerView.ViewHolder(binding.root) {
        init { binding.root.setOnClickListener { onClick(items[adapterPosition]) } }
        fun bind(item: BookInfo) {
            binding.tvName.text = item.name
            if (item.status.isNotEmpty()) {
                binding.tvStatusBadge.visibility = View.VISIBLE
                binding.tvStatusBadge.text = item.status
            } else {
                binding.tvStatusBadge.visibility = View.GONE
            }
            binding.tvProgress.visibility = View.GONE
        }
    }
}

fun BookEntity.toBookInfo() = BookInfo(
    bookId = bookId, name = name, author = author, desc = desc,
    thumbUrl = thumbUrl, chapterCount = "", category = "", score = "",
    wordCount = 0, status = status, tags = "", readCount = 0,
)

fun BookshelfItem.toBookInfo() = BookInfo(
    bookId = bookId, name = name, author = "", desc = desc,
    thumbUrl = thumbUrl, chapterCount = chapterCount.toString(), category = "",
    score = "", wordCount = 0, status = status, tags = "", readCount = 0,
)
