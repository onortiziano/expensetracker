package it.ciano.expensetracker.data.ocr

data class ParsedReceipt(
    val amount: Double?,
    val date: Long?,
    val title: String?,
    val suggestedCategoryName: String?
)
