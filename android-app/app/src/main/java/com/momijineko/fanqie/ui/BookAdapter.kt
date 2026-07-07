package com.momijineko.fanqie.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.momijineko.fanqie.data.api.BookInfo
import com.momijineko.fanqie.data.api.BookshelfItem
import com.momijineko.fanqie.data.db.BookEntity
import com.momijineko.fanqie.databinding.ItemBookBinding

class BookAdapter(
    private val onClick: (BookInfo) -> Unit,
) : RecyclerView.Adapter<BookAdapter.VH>() {
    private val items = mutableListOf<BookInfo>()

    fun submit(list: List<BookInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class VH(val binding: ItemBookBinding) : RecyclerView.ViewHolder(binding.root) {
        init { binding.root.setOnClickListener { onClick(items[adapterPosition]) } }
        fun bind(item: BookInfo) {
            binding.tvName.text = item.name
            binding.tvAuthor.text = item.author
            binding.tvStatus.text = item.status
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
