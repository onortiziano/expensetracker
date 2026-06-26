package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {

    // --- DATI PERSISTENTI ---
    val allTransactions: StateFlow<List<Transaction>> = repository.getAllTransactions()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    val totalIncome: StateFlow<Double> = repository.getTotalIncome()
        .map { it ?: 0.0 }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0.0)

    val totalExpenses: StateFlow<Double> = repository.getTotalExpenses()
        .map { it ?: 0.0 }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0.0)

    // --- STATO UI PER AGGIUNTA/MODIFICA (Sopravvive alla rotazione) ---
    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note

    private val _type = MutableStateFlow("EXPENSE")
    val type: StateFlow<String> = _type

    private val _selectedMainCategoryId = MutableStateFlow(0)
    val selectedMainCategoryId: StateFlow<Int> = _selectedMainCategoryId

    private val _selectedSubCategoryId = MutableStateFlow(0)
    val selectedSubCategoryId: StateFlow<Int> = _selectedSubCategoryId

    // --- FUNZIONI AGGIORNAMENTO STATO ---
    fun updateAmount(value: String) { _amount.value = value }
    fun updateNote(value: String) { _note.value = value }
    fun updateType(value: String) { _type.value = value }
    fun updateMainCategory(id: Int) { 
        _selectedMainCategoryId.value = id 
        _selectedSubCategoryId.value = 0 
    }
    fun updateSubCategory(id: Int) { _selectedSubCategoryId.value = id }

    // --- OPERAZIONI DB ---
    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
            resetForm() // Pulisce il form dopo il salvataggio
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.updateTransaction(transaction) }
    }

    private fun resetForm() {
        _amount.value = ""
        _note.value = ""
        _type.value = "EXPENSE"
        _selectedMainCategoryId.value = 0
        _selectedSubCategoryId.value = 0
    }
}
