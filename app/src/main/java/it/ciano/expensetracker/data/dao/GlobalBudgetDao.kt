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
}