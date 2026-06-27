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

    // --- STATO UI PER MODIFICA/AGGIUNTA (Sopravvive alla rotazione) ---
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

    // --- FUNZIONI DI AGGIORNAMENTO ---
    fun updateAmount(value: String) { _amount.value = value }
    fun updateNote(value: String) { _note.value = value }
    fun updateType(value: String) { _type.value = value }
    fun updateMainCategory(id: Int) { 
        _selectedMainCategoryId.value = id 
        _selectedSubCategoryId.value = 0 
    }
    fun updateSubCategory(id: Int) { _selectedSubCategoryId.value = id }

    fun updateCategoryPair(mainId: Int, subId: Int) {
        _selectedMainCategoryId.value = mainId
        _selectedSubCategoryId.value = subId
    }

    // Carica i dati di una transazione nel ViewModel
    fun loadTransaction(transaction: Transaction, allCategories: List<Category>) {
        _amount.value = transaction.amount.toString()
        _note.value = transaction.note
        _type.value = transaction.type
        
        val category = allCategories.find { it.id == transaction.categoryId }
        if (category != null && category.parentCategoryId != null) {
            // È una sottocategoria: imposta sia il padre che il figlio
            _selectedMainCategoryId.value = category.parentCategoryId!!
            _selectedSubCategoryId.value = category.id
        } else {
            // È una categoria principale (o senza categoria)
            _selectedMainCategoryId.value = transaction.categoryId
            _selectedSubCategoryId.value = 0
        }
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
            resetForm()
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun resetForm() {
        _amount.value = ""
        _note.value = ""
        _type.value = "EXPENSE"
        _selectedMainCategoryId.value = 0
        _selectedSubCategoryId.value = 0
    }
}
