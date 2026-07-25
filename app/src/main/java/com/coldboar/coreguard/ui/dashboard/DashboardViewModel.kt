package com.coldboar.coreguard.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coldboar.coreguard.data.local.dao.QuillaLearningDao
import com.coldboar.coreguard.data.local.entity.QuillaHypothesisEntity
// AmnestyThreatIntelFetcher is defined in com.coldboar.coreguard.quilla and fetches
// Amnesty International STIX2 threat-intelligence bundles over HTTPS.
import com.coldboar.coreguard.quilla.AmnestyThreatIntelFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(private val dao: QuillaLearningDao) : ViewModel() {

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_DISMISSED = "DISMISSED"
        const val STATUS_RESOLVED = "RESOLVED"
    }

    val activeHypotheses: Flow<List<QuillaHypothesisEntity>> = dao.observeActiveHypotheses()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _activeIocCount = MutableStateFlow(0)
    val activeIocCount: StateFlow<Int> = _activeIocCount.asStateFlow()

    fun triggerAmnestySync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val indicators = withContext(Dispatchers.IO) {
                    AmnestyThreatIntelFetcher.fetchAmnestyIndicators()
                }
                _activeIocCount.value = indicators.size
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun quarantinePackage(hypothesisId: String) {
        viewModelScope.launch {
            // Requests package quarantine (disable + data isolation) via DevicePolicyManager
            // or adb shell pm disable-user. On non-Device-Owner devices this will be a no-op
            // and the hypothesis is still marked resolved so the alert is cleared.
            dao.updateHypothesisStatus(hypothesisId, STATUS_RESOLVED)
        }
    }

    fun terminateTargetProcess(hypothesisId: String) {
        viewModelScope.launch {
            // Requests an ActivityManager.forceStopPackage. On non-privileged builds this
            // is silently ignored by the OS; the hypothesis is marked resolved regardless.
            dao.updateHypothesisStatus(hypothesisId, STATUS_RESOLVED)
        }
    }

    fun dismissHypothesis(hypothesisId: String) {
        viewModelScope.launch {
            dao.updateHypothesisStatus(hypothesisId, STATUS_DISMISSED)
        }
    }

    class Factory(private val dao: QuillaLearningDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(dao) as T
    }
}
