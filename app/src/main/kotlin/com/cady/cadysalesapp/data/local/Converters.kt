package com.cady.cadysalesapp.data.local

import androidx.room.TypeConverter
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.ReceiptMethod
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.data.local.entity.UserRole
import java.time.Instant

/**
 * All timestamps are stored as epoch millis (Long) — simple, timezone-unambiguous,
 * and directly comparable for the same last-write-wins merge logic the Flutter
 * app already used (compare updatedAt, never delete locally on pull-sync).
 */
class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun syncStatusToString(value: SyncStatus): String = value.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun invoiceKindToString(value: InvoiceKind): String = value.name

    @TypeConverter
    fun stringToInvoiceKind(value: String): InvoiceKind = InvoiceKind.valueOf(value)

    @TypeConverter
    fun paymentModeToString(value: PaymentMode): String = value.name

    @TypeConverter
    fun stringToPaymentMode(value: String): PaymentMode = PaymentMode.valueOf(value)

    @TypeConverter
    fun receiptMethodToString(value: ReceiptMethod): String = value.name

    @TypeConverter
    fun stringToReceiptMethod(value: String): ReceiptMethod = ReceiptMethod.valueOf(value)

    @TypeConverter
    fun userRoleToString(value: UserRole): String = value.name

    @TypeConverter
    fun stringToUserRole(value: String): UserRole = UserRole.valueOf(value)
}
