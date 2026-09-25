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
import androidx.navigation.fragment.navArgs
import com.example.equal_plus.R
import com.example.equal_plus.databinding.FragmentOtpEntryBinding
import com.example.equal_plus.ui.common.AppViewModelFactory
import kotlinx.coroutines.launch

/**
 * Step 2 of onboarding: user enters the SMS OTP.
 * On verified success, sets is_verified=true in DataStore and navigates to HomeFragment.
 */
class OtpEntryFragment : Fragment() {

    private var _binding: FragmentOtpEntryBinding? = null
    private val binding get() = _binding!!

    private val args: OtpEntryFragmentArgs by navArgs()

    private val viewModel: OtpEntryViewModel by viewModels {
        AppViewModelFactory(requireContext().applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.btnVerify.setOnClickListener { submitOtp() }
        binding.etOtp.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submitOtp(); true }
            else false
        }
        // Resend: pop back to PhoneVerificationFragment
        binding.tvResendHint.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun submitOtp() {
        val code = binding.etOtp.text?.toString()?.trim() ?: ""
        viewModel.verifyOtp(phoneNumber = args.phoneNumber, code = code)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBarOtp.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnVerify.isEnabled = !state.isLoading

                    if (state.error != null) {
                        binding.tvOtpError.visibility = View.VISIBLE
                        binding.tvOtpError.text = state.error
                        viewModel.clearError()
                    } else {
                        binding.tvOtpError.visibility = View.GONE
                    }

                    if (state.isVerified) {
                        // Navigate to Home, clearing the entire auth back-stack
                        findNavController().navigate(
                            R.id.action_otpEntryFragment_to_homeFragment,
                            null,
                            androidx.navigation.NavOptions.Builder()
                                .setPopUpTo(R.id.nav_graph, inclusive = true)
                                .build()
                        )
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
