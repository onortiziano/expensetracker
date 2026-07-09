package it.ciano.expensetracker.data.dao

import androidx.room.*
import it.ciano.expensetracker.data.model.GlobalBudget
import kotlinx.coroutines.flow.Flow

@Dao
interface GlobalBudgetDao {
    @Query("SELECT * FROM global_budgets WHERE month = :month AND year = :year LIMIT 1")
    fun getBudgetForMonth(month: Int, year: Int): Flow<GlobalBudget?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: GlobalBudget): Long

    @Query("SELECT * FROM global_budgets ORDER BY year DESC, month DESC")
    fun getAllBudgets(): Flow<List<GlobalBudget>>

    @Query("DELETE FROM global_budgets WHERE month = :month AND year = :year")
    suspend fun deleteBudgetForMonth(month: Int, year: Int)
}