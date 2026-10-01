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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

/**
 * The one place an entity becomes JSON and back. Backups (JSON Lines inside the
 * zip) and the manual sync files (JSON arrays) both go through these functions,
 * so the two can never drift into different shapes of "an invoice".
 *
 * Local file paths (signatures, product images, logo) are machine-specific, so
 * every function that touches one takes a [PathMapper]: backups translate them
 * to zip-relative names and back, manual sync files drop them entirely.
 */
typealias PathMapper = (String?) -> String?

/** Manual sync files never carry file paths — another device can't use them. */
internal val NoPaths: PathMapper = { null }

// ---- small, null-safe readers (org.json's own optString returns the text "null" for JSON null) ----

internal fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

internal fun JSONObject.requireString(key: String): String {
    if (isNull(key)) throw JSONException("Missing field: $key")
    return getString(key)
}

internal fun JSONObject.requireId(key: String): String {
    val value = requireString(key)
    if (value.isBlank()) throw JSONException("Blank field: $key")
    return value
}

internal fun JSONObject.doubleOr(key: String, default: Double): Double =
    if (isNull(key)) default else getDouble(key)

internal fun JSONObject.doubleOrNull(key: String): Double? =
    if (isNull(key)) null else getDouble(key)

internal fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val value = optString(i, "")
        if (value.isNotBlank()) out.add(value)
    }
    return out
}

private fun parseSyncStatus(raw: String?): SyncStatus =
    if (raw.equals("synced", ignoreCase = true)) SyncStatus.SYNCED else SyncStatus.PENDING

private fun parseKind(raw: String?): InvoiceKind =
    if (raw.equals("sale_return", ignoreCase = true)) InvoiceKind.SALE_RETURN else InvoiceKind.SALE

private fun parsePaymentMode(raw: String?): PaymentMode =
    if (raw.equals("credit", ignoreCase = true)) PaymentMode.CREDIT else PaymentMode.CASH

private fun parseMethod(raw: String?): ReceiptMethod =
    if (raw.equals("transfer", ignoreCase = true)) ReceiptMethod.TRANSFER else ReceiptMethod.CASH

// ---- customers ----

internal fun CustomerEntity.toJson(): JSONObject {
    val o = JSONObject()
    o.put("id", id)
    o.put("name", name)
    o.put("phone", phone ?: JSONObject.NULL)
    o.put("address", address ?: JSONObject.NULL)
    o.put("openingBalance", openingBalance)
    o.put("isPinned", isPinned)
    o.put("isActive", isActive)
    o.put("creditLimit", creditLimit ?: JSONObject.NULL)
    o.put("notes", notes ?: JSONObject.NULL)
    o.put("syncStatus", syncStatus.name.lowercase())
    o.put("updatedAt", updatedAt.toEpochMilli())
    o.put("ownerUid", ownerUid)
    return o
}

internal fun JSONObject.toCustomerEntity(): CustomerEntity = CustomerEntity(
    id = requireId("id"),
    name = requireString("name"),
    phone = stringOrNull("phone"),
    address = stringOrNull("address"),
    openingBalance = doubleOr("openingBalance", 0.0),
    isPinned = optBoolean("isPinned", false),
    isActive = optBoolean("isActive", true),
    creditLimit = doubleOrNull("creditLimit"),
    notes = stringOrNull("notes"),
    syncStatus = parseSyncStatus(stringOrNull("syncStatus")),
    updatedAt = Instant.ofEpochMilli(getLong("updatedAt")),
    ownerUid = requireId("ownerUid"),
)

// ---- products ----

internal fun ProductEntity.toJson(paths: PathMapper): JSONObject {
    val o = JSONObject()
    o.put("id", id)
    o.put("name", name)
    o.put("price", price)
    o.put("unit", unit)
    o.put("imagePath", paths(imagePath) ?: JSONObject.NULL)
    o.put("syncStatus", syncStatus.name.lowercase())
    return o
}

internal fun JSONObject.toProductEntity(paths: PathMapper): ProductEntity = ProductEntity(
    id = requireId("id"),
    name = requireString("name"),
    price = doubleOr("price", 0.0),
    unit = stringOrNull("unit").orEmpty(),
    imagePath = paths(stringOrNull("imagePath")),
    syncStatus = parseSyncStatus(stringOrNull("syncStatus")),
)

// ---- invoices (+ their line items) ----

internal fun InvoiceItemEntity.toJson(): JSONObject {
    val o = JSONObject()
    o.put("id", id)
    o.put("productId", productId)
    o.put("productName", productName)
    o.put("price", price)
    o.put("quantity", quantity)
    return o
}

internal fun InvoiceEntity.toJson(items: List<InvoiceItemEntity>, paths: PathMapper): JSONObject {
    val o = JSONObject()
    o.put("id", id)
    o.put("docNumber", docNumber)
    o.put("date", date.toEpochMilli())
    o.put("kind", kind.name.lowercase())
    o.put("customerId", customerId)
    o.put("customerName", customerName)
    o.put("paymentMode", paymentMode.name.lowercase())
    o.put("discountPercent", discountPercent)
    o.put("discountAmount", discountAmount)
    o.put("notes", notes ?: JSONObject.NULL)
    o.put("signaturePath", paths(signaturePath) ?: JSONObject.NULL)
    o.put("repName", repName ?: JSONObject.NULL)
    o.put("balanceAfter", balanceAfter)
    o.put("isPrinted", isPrinted)
    o.put("isShared", isShared)
    o.put("isPinned", isPinned)
    o.put("syncStatus", syncStatus.name.lowercase())
    o.put("ownerUid", ownerUid)
    val array = JSONArray()
    for (item in items) array.put(item.toJson())
    o.put("items", array)
    return o
}

internal fun JSONObject.toInvoiceEntity(paths: PathMapper): InvoiceEntity = InvoiceEntity(
    id = requireId("id"),
    docNumber = requireString("docNumber"),
    date = Instant.ofEpochMilli(getLong("date")),
    kind = parseKind(stringOrNull("kind")),
    customerId = requireId("customerId"),
    customerName = stringOrNull("customerName").orEmpty(),
    paymentMode = parsePaymentMode(stringOrNull("paymentMode")),
    discountPercent = doubleOr("discountPercent", 0.0),
    discountAmount = doubleOr("discountAmount", 0.0),
    notes = stringOrNull("notes"),
    signaturePath = paths(stringOrNull("signaturePath")),
    repName = stringOrNull("repName"),
    balanceAfter = doubleOr("balanceAfter", 0.0),
    isPrinted = optBoolean("isPrinted", false),
    isShared = optBoolean("isShared", false),
    isPinned = optBoolean("isPinned", false),
    syncStatus = parseSyncStatus(stringOrNull("syncStatus")),
    ownerUid = requireId("ownerUid"),
)

/** Item ids fall back to "invoiceId-index" — the same convention the Firestore pull uses. */
internal fun JSONObject.toInvoiceItems(invoiceId: String): List<InvoiceItemEntity> {
    val array = optJSONArray("items") ?: return emptyList()
    val out = ArrayList<InvoiceItemEntity>(array.length())
    for (index in 0 until array.length()) {
        val o = array.getJSONObject(index)
        val storedId = o.stringOrNull("id")
        out.add(
            InvoiceItemEntity(
                id = if (storedId.isNullOrBlank()) "$invoiceId-$index" else storedId,
                invoiceId = invoiceId,
                productId = o.stringOrNull("productId").orEmpty(),
                productName = o.stringOrNull("productName").orEmpty(),
                price = o.doubleOr("price", 0.0),
                quantity = o.doubleOr("quantity", 0.0),
            )
        )
    }
    return out
}

// ---- receipts ----

internal fun ReceiptEntity.toJson(paths: PathMapper): JSONObject {
    val o = JSONObject()
    o.put("id", id)
    o.put("docNumber", docNumber)
    o.put("date", date.toEpochMilli())
    o.put("amount", amount)
    o.put("method", method.name.lowercase())
    o.put("customerId", customerId)
    o.put("customerName", customerName)
    o.put("repSignaturePath", paths(repSignaturePath) ?: JSONObject.NULL)
    o.put("repName", repName ?: JSONObject.NULL)
    o.put("notes", notes ?: JSONObject.NULL)
    o.put("balanceAfter", balanceAfter)
    o.put("isPrinted", isPrinted)
    o.put("isShared", isShared)
    o.put("isPinned", isPinned)
    o.put("syncStatus", syncStatus.name.lowercase())
    o.put("ownerUid", ownerUid)
    return o
}

internal fun JSONObject.toReceiptEntity(paths: PathMapper): ReceiptEntity = ReceiptEntity(
    id = requireId("id"),
    docNumber = requireString("docNumber"),
    date = Instant.ofEpochMilli(getLong("date")),
    amount = doubleOr("amount", 0.0),
    method = parseMethod(stringOrNull("method")),
    customerId = requireId("customerId"),
    customerName = stringOrNull("customerName").orEmpty(),
    repSignaturePath = paths(stringOrNull("repSignaturePath")),
    repName = stringOrNull("repName"),
    notes = stringOrNull("notes"),
    balanceAfter = doubleOr("balanceAfter", 0.0),
    isPrinted = optBoolean("isPrinted", false),
    isShared = optBoolean("isShared", false),
    isPinned = optBoolean("isPinned", false),
    syncStatus = parseSyncStatus(stringOrNull("syncStatus")),
    ownerUid = requireId("ownerUid"),
)
