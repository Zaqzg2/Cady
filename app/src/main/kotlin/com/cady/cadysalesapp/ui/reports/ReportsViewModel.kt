package com.cady.cadysalesapp.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.CustomerRepository
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

data class ReportsState(
    val period: ReportPeriod = ReportPeriod.TODAY,
    /** How many whole periods back the view is stepped (0 = the current one). */
    val offset: Int = 0,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    /** Null = all reps (only a manager can pick one). */
    val repId: String? = null,
    val isManager: Boolean = false,
    val reps: List<UserAccountEntity> = emptyList(),
    /** What the selected period is called: "اليوم", "أمس", "أكتوبر 2026"… */
    val rangeLabel: String = "",
    /** The words after the ▲/▼ percentage: "عن أمس", "عن الشهر السابق"… */
    val compareLabel: String = "",
    /** The period can be stepped to an older one (not for "all time" or a custom range). */
    val canStepBack: Boolean = false,
    /** The period is already stepped back, so it can be stepped forward again. */
    val canStepForward: Boolean = false,
    val dashboard: DashboardData = DashboardData(),
    val loading: Boolean = true,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    invoiceRepository: InvoiceRepository,
    receiptRepository: ReceiptRepository,
    customerRepository: CustomerRepository,
    invoiceItemDao: InvoiceItemDao,
) : ViewModel() {

    private data class Choices(
        val period: ReportPeriod = ReportPeriod.TODAY,
        val from: LocalDate? = null,
        val to: LocalDate? = null,
        val offset: Int = 0,
        val repId: String? = null,
    )

    private class Source(
        val isManager: Boolean,
        val input: ReportInput,
        val selfId: String,
        val selfName: String,
    )

    private val choices = MutableStateFlow(Choices())

    /** A rep's report covers his own records; a manager's covers every rep's that this device holds. */
    private val source: Flow<Source?> = accountRepository.currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(null)
        } else {
            val manager = user.role == UserRole.MANAGER
            combine(
                if (manager) invoiceRepository.observeEveryone() else invoiceRepository.observeAll(user.id),
                if (manager) receiptRepository.observeEveryone() else receiptRepository.observeAll(user.id),
                if (manager) customerRepository.observeEveryone() else customerRepository.observeAll(user.id),
                invoiceItemDao.observeSubTotals(),
                invoiceItemDao.observeProductLines(),
            ) { invoices, receipts, customers, subTotals, lines ->
                Source(
                    isManager = manager,
                    input = ReportInput(
                        invoices = invoices,
                        receipts = receipts,
                        customers = customers,
                        subTotals = subTotals.associate { it.invoiceId to it.subTotal },
                        productLines = lines,
                    ),
                    selfId = user.id,
                    selfName = user.displayName,
                )
            }
        }
    }

    val state: StateFlow<ReportsState> = combine(source, choices, accountRepository.observeReps()) { src, c, reps ->
        if (src == null) {
            ReportsState(loading = false)
        } else {
            val today = LocalDate.now()
            val range = periodRange(c.period, c.from, c.to, c.offset, today)
            val previous = previousRange(c.period, c.from, c.to, c.offset, today)
            val repNames = reps.associate { it.id to it.displayName } + (src.selfId to src.selfName)
            ReportsState(
                period = c.period,
                offset = c.offset,
                customFrom = c.from,
                customTo = c.to,
                repId = c.repId,
                isManager = src.isManager,
                reps = reps.sortedBy { it.displayName },
                rangeLabel = periodLabel(c.period, c.from, c.to, c.offset, today),
                compareLabel = compareLabel(c.period, c.offset),
                canStepBack = c.period != ReportPeriod.ALL && c.period != ReportPeriod.CUSTOM,
                canStepForward = c.offset > 0,
                dashboard = buildDashboard(
                    input = src.input,
                    repId = c.repId,
                    range = range,
                    previousRange = previous,
                    repNames = repNames,
                    includeReps = src.isManager,
                    today = today,
                ),
                loading = false,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState())

    /** Picking a period always starts from the current one. */
    fun selectPeriod(period: ReportPeriod) = choices.update { current ->
        if (period == ReportPeriod.CUSTOM && current.from == null) {
            val today = LocalDate.now()
            current.copy(period = period, offset = 0, from = today, to = today)
        } else {
            current.copy(period = period, offset = 0)
        }
    }

    /** +1 = one period older, −1 = one period newer (never past the current one). */
    fun stepPeriod(delta: Int) = choices.update { current ->
        if (current.period == ReportPeriod.ALL || current.period == ReportPeriod.CUSTOM) {
            current
        } else {
            current.copy(offset = (current.offset + delta).coerceAtLeast(0))
        }
    }

    fun setCustomFrom(day: LocalDate) = choices.update { current ->
        val to = current.to
        current.copy(from = day, to = if (to == null || to.isBefore(day)) day else to)
    }

    fun setCustomTo(day: LocalDate) = choices.update { current ->
        val from = current.from
        current.copy(to = day, from = if (from == null || from.isAfter(day)) day else from)
    }

    fun selectRep(repId: String?) = choices.update { it.copy(repId = repId) }
}
