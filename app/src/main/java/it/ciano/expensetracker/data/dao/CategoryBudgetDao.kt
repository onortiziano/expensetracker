package it.ciano.expensetracker.data.dao

import androidx.room.*
import it.ciano.expensetracker.data.model.CategoryBudget
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: CategoryBudget)

    @Query("SELECT * FROM category_budgets WHERE categoryId = :categoryId AND month = :month AND year = :year LIMIT 1")
    fun getBudgetForCategoryMonth(categoryId: Int, month: Int, year: Int): Flow<CategoryBudget?>

    @Query("SELECT * FROM category_budgets WHERE month = :month AND year = :year")
    fun getAllBudgetsForMonth(month: Int, year: Int): Flow<List<CategoryBudget>>

    @Query("SELECT SUM(amount) FROM category_budgets WHERE month = :month AND year = :year")
    fun getTotalBudgetForMonth(month: Int, year: Int): Flow<Double?>

    @Query("DELETE FROM category_budgets WHERE categoryId = :categoryId AND month = :month AND year = :year")
    suspend fun deleteBudget(categoryId: Int, month: Int, year: Int)
}