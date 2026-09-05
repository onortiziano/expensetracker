package it.ciano.expensetracker.data.ocr

data class ParsedReceipt(
    val amount: Double?,
    val date: Long?,
    val merchant: String?,
    val suggestedCategoryName: String?
)