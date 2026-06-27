package it.ciano.expensetracker.data.dao

import androidx.room.*
import it.ciano.expensetracker.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE parentCategoryId = :parentId ORDER BY name ASC")
    fun getSubCategories(parentId: Int): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE parentCategoryId IS NULL ORDER BY name ASC")
    fun getMainCategories(): Flow<List<Category>>
}