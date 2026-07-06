package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.repository.TagRepository
import it.ciano.expensetracker.data.model.Tag
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TagViewModel(private val repository: TagRepository) : ViewModel() {
    
    val allTags: StateFlow<List<Tag>> = repository.getAllTags()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    fun addTag(name: String, color: Int) {
        viewModelScope.launch {
            repository.insertTag(Tag(name = name, color = color))
        }
    }

    fun updateTag(tag: Tag) {
        viewModelScope.launch {
            repository.updateTag(tag)
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }
}
