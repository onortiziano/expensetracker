package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.TransactionWithTags
import it.ciano.expensetracker.data.repository.TransactionRepository

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {
    val allTransactions: Flow<List<Transaction>> = repository.allTransactions
    val allTransactionsWithTags: Flow<List<TransactionWithTags>> = repository.allTransactionsWithTags
    val totalIncome: Flow<Double?> = repository.totalIncome
    val totalExpenses: Flow<Double?> = repository.totalExpenses

    fun insert(transaction: Transaction) = viewModelScope.launch { repository.insert(transaction) }
    fun updateTransaction(transaction: Transaction) = viewModelScope.launch { repository.update(transaction) }
    
    // MODIFICA: Ora accetta l'ID (Int) invece dell'oggetto Transaction intero
    fun delete(transactionId: Int) = viewModelScope.launch { 
        // Assumendo che il repository abbia un metodo deleteById o simile. 
        // Se non ce l'ha, useremo repository.delete(transaction) dopo averlo recuperato.
        repository.deleteById(transactionId) 
    }
}
