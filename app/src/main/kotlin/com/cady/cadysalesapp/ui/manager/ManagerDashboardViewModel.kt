package com.cady.cadysalesapp.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.sync.SyncActivityEntry
import com.cady.cadysalesapp.data.sync.SyncActivityStore
import com.cady.cadysalesapp.data.weborders.WebOrdersAlertSettings
import com.cady.cadysalesapp.data.weborders.WebOrdersMonitor
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
    /** Orders from the public sales page that nobody has looked at yet. */
    val newWebOrders: Int = 0,
    /** The new-order alerts switch; null until the saved value has been read. */
    val alertsEnabled: Boolean? = null,
)

@HiltViewModel
class ManagerDashboardViewModel @Inject constructor(
    accountRepository: AccountRepository,
    activityStore: SyncActivityStore,
    webOrdersMonitor: WebOrdersMonitor,
    webOrdersSettings: WebOrdersAlertSettings,
) : ViewModel() {

    val state: StateFlow<ManagerDashboardState> = combine(
        accountRepository.observeReps(),
        activityStore.entries,
        webOrdersMonitor.newCount,
        webOrdersSettings.alertsEnabled,
    ) { reps, log, newOrders, alerts ->
        ManagerDashboardState(
            repCount = reps.size,
            activeReps = reps.count { it.isActive },
            lastActivity = log.firstOrNull(),
            newWebOrders = newOrders,
            alertsEnabled = alerts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerDashboardState())
}
