package com.cady.cadysalesapp.ui.weborders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.weborders.WebOrder
import com.cady.cadysalesapp.data.weborders.WebOrderStatus
import com.cady.cadysalesapp.data.weborders.WebOrderInvoiceLink
import com.cady.cadysalesapp.data.weborders.WebOrdersAlertSettings
import com.cady.cadysalesapp.data.weborders.WebOrdersFeed
import com.cady.cadysalesapp.data.weborders.WebOrdersMonitor
import com.cady.cadysalesapp.data.weborders.WebOrdersNotifier
import com.cady.cadysalesapp.data.weborders.WebOrdersRepository
import com.cady.cadysalesapp.data.weborders.WebQuote
import com.cady.cadysalesapp.data.weborders.WebQuotesRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WebOrdersUiState(
    /** Every order the live feed holds, newest first (the screen shows the ones of the selected [filter]). */
    val orders: List<WebOrder> = emptyList(),
    val filter: WebOrderStatus = WebOrderStatus.NEW,
    val loading: Boolean = true,
    val error: String? = null,
    val alertsEnabled: Boolean = true,
    /** True once the saved alerts switch has been read — until then [alertsEnabled] is only the default. */
    val settled: Boolean = false,
    /** The order whose new status is being saved right now. */
    val busyId: String? = null,
    val message: String? = null,
) {
    val counts: Map<WebOrderStatus, Int> get() = orders.groupingBy { it.status }.eachCount()
    val visible: List<WebOrder> get() = orders.filter { it.status == filter }
}

/** The offers the AI seller built (newest first), for the "العروض" tab. */
data class QuotesUiState(
    val quotes: List<WebQuote> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WebOrdersViewModel @Inject constructor(
    private val monitor: WebOrdersMonitor,
    private val repository: WebOrdersRepository,
    private val settings: WebOrdersAlertSettings,
    private val notifier: WebOrdersNotifier,
    private val quotesRepository: WebQuotesRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(WebOrderStatus.NEW)
    private val busyId = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<WebOrdersUiState> =
        combine(monitor.feed, filter, settings.alertsEnabled, busyId, message) { feed, selected, alerts, busy, note ->
            WebOrdersUiState(
                orders = (feed as? WebOrdersFeed.Data)?.orders.orEmpty(),
                filter = selected,
                loading = feed is WebOrdersFeed.Loading,
                error = (feed as? WebOrdersFeed.Failed)?.message,
                alertsEnabled = alerts,
                settled = true,
                busyId = busy,
                message = note,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WebOrdersUiState())

    /** Invoices issued from orders and remembered on this phone (the order's own copy needs the newer Firestore rules). */
    val invoiceLinks: StateFlow<Map<String, WebOrderInvoiceLink>> = settings.invoiceLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Bumped by [refresh]: the offers listener is attached again (which also revives one that was refused). */
    private val quotesRestarts = MutableStateFlow(0)

    val quotes: StateFlow<QuotesUiState> = quotesRestarts
        .flatMapLatest {
            quotesRepository.observeRecent()
            .map { QuotesUiState(quotes = it, loading = false) }
            .catch { e ->
                val denied = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                emit(
                    QuotesUiState(
                        loading = false,
                        error = if (denied) {
                            "لا توجد صلاحية لقراءة العروض — انشر قواعد Firestore الجديدة (quotes)"
                        } else {
                            "تعذّر تحميل العروض — تحقّق من الإنترنت"
                        },
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuotesUiState())

    init {
        // Opening the list is looking at the orders: the notification about them has done its job.
        notifier.cancel()
    }

    private val _refreshing = MutableStateFlow(false)

    /** True while a pull-to-refresh is waiting for the server. */
    val refreshing: StateFlow<Boolean> = _refreshing

    /**
     * Pull-to-refresh on both tabs: the live listeners are attached again, and the spinner stops once the server
     * has answered (or after a few seconds when there is no connection).
     */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                quotesRestarts.update { it + 1 }
                monitor.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing to add: a failed list shows its own error card, and pulling again tries again.
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun select(status: WebOrderStatus) {
        filter.value = status
    }

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setAlertsEnabled(enabled) }
    }

    fun dismissMessage() {
        message.value = null
    }

    fun setStatus(order: WebOrder, status: WebOrderStatus) {
        if (busyId.value != null || order.status == status) return
        viewModelScope.launch {
            busyId.value = order.id
            try {
                val confirmed = repository.setStatus(order.id, status)
                message.value = if (confirmed) {
                    "تم نقل طلب «${order.customerName}» إلى «${status.label}»"
                } else {
                    "لا يوجد اتصال الآن — سيصل التغيير تلقائيًا عند عودة الإنترنت"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val denied = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                message.value = if (denied) {
                    "لا توجد صلاحية لتعديل الطلب — يجب أن تسمح قواعد Firestore للمدير بتعديل الحالة"
                } else {
                    "تعذّر تغيير الحالة — تحقّق من الإنترنت وأعد المحاولة"
                }
            } finally {
                busyId.value = null
            }
        }
    }
}

/** The number on the manager tab and on the dashboard tile: orders still waiting for a first look. */
@HiltViewModel
class WebOrdersBadgeViewModel @Inject constructor(
    monitor: WebOrdersMonitor,
) : ViewModel() {
    val newCount: StateFlow<Int> = monitor.newCount
}
