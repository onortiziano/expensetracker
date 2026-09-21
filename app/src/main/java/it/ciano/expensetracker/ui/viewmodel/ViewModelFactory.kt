package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.repository.*

class ViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    
    private val database = AppDatabase.getDatabase(application)
    private val transactionRepository = TransactionRepository(database, database.transactionDao(), database.transactionTagDao(), database.tagDao())
    private val categoryRepository = CategoryRepository(database.categoryDao())
    private val tagRepository = TagRepository(database.tagDao())
    private val recurringTransactionRepository = RecurringTransactionRepository(
        database = database,
        recurringDao = database.recurringTransactionDao(),
        recurringTagDao = database.recurringTransactionTagDao(),
        transactionDao = database.transactionDao(),
        transactionTagDao = database.transactionTagDao(),
        tagDao = database.tagDao()
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(TransactionViewModel::class.java) -> 
                TransactionViewModel(transactionRepository) as T
            modelClass.isAssignableFrom(CategoryViewModel::class.java) -> 
                CategoryViewModel(categoryRepository) as T
            modelClass.isAssignableFrom(MainViewModel::class.java) -> 
                MainViewModel(application) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> 
                SettingsViewModel(application) as T
            modelClass.isAssignableFrom(TagViewModel::class.java) -> 
                TagViewModel(tagRepository) as T
            modelClass.isAssignableFrom(AnalyticsViewModel::class.java) -> 
                AnalyticsViewModel(application) as T
            modelClass.isAssignableFrom(RecurringTransactionViewModel::class.java) ->
                RecurringTransactionViewModel(application, recurringTransactionRepository) as T
            modelClass.isAssignableFrom(ImportTransactionsViewModel::class.java) ->
                ImportTransactionsViewModel(application, transactionRepository, categoryRepository) as T
            modelClass.isAssignableFrom(CreditsViewModel::class.java) ->
                CreditsViewModel(transactionRepository) as T
            else -> throw IllegalArgumentException("Classe ViewModel sconosciuta: ${modelClass.name}")
        }
    }
}
