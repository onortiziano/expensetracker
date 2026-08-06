package it.ciano.expensetracker.data.repository

import android.app.Application
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.dao.GlobalBudgetDao
import it.ciano.expensetracker.data.model.GlobalBudget
import kotlinx.coroutines.flow.Flow

class GlobalBudgetRepository(application: Application) {
    private val globalBudgetDao = AppDatabase.getDatabase(application).globalBudgetDao()

    fun getBudgetForMonth(month: Int, year: Int): Flow<GlobalBudget?> {
        return globalBudgetDao.getBudgetForMonth(month, year)
    }

    suspend fun saveBudget(budget: GlobalBudget) {
        globalBudgetDao.insertBudget(budget)
    }
}