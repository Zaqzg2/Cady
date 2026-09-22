package com.cady.cadysalesapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.DataStats
import com.cady.cadysalesapp.data.repository.DataStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsDataViewModel @Inject constructor(
    private val repository: DataStatsRepository,
) : ViewModel() {

    private val _stats = MutableStateFlow<DataStats?>(null)
    val stats: StateFlow<DataStats?> = _stats

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _stats.value = repository.getStats() }
    }
}
