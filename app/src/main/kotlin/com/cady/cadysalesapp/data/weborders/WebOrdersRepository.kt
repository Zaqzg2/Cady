package com.cady.cadysalesapp.data.weborders

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** What the orders collection holds right now, and whether that is only what this phone remembered. */
data class WebOrdersSnapshot(val orders: List<WebOrder>, val fromCache: Boolean)

/**
 * The orders customers placed on the public sales page (collection `web_orders`). The page only ever adds
 * documents; the manager's app reads them and moves their `status` along — the Firestore rules decide who may.
 *
 * Newest first by `createdAt`. The page's assistant is what writes these documents, so if it does not stamp
 * `createdAt` the ordered query finds nothing even though orders exist; the server's "empty" answer then
 * makes both reads fall back to the same query without the ordering (the screen sorts what it gets).
 */
@Singleton
class WebOrdersRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /** Live: an order appears here the moment Firestore has it. Closes with the error if the listener is refused. */
    fun observeRecent(limit: Int = RECENT_LIMIT): Flow<WebOrdersSnapshot> = callbackFlow {
        var registration: ListenerRegistration? = null

        fun attach(ordered: Boolean) {
            registration?.remove()
            registration = query(ordered, limit).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                if (ordered && snapshot.isEmpty && !snapshot.metadata.isFromCache) {
                    attach(false)
                    return@addSnapshotListener
                }
                trySend(WebOrdersSnapshot(snapshot.toOrders(), snapshot.metadata.isFromCache))
            }
        }

        attach(true)
        awaitClose { registration?.remove() }
    }

    /** One read straight from the server — what the background check uses. Throws when it cannot get an answer. */
    suspend fun fetchRecent(limit: Int = CHECK_LIMIT): List<WebOrder> {
        val ordered = query(true, limit).get(Source.SERVER).await()
        val snapshot = if (ordered.isEmpty) query(false, limit).get(Source.SERVER).await() else ordered
        return snapshot.toOrders()
    }

    /**
     * Moves one order to [status]. Returns true once the server has confirmed it, false when there is no
     * connection yet: Firestore keeps the change and sends it as soon as the phone is online, and the list
     * already shows it — so the screen must not hang waiting for an answer that cannot come.
     * A refused write (the rules) throws.
     */
    suspend fun setStatus(orderId: String, status: WebOrderStatus): Boolean {
        val task = firestore.collection(COLLECTION).document(orderId).update(
            mapOf(
                "status" to status.key,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        return withTimeoutOrNull(SAVE_TIMEOUT_MS) {
            task.await()
            true
        } ?: false
    }

    private fun query(ordered: Boolean, limit: Int): Query {
        val collection = firestore.collection(COLLECTION)
        return if (ordered) {
            collection.orderBy("createdAt", Query.Direction.DESCENDING).limit(limit.toLong())
        } else {
            collection.limit(limit.toLong())
        }
    }

    private fun QuerySnapshot.toOrders(): List<WebOrder> =
        documents.mapNotNull { doc ->
            try {
                doc.toWebOrder()
            } catch (e: Exception) {
                null // one odd document must not hide the others
            }
        }.newestFirst()

    private companion object {
        const val COLLECTION = "web_orders"
        const val RECENT_LIMIT = 150
        const val CHECK_LIMIT = 40
        const val SAVE_TIMEOUT_MS = 8_000L
    }
}
