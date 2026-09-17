package com.cady.cadysalesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE id = :id")
    suspend fun getById(id: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts")
    suspend fun getAll(): List<UserAccountEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM user_accounts)")
    suspend fun hasAnyLocally(): Boolean

    /**
     * Local half of the "already have a manager?" check (see AccountRepository).
     * This alone is NOT enough to safely gate "create new manager" — a fresh
     * install or wiped device has an empty local table even though a manager
     * account already exists in Firestore. The repository combines this with an
     * online Firestore existence check before allowing a new manager to be created,
     * which is exactly the guard the current Flutter app is missing.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM user_accounts WHERE role = 'MANAGER')")
    suspend fun hasAnyManagerLocally(): Boolean

    @Upsert
    suspend fun upsert(account: UserAccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<UserAccountEntity>)
}
