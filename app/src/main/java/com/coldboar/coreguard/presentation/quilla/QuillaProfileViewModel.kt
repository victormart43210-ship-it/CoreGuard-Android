package com.coldboar.coreguard.presentation.quilla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coldboar.coreguard.data.local.dao.QuillaLearningDao
import com.coldboar.coreguard.data.local.entity.QuillaDeviceProfileEntity
import com.coldboar.coreguard.data.local.entity.QuillaLearningJournalEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuillaUiState(
    val deviceProfile: QuillaDeviceProfileEntity? = null,
    val journalEntries: List<QuillaLearningJournalEntity> = emptyList(),
    val isLearningPaused: Boolean = false
)

@HiltViewModel
class QuillaProfileViewModel @Inject constructor(
    private val dao: QuillaLearningDao
) : ViewModel() {

    val uiState: StateFlow<QuillaUiState> = combine(
        dao.getDeviceProfileFlow(),
        dao.getRecentJournalEntries(50)
    ) { profile, journal ->
        QuillaUiState(
            deviceProfile = profile,
            journalEntries = journal
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QuillaUiState()
    )

    /** Removes all learned behavioral data for the given package. */
    fun resetAppMemory(packageName: String) {
        viewModelScope.launch {
            dao.forgetApp(packageName)
        }
    }

    /** Performs a full baseline reset, erasing all journal entries, hypotheses, and the device profile. */
    fun resetAllLearning() {
        viewModelScope.launch {
            dao.fullLearningReset()
        }
    }
}
