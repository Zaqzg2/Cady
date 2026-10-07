package com.cady.cadysalesapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Products are a shared catalog: every active user reads them, only a manager
 * writes them (see firestore.rules) — unlike Customer/Invoice/Receipt there is
 * deliberately no ownerUid column here.
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val price: Double,
    /** e.g. قطعة / كرتون / عبوة — a free-text unit label, not an enum, matching the current app. */
    val unit: String,
    /** File path in app-private storage. Unlike the Flutter app's Base64-in-Hive
        approach (needed only for web parity), a native-only app can store a real file
        and keep just its path here — smaller DB rows, no 33% Base64 size overhead. */
    val imagePath: String?,
    val syncStatus: SyncStatus,
)
