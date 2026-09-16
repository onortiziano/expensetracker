package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.generation.RecurringTransactionGenerator
import it.ciano.expensetracker.data.model.RecurringTransaction
import it.ciano.expensetracker.data.model.RecurringTransactionWithTags
import it.ciano.expensetracker.data.reminder.RecurringReminderScheduler
import it.ciano.expensetracker.data.repository.RecurringTransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class RecurringTransactionViewModel(
    application: Application,
    private val repository: RecurringTransactionRepository
) : AndroidViewModel(application) {

    private val reminderScheduler = RecurringReminderScheduler(application)

    val recurringWithTags: StateFlow<List<RecurringTransactionWithTags>> =
        repository.getAllRecurringWithTags()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- STATO FORM ---
    private val _editId = MutableStateFlow<Int?>(null)
    val editId: StateFlow<Int?> = _editId.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _type = MutableStateFlow("EXPENSE")
    val type: StateFlow<String> = _type.asStateFlow()

    private val _categoryId = MutableStateFlow(0)
    val categoryId: StateFlow<Int> = _categoryId.asStateFlow()

    private val _frequency = MutableStateFlow("MONTHLY")
    val frequency: StateFlow<String> = _frequency.asStateFlow()

    private val _startDate = MutableStateFlow(0L)
    val startDate: StateFlow<Long> = _startDate.asStateFlow()

    private val _endDateEnabled = MutableStateFlow(false)
    val endDateEnabled: StateFlow<Boolean> = _endDateEnabled.asStateFlow()

    private val _endDate = MutableStateFlow(0L)
    val endDate: StateFlow<Long> = _endDate.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private val _selectedTags = MutableStateFlow(setOf<Int>())
    val selectedTags: StateFlow<Set<Int>> = _selectedTags.asStateFlow()

    // --- UPDATER ---
    fun updateTitle(v: String) { _title.value = v }
    fun updateAmount(v: String) { _amount.value = v }
    fun updateType(v: String) { _type.value = v }
    fun updateCategory(id: Int) { _categoryId.value = id }
    fun updateFrequency(v: String) { _frequency.value = v }
    fun updateStartDate(v: Long) { _startDate.value = v }
    fun setEndDateEnabled(enabled: Boolean) {
        _endDateEnabled.value = enabled
        if (!enabled) _endDate.value = 0L
    }
    fun updateEndDate(v: Long) { _endDate.value = v }
    fun updateNote(v: String) { _note.value = v }
    fun toggleTag(tagId: Int) {
        val current = _selectedTags.value
        _selectedTags.value = if (tagId in current) current - tagId else current + tagId
    }

    fun startNew() {
        _editId.value = null
        resetForm()
    }

    fun startEdit(item: RecurringTransactionWithTags) {
        val r = item.recurring
        _editId.value = r.id
        _title.value = r.title
        _amount.value = r.amount.toString()
        _type.value = r.type
        _categoryId.value = r.categoryId
        _frequency.value = r.frequency
        _startDate.value = r.startDate
        _endDateEnabled.value = r.endDate != null
        _endDate.value = r.endDate ?: 0L
        _note.value = r.note
        _selectedTags.value = item.tags.map { it.tagId }.toSet()
    }

    fun save(onSaved: () -> Unit) {
        val normalizedAmount = _amount.value.replaceFirst(',', '.')
        val amountValue = normalizedAmount.toDoubleOrNull() ?: 0.0
        if (_title.value.isBlank() || amountValue <= 0.0 || _categoryId.value == 0) return
        val start = _startDate.value
        if (start == 0L) return
        if (_endDateEnabled.value && _endDate.value != 0L && _endDate.value < start) return

        val existingId = _editId.value
        val snapshotNextDue = existingId?.let { id ->
            recurringWithTags.value.firstOrNull { it.recurring.id == id }?.recurring?.nextDueDate
        }

        val recurring = RecurringTransaction(
            id = existingId ?: 0,
            title = _title.value,
            amount = amountValue,
            type = _type.value,
            categoryId = _categoryId.value,
            frequency = _frequency.value,
            startDate = start,
            endDate = if (_endDateEnabled.value) _endDate.value else null,
            nextDueDate = snapshotNextDue ?: start,
            note = _note.value,
            isActive = true,
            lastGeneratedDate = existingId?.let { id ->
                recurringWithTags.value.firstOrNull { it.recurring.id == id }?.recurring?.lastGeneratedDate
            }
        )

        viewModelScope.launch(Dispatchers.IO) {
            if (existingId != null) {
                repository.updateRecurring(recurring, _selectedTags.value)
            } else {
                repository.insertRecurring(recurring, _selectedTags.value)
            }
            rescheduleReminders()
        }
        onSaved()
    }

    fun setActive(item: RecurringTransactionWithTags, active: Boolean) {
        val updated = item.recurring.copy(isActive = active)
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateRecurring(updated, item.tags.map { it.tagId }.toSet())
            if (active) rescheduleReminders() else reminderScheduler.cancelReminder(updated.id)
        }
    }

    fun delete(item: RecurringTransactionWithTags) {
        val id = item.recurring.id
        viewModelScope.launch(Dispatchers.IO) {
            reminderScheduler.cancelReminder(id)
            repository.deleteRecurring(item.recurring)
        }
    }

    fun runPendingGeneration() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val due = repository.getDueTemplates(today)
            if (due.isEmpty()) return@launch
            val tagMap = due.associate { it.id to repository.getTagIds(it.id) }
            val result = RecurringTransactionGenerator.generate(due, tagMap, today)
            repository.applyGeneration(result.occurrences, result.updated)
            rescheduleReminders()
        }
    }

    fun rescheduleReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = recurringWithTags.value.map { it.recurring }
            reminderScheduler.rescheduleAll(all)
        }
    }

    private fun resetForm() {
        _title.value = ""
        _amount.value = ""
        _type.value = "EXPENSE"
        _categoryId.value = 0
        _frequency.value = "MONTHLY"
        _startDate.value = 0L
        _endDateEnabled.value = false
        _endDate.value = 0L
        _note.value = ""
        _selectedTags.value = emptySet()
    }
}