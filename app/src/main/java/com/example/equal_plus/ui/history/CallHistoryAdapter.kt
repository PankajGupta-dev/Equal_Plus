package com.example.equal_plus.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.equal_plus.R
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.databinding.ItemCallHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallHistoryAdapter(
    private val onCallClick: (CallEntity) -> Unit
) : ListAdapter<CallEntity, CallHistoryAdapter.CallViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val binding = ItemCallHistoryBinding.inflate(
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
        private val binding: ItemCallHistoryBinding,
        private val onCallClick: (CallEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        fun bind(call: CallEntity) {
            binding.tvCallerTitle.text = call.contactName ?: call.phoneNumber
            binding.tvCallerNumber.text = if (call.contactName != null) call.phoneNumber else "Unknown Caller"

            val isToday = (System.currentTimeMillis() - call.createdAt) < (24 * 60 * 60 * 1000)
            binding.tvCallTime.text = if (isToday) {
                "Today, ${timeFormat.format(Date(call.createdAt))}"
            } else {
                dateFormat.format(Date(call.createdAt))
            }

            // Duration format
            val minutes = call.durationSeconds / 60
            val seconds = call.durationSeconds % 60
            binding.tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

            // Summary or transcript
            if (!call.summary.isNullOrBlank()) {
                binding.tvSummary.visibility = View.VISIBLE
                binding.tvSummary.text = call.summary
            } else if (!call.transcription.isNullOrBlank()) {
                binding.tvSummary.visibility = View.VISIBLE
                binding.tvSummary.text = call.transcription
            } else {
                binding.tvSummary.visibility = View.GONE
            }

            // Call type icon
            if (call.callType == CallType.MISSED || call.status == CallStatus.MISSED) {
                binding.ivCallIcon.setImageResource(R.drawable.ic_call_missed)
            } else if (call.status == CallStatus.BLOCKED || call.isSpam) {
                binding.ivCallIcon.setImageResource(R.drawable.ic_block)
            } else {
                binding.ivCallIcon.setImageResource(R.drawable.ic_call)
            }

            // Risk badge setup
            when (call.riskLevel) {
                RiskLevel.SAFE, RiskLevel.LOW -> {
                    binding.tvRiskBadge.text = "SAFE"
                    binding.tvRiskBadge.setBackgroundColor(Color.parseColor("#E8F5E9"))
                    binding.tvRiskBadge.setTextColor(Color.parseColor("#2E7D32"))
                }
                RiskLevel.MEDIUM -> {
                    binding.tvRiskBadge.text = "SUSPICIOUS"
                    binding.tvRiskBadge.setBackgroundColor(Color.parseColor("#FFF3E0"))
                    binding.tvRiskBadge.setTextColor(Color.parseColor("#E65100"))
                }
                RiskLevel.HIGH, RiskLevel.CRITICAL -> {
                    binding.tvRiskBadge.text = if (call.riskLevel == RiskLevel.CRITICAL) "CRITICAL SCAM" else "HIGH RISK"
                    binding.tvRiskBadge.setBackgroundColor(Color.parseColor("#FFEBEE"))
                    binding.tvRiskBadge.setTextColor(Color.parseColor("#C62828"))
                }
                RiskLevel.UNKNOWN -> {
                    binding.tvRiskBadge.text = "UNVERIFIED"
                    binding.tvRiskBadge.setBackgroundColor(Color.parseColor("#F5F5F5"))
                    binding.tvRiskBadge.setTextColor(Color.parseColor("#616161"))
                }
            }

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
                CallStatus.MISSED -> {
                    binding.tvStatusBadge.visibility = View.VISIBLE
                    binding.tvStatusBadge.text = "MISSED"
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#FFF3E0"))
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#E65100"))
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

            binding.cardCallHistory.setOnClickListener {
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
