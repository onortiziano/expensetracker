package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.repository.TagRepository
import it.ciano.expensetracker.data.model.Tag
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TagViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TagRepository(application)

    val allTags: StateFlow<List<Tag>> = repository.getAllTags()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    fun addTag(name: String, colorHex: String = "#808080") {
        viewModelScope.launch {
            val colorInt = try {
                Color.parseColor(colorHex)
            } catch (e: Exception) {
                0xFF6200EE.toInt()
            }
            repository.insertTag(Tag(name = name, color = colorInt))
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }
}
