package com.cady.cadysalesapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A promotion: a percentage off ONE product during a period (from [startsAt] to [endsAt],
 * both ends included). Like the product list it belongs to, it is the manager's to write and
 * every user reads it — so there is deliberately no ownerUid.
 *
 * [productName] is copied in so the offers list can still say what the offer was for after
 * the product itself is gone. [isActive] is the manager's manual on/off switch on top of the
 * dates (a paused offer never applies, whatever the date).
 */
@Entity(
    tableName = "offers",
    indices = [Index("productId"), Index("syncStatus")],
)
data class OfferEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val percent: Double,
    val startsAt: Instant,
    val endsAt: Instant,
    val isActive: Boolean,
    val syncStatus: SyncStatus,
    val updatedAt: Instant,
)
