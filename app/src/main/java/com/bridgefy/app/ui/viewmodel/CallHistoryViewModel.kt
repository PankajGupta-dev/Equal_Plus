package com.bridgefy.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridgefy.app.db.DatabaseProvider
import com.bridgefy.app.db.CallLogQueries
import com.bridgefy.app.model.CallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallHistoryViewModel : ViewModel() {

    private val _callLogs = MutableStateFlow<List<CallLog>>(emptyList())
    val callLogs: StateFlow<List<CallLog>> = _callLogs.asStateFlow()

    init {
        loadCallLogs()
    }

    fun loadCallLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = CallLogQueries.getCallLogs(DatabaseProvider.db)
                withContext(Dispatchers.Main) {
                    _callLogs.value = logs
                }
            } catch (e: Exception) {
                // Log/handle exception
            }
        }
    }
}
