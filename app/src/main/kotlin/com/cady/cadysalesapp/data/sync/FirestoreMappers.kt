package com.cady.cadysalesapp.data.sync

import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptMethod
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

/**
 * Every *ToMap()/from*() pair here is the single source of truth for the
 * Firestore document shape — reused identically by SyncRepository's live
 * per-write push/pull AND by ManagerSyncService's manual JSON file exchange
 * (Phase 6's second half), so the two channels can never silently drift into
 * incompatible payload shapes.
 */

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
        name = getString("name") ?: return null,
        phone = getString("phone"),
        address = getString("address"),
        openingBalance = getDouble("openingBalance") ?: 0.0,
        isPinned = getBoolean("isPinned") ?: false,
        isActive = getBoolean("isActive") ?: true,
        creditLimit = getDouble("creditLimit"),
        notes = getString("notes"),
        syncStatus = SyncStatus.SYNCED,
        updatedAt = getTimestamp("updatedAt")?.toInstant() ?: Instant.now(),
        ownerUid = getString("ownerUid") ?: return null,
    )
}

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
        name = getString("name") ?: return null,
        price = getDouble("price") ?: 0.0,
        unit = getString("unit").orEmpty(),
        imagePath = getString("imagePath"),
        syncStatus = SyncStatus.SYNCED,
    )
}

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

fun DocumentSnapshot.toInvoiceEntity(): InvoiceEntity? {
    if (!exists()) return null
    return InvoiceEntity(
        id = id,
        docNumber = getString("docNumber") ?: return null,
        date = getTimestamp("date")?.toInstant() ?: Instant.now(),
        kind = if (getString("kind") == "sale_return") InvoiceKind.SALE_RETURN else InvoiceKind.SALE,
        customerId = getString("customerId") ?: return null,
        customerName = getString("customerName").orEmpty(),
        paymentMode = if (getString("paymentMode") == "credit") PaymentMode.CREDIT else PaymentMode.CASH,
        discountPercent = getDouble("discountPercent") ?: 0.0,
        discountAmount = getDouble("discountAmount") ?: 0.0,
        notes = getString("notes"),
        signaturePath = getString("signaturePath"),
        repName = getString("repName"),
        balanceAfter = getDouble("balanceAfter") ?: 0.0,
        isPrinted = getBoolean("isPrinted") ?: false,
        isShared = getBoolean("isShared") ?: false,
        isPinned = getBoolean("isPinned") ?: false,
        syncStatus = SyncStatus.SYNCED,
        ownerUid = getString("ownerUid") ?: return null,
    )
}

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toInvoiceItemEntities(invoiceId: String): List<InvoiceItemEntity> {
    val rawItems = get("items") as? List<Map<String, Any?>> ?: return emptyList()
    return rawItems.mapIndexed { index, raw ->
        InvoiceItemEntity(
            id = "$invoiceId-$index",
            invoiceId = invoiceId,
            productId = raw["productId"] as? String ?: "",
            productName = raw["productName"] as? String ?: "",
            price = (raw["price"] as? Number)?.toDouble() ?: 0.0,
            quantity = (raw["quantity"] as? Number)?.toDouble() ?: 0.0,
        )
    }
}

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
        docNumber = getString("docNumber") ?: return null,
        date = getTimestamp("date")?.toInstant() ?: Instant.now(),
        amount = getDouble("amount") ?: 0.0,
        method = if (getString("method") == "transfer") ReceiptMethod.TRANSFER else ReceiptMethod.CASH,
        customerId = getString("customerId") ?: return null,
        customerName = getString("customerName").orEmpty(),
        repSignaturePath = getString("repSignaturePath"),
        repName = getString("repName"),
        notes = getString("notes"),
        balanceAfter = getDouble("balanceAfter") ?: 0.0,
        isPrinted = getBoolean("isPrinted") ?: false,
        isShared = getBoolean("isShared") ?: false,
        isPinned = getBoolean("isPinned") ?: false,
        syncStatus = SyncStatus.SYNCED,
        ownerUid = getString("ownerUid") ?: return null,
    )
}

private fun Instant.toTimestamp() = Timestamp(epochSecond, nano)
private fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanoseconds.toLong())
