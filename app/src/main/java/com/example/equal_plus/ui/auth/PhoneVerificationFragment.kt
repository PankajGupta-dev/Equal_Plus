package com.example.equal_plus.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.equal_plus.R
import com.example.equal_plus.databinding.FragmentPhoneVerificationBinding
import com.example.equal_plus.ui.common.AppViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

private const val TAG = "PhoneVerifyFrag"

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
        observeEvents()
    }

    private fun setupListeners() {
        binding.btnSendCode.setOnClickListener { submitPhone() }
        binding.etPhone.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submitPhone(); true }
            else false
        }
        // Live preview of the E.164-formatted number
        binding.etPhone.doAfterTextChanged { text ->
            val raw = text?.toString()?.trim() ?: ""
            if (raw.isNotEmpty()) {
                binding.tilPhone.helperText = "Will send to: ${formatIndianE164(raw)}"
            } else {
                binding.tilPhone.helperText = "+91 country code added automatically for India"
            }
        }
    }

    private fun submitPhone() {
        val raw = binding.etPhone.text?.toString()?.trim() ?: ""
        val formatted = formatIndianE164(raw)
        Log.d(TAG, "Submit pressed — raw='$raw' formatted='$formatted'")
        viewModel.sendCode(formatted)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBarPhone.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnSendCode.isEnabled = !state.isLoading
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is PhoneVerificationEvent.Error -> {
                            Log.e(TAG, "Auth0 error received in UI: ${event.message}")
                            // Snackbar stays visible — user can actually read the error
                            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                                .setAction("OK") {}
                                .show()
                        }
                        is PhoneVerificationEvent.CodeSent -> {
                            Log.d(TAG, "Navigating to OTP entry for ${event.phoneNumber}")
                            findNavController().navigate(
                                R.id.action_phoneVerificationFragmentToOtpEntryFragment,
                                bundleOf("phoneNumber" to event.phoneNumber)
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Converts Indian mobile numbers to E.164 (+91XXXXXXXXXX).
     * Other country codes (already starting with +) are passed through unchanged.
     */
    private fun formatIndianE164(input: String): String {
        if (input.isBlank()) return input
        if (input.startsWith("+") && !input.startsWith("+91")) return input
        val digits = input.filter { it.isDigit() }
        return when {
            digits.length == 10                                  -> "+91$digits"
            digits.length == 11 && digits.startsWith("0")       -> "+91${digits.substring(1)}"
            digits.length == 12 && digits.startsWith("91")      -> "+$digits"
            digits.length == 13 && digits.startsWith("091")     -> "+91${digits.substring(3)}"
            input.startsWith("+91")                             -> input
            else                                                -> input
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
