package com.cady.cadysalesapp.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.AccountException
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.sync.ManagerCloudService
import com.cady.cadysalesapp.data.sync.RepDayActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepRow(val account: UserAccountEntity, val today: RepDayActivity?)

data class ManagerUsersState(
    val reps: List<RepRow> = emptyList(),
    val refreshing: Boolean = false,
    /** True once today's figures were read from the cloud; false = they could not be (offline). */
    val todayAvailable: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ManagerUsersViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cloudService: ManagerCloudService,
) : ViewModel() {

    private data class Local(
        val today: Map<String, RepDayActivity>? = null,
        val refreshing: Boolean = false,
        val message: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ManagerUsersState> = combine(accountRepository.observeReps(), local) { reps, l ->
        val ordered = reps.sortedWith(compareBy({ it.repNumber ?: Int.MAX_VALUE }, { it.displayName }))
        ManagerUsersState(
            reps = ordered.map { rep ->
                // A rep with nothing in the cloud today really has zero; "unknown" is only when
                // the cloud could not be read at all.
                RepRow(rep, if (l.today == null) null else (l.today[rep.id] ?: RepDayActivity(0, 0)))
            },
            refreshing = l.refreshing,
            todayAvailable = l.today != null,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerUsersState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true, message = null) }
            var message: String? = null
            try {
                accountRepository.refreshRepsFromCloud()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "تعذّر تحديث قائمة المندوبين من السحابة — تحقّق من الاتصال بالإنترنت"
            }
            val today = try {
                cloudService.todayActivityByRep()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            local.update { it.copy(today = today, refreshing = false, message = message) }
        }
    }

    /** [onResult] gets null on success, otherwise the reason — shown inside the dialog that asked. */
    fun addRep(
        username: String,
        password: String,
        displayName: String,
        repNumber: Int,
        deviceName: String?,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                accountRepository.createRep(username.trim(), password, displayName.trim(), repNumber, deviceName?.trim()?.ifEmpty { null })
                onResult(null)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountException) {
                onResult(e.message)
            } catch (e: Exception) {
                onResult("تعذّر إنشاء الحساب: ${e.message ?: "خطأ غير متوقع"}")
            }
        }
    }

    fun saveProfile(rep: UserAccountEntity, displayName: String, repNumber: Int?, deviceName: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                accountRepository.updateRepProfile(rep.id, displayName.trim(), repNumber, deviceName?.trim()?.ifEmpty { null })
                onResult(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult("تعذّر الحفظ — تحقّق من الاتصال بالإنترنت")
            }
        }
    }

    fun setActive(rep: UserAccountEntity, active: Boolean) {
        viewModelScope.launch {
            try {
                accountRepository.setRepActive(rep.id, active)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(message = "تعذّر تغيير حالة الحساب — تحقّق من الاتصال بالإنترنت") }
            }
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }
}
