package com.cady.cadysalesapp.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
) : ViewModel() {

    val products: StateFlow<List<ProductEntity>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveProduct(existing: ProductEntity?, name: String, price: Double, unit: String) {
        viewModelScope.launch {
            if (existing == null) {
                productRepository.createProduct(name, price, unit, imagePath = null)
            } else {
                productRepository.updateProduct(existing.copy(name = name, price = price, unit = unit))
            }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch { productRepository.deleteProduct(product) }
    }
}
