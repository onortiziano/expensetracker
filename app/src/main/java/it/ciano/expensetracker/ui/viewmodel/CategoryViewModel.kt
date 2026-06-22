package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.repository.CategoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CategoryViewModel(private val repository: CategoryRepository) : ViewModel() {

    // 1. Tutte le categorie (per la pagina Settings)
    val allCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 2. Solo le categorie principali (per il menu a tendina dell'inserimento)
    val mainCategories: StateFlow<List<Category>> = repository.getMainCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 3. Sottocategorie di un padre specifico
    // Nota: qui non usiamo StateFlow perché il parentId cambia in base a cosa clicca l'utente
    fun getSubCategories(parentId: Int): Flow<List<Category>> {
        return repository.getSubCategories(parentId)
    }

    // Aggiungi o aggiorna una categoria
    fun addCategory(category: Category) {
        viewModelScope.launch {
            repository.insertCategory(category)
        }
    }

    // Elimina una categoria
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }
}