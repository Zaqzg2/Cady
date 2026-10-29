package com.cady.cadysalesapp.data.weborders

import android.util.Log
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed interface WebOrdersFeed {
    /** Nobody to watch for: not signed in, or not a manager. */
    data object Idle : WebOrdersFeed

    data object Loading : WebOrdersFeed

    data class Data(val orders: List<WebOrder>) : WebOrdersFeed

    data class Failed(val message: String) : WebOrdersFeed
}

/**
 * The single live view of the website's orders, shared by everything that shows them (the manager tab's badge,
 * the dashboard tile, the orders screen) — so there is one Firestore listener, not one per screen.
 *
 * It only listens while something is looking: the screens collect it with the lifecycle, and it stops a few
 * seconds after the app leaves the screen (the background check takes over from there). A rep account never
 * touches Firestore's `web_orders` at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WebOrdersMonitor @Inject constructor(
    private val repository: WebOrdersRepository,
    private val notifier: WebOrdersNotifier,
    private val settings: WebOrdersAlertSettings,
    accountRepository: AccountRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bumped by [refresh]: the live listener is dropped and attached again (which also revives one that failed). */
    private val restarts = MutableStateFlow(0)

    /** How many times the SERVER (not this phone's cache) has answered the listener — [refresh] waits for the next one. */
    private val serverAnswers = MutableStateFlow(0)

    /** The last list shown, so a refresh keeps it on screen instead of flashing an empty page while it reloads. */
    @Volatile
    private var lastData: WebOrdersFeed.Data? = null

    val feed: StateFlow<WebOrdersFeed> = accountRepository.currentUser
        .map { it?.role == UserRole.MANAGER }
        .distinctUntilChanged()
        .combine(restarts) { isManager, _ -> isManager }
        .flatMapLatest { isManager ->
            if (isManager) {
                liveFeed()
            } else {
                lastData = null
                flowOf<WebOrdersFeed>(WebOrdersFeed.Idle)
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WebOrdersFeed.Loading)

    /** How many orders are still waiting for the manager's first look (status NEW). */
    val newCount: StateFlow<Int> = feed
        .map { current -> (current as? WebOrdersFeed.Data)?.orders.orEmpty().count { it.status == WebOrderStatus.NEW } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /**
     * Pull-to-refresh: drops the live listener and attaches it again, then waits (a few seconds at most) for the
     * server's first answer. A listener that failed — no connection when the screen opened, rules not yet
     * published — is revived the same way, which the error card used to ask for by saying "open the screen again".
     */
    suspend fun refresh() {
        val before = serverAnswers.value
        restarts.update { it + 1 }
        withTimeoutOrNull(REFRESH_TIMEOUT_MS) { serverAnswers.first { it > before } }
    }

    private fun liveFeed(): Flow<WebOrdersFeed> {
        // Per listening session: the first answer from the server is "what was waiting when the app opened" — the
        // manager sees it on screen right away, so it is not announced. Only what arrives after that rings.
        var baselineDone = false
        return repository.observeRecent()
            .onEach { snapshot ->
                if (snapshot.fromCache) return@onEach
                serverAnswers.update { it + 1 }
                try {
                    if (!baselineDone) {
                        baselineDone = true
                        val (waiting, handled) = snapshot.orders.partition { it.status == WebOrderStatus.NEW }
                        settings.markSeen(
                            waitingIds = waiting.mapTo(HashSet()) { it.id },
                            handledIds = handled.mapTo(HashSet()) { it.id },
                        )
                        if (waiting.isEmpty()) notifier.cancel() // an old notification about orders that are done
                    } else {
                        notifier.announce(snapshot.orders)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // An alert that could not be shown must never take the list down with it.
                    Log.w(TAG, "Could not announce web orders: ${e.message}")
                }
            }
            .map<WebOrdersSnapshot, WebOrdersFeed> { snapshot ->
                WebOrdersFeed.Data(snapshot.orders).also { lastData = it }
            }
            // A refresh starts from the list that was on screen, not from an empty page.
            .onStart { emit(lastData ?: WebOrdersFeed.Loading) }
            .catch { e -> emit(WebOrdersFeed.Failed(failureMessage(e))) }
    }

    private fun failureMessage(e: Throwable): String {
        val denied = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        return if (denied) {
            "لا توجد صلاحية لقراءة طلبات الموقع بهذا الحساب — يجب أن تسمح قواعد Firestore للمدير بقراءة web_orders"
        } else {
            "تعذّر الاتصال بطلبات الموقع — تحقّق من الإنترنت ثم اسحب الشاشة لأسفل للتحديث"
        }
    }

    private companion object {
        const val TAG = "CadyWebOrders"
        const val STOP_TIMEOUT_MS = 5_000L
        const val REFRESH_TIMEOUT_MS = 8_000L
    }
}
