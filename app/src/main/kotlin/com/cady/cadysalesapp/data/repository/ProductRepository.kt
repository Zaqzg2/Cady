package com.cady.cadysalesapp.data.repository

import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.data.sync.SyncRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Shared catalog — same product list for every user, manager-writable only
    (see firestore.rules); there is deliberately no ownerUid parameter here. */
@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val syncRepository: SyncRepository,
) {
    fun observeAll(): Flow<List<ProductEntity>> = productDao.observeAll()

    suspend fun getById(id: String): ProductEntity? = productDao.getById(id)

    suspend fun createProduct(name: String, price: Double, unit: String, imagePath: String?): ProductEntity {
        val product = ProductEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            price = price,
            unit = unit,
            imagePath = imagePath,
            syncStatus = SyncStatus.PENDING,
        )
        productDao.upsert(product)
        syncRepository.pushProduct(product)
        return product
    }

    suspend fun updateProduct(product: ProductEntity) {
        val updated = product.copy(syncStatus = SyncStatus.PENDING)
        productDao.upsert(updated)
        syncRepository.pushProduct(updated)
    }

    suspend fun deleteProduct(product: ProductEntity) {
        productDao.delete(product)
        // TODO(Phase 6 polish): push a Firestore delete too — deleting a
        // product locally currently doesn't remove it from the shared catalog
        // other reps' devices pull, only from this device.
    }
}
