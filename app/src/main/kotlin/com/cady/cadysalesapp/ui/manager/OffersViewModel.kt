package com.cady.cadysalesapp.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.OfferEntity
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.repository.OfferRepository
import com.cady.cadysalesapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class OffersViewModel @Inject constructor(
    private val offerRepository: OfferRepository,
    productRepository: ProductRepository,
) : ViewModel() {

    val offers: StateFlow<List<OfferEntity>> = offerRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Saves a new offer, or the edited [existing] one. The period runs from the start of [firstDay] to the
     * very end of [lastDay], so "من 1/10 إلى 15/10" includes the whole of the 15th.
     */
    fun save(
        existing: OfferEntity?,
        product: ProductEntity,
        percent: Double,
        firstDay: LocalDate,
        lastDay: LocalDate,
        isActive: Boolean,
    ) {
        val zone = ZoneId.systemDefault()
        val startsAt = firstDay.atStartOfDay(zone).toInstant()
        val endsAt = lastDay.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
        viewModelScope.launch {
            offerRepository.save(existing, product.id, product.name, percent, startsAt, endsAt, isActive)
        }
    }

    fun setActive(offer: OfferEntity, active: Boolean) {
        viewModelScope.launch { offerRepository.setActive(offer, active) }
    }

    fun delete(offer: OfferEntity) {
        viewModelScope.launch { offerRepository.delete(offer) }
    }
}
