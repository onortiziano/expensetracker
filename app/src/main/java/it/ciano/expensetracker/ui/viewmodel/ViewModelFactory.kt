package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.repository.*

class ViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    
    // Creiamo le istanze dei repository una sola volta per l'intera app
    private val database = AppDatabase.getDatabase(application)
    private val transactionRepository = TransactionRepository(database.transactionDao())
    private val categoryRepository = CategoryRepository(database.categoryDao())
    private val budgetRepository = BudgetRepository(database.budgetDao())

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(TransactionViewModel::class.java) -> 
                TransactionViewModel(transactionRepository) as T
            modelClass.isAssignableFrom(CategoryViewModel::class.java) -> 
                CategoryViewModel(categoryRepository) as T
            modelClass.isAssignableFrom(BudgetViewModel::class.java) -> 
                BudgetViewModel(budgetRepository) as T
            modelClass.isAssignableFrom(MainViewModel::class.java) -> 
                MainViewModel() as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> 
                SettingsViewModel(application) as T
            else -> throw IllegalArgumentException("Classe ViewModel sconosciuta: ${modelClass.name}")
        }
    }
}