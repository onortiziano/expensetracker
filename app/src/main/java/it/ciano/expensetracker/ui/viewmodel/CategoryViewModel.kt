package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.repository.CategoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CategoryViewModel(private val repository: CategoryRepository) : ViewModel() {

    // 1. Tutte le categorie
    val allCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 2. Solo le categorie principali (Padri)
    val mainCategories: StateFlow<List<Category>> = repository.getMainCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 3. MAPPA ID -> NOME (Spostata qui per coerenza e performance)
    val categoryMap: StateFlow<Map<Int, String>> = allCategories.map { list ->
        list.associate { it.id to it.name }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    suspend fun addCategory(category: Category): Long {
        return repository.insertCategory(category)
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }
}
