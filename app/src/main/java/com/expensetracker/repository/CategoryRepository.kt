package com.expensetracker.repository

import com.expensetracker.data.dao.CategoryDao
import com.expensetracker.data.entity.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()
    
    fun getDefaultCategories(): Flow<List<Category>> = categoryDao.getDefaultCategories()
    
    fun getCustomCategories(): Flow<List<Category>> = categoryDao.getCustomCategories()
    
    suspend fun getCategoryById(categoryId: String): Category? = 
        categoryDao.getCategoryById(categoryId)
    
    suspend fun getCategoryByName(name: String): Category? = 
        categoryDao.getCategoryByName(name)
    
    suspend fun insertCategory(category: Category) = categoryDao.insertCategory(category)
    
    suspend fun insertDefaultCategories() {
        val defaultCategories = listOf(
            Category(name = "Food & Dining", color = "#FF9800", icon = "restaurant", isDefault = true),
            Category(name = "Groceries", color = "#4CAF50", icon = "shopping_cart", isDefault = true),
            Category(name = "Transport", color = "#2196F3", icon = "directions_car", isDefault = true),
            Category(name = "Shopping", color = "#E91E63", icon = "shopping_bag", isDefault = true),
            Category(name = "Entertainment", color = "#9C27B0", icon = "movie", isDefault = true),
            Category(name = "Bills & Utilities", color = "#607D8B", icon = "receipt", isDefault = true),
            Category(name = "Healthcare", color = "#F44336", icon = "local_hospital", isDefault = true),
            Category(name = "Education", color = "#3F51B5", icon = "school", isDefault = true),
            Category(name = "Investment", color = "#00BCD4", icon = "trending_up", isDefault = true),
            Category(name = "Recurring", color = "#7E57C2", icon = "autorenew", isDefault = true),
            Category(name = "Transfer", color = "#FFC107", icon = "swap_horiz", isDefault = true)
        )

        // Ensure each default exists without duplicating what's already there.
        for (category in defaultCategories) {
            if (categoryDao.getCategoryByName(category.name) == null) {
                categoryDao.insertCategory(category)
            }
        }
    }

    /** Deprecated default categories to remove during standardization. */
    private val deprecatedDefaults = listOf("Fuel", "Transportation", "People", "Salary", "Refund", "Other")

    /** Remove deprecated default categories (merged/renamed/no-longer-needed). Idempotent. */
    suspend fun standardizeCategories() {
        deprecatedDefaults.forEach { categoryDao.deleteDefaultByName(it) }
    }
    
    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)
    
    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)
    
    suspend fun deleteCustomCategoryById(categoryId: String) = 
        categoryDao.deleteCustomCategoryById(categoryId)
    
    suspend fun clearAndReset() {
        categoryDao.clearAllCategories()
        insertDefaultCategories()
    }
    
    suspend fun removeDuplicateCategories() {
        val allCategories = getAllCategories().first()
        val uniqueCategories = mutableMapOf<String, Category>()
        val duplicates = mutableListOf<Category>()
        
        // Find duplicates - keep the first occurrence of each name
        for (category in allCategories) {
            if (uniqueCategories.containsKey(category.name)) {
                duplicates.add(category)
            } else {
                uniqueCategories[category.name] = category
            }
        }
        
        // Delete duplicates
        for (duplicate in duplicates) {
            categoryDao.deleteCategory(duplicate)
        }
    }
} 