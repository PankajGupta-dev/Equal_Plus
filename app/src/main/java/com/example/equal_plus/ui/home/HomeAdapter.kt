package com.example.equal_plus.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.databinding.ItemRecentCallBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeAdapter(
    private val onCallClick: (CallEntity) -> Unit
) : ListAdapter<CallEntity, HomeAdapter.CallViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val binding = ItemRecentCallBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CallViewHolder(binding, onCallClick)
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CallViewHolder(
        private val binding: ItemRecentCallBinding,
        private val onCallClick: (CallEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        fun bind(call: CallEntity) {
            binding.tvCallerTitle.text = call.contactName ?: call.phoneNumber
            binding.tvCallerNumber.text = if (call.contactName != null) call.phoneNumber else "Unknown Caller"

            val isToday = (System.currentTimeMillis() - call.createdAt) < (24 * 60 * 60 * 1000)
            binding.tvCallTime.text = if (isToday) {
                timeFormat.format(Date(call.createdAt))
            } else {
                dateFormat.format(Date(call.createdAt))
            }

            if (!call.summary.isNullOrBlank()) {
                binding.tvSummary.visibility = View.VISIBLE
                binding.tvSummary.text = call.summary
            } else if (!call.transcription.isNullOrBlank()) {
                binding.tvSummary.visibility = View.VISIBLE
                binding.tvSummary.text = call.transcription
            } else {
                binding.tvSummary.visibility = View.GONE
            }

            // Risk badge setup via shared RiskBadgeUtil
            com.example.equal_plus.ui.common.RiskBadgeUtil.applyRiskBadge(binding.tvRiskBadge, call.riskLevel)

            // Status badge setup
            when (call.status) {
                CallStatus.BLOCKED -> {
                    binding.tvStatusBadge.visibility = View.VISIBLE
                    binding.tvStatusBadge.text = "BLOCKED"
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#FFEBEE"))
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#C62828"))
                }
                CallStatus.COMPLETED -> {
                    binding.tvStatusBadge.visibility = View.VISIBLE
                    binding.tvStatusBadge.text = "RESOLVED"
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#E8F5E9"))
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#2E7D32"))
                }
                CallStatus.SCREENING -> {
                    binding.tvStatusBadge.visibility = View.VISIBLE
                    binding.tvStatusBadge.text = "SCREENING"
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#E3F2FD"))
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#1565C0"))
                }
                CallStatus.ACTIVE -> {
                    binding.tvStatusBadge.visibility = View.VISIBLE
                    binding.tvStatusBadge.text = "ACTIVE"
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#E0F2F1"))
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#00796B"))
                }
                else -> {
                    binding.tvStatusBadge.visibility = View.GONE
                }
            }

            // Category badge
            if (!call.category.isNullOrBlank()) {
                binding.tvCategoryBadge.visibility = View.VISIBLE
                binding.tvCategoryBadge.text = call.category.uppercase(Locale.getDefault())
            } else {
                binding.tvCategoryBadge.visibility = View.GONE
            }

            binding.cardRecentCall.setOnClickListener {
                onCallClick(call)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<CallEntity>() {
        override fun areItemsTheSame(oldItem: CallEntity, newItem: CallEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallEntity, newItem: CallEntity): Boolean {
            return oldItem == newItem
        }
    }
}
