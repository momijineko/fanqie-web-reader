package com.momijineko.fanqie.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.momijineko.fanqie.R
import com.momijineko.fanqie.data.api.CommentInfo
import com.momijineko.fanqie.databinding.ItemCommentBinding

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.VH>() {

    private val items = mutableListOf<CommentInfo>()

    fun submit(list: List<CommentInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun addAll(list: List<CommentInfo>) {
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommentInfo) {
            binding.tvUserName.text = item.userName
            binding.tvContent.text = item.content
            binding.tvTime.text = relativeTime(item.createTime)
            binding.tvReplies.text = if (item.replies.isNotEmpty()) "${item.replies.size}条回复" else ""

            binding.ivAvatar.load(item.avatarUrl) {
                crossfade(false)
                placeholder(R.drawable.ic_person)
                error(R.drawable.ic_person)
            }

            binding.replyContainer.removeAllViews()
            if (item.replies.isNotEmpty()) {
                binding.replyContainer.visibility = View.VISIBLE
                for (reply in item.replies) {
                    val tv = TextView(binding.root.context).apply {
                        text = "${reply.userName}: ${reply.content}"
                        textSize = 12f
                        setTextColor(context.getColor(R.color.text_secondary))
                        val dp8 = (context.resources.displayMetrics.density * 8).toInt()
                        setPadding(dp8, dp8 / 2, 0, dp8 / 2)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                    }
                    binding.replyContainer.addView(tv)
                }
            }
        }
    }
}
