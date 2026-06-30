package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.TransactionWithTags
import it.ciano.expensetracker.data.model.TransactionTag
import it.ciano.expensetracker.data.repository.TransactionRepository
import it.ciano.expensetracker.data.repository.TagRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers

class TransactionViewModel(
    private val repository: TransactionRepository,
    private val tagRepository: TagRepository
) : ViewModel() {
    
    private val deleteChannel = Channel<Transaction>(Channel.UNLIMITED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (transaction in deleteChannel) {
                try {
                    val deletedCount = repository.deleteTransaction(transaction)
                    if (deletedCount == 0) {
                        android.util.Log.e("TRANSACTION_VM", "FALLIMENTO: Il record con ID ${transaction.transactionId} non è stato trovato nel DB.")
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("TRANSACTION_VM", "ERRORE CRITICO: ${t.message}")
                }
            }
        }
    }

    val allTransactions: StateFlow<List<Transaction>> = repository.getAllTransactions()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    val allTransactionsWithTags: StateFlow<List<TransactionWithTags>> = repository.getAllTransactionsWithTags()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    val totalIncome: StateFlow<Double> = repository.getTotalIncome()
        .map { it ?: 0.0 }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0.0)

    val totalExpenses: StateFlow<Double> = repository.getTotalExpenses()
        .map { it ?: 0.0 }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0.0)

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

    private val _selectedTags = MutableStateFlow<Set<Tag>>(emptySet())
    val selectedTags: StateFlow<Set<Tag>> = _selectedTags.asStateFlow()

    fun updateTitle(value: String) { _title.value = value }
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

    fun toggleTagSelection(tag: Tag) {
        val current = _selectedTags.value.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        _selectedTags.value = current
    }

    fun loadTransaction(transaction: Transaction, allCategories: List<Category>, tags: List<Tag>) {
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
        _selectedTags.value = tags.toSet()
    }

    fun addTransaction(transaction: Transaction, tags: Set<Tag>) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
            // Implementazione collegamento tag via TagRepository (da fare in repository)
            resetForm()
        }
    }

    fun updateTransaction(transaction: Transaction, tags: Set<Tag>) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            // Implementazione aggiornamento tag
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
