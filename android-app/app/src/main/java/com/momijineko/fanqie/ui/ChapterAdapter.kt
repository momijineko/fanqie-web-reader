package com.momijineko.fanqie.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.momijineko.fanqie.data.api.ChapterInfo
import com.momijineko.fanqie.databinding.ItemChapterBinding

class ChapterAdapter(
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<ChapterAdapter.VH>() {
    private val items = mutableListOf<ChapterInfo>()

    fun submit(list: List<ChapterInfo>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position], position)
    override fun getItemCount(): Int = items.size

    inner class VH(val binding: ItemChapterBinding) : RecyclerView.ViewHolder(binding.root) {
        init { binding.root.setOnClickListener { onClick(adapterPosition) } }
        fun bind(item: ChapterInfo, pos: Int) {
            binding.tvChapterName.text = item.name.ifEmpty { "第${pos + 1}章" }
        }
    }
}
