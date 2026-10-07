package com.cady.cadysalesapp.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.sync.SyncActivityEntry
import com.cady.cadysalesapp.data.sync.SyncActivityStatus
import com.cady.cadysalesapp.data.sync.SyncActivityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ManagerSyncHubState(
    val reps: List<UserAccountEntity> = emptyList(),
    val activeReps: Int = 0,
    val operationsToday: Int = 0,
    /** Log entries from the last 7 days that did not fully succeed. */
    val problems: Int = 0,
    val pendingProducts: Int = 0,
    val lastActivity: SyncActivityEntry? = null,
)

@HiltViewModel
class ManagerSyncHubViewModel @Inject constructor(
    accountRepository: AccountRepository,
    activityStore: SyncActivityStore,
    productDao: ProductDao,
) : ViewModel() {

    val state: StateFlow<ManagerSyncHubState> = combine(
        accountRepository.observeReps(),
        activityStore.entries,
        productDao.observePendingCount(),
    ) { reps, log, pendingProducts ->
        val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS)
        ManagerSyncHubState(
            reps = reps.sortedWith(compareBy({ it.repNumber ?: Int.MAX_VALUE }, { it.displayName })),
            activeReps = reps.count { it.isActive },
            operationsToday = log.count { !it.at.isBefore(startOfToday) },
            problems = log.count { it.status != SyncActivityStatus.SUCCESS && !it.at.isBefore(weekAgo) },
            pendingProducts = pendingProducts,
            lastActivity = log.firstOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerSyncHubState())
}
