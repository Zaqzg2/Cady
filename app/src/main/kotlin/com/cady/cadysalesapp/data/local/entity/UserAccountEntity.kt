package com.cady.cadysalesapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Local offline-fallback cache of accounts whose real identity lives in Firebase Auth.
 * id == the Firebase Auth UID directly (no separate cloudUid indirection needed —
 * simpler than it sounds from the Flutter app's naming, since the UID already *is*
 * the local primary key end to end).
 */
@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey val id: String,
    val username: String,
    /** SHA-256 hex digest — checked locally only when Firebase Auth is unreachable. */
    val passwordHash: String,
    val displayName: String,
    val role: UserRole,
    val repNumber: Int?,
    val deviceName: String?,
    val isActive: Boolean,
    val lastSyncAt: Instant?,
    val createdAt: Instant,
)
