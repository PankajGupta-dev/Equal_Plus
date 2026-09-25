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
import com.example.equal_plus.databinding.FragmentCallHistoryBinding
import com.example.equal_plus.ui.common.AppViewModelFactory
import com.example.equal_plus.ui.history.CallFilter
import com.example.equal_plus.ui.history.CallHistoryAdapter
import com.example.equal_plus.ui.history.CallHistoryViewModel
import kotlinx.coroutines.launch

class CallHistoryFragment : Fragment() {

    private var _binding: FragmentCallHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CallHistoryViewModel by viewModels {
        AppViewModelFactory(requireContext().applicationContext)
    }

    private lateinit var historyAdapter: CallHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupChipFilters()
        setupListeners()
        observeState()
    }

    private fun setupRecyclerView() {
        historyAdapter = CallHistoryAdapter { selectedCall ->
            val bundle = bundleOf(ConversationDetailsFragment.ARG_CALL_ID to selectedCall.id)
            findNavController().navigate(
                R.id.action_callHistoryFragment_to_conversationDetailsFragment,
                bundle
            )
        }

        binding.rvCallHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupChipFilters() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when {
                checkedIds.contains(R.id.chipResolved) -> CallFilter.RESOLVED
                checkedIds.contains(R.id.chipBlocked) -> CallFilter.BLOCKED
                checkedIds.contains(R.id.chipEscalated) -> CallFilter.ESCALATED
                checkedIds.contains(R.id.chipMissed) -> CallFilter.MISSED
                else -> CallFilter.ALL
            }
            viewModel.setFilter(filter)
        }
    }

    private fun setupListeners() {
        binding.btnSeedHistoryData.setOnClickListener {
            viewModel.seedDemoData()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val filterLabel = when (state.activeFilter) {
                        CallFilter.ALL -> "all calls"
                        CallFilter.RESOLVED -> "resolved calls"
                        CallFilter.BLOCKED -> "blocked scams"
                        CallFilter.ESCALATED -> "calls needing review"
                        CallFilter.MISSED -> "missed calls"
                    }

                    binding.tvFilteredCount.text = "Showing ${state.filteredCalls.size} $filterLabel"

                    if (state.filteredCalls.isEmpty()) {
                        binding.layoutEmptyHistory.visibility = View.VISIBLE
                        binding.rvCallHistory.visibility = View.GONE
                        binding.tvEmptySubtitle.text = "No calls match the \"$filterLabel\" filter."
                    } else {
                        binding.layoutEmptyHistory.visibility = View.GONE
                        binding.rvCallHistory.visibility = View.VISIBLE
                        historyAdapter.submitList(state.filteredCalls)
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
