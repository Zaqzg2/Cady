package com.cady.cadysalesapp.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.cady.cadysalesapp.data.sync.IncomingFileHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Lets the navigation host notice that a shared sync file is waiting. */
@HiltViewModel
class IncomingFileViewModel @Inject constructor(
    holder: IncomingFileHolder,
) : ViewModel() {
    val pending: StateFlow<Uri?> = holder.pending
}
