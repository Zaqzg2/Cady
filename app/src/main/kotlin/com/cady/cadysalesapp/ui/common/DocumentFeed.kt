package com.cady.cadysalesapp.ui.common

import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.ReceiptRepository
import com.cady.cadysalesapp.ui.home.RecentActivityItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Invoices and receipts merged into one list, newest first. */
fun mergeDocuments(invoices: List<InvoiceEntity>, receipts: List<ReceiptEntity>): List<RecentActivityItem> =
    (invoices.map { RecentActivityItem.Invoice(it) } + receipts.map { RecentActivityItem.Receipt(it) })
        .sortedByDescending { it.date }

/**
 * THE one answer to "which documents may this account see": a manager sees every rep's, a rep only his own.
 *
 * Home's "آخر العمليات" and the documents log used to decide this separately — the log gave a manager
 * everyone's documents, Home read only the documents owned by the manager's own account — so a rep's
 * invoice showed up in the log and never on Home. Both screens now read this single flow.
 */
fun observeVisibleDocuments(
    accountRepository: AccountRepository,
    invoiceRepository: InvoiceRepository,
    receiptRepository: ReceiptRepository,
): Flow<List<RecentActivityItem>> =
    accountRepository.currentUser.flatMapLatest { user ->
        when {
            user == null -> flowOf(emptyList())
            user.role == UserRole.MANAGER -> combine(
                invoiceRepository.observeEveryone(),
                receiptRepository.observeEveryone(),
            ) { invoices, receipts -> mergeDocuments(invoices, receipts) }
            else -> combine(
                invoiceRepository.observeAll(user.id),
                receiptRepository.observeAll(user.id),
            ) { invoices, receipts -> mergeDocuments(invoices, receipts) }
        }
    }
