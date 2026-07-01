package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.data.repository.TagRepository

class TagViewModel(private val tagRepository: TagRepository) : ViewModel() {
    val allTags: Flow<List<Tag>> = tagRepository.allTags

    fun addTag(name: String, color: Int) {
        viewModelScope.launch {
            tagRepository.insert(Tag(name = name, color = color))
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            tagRepository.delete(tag)
        }
    }
}
