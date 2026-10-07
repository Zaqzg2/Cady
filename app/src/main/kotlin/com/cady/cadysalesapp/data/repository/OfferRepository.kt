package com.cady.cadysalesapp.data.repository

import com.cady.cadysalesapp.data.local.dao.OfferDao
import com.cady.cadysalesapp.data.local.entity.OfferEntity
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.data.sync.SyncRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Promotions — like the price list, only a manager writes them and everyone reads them. */
@Singleton
class OfferRepository @Inject constructor(
    private val offerDao: OfferDao,
    private val syncRepository: SyncRepository,
) {
    fun observeAll(): Flow<List<OfferEntity>> = offerDao.observeAll()

    suspend fun save(
        existing: OfferEntity?,
        productId: String,
        productName: String,
        percent: Double,
        startsAt: Instant,
        endsAt: Instant,
        isActive: Boolean,
    ): OfferEntity {
        val offer = OfferEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            productId = productId,
            productName = productName,
            percent = percent,
            startsAt = startsAt,
            endsAt = endsAt,
            isActive = isActive,
            syncStatus = SyncStatus.PENDING,
            updatedAt = Instant.now(),
        )
        offerDao.upsert(offer)
        syncRepository.pushOffer(offer)
        return offer
    }

    suspend fun setActive(offer: OfferEntity, active: Boolean) {
        save(offer, offer.productId, offer.productName, offer.percent, offer.startsAt, offer.endsAt, active)
    }

    suspend fun delete(offer: OfferEntity) {
        offerDao.deleteById(offer.id)
        syncRepository.deleteOfferRemote(offer.id)
    }
}
