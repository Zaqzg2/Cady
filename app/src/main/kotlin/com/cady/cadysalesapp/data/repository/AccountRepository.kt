package com.cady.cadysalesapp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cady.cadysalesapp.data.local.dao.UserAccountDao
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** All user-facing messages here are in Arabic, matching every other screen. */
sealed class AccountException(message: String) : Exception(message) {
    class InvalidUsername : AccountException("اسم المستخدم يجب أن يكون بأحرف إنجليزية وأرقام فقط")
    class UsernameTaken : AccountException("اسم المستخدم مستخدم بالفعل")
    class WrongCredentials : AccountException("اسم المستخدم أو كلمة المرور غير صحيحة")
    class NetworkAndNoLocalCache : AccountException("لا يوجد اتصال بالإنترنت، ولا حساب محفوظ محليًا على هذا الجهاز")

    /** The fix for the current app's real bug: creating a manager account is only
        ever allowed once this check has run and come back negative. */
    class ManagerAlreadyExists : AccountException("يوجد حساب مدير مسجّل بالفعل — سجّل الدخول بدل إنشاء حساب جديد")
    class ManagerCheckUnavailable : AccountException("تعذّر التأكد من عدم وجود حساب مدير سابق — تحقّق من اتصال الإنترنت وأعد المحاولة")
}

@Singleton
class AccountRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userAccountDao: UserAccountDao,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val appContext: Context,
) {
    private val currentUserIdKey = stringPreferencesKey("current_user_id")
    private val usersCollection get() = firestore.collection("users")

    /** Null while logged out. Backed by DataStore so the session survives an app restart. */
    val currentUser: Flow<UserAccountEntity?> = dataStore.data
        .map { it[currentUserIdKey] }
        .distinctUntilChanged()
        .map { id -> id?.let { userAccountDao.getById(it) } }

    /**
     * Combines the local cache with a live Firestore query — a fresh install's
     * local table is always empty, so the local half alone would let the exact
     * bug back in. Throws ManagerCheckUnavailable rather than guessing when
     * offline, since a wrong "no manager exists" here is how the current bug happens.
     */
    suspend fun managerAlreadyExists(): Boolean {
        if (userAccountDao.hasAnyManagerLocally()) return true
        return try {
            val snapshot = usersCollection.whereEqualTo("role", "manager").limit(1).get().await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            throw AccountException.ManagerCheckUnavailable()
        }
    }

    suspend fun createManager(username: String, password: String, displayName: String): UserAccountEntity {
        validateUsername(username)
        if (managerAlreadyExists()) throw AccountException.ManagerAlreadyExists()
        return createAccountInternal(username, password, displayName, UserRole.MANAGER, repNumber = null, deviceName = null)
    }

    /** Called by an already-logged-in manager. Runs on a temporary secondary
        FirebaseApp so creating the rep's auth user never disturbs the manager's
        own signed-in session — ported directly from the current app's AccountService. */
    suspend fun createRep(
        username: String,
        password: String,
        displayName: String,
        repNumber: Int,
        deviceName: String?,
    ): UserAccountEntity {
        validateUsername(username)
        return createAccountInternal(
            username, password, displayName, UserRole.REP, repNumber, deviceName, useSecondaryApp = true
        )
    }

    suspend fun login(username: String, password: String): UserAccountEntity {
        val email = emailFor(username)
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw AccountException.WrongCredentials()
            val doc = usersCollection.document(uid).get().await()
            val account = doc.toUserAccountEntity(uid, fallbackUsername = username)
                // The Firestore doc never carries a password hash (never stored
                // server-side) — but a successful sign-in above just proved this
                // password correct, so that's what gets cached for offline fallback.
                .copy(passwordHash = sha256(password))
            userAccountDao.upsert(account)
            setCurrentUserId(account.id)
            // TODO(Phase 6): kick off SyncRepository.pullFromFirestore() here once it
            // exists, mirroring AccountService's post-login full pull so a fresh
            // install/reinstall repopulates Room immediately.
            account
        } catch (e: FirebaseNetworkException) {
            loginOffline(username, password)
        } catch (e: com.google.firebase.auth.FirebaseAuthException) {
            // Covers FirebaseAuthInvalidUserException (no account exists for this
            // username at all) as well as FirebaseAuthInvalidCredentialsException
            // (wrong password / malformed email) — both are just "wrong
            // credentials" from the UI's point of view, but previously only the
            // second one was caught here, so "no such user" fell through to a
            // generic, unhelpful error instead of this specific message.
            throw AccountException.WrongCredentials()
        }
    }

    private suspend fun loginOffline(username: String, password: String): UserAccountEntity {
        val local = userAccountDao.getByUsername(username) ?: throw AccountException.NetworkAndNoLocalCache()
        if (local.passwordHash != sha256(password)) throw AccountException.WrongCredentials()
        setCurrentUserId(local.id)
        return local
    }

    suspend fun logout() {
        firebaseAuth.signOut()
        dataStore.edit { it.remove(currentUserIdKey) }
    }

    private suspend fun createAccountInternal(
        username: String,
        password: String,
        displayName: String,
        role: UserRole,
        repNumber: Int?,
        deviceName: String?,
        useSecondaryApp: Boolean = false,
    ): UserAccountEntity {
        val email = emailFor(username)
        var secondaryApp: FirebaseApp? = null
        val auth = if (useSecondaryApp) {
            val app = FirebaseApp.initializeApp(
                appContext,
                FirebaseApp.getInstance().options,
                "SecondaryApp-${System.currentTimeMillis()}",
            )
            secondaryApp = app
            FirebaseAuth.getInstance(app)
        } else {
            firebaseAuth
        }

        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw IllegalStateException("Firebase did not return a UID")

            val account = UserAccountEntity(
                id = uid,
                username = username,
                passwordHash = sha256(password),
                displayName = displayName,
                role = role,
                repNumber = repNumber,
                deviceName = deviceName,
                isActive = true,
                lastSyncAt = null,
                createdAt = Instant.now(),
            )
            usersCollection.document(uid).set(account.toFirestoreMap()).await()
            userAccountDao.upsert(account)

            // First-ever account only: Firestore's rules require authentication
            // before any write succeeds, so this signs in on the MAIN session
            // right away rather than leaving the app stuck logged out.
            if (!useSecondaryApp) setCurrentUserId(account.id)

            return account
        } catch (e: FirebaseAuthUserCollisionException) {
            throw AccountException.UsernameTaken()
        } finally {
            secondaryApp?.delete()
        }
    }

    private suspend fun setCurrentUserId(userId: String) {
        dataStore.edit { it[currentUserIdKey] = userId }
    }

    private fun validateUsername(username: String) {
        // Matches the real Flutter app's _emailFor validation exactly
        // (account_service.dart) — it allows a trailing hyphen too, which this
        // regex previously didn't, and normalizes to lowercase before checking.
        if (!username.trim().lowercase().matches(Regex("^[a-z0-9_.-]+$"))) {
            throw AccountException.InvalidUsername()
        }
    }

    private fun emailFor(username: String) = "${username.trim().lowercase()}@cady-34220.firebaseapp.com"

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun UserAccountEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
        "username" to username,
        "displayName" to displayName,
        "role" to role.name.lowercase(),
        "repNumber" to repNumber,
        "deviceName" to deviceName,
        "isActive" to isActive,
        "lastSyncAt" to lastSyncAt?.let { Timestamp(it.epochSecond, it.nano) },
        "createdAt" to Timestamp(createdAt.epochSecond, createdAt.nano),
    )

    private fun DocumentSnapshot.toUserAccountEntity(uid: String, fallbackUsername: String): UserAccountEntity {
        if (!exists()) {
            // Auth succeeded but there's no matching Firestore profile — a genuinely
            // different situation from a wrong password (e.g. account creation
            // partially failed before its Firestore write). Worth its own clear
            // message rather than silently becoming "wrong credentials".
            throw IllegalStateException("تم التحقق من الحساب لكن تعذّر العثور على بيانات المستخدم بقاعدة البيانات")
        }
        return UserAccountEntity(
            id = uid,
            username = getString("username") ?: fallbackUsername,
            passwordHash = "", // never stored server-side; only meaningful in the local cache
            displayName = getString("displayName") ?: fallbackUsername,
            role = if (getString("role") == "manager") UserRole.MANAGER else UserRole.REP,
            repNumber = getLong("repNumber")?.toInt(),
            deviceName = getString("deviceName"),
            isActive = getBoolean("isActive") ?: true,
            lastSyncAt = getTimestamp("lastSyncAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) },
            createdAt = getTimestamp("createdAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
                ?: Instant.now(),
        )
    }
}
