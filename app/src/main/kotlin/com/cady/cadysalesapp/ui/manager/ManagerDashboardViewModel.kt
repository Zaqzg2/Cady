package com.cady.cadysalesapp.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.sync.SyncActivityEntry
import com.cady.cadysalesapp.data.sync.SyncActivityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ManagerDashboardState(
    val repCount: Int = 0,
    val activeReps: Int = 0,
    /** The newest line of the real sync log (never a made-up figure). */
    val lastActivity: SyncActivityEntry? = null,
)

@HiltViewModel
class ManagerDashboardViewModel @Inject constructor(
    accountRepository: AccountRepository,
    activityStore: SyncActivityStore,
) : ViewModel() {

    val state: StateFlow<ManagerDashboardState> = combine(
        accountRepository.observeReps(),
        activityStore.entries,
    ) { reps, log ->
        ManagerDashboardState(
            repCount = reps.size,
            activeReps = reps.count { it.isActive },
            lastActivity = log.firstOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerDashboardState())
}
