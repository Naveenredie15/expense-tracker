package com.expensetracker.ui.screen.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.entity.Category
import com.expensetracker.repository.CategoryRepository
import com.expensetracker.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val categoryTransactionCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()
    
    init {
        loadCategories()
    }
    
    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                categoryRepository.getAllCategories().collect { categories ->
                    // Load transaction counts for each category
                    val transactionCounts = mutableMapOf<String, Int>()
                    
                    // Get all transactions to count by category
                    transactionRepository.getAllTransactions().first().let { transactions ->
                        // Count default categories
                        categories.forEach { category ->
                            transactionCounts[category.name] = transactions.count { it.category == category.name }
                        }
                        
                        // Count uncategorized transactions
                        val uncategorizedCount = transactions.count { it.category == null }
                        if (uncategorizedCount > 0) {
                            transactionCounts["Uncategorized"] = uncategorizedCount
                        }
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        categories = categories,
                        categoryTransactionCounts = transactionCounts,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }
    
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            try {
                if (!category.isDefault) {
                    categoryRepository.deleteCategory(category)
                    // Refresh data
                    loadCategories()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
    
    fun addCategory(name: String, color: String) {
        viewModelScope.launch {
            try {
                val newCategory = Category(
                    name = name,
                    color = color,
                    icon = "category",
                    isDefault = false
                )
                categoryRepository.insertCategory(newCategory)
                // Refresh data
                loadCategories()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
    
    fun clearAndResetCategories() {
        viewModelScope.launch {
            try {
                // Clear all categories and reset defaults
                categoryRepository.clearAndReset()
                // Refresh data
                loadCategories()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
    
    fun removeDuplicates() {
        viewModelScope.launch {
            try {
                categoryRepository.removeDuplicateCategories()
                // Refresh data
                loadCategories()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
} 