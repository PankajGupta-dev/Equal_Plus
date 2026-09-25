package com.example.equal_plus.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.equal_plus.R
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.databinding.FragmentLiveAiCallBinding
import com.example.equal_plus.telephony.VoipConnectionState
import com.example.equal_plus.ui.livecall.LiveAiCallViewModel
import com.example.equal_plus.ui.livecall.LiveCallState
import com.example.equal_plus.ui.livecall.LiveCallStatus
import kotlinx.coroutines.launch

class LiveAiCallFragment : Fragment() {

    private var _binding: FragmentLiveAiCallBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LiveAiCallViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveAiCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupButtons()
        observeState()
    }

    private fun setupButtons() {
        binding.btnEndCall.setOnClickListener {
            viewModel.endCall()
        }

        binding.btnTakeOverCall.setOnClickListener {
            viewModel.takeOverCall()
        }

        binding.btnTestNavigateDetails.setOnClickListener {
            viewModel.triggerEndCallForNavigation()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.liveCallState.collect { state ->
                    renderState(state)

                    if (state.status == LiveCallStatus.ENDED) {
                        val bundle = bundleOf(ConversationDetailsFragment.ARG_CALL_ID to state.callId)
                        findNavController().navigate(
                            R.id.action_liveAiCallFragment_to_conversationDetailsFragment,
                            bundle
                        )
                    }
                }
            }
        }
    }

    private fun renderState(state: LiveCallState) {
        binding.tvCallerName.text = state.callerName
        binding.tvPhoneNumber.text = state.phoneNumber
        binding.tvCallDuration.text = state.durationFormatted
        binding.tvDetectedPurpose.text = state.detectedPurpose
        binding.tvAiStatusText.text = state.aiStatusText
        binding.tvLiveTranscript.text = state.latestTranscript

        when (state.connectionState) {
            is VoipConnectionState.Connecting -> {
                binding.tvLiveSessionStatus.text = "CONNECTING VOIP GATEWAY..."
            }
            is VoipConnectionState.Connected -> {
                binding.tvLiveSessionStatus.text = "VOIP CONNECTED - SCREENING"
            }
            is VoipConnectionState.Streaming -> {
                binding.tvLiveSessionStatus.text = "LIVE AUDIO STREAMING ACTIVE"
            }
            is VoipConnectionState.CallEnded -> {
                binding.tvLiveSessionStatus.text = "VOIP SESSION ENDED"
            }
            is VoipConnectionState.Disconnected -> {
                binding.tvLiveSessionStatus.text = "VOIP DISCONNECTED"
            }
            is VoipConnectionState.Error -> {
                binding.tvLiveSessionStatus.text = "VOIP ERROR: ${(state.connectionState as VoipConnectionState.Error).message}"
            }
        }

        when (state.riskLevel) {
            RiskLevel.SAFE, RiskLevel.LOW -> {
                binding.tvRiskBadge.text = "SAFE CALLER"
                binding.tvRiskBadge.setBackgroundColor(Color.parseColor("#E8F5E9"))
                binding.tvRiskBadge.setTextColor(Color.parseColor("#2E7D32"))
            }
            RiskLevel.MEDIUM -> {
                binding.tvRiskBadge.text = "SUSPICIOUS / UNVERIFIED"
                binding.tvRiskBadge.setBackgroundColor(Color.parseColor("#FFF3E0"))
                binding.tvRiskBadge.setTextColor(Color.parseColor("#E65100"))
            }
            RiskLevel.HIGH, RiskLevel.CRITICAL -> {
                binding.tvRiskBadge.text = "HIGH RISK / SCAM DETECTED"
                binding.tvRiskBadge.setBackgroundColor(Color.parseColor("#FFEBEE"))
                binding.tvRiskBadge.setTextColor(Color.parseColor("#C62828"))
            }
            RiskLevel.UNKNOWN -> {
                binding.tvRiskBadge.text = "ANALYZING CALL..."
                binding.tvRiskBadge.setBackgroundColor(Color.parseColor("#F5F5F5"))
                binding.tvRiskBadge.setTextColor(Color.parseColor("#616161"))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
