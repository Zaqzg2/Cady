package com.cady.cadysalesapp.data.local.entity

/** Whether a record has been picked up by the manager yet (Firebase-auto or manual-file channel). */
enum class SyncStatus { PENDING, SYNCED }

enum class InvoiceKind { SALE, SALE_RETURN }

enum class PaymentMode { CASH, CREDIT }

enum class ReceiptMethod { CASH, TRANSFER }

enum class UserRole { MANAGER, REP }
