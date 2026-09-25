package com.example.equal_plus.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.equal_plus.data.model.PolicyCategory
import com.example.equal_plus.databinding.FragmentAiPolicyBinding
import com.example.equal_plus.ui.common.AppViewModelFactory
import com.example.equal_plus.ui.policy.AiPolicyUiState
import com.example.equal_plus.ui.policy.AiPolicyViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class AiPolicyFragment : Fragment() {

    private var _binding: FragmentAiPolicyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AiPolicyViewModel by viewModels {
        AppViewModelFactory(requireContext().applicationContext)
    }

    private var isUpdatingUi = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiPolicyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        // Master switch
        binding.switchMasterScreening.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.toggleGlobalScreening(isChecked)
            }
        }

        // Unknown Callers
        binding.switchUnknownScreen.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.UNKNOWN_CALLER, autoScreen = isChecked)
            }
        }
        binding.switchUnknownBlock.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.UNKNOWN_CALLER, autoBlock = isChecked)
            }
        }

        // Bank / Financial
        binding.switchBankScreen.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.FINANCIAL, autoScreen = isChecked)
            }
        }
        binding.switchBankRecord.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.FINANCIAL, autoRecord = isChecked)
            }
        }

        // Sales / Telemarketing
        binding.switchSalesScreen.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.TELEMARKETING, autoScreen = isChecked)
            }
        }
        binding.switchSalesBlock.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.TELEMARKETING, autoBlock = isChecked)
            }
        }

        // Delivery
        binding.switchDeliveryScreen.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.DELIVERY, autoScreen = isChecked)
            }
        }
        binding.switchDeliveryRecord.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.DELIVERY, autoRecord = isChecked)
            }
        }

        // Recruiters & Work
        binding.switchRecruiterScreen.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.GENERAL, autoScreen = isChecked)
            }
        }
        binding.switchRecruiterBlock.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.GENERAL, autoBlock = isChecked)
            }
        }

        // Personal Contacts
        binding.switchPersonalPassThrough.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                // Pass-through means autoScreen is false
                viewModel.updatePolicy(PolicyCategory.PERSONAL, autoScreen = !isChecked)
            }
        }
        binding.switchPersonalRecord.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.updatePolicy(PolicyCategory.PERSONAL, autoRecord = isChecked)
            }
        }

        // Reset
        binding.btnResetDefaults.setOnClickListener {
            viewModel.resetAllToDefaults()
            Snackbar.make(binding.root, "All screening rules reset to default policy", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: AiPolicyUiState) {
        binding.progressBarPolicy.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        if (state.isLoading) return

        isUpdatingUi = true
        try {
            binding.switchMasterScreening.isChecked = state.isGlobalScreeningEnabled

            val unknownPolicy = state.policies[PolicyCategory.UNKNOWN_CALLER]
            unknownPolicy?.let {
                binding.switchUnknownScreen.isChecked = it.autoScreen
                binding.switchUnknownBlock.isChecked = it.autoBlock
            }

            val bankPolicy = state.policies[PolicyCategory.FINANCIAL]
            bankPolicy?.let {
                binding.switchBankScreen.isChecked = it.autoScreen
                binding.switchBankRecord.isChecked = it.autoRecord
            }

            val salesPolicy = state.policies[PolicyCategory.TELEMARKETING]
            salesPolicy?.let {
                binding.switchSalesScreen.isChecked = it.autoScreen
                binding.switchSalesBlock.isChecked = it.autoBlock
            }

            val deliveryPolicy = state.policies[PolicyCategory.DELIVERY]
            deliveryPolicy?.let {
                binding.switchDeliveryScreen.isChecked = it.autoScreen
                binding.switchDeliveryRecord.isChecked = it.autoRecord
            }

            val recruiterPolicy = state.policies[PolicyCategory.GENERAL]
            recruiterPolicy?.let {
                binding.switchRecruiterScreen.isChecked = it.autoScreen
                binding.switchRecruiterBlock.isChecked = it.autoBlock
            }

            val personalPolicy = state.policies[PolicyCategory.PERSONAL]
            personalPolicy?.let {
                binding.switchPersonalPassThrough.isChecked = !it.autoScreen
                binding.switchPersonalRecord.isChecked = it.autoRecord
            }
        } finally {
            isUpdatingUi = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
