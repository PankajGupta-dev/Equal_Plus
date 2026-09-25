package com.example.equal_plus.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.equal_plus.R
import com.example.equal_plus.databinding.FragmentPhoneVerificationBinding
import com.example.equal_plus.ui.common.AppViewModelFactory
import kotlinx.coroutines.launch

/**
 * Step 1 of onboarding: user enters their phone number (E.164).
 * On success, navigates forward to OtpEntryFragment passing the phone.
 */
class PhoneVerificationFragment : Fragment() {

    private var _binding: FragmentPhoneVerificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PhoneVerificationViewModel by viewModels {
        AppViewModelFactory(requireContext().applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhoneVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.btnSendCode.setOnClickListener { submitPhone() }
        binding.etPhone.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submitPhone(); true }
            else false
        }
    }

    private fun submitPhone() {
        val phone = binding.etPhone.text?.toString()?.trim() ?: ""
        viewModel.sendCode(phone)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBarPhone.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnSendCode.isEnabled = !state.isLoading

                    if (state.error != null) {
                        binding.tvError.visibility = View.VISIBLE
                        binding.tvError.text = state.error
                        viewModel.clearError()
                    } else {
                        binding.tvError.visibility = View.GONE
                    }

                    if (state.codeSent) {
                        val phone = binding.etPhone.text?.toString()?.trim() ?: ""
                        val action = PhoneVerificationFragmentDirections
                            .actionPhoneVerificationFragmentToOtpEntryFragment(phone)
                        findNavController().navigate(action)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
