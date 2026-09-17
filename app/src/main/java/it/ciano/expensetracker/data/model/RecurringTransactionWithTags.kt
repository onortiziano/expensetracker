package it.ciano.expensetracker.data.model

data class RecurringTransactionWithTags(
    val recurring: RecurringTransaction,
    val tags: List<Tag>
)
