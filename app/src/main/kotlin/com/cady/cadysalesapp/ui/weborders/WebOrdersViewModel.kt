package com.cady.cadysalesapp.ui.weborders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.weborders.WebOrder
import com.cady.cadysalesapp.data.weborders.WebOrderStatus
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

@HiltViewModel
class WebOrdersViewModel @Inject constructor(
    monitor: WebOrdersMonitor,
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

    val quotes: StateFlow<QuotesUiState> = quotesRepository.observeRecent()
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuotesUiState())

    init {
        // Opening the list is looking at the orders: the notification about them has done its job.
        notifier.cancel()
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
