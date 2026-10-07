package com.cady.cadysalesapp.domain

import java.time.Instant

/** One line of a customer account statement — computed, never stored;
    mirrors the current app's LedgerEntry model. */
data class LedgerRow(
    val date: Instant,
    val description: String,
    val docNumber: String?,
    val debit: Double,
    val credit: Double,
    val runningBalance: Double,
)
