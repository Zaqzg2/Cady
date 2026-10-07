package com.cady.cadysalesapp.data.sync

import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.OfferEntity
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptMethod
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Every *ToMap()/from*() pair here is the single source of truth for the
 * Firestore document shape — reused identically by SyncRepository's live
 * per-write push/pull AND by ManagerSyncService's manual JSON file exchange
 * (Phase 6's second half), so the two channels can never silently drift into
 * incompatible payload shapes.
 *
 * READING IS DELIBERATELY FORGIVING (phase 8). The collections are shared with the older
 * Flutter app and with hand-edited data, and the Firestore SDK's typed getters (getTimestamp,
 * getString, getDouble…) THROW when a field holds a different type than asked for — one such
 * document used to abort a whole pull (the manager's "Firebase sync fails, no invoices") and,
 * inside the live listener, crash the app (the "النشاط المباشر" closing). Every read below
 * accepts the shapes that really occur (a date as Timestamp / epoch number / ISO text, a number
 * as number or text…) and falls back to a default instead of throwing.
 */

// ------------------------------------------------------------------ forgiving readers

private fun DocumentSnapshot.str(field: String): String? = when (val v = get(field)) {
    null -> null
    is String -> v
    else -> v.toString()
}

private fun DocumentSnapshot.dbl(field: String): Double? = anyToDouble(get(field))

private fun DocumentSnapshot.bool(field: String): Boolean? = when (val v = get(field)) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> when (v.trim().lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }
    else -> null
}

private fun DocumentSnapshot.instant(field: String): Instant? = anyToInstant(get(field))

internal fun anyToDouble(raw: Any?): Double? = when (raw) {
    is Number -> raw.toDouble()
    is String -> raw.trim().replace(",", "").toDoubleOrNull()
    else -> null
}

/** A moment from whatever a document holds: Timestamp, Date, epoch seconds/millis, or ISO text. */
internal fun anyToInstant(raw: Any?): Instant? = when (raw) {
    is Timestamp -> Instant.ofEpochSecond(raw.seconds, raw.nanoseconds.toLong())
    is java.util.Date -> raw.toInstant()
    is Number -> epochToInstant(raw.toLong())
    is String -> parseInstantText(raw)
    else -> null
}

private fun epochToInstant(value: Long): Instant =
    // Anything this large can only be milliseconds (seconds would be year 5138+).
    if (value > 100_000_000_000L) Instant.ofEpochMilli(value) else Instant.ofEpochSecond(value)

private fun parseInstantText(text: String): Instant? {
    val t = text.trim()
    if (t.isEmpty()) return null
    t.toLongOrNull()?.let { return epochToInstant(it) }
    val zone = ZoneId.systemDefault()
    return try {
        Instant.parse(t)
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(t).toInstant()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(t).atZone(zone).toInstant()
            } catch (_: Exception) {
                try {
                    LocalDate.parse(t).atStartOfDay(zone).toInstant()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}

// ------------------------------------------------------------------ customers

fun CustomerEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "phone" to phone,
    "address" to address,
    "openingBalance" to openingBalance,
    "isPinned" to isPinned,
    "isActive" to isActive,
    "creditLimit" to creditLimit,
    "notes" to notes,
    "updatedAt" to updatedAt.toTimestamp(),
    "ownerUid" to ownerUid,
)

fun DocumentSnapshot.toCustomerEntity(): CustomerEntity? {
    if (!exists()) return null
    return CustomerEntity(
        id = id,
        name = str("name") ?: return null,
        phone = str("phone"),
        address = str("address"),
        openingBalance = dbl("openingBalance") ?: 0.0,
        isPinned = bool("isPinned") ?: false,
        isActive = bool("isActive") ?: true,
        creditLimit = dbl("creditLimit"),
        notes = str("notes"),
        syncStatus = SyncStatus.SYNCED,
        // A missing stamp must lose a last-write-wins comparison, never win it.
        updatedAt = instant("updatedAt") ?: Instant.EPOCH,
        ownerUid = str("ownerUid") ?: return null,
    )
}

// ------------------------------------------------------------------ products

fun ProductEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "price" to price,
    "unit" to unit,
    "imagePath" to imagePath,
)

fun DocumentSnapshot.toProductEntity(): ProductEntity? {
    if (!exists()) return null
    return ProductEntity(
        id = id,
        name = str("name") ?: return null,
        price = dbl("price") ?: 0.0,
        unit = str("unit").orEmpty(),
        imagePath = str("imagePath"),
        syncStatus = SyncStatus.SYNCED,
    )
}

// ------------------------------------------------------------------ offers

fun OfferEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "productId" to productId,
    "productName" to productName,
    "percent" to percent,
    "startsAt" to startsAt.toTimestamp(),
    "endsAt" to endsAt.toTimestamp(),
    "isActive" to isActive,
    "updatedAt" to updatedAt.toTimestamp(),
)

fun DocumentSnapshot.toOfferEntity(): OfferEntity? {
    if (!exists()) return null
    return OfferEntity(
        id = id,
        productId = str("productId") ?: return null,
        productName = str("productName").orEmpty(),
        percent = dbl("percent") ?: return null,
        startsAt = instant("startsAt") ?: return null,
        endsAt = instant("endsAt") ?: return null,
        isActive = bool("isActive") ?: true,
        syncStatus = SyncStatus.SYNCED,
        updatedAt = instant("updatedAt") ?: Instant.EPOCH,
    )
}

// ------------------------------------------------------------------ invoices

fun InvoiceEntity.toFirestoreMap(items: List<InvoiceItemEntity>): Map<String, Any?> = mapOf(
    "docNumber" to docNumber,
    "date" to date.toTimestamp(),
    "kind" to kind.name.lowercase(),
    "customerId" to customerId,
    "customerName" to customerName,
    "paymentMode" to paymentMode.name.lowercase(),
    "discountPercent" to discountPercent,
    "discountAmount" to discountAmount,
    "notes" to notes,
    "signaturePath" to signaturePath,
    "repName" to repName,
    "balanceAfter" to balanceAfter,
    "isPrinted" to isPrinted,
    "isShared" to isShared,
    "isPinned" to isPinned,
    "ownerUid" to ownerUid,
    "items" to items.map {
        mapOf("productId" to it.productId, "productName" to it.productName, "price" to it.price, "quantity" to it.quantity)
    },
)

private fun isReturnKind(text: String?): Boolean =
    when (text?.trim()?.lowercase()) {
        "sale_return", "salereturn", "sale-return", "return", "returns", "refund", "مرتجع" -> true
        else -> false
    }

private fun isCreditMode(text: String?): Boolean =
    when (text?.trim()?.lowercase()) {
        "credit", "deferred", "later", "آجل", "اجل" -> true
        else -> false
    }

fun DocumentSnapshot.toInvoiceEntity(): InvoiceEntity? {
    if (!exists()) return null
    return InvoiceEntity(
        id = id,
        docNumber = str("docNumber") ?: return null,
        date = instant("date") ?: Instant.now(),
        kind = if (isReturnKind(str("kind"))) InvoiceKind.SALE_RETURN else InvoiceKind.SALE,
        customerId = str("customerId") ?: return null,
        customerName = str("customerName").orEmpty(),
        paymentMode = if (isCreditMode(str("paymentMode"))) PaymentMode.CREDIT else PaymentMode.CASH,
        discountPercent = dbl("discountPercent") ?: 0.0,
        discountAmount = dbl("discountAmount") ?: 0.0,
        notes = str("notes"),
        signaturePath = str("signaturePath"),
        repName = str("repName"),
        balanceAfter = dbl("balanceAfter") ?: 0.0,
        isPrinted = bool("isPrinted") ?: false,
        isShared = bool("isShared") ?: false,
        isPinned = bool("isPinned") ?: false,
        syncStatus = SyncStatus.SYNCED,
        ownerUid = str("ownerUid") ?: return null,
    )
}

fun DocumentSnapshot.toInvoiceItemEntities(invoiceId: String): List<InvoiceItemEntity> {
    val rawItems = get("items") as? List<*> ?: return emptyList()
    return rawItems.mapIndexedNotNull { index, entry ->
        val raw = entry as? Map<*, *> ?: return@mapIndexedNotNull null
        InvoiceItemEntity(
            id = "$invoiceId-$index",
            invoiceId = invoiceId,
            productId = (raw["productId"] as? String).orEmpty(),
            productName = (raw["productName"] as? String).orEmpty(),
            price = anyToDouble(raw["price"]) ?: 0.0,
            quantity = anyToDouble(raw["quantity"]) ?: 0.0,
        )
    }
}

// ------------------------------------------------------------------ receipts

fun ReceiptEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "docNumber" to docNumber,
    "date" to date.toTimestamp(),
    "amount" to amount,
    "method" to method.name.lowercase(),
    "customerId" to customerId,
    "customerName" to customerName,
    "repSignaturePath" to repSignaturePath,
    "repName" to repName,
    "notes" to notes,
    "balanceAfter" to balanceAfter,
    "isPrinted" to isPrinted,
    "isShared" to isShared,
    "isPinned" to isPinned,
    "ownerUid" to ownerUid,
)

fun DocumentSnapshot.toReceiptEntity(): ReceiptEntity? {
    if (!exists()) return null
    return ReceiptEntity(
        id = id,
        docNumber = str("docNumber") ?: return null,
        date = instant("date") ?: Instant.now(),
        amount = dbl("amount") ?: 0.0,
        method = if (str("method")?.trim()?.lowercase() == "transfer") ReceiptMethod.TRANSFER else ReceiptMethod.CASH,
        customerId = str("customerId") ?: return null,
        customerName = str("customerName").orEmpty(),
        repSignaturePath = str("repSignaturePath"),
        repName = str("repName"),
        notes = str("notes"),
        balanceAfter = dbl("balanceAfter") ?: 0.0,
        isPrinted = bool("isPrinted") ?: false,
        isShared = bool("isShared") ?: false,
        isPinned = bool("isPinned") ?: false,
        syncStatus = SyncStatus.SYNCED,
        ownerUid = str("ownerUid") ?: return null,
    )
}

private fun Instant.toTimestamp() = Timestamp(epochSecond, nano)
