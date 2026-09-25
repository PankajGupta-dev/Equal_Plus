package com.example.equal_plus.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.equal_plus.R
import com.example.equal_plus.databinding.FragmentOtpEntryBinding
import com.example.equal_plus.ui.common.AppViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

private const val TAG = "OtpEntryFrag"

/**
 * Step 2 of onboarding: user enters the 6-digit SMS OTP.
 * Phone number received via requireArguments().getString("phoneNumber").
 * On verified success → navigates to HomeFragment clearing the auth back-stack.
 */
class OtpEntryFragment : Fragment() {

    private var _binding: FragmentOtpEntryBinding? = null
    private val binding get() = _binding!!

    private val phoneNumber: String
        get() = requireArguments().getString("phoneNumber", "")

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
        // Show the number we're verifying so user can confirm
        binding.tvOtpSubtitle.text = "We sent a 6-digit code to $phoneNumber"
        setupListeners()
        observeState()
        observeEvents()
    }

    private fun setupListeners() {
        binding.btnVerify.setOnClickListener { submitOtp() }
        binding.etOtp.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submitOtp(); true }
            else false
        }
        binding.tvResendHint.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun submitOtp() {
        val code = binding.etOtp.text?.toString()?.trim() ?: ""
        Log.d(TAG, "Verify tapped — phone=$phoneNumber code=$code")
        viewModel.verifyOtp(phoneNumber = phoneNumber, code = code)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBarOtp.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnVerify.isEnabled = !state.isLoading
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is OtpEntryEvent.Error -> {
                            Log.e(TAG, "Auth0 OTP error in UI: ${event.message}")
                            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                                .setAction("OK") {}
                                .show()
                        }
                        OtpEntryEvent.Verified -> {
                            Log.d(TAG, "Verified — navigating to Home")
                            findNavController().navigate(
                                R.id.action_otpEntryFragment_to_homeFragment,
                                null,
                                NavOptions.Builder()
                                    .setPopUpTo(R.id.nav_graph, inclusive = true)
                                    .build()
                            )
                        }
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
