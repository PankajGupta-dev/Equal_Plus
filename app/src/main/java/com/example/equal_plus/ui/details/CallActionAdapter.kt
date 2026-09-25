package com.example.equal_plus.ui.details

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.equal_plus.R
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.databinding.ItemCallActionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallActionAdapter :
    ListAdapter<ActionEntity, CallActionAdapter.ActionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val binding = ItemCallActionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ActionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ActionViewHolder(
        private val binding: ItemCallActionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())

        fun bind(action: ActionEntity) {
            binding.tvActionTitle.text = action.actionType.name.replace("_", " ")

            val execTime = action.executedAt ?: action.timestamp
            binding.tvActionTimestamp.text = "Executed at ${timeFormat.format(Date(execTime))}"

            if (!action.description.isNullOrBlank()) {
                binding.tvActionDescription.visibility = View.VISIBLE
                binding.tvActionDescription.text = action.description
            } else if (!action.payloadJson.isNullOrBlank()) {
                binding.tvActionDescription.visibility = View.VISIBLE
                binding.tvActionDescription.text = action.payloadJson
            } else {
                binding.tvActionDescription.visibility = View.GONE
            }

            // Icon by ActionType
            when (action.actionType) {
                ActionType.BLOCK_NUMBER -> {
                    binding.ivActionIcon.setImageResource(R.drawable.ic_block)
                }
                ActionType.WARN_USER -> {
                    binding.ivActionIcon.setImageResource(R.drawable.ic_shield_alert)
                }
                ActionType.RECORD_AUDIO -> {
                    binding.ivActionIcon.setImageResource(R.drawable.ic_call)
                }
                else -> {
                    binding.ivActionIcon.setImageResource(R.drawable.ic_shield_check)
                }
            }

            // Status badge
            when (action.status) {
                ActionStatus.EXECUTED -> {
                    binding.tvActionStatusBadge.text = "EXECUTED"
                    binding.tvActionStatusBadge.setBackgroundColor(Color.parseColor("#E8F5E9"))
                    binding.tvActionStatusBadge.setTextColor(Color.parseColor("#2E7D32"))
                }
                ActionStatus.PENDING -> {
                    binding.tvActionStatusBadge.text = "PENDING"
                    binding.tvActionStatusBadge.setBackgroundColor(Color.parseColor("#FFF3E0"))
                    binding.tvActionStatusBadge.setTextColor(Color.parseColor("#E65100"))
                }
                ActionStatus.FAILED -> {
                    binding.tvActionStatusBadge.text = "FAILED"
                    binding.tvActionStatusBadge.setBackgroundColor(Color.parseColor("#FFEBEE"))
                    binding.tvActionStatusBadge.setTextColor(Color.parseColor("#C62828"))
                }
                ActionStatus.CANCELLED -> {
                    binding.tvActionStatusBadge.text = "CANCELLED"
                    binding.tvActionStatusBadge.setBackgroundColor(Color.parseColor("#F5F5F5"))
                    binding.tvActionStatusBadge.setTextColor(Color.parseColor("#616161"))
                }
                ActionStatus.IN_PROGRESS -> {
                    binding.tvActionStatusBadge.text = "RUNNING"
                    binding.tvActionStatusBadge.setBackgroundColor(Color.parseColor("#E3F2FD"))
                    binding.tvActionStatusBadge.setTextColor(Color.parseColor("#1565C0"))
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ActionEntity>() {
        override fun areItemsTheSame(oldItem: ActionEntity, newItem: ActionEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ActionEntity, newItem: ActionEntity): Boolean {
            return oldItem == newItem
        }
    }
}
