package it.ciano.expensetracker.data.repository

import it.ciano.expensetracker.data.dao.BudgetDao
import it.ciano.expensetracker.data.model.Budget
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val budgetDao: BudgetDao) {

    // Recupera il budget per un mese e un anno specifici
    // Restituisce un Flow, quindi se l'utente cambia il budget nei settings,
    // la Home si aggiornerà all'istante.
    fun getBudgetForMonth(month: Int, year: Int): Flow<Budget?> {
        return budgetDao.getBudgetForMonth(month, year)
    }

    // Imposta o aggiorna il budget mensile
    suspend fun insertBudget(budget: Budget) {
        budgetDao.insertBudget(budget)
    }

    // Elimina il budget (per tornare allo stato "non impostato")
    suspend fun deleteBudget(budget: Budget) {
        budgetDao.deleteBudget(budget)
    }
}