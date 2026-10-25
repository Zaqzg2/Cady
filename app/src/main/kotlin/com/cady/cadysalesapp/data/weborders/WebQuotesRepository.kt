package com.cady.cadysalesapp.data.weborders

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The offers the AI seller built (collection `quotes`). Read-only here: the Firestore rules give the app no write. */
@Singleton
class WebQuotesRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /** Live, newest first. Closes with the error if the listener is refused (the rules were not published yet). */
    fun observeRecent(limit: Int = RECENT_LIMIT): Flow<List<WebQuote>> = callbackFlow {
        val registration = firestore.collection(COLLECTION)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toWebQuote()
                        } catch (e: Exception) {
                            null // one odd document must not hide the others
                        }
                    }
                )
            }
        awaitClose { registration.remove() }
    }

    companion object {
        /** When the list is shorter than this it is every offer there is; a full list may hide older ones. */
        const val RECENT_LIMIT = 300
        private const val COLLECTION = "quotes"
    }
}
