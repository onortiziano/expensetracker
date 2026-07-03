package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.TransactionWithTags
import it.ciano.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {
    
    private val deleteChannel = Channel<Transaction>(Channel.UNLIMITED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (transaction in deleteChannel) {
                try {
                    repository.deleteTransaction(transaction)
                } catch (t: Throwable) {
                    android.util.Log.e("TRANSACTION_VM", "ERRORE: ${t.message}")
                }
            }
        }
    }

    // --- DATI PERSISTENTI ---
    // Usiamo TransactionWithTags per l'elenco in Home e i Dettagli
    val transactionsWithTags: StateFlow<List<TransactionWithTags>> = repository.getAllTransactionsWithTags()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    val totalIncome: StateFlow<Double> = repository.getTotalIncome()
        .map { it ?: 0.0 }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0.0)

    val totalExpenses: StateFlow<Double> = repository.getTotalExpenses()
        .map { it ?: 0.0 }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0.0)

    // --- STATO UI PER MODIFICA/AGGIUNTA ---
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

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

    private val _selectedTags = MutableStateFlow(setOf<Int>())
    val selectedTags: StateFlow<Set<Int>> = _selectedTags

    // --- FUNZIONI DI AGGIORNAMENTO ---
    fun updateTitle(value: String) { _title.value = value }
    fun updateAmount(value: String) { _amount.value = value }
    fun updateNote(value: String) { _note.value = value }
    fun updateType(value: String) { _type.value = value }
    fun updateMainCategory(id: Int) { 
        _selectedMainCategoryId.value = id 
        _selectedSubCategoryId.value = 0 
    }
    fun updateSubCategory(id: Int) { _selectedSubCategoryId.value = id }
    fun toggleTag(tagId: Int) {
        val current = _selectedTags.value
        val next = if (current.contains(tagId)) current - tagId else current + tagId
        _selectedTags.value = next
        android.util.Log.d("TAG_VM", "Toggled tag $tagId. New set: $next")
    }

    fun loadTransaction(item: TransactionWithTags, allCategories: List<Category>) {
        val transaction = item.transaction
        _title.value = transaction.title
        _amount.value = transaction.amount.toString()
        _note.value = transaction.note
        _type.value = transaction.type
        
        val category = allCategories.find { it.id == transaction.categoryId }
        if (category != null && category.parentCategoryId != null) {
            _selectedMainCategoryId.value = category.parentCategoryId
            _selectedSubCategoryId.value = category.id
        } else {
            _selectedMainCategoryId.value = transaction.categoryId
            _selectedSubCategoryId.value = 0
        }
        _selectedTags.value = item.tags.map { it.tagId }.toSet()
    }

    fun addTransaction(transaction: Transaction, tagIds: Set<Int>) {
        viewModelScope.launch {
            repository.insertTransaction(transaction, tagIds)
            // Nota: Per salvare i tag servirebbe l'ID generato. 
            // Implementeremo la logica di recupero ID nel ViewModel o aggiorneremo il DAO.
            resetForm()
        }
    }

    fun updateTransaction(transaction: Transaction, tagIds: Set<Int>) {
        viewModelScope.launch {
            repository.updateTransaction(transaction, tagIds)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            deleteChannel.send(transaction)
        }
    }

    fun resetForm() {
        _title.value = ""
        _amount.value = ""
        _note.value = ""
        _type.value = "EXPENSE"
        _selectedMainCategoryId.value = 0
        _selectedSubCategoryId.value = 0
        _selectedTags.value = emptySet()
    }
}
