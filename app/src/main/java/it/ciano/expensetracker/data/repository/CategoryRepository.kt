package it.ciano.expensetracker.data.repository

import it.ciano.expensetracker.data.dao.CategoryDao
import it.ciano.expensetracker.data.model.Category
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val categoryDao: CategoryDao) {

    // Restituisce tutte le categorie in ordine alfabetico
    fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories()
    }

    // Restituisce solo le categorie principali
    fun getMainCategories(): Flow<List<Category>> {
        return categoryDao.getMainCategories()
    }

    // Restituisce le sottocategorie di una categoria specifica
    fun getSubCategories(parentId: Int): Flow<List<Category>> {
        return categoryDao.getSubCategories(parentId)
    }

    // Aggiunge o aggiorna una categoria
    suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(category)
    }

    // Elimina una categoria
    suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category)
    }
}