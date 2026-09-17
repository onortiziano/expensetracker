package it.ciano.expensetracker.data.generation

import it.ciano.expensetracker.data.model.RecurringTransaction

data class GeneratedOccurrence(
    val title: String,
    val amount: Double,
    val type: String,
    val categoryId: Int,
    val note: String,
    val date: Long,
    val tagIds: Set<Int>
)

data class GenerationResult(
    val occurrences: List<GeneratedOccurrence>,
    val updated: List<RecurringTransaction>
)