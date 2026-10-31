package com.cady.cadysalesapp.data.weborders

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** What the settings document holds right now; [config] is null while nothing has been saved. */
data class BotConfigSnapshot(val config: BotConfig?, val fromCache: Boolean)

/** The manager's items and pricing rules for the page's AI seller. The Firestore rules let only a manager read or write them. */
@Singleton
class BotConfigRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /** Live. Closes with the error if the listener is refused (for example the rules were not published yet). */
    fun observe(): Flow<BotConfigSnapshot> = callbackFlow {
        val registration = document().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            trySend(BotConfigSnapshot(snapshot.toBotConfig(), snapshot.metadata.isFromCache))
        }
        awaitClose { registration.remove() }
    }

    /**
     * True once the server has confirmed the save, false when there is no connection yet (Firestore keeps the
     * change and sends it when the phone is back online). A refused write (the rules) throws.
     */
    suspend fun save(config: BotConfig): Boolean {
        // The first available item is also written into the old single-price fields, so a Worker or Script that
        // has not been updated yet keeps working with that price until it is.
        val lead = config.items.firstOrNull { it.active } ?: config.items.firstOrNull()
        val task = document().set(
            mapOf(
                "enabled" to config.enabled,
                "validHours" to config.validHours,
                "voiceEnabled" to config.voiceEnabled,
                "items" to config.items.map { item ->
                    mapOf(
                        "id" to item.id,
                        "name" to item.name,
                        "price" to item.price,
                        "maxDiscount" to item.maxDiscount,
                        "step" to item.step,
                        "active" to item.active,
                        "note" to item.note,
                        "productId" to item.productId,
                    )
                },
                "price" to (lead?.price ?: BotDefaults.PRICE),
                "maxDiscount" to (lead?.maxDiscount ?: BotDefaults.MAX_DISCOUNT),
                "step" to (lead?.step ?: BotDefaults.STEP),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        return withTimeoutOrNull(SAVE_TIMEOUT_MS) {
            task.await()
            true
        } ?: false
    }

    private fun document(): DocumentReference = firestore.collection(COLLECTION).document(DOCUMENT)

    private companion object {
        const val COLLECTION = "bot_config"
        const val DOCUMENT = "pricing"
        const val SAVE_TIMEOUT_MS = 8_000L
    }
}
