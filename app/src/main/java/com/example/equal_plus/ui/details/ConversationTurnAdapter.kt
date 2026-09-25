package com.example.equal_plus.ui.details

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.equal_plus.data.local.entity.ConversationEntity
import com.example.equal_plus.data.model.SpeakerType
import com.example.equal_plus.databinding.ItemConversationTurnBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationTurnAdapter :
    ListAdapter<ConversationEntity, ConversationTurnAdapter.TurnViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TurnViewHolder {
        val binding = ItemConversationTurnBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TurnViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TurnViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TurnViewHolder(
        private val binding: ItemConversationTurnBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())

        fun bind(turn: ConversationEntity) {
            binding.tvMessageText.text = turn.message
            binding.tvTurnTime.text = timeFormat.format(Date(turn.timestamp))

            when (turn.speaker) {
                SpeakerType.CALLER -> {
                    binding.tvSpeakerTag.text = "CALLER"
                    binding.tvSpeakerTag.setBackgroundColor(Color.parseColor("#FFF3E0"))
                    binding.tvSpeakerTag.setTextColor(Color.parseColor("#E65100"))
                    binding.cardTurnBubble.strokeColor = Color.parseColor("#FFE0B2")
                }
                SpeakerType.ASSISTANT -> {
                    binding.tvSpeakerTag.text = "AI ASSISTANT"
                    binding.tvSpeakerTag.setBackgroundColor(Color.parseColor("#E3F2FD"))
                    binding.tvSpeakerTag.setTextColor(Color.parseColor("#1565C0"))
                    binding.cardTurnBubble.strokeColor = Color.parseColor("#BBDEFB")
                }
                SpeakerType.USER -> {
                    binding.tvSpeakerTag.text = "USER"
                    binding.tvSpeakerTag.setBackgroundColor(Color.parseColor("#E8F5E9"))
                    binding.tvSpeakerTag.setTextColor(Color.parseColor("#2E7D32"))
                    binding.cardTurnBubble.strokeColor = Color.parseColor("#C8E6C9")
                }
                SpeakerType.SYSTEM -> {
                    binding.tvSpeakerTag.text = "SYSTEM"
                    binding.tvSpeakerTag.setBackgroundColor(Color.parseColor("#F5F5F5"))
                    binding.tvSpeakerTag.setTextColor(Color.parseColor("#616161"))
                    binding.cardTurnBubble.strokeColor = Color.parseColor("#E0E0E0")
                }
            }

            if (turn.isFlagged) {
                binding.tvFlaggedTag.visibility = View.VISIBLE
            } else {
                binding.tvFlaggedTag.visibility = View.GONE
            }

            val metaParts = mutableListOf<String>()
            turn.intent?.let { metaParts.add("Intent: $it") }
            turn.confidence?.let { metaParts.add("Confidence: ${(it * 100).toInt()}%") }
            turn.sentiment?.let { metaParts.add("Sentiment: $it") }

            if (metaParts.isNotEmpty()) {
                binding.tvIntentMetadata.visibility = View.VISIBLE
                binding.tvIntentMetadata.text = metaParts.joinToString(" • ")
            } else {
                binding.tvIntentMetadata.visibility = View.GONE
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ConversationEntity>() {
        override fun areItemsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean {
            return oldItem == newItem
        }
    }
}
