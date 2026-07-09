package it.ciano.expensetracker.data.repository

import android.app.Application
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.dao.CategoryBudgetDao
import it.ciano.expensetracker.data.model.CategoryBudget
import kotlinx.coroutines.flow.Flow

class CategoryBudgetRepository(application: Application) {
    private val categoryBudgetDao = AppDatabase.getDatabase(application).categoryBudgetDao()

    fun getBudgetForCategoryMonth(categoryId: Int, month: Int, year: Int): Flow<CategoryBudget?> {
        return categoryBudgetDao.getBudgetForCategoryMonth(categoryId, month, year)
    }

    fun getAllBudgetsForMonth(month: Int, year: Int): Flow<List<CategoryBudget>> {
        return categoryBudgetDao.getAllBudgetsForMonth(month, year)
    }

    fun getTotalBudgetForMonth(month: Int, year: Int): Flow<Double?> {
        return categoryBudgetDao.getTotalBudgetForMonth(month, year)
    }

    suspend fun saveBudget(budget: CategoryBudget) {
        categoryBudgetDao.insertBudget(budget)
    }

    suspend fun deleteBudget(categoryId: Int, month: Int, year: Int) {
        categoryBudgetDao.deleteBudget(categoryId, month, year)
    }
}