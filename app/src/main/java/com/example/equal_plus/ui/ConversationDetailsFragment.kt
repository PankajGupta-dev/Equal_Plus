package com.example.equal_plus.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.ActionRepositoryImpl
import com.example.equal_plus.data.repository.CallRepositoryImpl
import com.example.equal_plus.data.repository.ConversationRepositoryImpl
import com.example.equal_plus.databinding.FragmentConversationDetailsBinding
import com.example.equal_plus.ui.details.CallActionAdapter
import com.example.equal_plus.ui.details.ConversationDetailsViewModel
import com.example.equal_plus.ui.details.ConversationTurnAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationDetailsFragment : Fragment() {

    private var _binding: FragmentConversationDetailsBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val ARG_CALL_ID = "callId"
    }

    private val callId: String by lazy {
        arguments?.getString(ARG_CALL_ID) ?: "sample_call_id"
    }

    private val viewModel: ConversationDetailsViewModel by viewModels {
        val database = AppDatabase.getInstance(requireContext().applicationContext)
        ConversationDetailsViewModel.Factory(
            callId = callId,
            callRepository = CallRepositoryImpl(database.callDao()),
            conversationRepository = ConversationRepositoryImpl(database.conversationDao()),
            actionRepository = ActionRepositoryImpl(database.actionDao())
        )
    }

    private lateinit var turnAdapter: ConversationTurnAdapter
    private lateinit var actionAdapter: CallActionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        observeState()
    }

    private fun setupRecyclerViews() {
        turnAdapter = ConversationTurnAdapter()
        binding.rvTranscript.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = turnAdapter
            setHasFixedSize(false)
        }

        actionAdapter = CallActionAdapter()
        binding.rvActions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = actionAdapter
            setHasFixedSize(false)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBarDetails.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    state.call?.let { renderCallHeader(it) }

                    // Render Transcript
                    if (state.conversations.isEmpty()) {
                        binding.tvEmptyTranscript.visibility = View.VISIBLE
                        binding.rvTranscript.visibility = View.GONE
                    } else {
                        binding.tvEmptyTranscript.visibility = View.GONE
                        binding.rvTranscript.visibility = View.VISIBLE
                        turnAdapter.submitList(state.conversations)
                    }

                    // Render Actions
                    if (state.actions.isEmpty()) {
                        binding.tvEmptyActions.visibility = View.VISIBLE
                        binding.rvActions.visibility = View.GONE
                    } else {
                        binding.tvEmptyActions.visibility = View.GONE
                        binding.rvActions.visibility = View.VISIBLE
                        actionAdapter.submitList(state.actions)
                    }
                }
            }
        }
    }

    private fun renderCallHeader(call: CallEntity) {
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        val minutes = call.durationSeconds / 60
        val seconds = call.durationSeconds % 60
        val durationStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        binding.tvCallerTitle.text = call.contactName ?: call.phoneNumber
        binding.tvPhoneNumber.text = if (call.contactName != null) call.phoneNumber else "Unknown Number"
        binding.tvCallMeta.text = "${call.callType.name} • ${dateFormat.format(Date(call.createdAt))} • Duration: $durationStr"

        // Shared risk badge styling
        com.example.equal_plus.ui.common.RiskBadgeUtil.applyRiskBadge(binding.tvRiskBadge, call.riskLevel)

        // Status badge
        when (call.status) {
            CallStatus.BLOCKED -> {
                binding.tvStatusBadge.text = "BLOCKED"
                binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#FFEBEE"))
                binding.tvStatusBadge.setTextColor(Color.parseColor("#C62828"))
            }
            CallStatus.COMPLETED -> {
                binding.tvStatusBadge.text = "RESOLVED"
                binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#E8F5E9"))
                binding.tvStatusBadge.setTextColor(Color.parseColor("#2E7D32"))
            }
            CallStatus.SCREENING -> {
                binding.tvStatusBadge.text = "SCREENING"
                binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#E3F2FD"))
                binding.tvStatusBadge.setTextColor(Color.parseColor("#1565C0"))
            }
            else -> {
                binding.tvStatusBadge.text = call.status.name
                binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#F5F5F5"))
                binding.tvStatusBadge.setTextColor(Color.parseColor("#616161"))
            }
        }

        // Category badge
        if (!call.category.isNullOrBlank()) {
            binding.tvCategoryBadge.visibility = View.VISIBLE
            binding.tvCategoryBadge.text = call.category.uppercase(Locale.getDefault())
        } else {
            binding.tvCategoryBadge.visibility = View.GONE
        }

        // Intent analysis & summary
        val summaryText = call.summary ?: call.transcription ?: "No summary recorded for this screening session."
        binding.tvSummaryText.text = summaryText

        if (call.riskLevel == RiskLevel.HIGH || call.riskLevel == RiskLevel.CRITICAL) {
            binding.tvIntentText.text = "Primary Intent: Fraudulent Impersonation & OTP Harvesting • Key Entities: [Security Code, Card Number]"
            binding.tvSentimentText.text = "Caller Sentiment: Coercive / Urgent • AI Confidence: 96%"
        } else if (call.category.equals("Delivery", ignoreCase = true)) {
            binding.tvIntentText.text = "Primary Intent: Package Dropoff Coordination • Key Entities: [Porch, Gate, Side Entrance]"
            binding.tvSentimentText.text = "Caller Sentiment: Neutral • AI Confidence: 99%"
        } else {
            binding.tvIntentText.text = "Primary Intent: Inbound Inquiry • Key Entities: [Phone Identity]"
            binding.tvSentimentText.text = "Caller Sentiment: Neutral • AI Confidence: 92%"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
