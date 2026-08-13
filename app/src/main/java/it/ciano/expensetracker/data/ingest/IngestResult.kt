package it.ciano.expensetracker.data.ingest

sealed class IngestResult {
    data class Success(val expense: ParsedExpense) : IngestResult()
    data class Error(val error: IngestError) : IngestResult()
}

enum class IngestError { MISSING_AMOUNT, INVALID_AMOUNT }

data class ParsedExpense(
    val amount: Double,
    val categoryName: String,
    val title: String,
    val date: Long
)
