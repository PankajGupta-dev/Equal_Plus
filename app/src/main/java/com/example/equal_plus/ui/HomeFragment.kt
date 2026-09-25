package com.example.equal_plus.ui

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.equal_plus.R
import com.example.equal_plus.databinding.FragmentHomeBinding
import com.example.equal_plus.ui.common.AppViewModelFactory
import com.example.equal_plus.ui.home.HomeAdapter
import com.example.equal_plus.ui.home.HomeViewModel
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        AppViewModelFactory(requireContext().applicationContext)
    }

    private lateinit var homeAdapter: HomeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupRecyclerView() {
        homeAdapter = HomeAdapter { selectedCall ->
            val bundle = bundleOf(ConversationDetailsFragment.ARG_CALL_ID to selectedCall.id)
            findNavController().navigate(
                R.id.action_homeFragment_to_conversationDetailsFragment,
                bundle
            )
        }

        binding.rvRecentCalls.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = homeAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        // Summary Cards navigation
        binding.cardHandled.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_callHistoryFragment)
        }

        binding.cardNeedingAttention.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_callHistoryFragment)
        }

        binding.cardAutoResolved.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_aiPolicyFragment)
        }

        binding.cardBlocked.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_aiPolicyFragment)
        }

        binding.btnViewAllHistory.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_callHistoryFragment)
        }

        binding.btnSeedDemoData.setOnClickListener {
            viewModel.seedDemoData()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvHandledCount.text = state.handledCount.toString()
                    binding.tvAutoResolvedCount.text = state.autoResolvedCount.toString()
                    binding.tvBlockedCount.text = state.blockedCount.toString()
                    binding.tvAttentionCount.text = state.needingAttentionCount.toString()

                    binding.progressBarHome.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    if (state.isLoading) {
                        binding.layoutEmptyState.visibility = View.GONE
                        binding.rvRecentCalls.visibility = View.GONE
                    } else if (state.recentCalls.isEmpty()) {
                        binding.layoutEmptyState.visibility = View.VISIBLE
                        binding.rvRecentCalls.visibility = View.GONE
                    } else {
                        binding.layoutEmptyState.visibility = View.GONE
                        binding.rvRecentCalls.visibility = View.VISIBLE
                        homeAdapter.submitList(state.recentCalls)
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
